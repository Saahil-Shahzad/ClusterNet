package kmeans;

import java.util.concurrent.RecursiveAction;

// RecursiveAction for K-Means assignment using ForkJoinPool
public class AssignTask extends RecursiveAction {

    /** Leaf tasks process at most this many points before computing directly. */
    private static final int THRESHOLD = 50_000;

    private final float[][] data;
    private final float[][] centroids;
    private final int start;        // inclusive
    private final int end;          // exclusive

    /** Partial centroid sums for this sub-range: shape [K][F], float64 for precision. */
    public final double[][] partialSums;

    /** Number of points assigned to each centroid in this sub-range: length K. */
    public final int[] partialCounts;

    public AssignTask(float[][] data, int start, int end, float[][] centroids) {
        this.data       = data;
        this.start      = start;
        this.end        = end;
        this.centroids  = centroids;
        int K = centroids.length;
        int F = data[0].length;
        this.partialSums   = new double[K][F];
        this.partialCounts = new int[K];
    }

    @Override
    protected void compute() {
        int size = end - start;

        if (size <= THRESHOLD) {
            // LEAF: direct computation
            int K = centroids.length;
            int F = data[0].length;
            for (int i = start; i < end; i++) {
                int best = DataGenerator.nearestCentroid(data[i], centroids);
                partialCounts[best]++;
                double[] sumRow = partialSums[best];
                float[]  point  = data[i];
                for (int f = 0; f < F; f++) {
                    sumRow[f] += point[f];
                }
            }
        } else {
            // INTERNAL: fork-and-merge
            int mid = (start + end) >>> 1;
            AssignTask left  = new AssignTask(data, start, mid, centroids);
            AssignTask right = new AssignTask(data, mid,   end, centroids);

            left.fork();        // left runs on a pool thread (work-stealing)
            right.compute();    // right runs on THIS thread
            left.join();        // wait for left subtree

            // Merge child results into this task's arrays
            int K = centroids.length;
            int F = data[0].length;
            for (int k = 0; k < K; k++) {
                partialCounts[k] = left.partialCounts[k] + right.partialCounts[k];
                double[] dst = partialSums[k];
                double[] ls  = left.partialSums[k];
                double[] rs  = right.partialSums[k];
                for (int f = 0; f < F; f++) {
                    dst[f] = ls[f] + rs[f];
                }
            }
        }
    }
}
