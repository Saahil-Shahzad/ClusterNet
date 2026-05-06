package kmeans;

import java.io.*;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

// Master / Coordinator process for distributed K-Means
// Orchestration entrypoint (annotated by Saahil for commit)
public class Master {

    // Configuration
    private final int   numWorkers;
    private final int   listenPort;
    private final int   N, K, F, maxIter;
    private final float epsilon;

    // Shared accumulation (protected by aggLock)
    private double[][]    aggSums;
    private int[]         aggCounts;
    private final ReentrantLock aggLock = new ReentrantLock();
    private CountDownLatch barrier;

    public Master(int numWorkers, int listenPort, int N, int K, int F, int maxIter, float epsilon) {
        this.numWorkers = numWorkers;
        this.listenPort = listenPort;
        this.N          = N;
        this.K          = K;
        this.F          = F;
        this.maxIter    = maxIter;
        this.epsilon    = epsilon;
    }

    // Main execution

    public float[][] run() throws Exception {
        System.out.printf("[Master] Distributed K-Means  N=%d  K=%d  F=%d  W=%d  maxIter=%d%n",
                N, K, F, numWorkers, maxIter);

        // Open server socket, accept W workers BEFORE generating data
        // Workers retry connection for 30 s. At large N, data generation can
        // take longer than that, so the socket must be open first. Workers
        // connect and then block on in.readInt() waiting for INIT — harmless.
        Socket[]          workerSockets = new Socket[numWorkers];
        DataInputStream[] ins           = new DataInputStream[numWorkers];
        DataOutputStream[] outs         = new DataOutputStream[numWorkers];

        try (ServerSocket server = new ServerSocket(listenPort)) {
            System.out.printf("[Master] Listening on port %d, waiting for %d worker(s)...%n",
                    listenPort, numWorkers);

            for (int w = 0; w < numWorkers; w++) {
                workerSockets[w] = server.accept();
                workerSockets[w].setTcpNoDelay(true);
                ins[w]  = new DataInputStream(
                        new BufferedInputStream(workerSockets[w].getInputStream(), 1 << 20));
                outs[w] = new DataOutputStream(
                        new BufferedOutputStream(workerSockets[w].getOutputStream(), 1 << 20));
                System.out.printf("[Master] Worker %d connected from %s%n",
                        w + 1, workerSockets[w].getRemoteSocketAddress());
            }
        } // ServerSocket closed here; worker sockets remain open

        // Generate data + initial centroids (same seeds as sequential baseline)
        System.out.println("[Master] Generating dataset...");
        float[][] data      = DataGenerator.generate(N, F, 12345L);
        float[][] centroids = DataGenerator.initCentroids(data, K, 42L);
        System.out.println("[Master] Dataset generated.");

        // Send INIT messages with data partitions
        int baseN = N / numWorkers;
        int[] workerN = new int[numWorkers];
        for (int w = 0; w < numWorkers - 1; w++) workerN[w] = baseN;
        workerN[numWorkers - 1] = N - baseN * (numWorkers - 1);   // last worker gets remainder

        int offset = 0;
        for (int w = 0; w < numWorkers; w++) {
            int nw = workerN[w];
            // Payload: workerId(4) + nWorker(4) + K(4) + F(4) + nw*F floats(4 each)
            int payloadLen = 4 + 4 + 4 + 4 + nw * F * 4;
            outs[w].writeInt(MessageType.INIT);
            outs[w].writeInt(payloadLen);
            outs[w].writeInt(w + 1);        // workerId (1-indexed)
            outs[w].writeInt(nw);
            outs[w].writeInt(K);
            outs[w].writeInt(F);
            for (int i = offset; i < offset + nw; i++) {
                for (int f = 0; f < F; f++) {
                    outs[w].writeFloat(data[i][f]);
                }
            }
            outs[w].flush();
            offset += nw;
            System.out.printf("[Master] INIT sent to Worker %d  (rows %d..%d)%n",
                    w + 1, offset - nw, offset - 1);
        }

        // Wait for ready signals (HEARTBEAT iter=0 from each worker)
        System.out.println("[Master] Waiting for worker ready signals...");
        for (int w = 0; w < numWorkers; w++) {
            workerSockets[w].setSoTimeout(30_000);
            int msg = ins[w].readInt();
            if (msg == MessageType.HEARTBEAT) {
                ins[w].readInt(); ins[w].readInt(); // payloadLen + iterNum
            }
            workerSockets[w].setSoTimeout(0);
            System.out.printf("[Master] Worker %d ready%n", w + 1);
        }

        // Iteration loop
        long totalStart = System.currentTimeMillis();
        int  convergedAt = maxIter;

        for (int iter = 0; iter < maxIter; iter++) {

            // 1. HEARTBEAT round-trip
            for (int w = 0; w < numWorkers; w++) {
                outs[w].writeInt(MessageType.HEARTBEAT);
                outs[w].writeInt(4);
                outs[w].writeInt(iter + 1);
                outs[w].flush();
            }
            for (int w = 0; w < numWorkers; w++) {
                try {
                    workerSockets[w].setSoTimeout(5_000);
                    int msg = ins[w].readInt();
                    if (msg == MessageType.HEARTBEAT) {
                        ins[w].readInt(); ins[w].readInt();
                    }
                    workerSockets[w].setSoTimeout(0);
                } catch (SocketTimeoutException e) {
                    System.err.printf("[Master] WARNING: Worker %d heartbeat timeout at iter %d%n",
                            w + 1, iter + 1);
                }
            }

            // 2. CENTROID_BROADCAST
            int cbPayload = K * F * 4;
            for (int w = 0; w < numWorkers; w++) {
                outs[w].writeInt(MessageType.CENTROID_BROADCAST);
                outs[w].writeInt(cbPayload);
                for (int k = 0; k < K; k++) {
                    for (int f = 0; f < F; f++) {
                        outs[w].writeFloat(centroids[k][f]);
                    }
                }
                outs[w].flush();
            }
            System.out.printf("[Master] Iter %2d: centroids broadcast, awaiting results...%n", iter + 1);

            // 3. BARRIER: collect PARTIAL_RESULT from all workers
            // Reset accumulation buffers
            aggSums   = new double[K][F];
            aggCounts = new int[K];
            barrier   = new CountDownLatch(numWorkers);

            // One reader thread per worker for true concurrent reception
            Thread[] readers = new Thread[numWorkers];
            final int iterFinal = iter;
            for (int w = 0; w < numWorkers; w++) {
                final int wIdx = w;
                readers[w] = new Thread(() -> {
                    try {
                        int msg = ins[wIdx].readInt();
                        if (msg != MessageType.PARTIAL_RESULT) {
                            System.err.printf("[Master] Worker %d sent unexpected msg 0x%X%n",
                                    wIdx + 1, msg);
                            barrier.countDown();
                            return;
                        }
                        /* payloadLen = */ ins[wIdx].readInt();

                        // Read sums and counts
                        double[][] localSums   = new double[K][F];
                        int[]      localCounts = new int[K];
                        for (int k = 0; k < K; k++) {
                            for (int f = 0; f < F; f++) {
                                localSums[k][f] = ins[wIdx].readDouble();
                            }
                        }
                        for (int k = 0; k < K; k++) {
                            localCounts[k] = ins[wIdx].readInt();
                        }

                        // Merge into shared accumulation under lock
                        aggLock.lock();
                        try {
                            for (int k = 0; k < K; k++) {
                                aggCounts[k] += localCounts[k];
                                for (int f = 0; f < F; f++) {
                                    aggSums[k][f] += localSums[k][f];
                                }
                            }
                        } finally {
                            aggLock.unlock();
                        }

                        System.out.printf("[Master] Partial result received from Worker %d (iter %d)%n",
                                wIdx + 1, iterFinal + 1);
                    } catch (IOException e) {
                        System.err.printf("[Master] IO error reading from Worker %d: %s%n",
                                wIdx + 1, e.getMessage());
                    } finally {
                        barrier.countDown();
                    }
                }, "reader-" + (w + 1));
                readers[w].start();
            }

            barrier.await();        // wait until all W workers have reported

            // 4. AGGREGATE: compute new centroids
            float maxShift = 0f;
            for (int k = 0; k < K; k++) {
                if (aggCounts[k] == 0) continue;    // empty cluster: centroid unchanged
                float shift = 0f;
                for (int f = 0; f < F; f++) {
                    float newVal = (float) (aggSums[k][f] / aggCounts[k]);
                    float diff   = newVal - centroids[k][f];
                    shift       += diff * diff;
                    centroids[k][f] = newVal;
                }
                maxShift = Math.max(maxShift, (float) Math.sqrt(shift));
            }

            System.out.printf("[Master] Iter %2d complete  max-shift=%.6f  counts=[", iter + 1, maxShift);
            for (int k = 0; k < Math.min(K, 5); k++)
                System.out.printf("%d%s", aggCounts[k], k < Math.min(K, 5) - 1 ? "," : "");
            System.out.println((K > 5 ? "..." : "") + "]");

            // 5. Convergence check
            boolean converged = maxShift < epsilon || iter == maxIter - 1;
            if (converged) {
                convergedAt = iter + 1;
                System.out.printf("[Master] Converged at iteration %d (shift=%.6f)%n", convergedAt, maxShift);

                for (int w = 0; w < numWorkers; w++) {
                    outs[w].writeInt(MessageType.CONVERGED);
                    outs[w].writeInt(0);
                    outs[w].writeInt(MessageType.SHUTDOWN);
                    outs[w].writeInt(0);
                    outs[w].flush();
                }
                break;
            }
        }

        long totalElapsed = System.currentTimeMillis() - totalStart;

        // Final report
        System.out.println();
        System.out.println("=== Distributed K-Means Result ===");
        System.out.printf("Wall-clock time    : %d ms%n", totalElapsed);
        System.out.printf("Converged at iter  : %d%n", convergedAt);
        System.out.printf("Workers            : %d (each with ForkJoinPool threads)%n", numWorkers);

        double wcss = DataGenerator.computeWCSS(data, centroids);
        System.out.printf("WCSS (quality)     : %.2f%n", wcss);

        System.out.println("Final centroids (first 3):");
        for (int k = 0; k < Math.min(3, K); k++) {
            System.out.printf("  C[%d] = [", k);
            for (int f = 0; f < Math.min(F, 4); f++) {
                System.out.printf("%.3f%s", centroids[k][f], f < Math.min(F, 4) - 1 ? ", " : "");
            }
            System.out.println((F > 4 ? ", ..." : "") + "]");
        }

        // Close sockets
        for (Socket s : workerSockets) {
            try { s.close(); } catch (IOException ignored) {}
        }

        return centroids;
    }

    // Entry point

    // Usage: Master [N] [K] [F] [maxIter]
    public static void main(String[] args) throws Exception {
        int   numWorkers = 3;
        int   listenPort = 9001;
        int   N          = 90_000;
        int   K          = 5;
        int   F          = 16;
        int   maxIter    = 30;
        float epsilon    = 0.01f;

        if (args.length >= 1) N       = Integer.parseInt(args[0]);
        if (args.length >= 2) K       = Integer.parseInt(args[1]);
        if (args.length >= 3) F       = Integer.parseInt(args[2]);
        if (args.length >= 4) maxIter = Integer.parseInt(args[3]);

        System.out.println("=== Distributed K-Means Master ===");
        System.out.printf("Parameters: N=%d  K=%d  F=%d  maxIter=%d  workers=%d  port=%d%n%n",
                N, K, F, maxIter, numWorkers, listenPort);

        new Master(numWorkers, listenPort, N, K, F, maxIter, epsilon).run();
    }
}
