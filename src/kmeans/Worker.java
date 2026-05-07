package kmeans;

import java.io.*;
import java.net.*;
import java.util.concurrent.ForkJoinPool;

public class Worker {

    private final String masterHost;
    private final int    masterPort;
    private float[][] localData;
    private int K, F, workerId;
    private ForkJoinPool pool;
    private volatile float[][] currentCentroids;

    public Worker(String masterHost, int masterPort) {
        this.masterHost = masterHost;
        this.masterPort = masterPort;
    }

    public void run() throws IOException, InterruptedException {
        System.out.printf("[Worker] Connecting to %s:%d...%n", masterHost, masterPort);

        Socket socket = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                socket = new Socket(masterHost, masterPort);
                socket.setTcpNoDelay(true);
                System.out.printf("[Worker] Connected on attempt %d%n", attempt);
                break;
            } catch (IOException e) {
                System.out.printf("[Worker] Attempt %d failed, retrying in 1s...%n", attempt);
                Thread.sleep(1000);
            }
        }
        if (socket == null) throw new IOException("Could not connect to master after 30 attempts");

        DataInputStream  in  = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 1<<20));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 1<<20));

        try {
            // INIT
            int msgType = in.readInt();
            if (msgType != MessageType.INIT)
                throw new IOException("Expected INIT, got " + MessageType.name(msgType));
            in.readInt(); // payloadLen
            workerId    = in.readInt();
            int nWorker = in.readInt();
            K = in.readInt();
            F = in.readInt();
            System.out.printf("[Worker %d] INIT: partition=%d K=%d F=%d%n", workerId, nWorker, K, F);

            localData = new float[nWorker][F];
            for (int i = 0; i < nWorker; i++)
                for (int f = 0; f < F; f++)
                    localData[i][f] = in.readFloat();

            int threads = Math.min(Runtime.getRuntime().availableProcessors(), 8);
            pool = new ForkJoinPool(threads);
            System.out.printf("[Worker %d] ForkJoinPool: %d threads%n", workerId, threads);

            // Ready signal
            out.writeInt(MessageType.HEARTBEAT); out.writeInt(4); out.writeInt(0); out.flush();

            // Main loop
            while (true) {
                int msg = in.readInt();

                if (msg == MessageType.HEARTBEAT) {
                    in.readInt(); int iter = in.readInt();
                    System.out.printf("[Worker %d] HEARTBEAT iter=%d%n", workerId, iter);
                    out.writeInt(MessageType.HEARTBEAT); out.writeInt(4); out.writeInt(iter); out.flush();
                    continue;
                }

                if (msg == MessageType.CENTROID_BROADCAST) {
                    in.readInt(); // payloadLen
                    float[][] centroids = new float[K][F];
                    for (int k = 0; k < K; k++)
                        for (int f = 0; f < F; f++)
                            centroids[k][f] = in.readFloat();
                    currentCentroids = centroids;

                    long t0 = System.currentTimeMillis();
                    AssignTask task = new AssignTask(localData, 0, localData.length, currentCentroids);
                    pool.invoke(task);
                    System.out.printf("[Worker %d] Assignment done in %d ms%n",
                            workerId, System.currentTimeMillis() - t0);

                    int payloadLen = K * F * 8 + K * 4;
                    out.writeInt(MessageType.PARTIAL_RESULT); out.writeInt(payloadLen);
                    for (int k = 0; k < K; k++)
                        for (int f = 0; f < F; f++)
                            out.writeDouble(task.partialSums[k][f]);
                    for (int k = 0; k < K; k++)
                        out.writeInt(task.partialCounts[k]);
                    out.flush();
                    continue;
                }

                if (msg == MessageType.CONVERGED || msg == MessageType.SHUTDOWN) {
                    in.readInt(); // payloadLen
                    System.out.printf("[Worker %d] %s - shutting down%n", workerId, MessageType.name(msg));
                    pool.shutdown();
                    break;
                }

                System.err.printf("[Worker %d] Unknown msg 0x%X%n", workerId, msg);
            }
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
        System.out.printf("[Worker %d] Done%n", workerId);
    }

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int    port = 9001;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        new Worker(host, port).run();
    }
}
