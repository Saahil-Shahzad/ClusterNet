package kmeans;

// Sequential K-Means baseline
public class SequentialKMeans {

    // Run K-Means on the provided dataset
    public static float[][] run(float[][] data, int K, int maxIter, float epsilon) {
        int N = data.length;
        int F = data[0].length;

        float[][] centroids = DataGenerator.initCentroids(data, K, 42L);

        System.out.println("[Sequential] Starting K-Means...");
        System.out.printf("[Sequential] N=%d  F=%d  K=%d  maxIter=%d  eps=%.4f%n",
                N, F, K, maxIter, epsilon);

        for (int iter = 0; iter < maxIter; iter++) {
            // Assignment step: O(N × K × F)
            double[][] sums   = new double[K][F];
            int[]      counts = new int[K];

            for (int i = 0; i < N; i++) {
                int best = DataGenerator.nearestCentroid(data[i], centroids);
                counts[best]++;
                double[] sumRow = sums[best];
                float[]  point  = data[i];
                for (int f = 0; f < F; f++) {
                    sumRow[f] += point[f];
                }
            }

            // Update step: compute new centroids
            float maxShift = 0f;
            for (int k = 0; k < K; k++) {
                if (counts[k] == 0) continue;          // empty cluster: leave centroid unchanged
                float shift = 0f;
                for (int f = 0; f < F; f++) {
                    float newVal = (float) (sums[k][f] / counts[k]);
                    float diff   = newVal - centroids[k][f];
                    shift       += diff * diff;
                    centroids[k][f] = newVal;
                }
                maxShift = Math.max(maxShift, (float) Math.sqrt(shift));
            }

            System.out.printf("[Sequential] Iter %2d  max-shift=%.6f  counts=[", iter + 1, maxShift);
            for (int k = 0; k < Math.min(K, 5); k++) System.out.printf("%d%s", counts[k], k < Math.min(K,5)-1 ? "," : "");
            System.out.println((K > 5 ? "..." : "") + "]");

            if (maxShift < epsilon) {
                System.out.printf("[Sequential] Converged at iteration %d%n", iter + 1);
                break;
            }
        }
        return centroids;
    }

    // Standalone entry point

    public static void main(String[] args) {
        // Default parameters match the distributed run so speedup is comparable
        int   N       = 90_000;
        int   F       = 16;
        int   K       = 5;
        int   maxIter = 30;
        float epsilon = 0.01f;

        if (args.length >= 1) N       = Integer.parseInt(args[0]);
        if (args.length >= 2) K       = Integer.parseInt(args[1]);
        if (args.length >= 3) F       = Integer.parseInt(args[2]);
        if (args.length >= 4) maxIter = Integer.parseInt(args[3]);

        System.out.println("=== Sequential K-Means Baseline ===");
        System.out.printf("Parameters: N=%d  K=%d  F=%d  maxIter=%d%n%n", N, K, F, maxIter);

        float[][] data = DataGenerator.generate(N, F, 12345L);

        long   start     = System.currentTimeMillis();
        float[][] centroids = run(data, K, maxIter, epsilon);
        long   elapsed   = System.currentTimeMillis() - start;

        double wcss = DataGenerator.computeWCSS(data, centroids);

        System.out.println();
        System.out.println("=== Sequential Result ===");
        System.out.printf("Wall-clock time : %d ms%n", elapsed);
        System.out.printf("WCSS (quality)  : %.2f%n", wcss);
        System.out.println("Final centroids (first 3):");
        int show = Math.min(3, K);
        for (int k = 0; k < show; k++) {
            System.out.printf("  C[%d] = [", k);
            for (int f = 0; f < Math.min(F, 4); f++) {
                System.out.printf("%.3f%s", centroids[k][f], f < Math.min(F,4)-1 ? ", " : "");
            }
            System.out.println((F > 4 ? ", ..." : "") + "]");
        }
    }
}
