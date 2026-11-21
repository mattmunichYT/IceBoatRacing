package fr.mattmunich.iceBoatRacing.training.ghost;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a recorded lap trajectory for ghost replay and real-time comparison.
 */
public class GhostTrajectory {

    private final UUID playerUuid;
    private final String raceName;
    private final long lapTimeMs;
    private final List<GhostPoint> points;

    public GhostTrajectory(UUID playerUuid, String raceName, long lapTimeMs, List<GhostPoint> points) {
        this.playerUuid = playerUuid;
        this.raceName = raceName;
        this.lapTimeMs = lapTimeMs;
        this.points = points != null ? List.copyOf(points) : Collections.emptyList();
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getRaceName() {
        return raceName;
    }

    public long getLapTimeMs() {
        return lapTimeMs;
    }

    public List<GhostPoint> getPoints() {
        return points;
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public GhostPoint getStartPoint() {
        return points.isEmpty() ? null : points.getFirst();
    }

    public GhostPoint getEndPoint() {
        return points.isEmpty() ? null : points.getLast();
    }

    /**
     * Interpolates the trajectory position and rotation for the given elapsed time.
     *
     * @param elapsedMs Elapsed milliseconds since the start of the lap.
     * @return Interpolated point, or null if trajectory is empty.
     */
    public GhostPoint interpolate(long elapsedMs) {
        if (points.isEmpty()) return null;
        if (points.size() == 1 || elapsedMs <= points.getFirst().elapsedMs()) {
            return points.getFirst();
        }
        if (elapsedMs >= points.getLast().elapsedMs()) {
            return points.getLast();
        }

        // Binary search for segment: points[low - 1].elapsedMs <= elapsedMs < points[low].elapsedMs
        int low = 0;
        int high = points.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = points.get(mid).elapsedMs();
            if (midVal < elapsedMs) {
                low = mid + 1;
            } else if (midVal > elapsedMs) {
                high = mid - 1;
            } else {
                return points.get(mid);
            }
        }

        int idx1 = Math.max(0, low - 1);
        int idx2 = Math.min(points.size() - 1, low);
        if (idx1 == idx2) return points.get(idx1);

        GhostPoint p1 = points.get(idx1);
        GhostPoint p2 = points.get(idx2);

        long dt = p2.elapsedMs() - p1.elapsedMs();
        double alpha = dt <= 0 ? 0.0 : (double) (elapsedMs - p1.elapsedMs()) / (double) dt;

        double x = p1.x() + alpha * (p2.x() - p1.x());
        double y = p1.y() + alpha * (p2.y() - p1.y());
        double z = p1.z() + alpha * (p2.z() - p1.z());

        float dyaw = p2.yaw() - p1.yaw();
        while (dyaw > 180f) dyaw -= 360f;
        while (dyaw < -180f) dyaw += 360f;
        float yaw = p1.yaw() + (float) (alpha * dyaw);
        float pitch = p1.pitch() + (float) (alpha * (p2.pitch() - p1.pitch()));

        return new GhostPoint(x, y, z, yaw, pitch, elapsedMs);
    }

    /**
     * Finds the index of the closest trajectory point to the given coordinates within a
     * local forward-progress window starting around {@code lastMatchedIndex}.
     *
     * @param x                Current X
     * @param y                Current Y
     * @param z                Current Z
     * @param lastMatchedIndex Previous closest index to avoid jumping between distant track loops
     * @return Index of the closest point, or -1 if empty
     */
    public int findClosestPointIndex(double x, double y, double z, int lastMatchedIndex) {
        if (points.isEmpty()) return -1;

        // Search in a window around lastMatchedIndex (e.g. from -10 to +50 points)
        int searchBackward = 10;
        int searchForward = 50;

        int start = Math.max(0, lastMatchedIndex - searchBackward);
        int end = Math.min(points.size() - 1, lastMatchedIndex + searchForward);

        double bestDistSq = Double.MAX_VALUE;
        int bestIdx = lastMatchedIndex >= 0 && lastMatchedIndex < points.size() ? lastMatchedIndex : 0;

        for (int i = start; i <= end; i++) {
            GhostPoint p = points.get(i);
            double dx = x - p.x();
            double dy = y - p.y();
            double dz = z - p.z();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestIdx = i;
            }
        }

        // If distance is exceedingly large (e.g., player reset or went off track), fallback to full search
        if (bestDistSq > 900.0) { // > 30 blocks
            for (int i = 0; i < points.size(); i++) {
                GhostPoint p = points.get(i);
                double dx = x - p.x();
                double dy = y - p.y();
                double dz = z - p.z();
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestIdx = i;
                }
            }
        }

        return bestIdx;
    }
}
