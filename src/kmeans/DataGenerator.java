package kmeans;

import java.util.Random;

// Dataset generation and centroid initialization
public final class DataGenerator {

    private DataGenerator() {}

    // Generate synthetic clustered dataset
    public static float[][] generate(int N, int F, long seed) {
        Random rng = new Random(seed);
        int K_TRUE = 10;                    // hidden ground-truth clusters

        // Create K_TRUE cluster centres spread in feature space
        float[][] centres = new float[K_TRUE][F];
        for (int k = 0; k < K_TRUE; k++) {
            for (int f = 0; f < F; f++) {
                centres[k][f] = (float) (rng.nextGaussian() * 50.0);
            }
        }

        // Sample each point around one of the true centres
        float[][] data = new float[N][F];
        for (int i = 0; i < N; i++) {
            int k = rng.nextInt(K_TRUE);
            for (int f = 0; f < F; f++) {
                data[i][f] = centres[k][f] + (float) (rng.nextGaussian() * 5.0);
            }
        }
        return data;
    }

    // Initialize centroids from data points
    public static float[][] initCentroids(float[][] data, int K, long seed) {
        int N = data.length;
        int F = data[0].length;
        Random rng = new Random(seed);
        boolean[] used = new boolean[N];
        float[][] centroids = new float[K][F];

        for (int k = 0; k < K; k++) {
            int idx;
            do { idx = rng.nextInt(N); } while (used[idx]);
            used[idx] = true;
            System.arraycopy(data[idx], 0, centroids[k], 0, F);
        }
        return centroids;
    }

    // Find nearest centroid for a point
    public static int nearestCentroid(float[] point, float[][] centroids) {
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        int K = centroids.length;
        int F = point.length;
        for (int k = 0; k < K; k++) {
            float dist = 0f;
            for (int f = 0; f < F; f++) {
                float d = point[f] - centroids[k][f];
                dist += d * d;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = k;
            }
        }
        return best;
    }

    // Compute WCSS quality metric
    public static double computeWCSS(float[][] data, float[][] centroids) {
        double wcss = 0.0;
        for (float[] point : data) {
            int k = nearestCentroid(point, centroids);
            for (int f = 0; f < centroids[k].length; f++) {
                double d = point[f] - centroids[k][f];
                wcss += d * d;
            }
        }
        return wcss;
    }
}
