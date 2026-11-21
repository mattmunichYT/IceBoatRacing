package fr.mattmunich.iceBoatRacing.training.ghost;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Records movement samples for a single active training lap.
 */
public class GhostRecorder {

    /** Minimum interval between recorded points (20 Hz = 50ms). */
    public static final long SAMPLE_INTERVAL_MS = 50L;

    private final UUID playerUuid;
    private final String raceName;
    private final List<GhostPoint> points = new ArrayList<>();

    private long lastRecordedElapsedMs = -1L;
    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastZ = Double.NaN;

    public GhostRecorder(UUID playerUuid, String raceName) {
        this.playerUuid = playerUuid;
        this.raceName = raceName;
    }

    /**
     * Records a location sample at the given elapsed time into the lap.
     *
     * @param loc       Current location (boat)
     * @param elapsedMs Elapsed milliseconds from the start of this lap
     */
    public void record(Location loc, long elapsedMs) {
        if (loc == null || elapsedMs < 0) return;
        record(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), elapsedMs);
    }

    /**
     * Records raw spatial coordinates at the given elapsed time into the lap.
     */
    public void record(double x, double y, double z, float yaw, float pitch, long elapsedMs) {
        if (elapsedMs < 0) return;

        // Sample at most once per SAMPLE_INTERVAL_MS unless significant distance moved
        if (lastRecordedElapsedMs >= 0 && (elapsedMs - lastRecordedElapsedMs) < SAMPLE_INTERVAL_MS) {
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            // Only force record if moved > 0.5 blocks within the same 50ms window
            if ((dx * dx + dy * dy + dz * dz) < 0.25) {
                return;
            }
        }

        points.add(new GhostPoint(x, y, z, yaw, pitch, elapsedMs));
        lastRecordedElapsedMs = elapsedMs;
        lastX = x;
        lastY = y;
        lastZ = z;
    }

    /**
     * Finishes recording the lap and builds the immutable {@link GhostTrajectory}.
     *
     * @param totalLapTimeMs Total lap duration in milliseconds
     * @return Completed trajectory
     */
    public GhostTrajectory finish(long totalLapTimeMs) {
        // If the last point's elapsed time is slightly before totalLapTimeMs, add a terminal point
        if (!points.isEmpty()) {
            GhostPoint last = points.getLast();
            if (last.elapsedMs() < totalLapTimeMs) {
                points.add(new GhostPoint(last.x(), last.y(), last.z(), last.yaw(), last.pitch(), totalLapTimeMs));
            }
        }
        return new GhostTrajectory(playerUuid, raceName, totalLapTimeMs, points);
    }

    public int getPointCount() {
        return points.size();
    }
}
