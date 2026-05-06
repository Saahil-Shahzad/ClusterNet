package kmeans;

// TCP message type codes for distributed K-Means communication
// (annotation added by Saahil for commit)
public final class MessageType {
    public static final int INIT               = 0x01;  // Master -> Worker: send data partition
    public static final int CENTROID_BROADCAST = 0x02;  // Master -> Worker: current centroids
    public static final int PARTIAL_RESULT     = 0x03;  // Worker -> Master: partial sums + counts
    public static final int CONVERGED          = 0x04;  // Master -> Worker: algorithm done
    public static final int SHUTDOWN           = 0x05;  // Master -> Worker: close and exit JVM
    public static final int HEARTBEAT          = 0x06;  // Master <-> Worker: keep-alive check

    private MessageType() {}

    public static String name(int code) {
        switch (code) {
            case INIT:               return "INIT";
            case CENTROID_BROADCAST: return "CENTROID_BROADCAST";
            case PARTIAL_RESULT:     return "PARTIAL_RESULT";
            case CONVERGED:          return "CONVERGED";
            case SHUTDOWN:           return "SHUTDOWN";
            case HEARTBEAT:          return "HEARTBEAT";
            default:                 return "UNKNOWN(0x" + Integer.toHexString(code) + ")";
        }
    }
}
