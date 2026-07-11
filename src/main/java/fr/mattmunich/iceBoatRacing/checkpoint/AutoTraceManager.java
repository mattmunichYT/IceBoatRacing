package fr.mattmunich.iceBoatRacing.checkpoint;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.race.Race;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public class AutoTraceManager {

    private static final double RDP_EPSILON = 0.4;
    private static final double LOOP_CLOSE_THRESHOLD = 3.0;
    private static final double MIN_DISTANCE_BEFORE_LOOP_CHECK = 20.0;
    private static final double MAX_HALF_WIDTH_MULTIPLIER = 3.0;

    private final Main main;
    private final CheckpointManager checkpointManager;
    private final Map<Player, AutoTraceSession> sessions = new HashMap<>();
    private final Map<Player, org.bukkit.scheduler.BukkitTask> previewTasks = new HashMap<>();

    public AutoTraceManager(Main main, CheckpointManager checkpointManager) {
        this.main = main;
        this.checkpointManager = checkpointManager;
    }

    public boolean isRecording(Player p) {
        AutoTraceSession session = sessions.get(p);
        return session != null && session.samplingTask != null;
    }

    public boolean hasSession(Player p) {
        return sessions.containsKey(p);
    }

    public AutoTraceSession getSession(Player p) {
        return sessions.get(p);
    }

    /**
     * Begins recording the player's location every 2 ticks. Auto-stops (and generates the preview)
     * once the recorded path travels back within LOOP_CLOSE_THRESHOLD of its starting point, after
     * at least MIN_DISTANCE_BEFORE_LOOP_CHECK blocks have been recorded so it can't trigger instantly.
     */
    public void start(Player p, Race race, double spacing, double halfWidth, double halfHeight, boolean loop) {
        cancel(p);

        AutoTraceSession session = new AutoTraceSession(p, race);
        session.spacing = spacing;
        session.halfWidth = halfWidth;
        session.halfHeight = halfHeight;
        session.loop = loop;
        sessions.put(p, session);

        session.samplingTask = Bukkit.getScheduler().runTaskTimer(main, () -> {
            if (!p.isOnline()) {
                stop(p);
                return;
            }

            Location current = p.getLocation().clone();

            if (!session.rawPoints.isEmpty()) {
                Location last = session.rawPoints.get(session.rawPoints.size() - 1);
                session.traveledDistance += last.distance(current);
            }
            session.rawPoints.add(current);

            if (session.loop
                    && session.traveledDistance > MIN_DISTANCE_BEFORE_LOOP_CHECK
                    && session.rawPoints.size() > 1) {
                Location first = session.rawPoints.get(0);
                if (current.getWorld().equals(first.getWorld()) && current.distance(first) <= LOOP_CLOSE_THRESHOLD) {
                    stop(p);
                    List<Checkpoint> generated = generatePreview(p);
                    p.sendMessage(getMessage("checkpoint.autotrace.loopClosed", formatArguments("count", "" + generated.size())));
                }
            }
        }, 0L, 2L);
    }

    /** Stops sampling but keeps the recorded points so generatePreview() can still be called. */
    public void stop(Player p) {
        AutoTraceSession session = sessions.get(p);
        if (session == null || session.samplingTask == null) return;
        session.samplingTask.cancel();
        session.samplingTask = null;
    }

    /** Discards the session entirely, recorded points and all. */
    public void cancel(Player p) {
        stop(p);
        sessions.remove(p);

        org.bukkit.scheduler.BukkitTask previewTask = previewTasks.remove(p);
        if (previewTask != null) previewTask.cancel();
    }

    /**
     * Runs simplify -> resample -> recenter-on-track -> plane generation on the recorded points
     * and stores the result as a preview. Does not touch storage.
     * @return the generated checkpoints, or an empty list if there weren't enough recorded points
     */
    public List<Checkpoint> generatePreview(Player p) {
        AutoTraceSession session = sessions.get(p);
        if (session == null || session.rawPoints.size() < 2) return List.of();

        List<Location> simplified = CheckpointGeometry.simplify(session.rawPoints, RDP_EPSILON);
        List<Location> resampled = CheckpointGeometry.resample(simplified, session.spacing);

        double maxHalfWidth = session.halfWidth * MAX_HALF_WIDTH_MULTIPLIER;
        List<CheckpointGeometry.RecenterResult> recentered =
                CheckpointGeometry.recenterOnTrack(resampled, session.loop, session.halfWidth, maxHalfWidth);

        List<Location> centerline = new ArrayList<>();
        List<Double> halfWidths = new ArrayList<>();
        for (CheckpointGeometry.RecenterResult r : recentered) {
            centerline.add(r.center);
            halfWidths.add(r.halfWidth);
        }

        int startId = session.race.getCheckpoints().stream()
                .mapToInt(Checkpoint::getId)
                .max()
                .orElse(0) + 1;

        boolean firstIsStartFinish = session.race.getCheckpoints().isEmpty();

        session.preview = CheckpointGeometry.buildPlaneCheckpoints(
                centerline, halfWidths, session.halfHeight, startId, session.loop, firstIsStartFinish, maxHalfWidth
        );
        session.centerline = centerline;
        session.centerlineArcLengths = CheckpointGeometry.cumulativeArcLengths(centerline);

        return session.preview;
    }

    /**
     * Marks a sector checkpoint from two clicked block locations (exact position and width,
     * unlike normal/start-finish checkpoints which are auto-detected from the track surface).
     * Requires a generated preview to exist (run start then stop first).
     * @return true if the sector was added
     */
    public boolean addSectorMarker(Player p, Location l1, Location l2) {
        AutoTraceSession session = sessions.get(p);
        if (session == null || session.centerline.isEmpty()) return false;

        Location center = CheckpointGeometry.midpoint(l1, l2);
        double halfWidth = Math.max(l1.distance(l2) / 2.0, 0.5);
        double heightDiff = Math.abs(l1.getY() - l2.getY());
        double halfHeight = heightDiff > 0.5 ? heightDiff / 2.0 : session.halfHeight;

        int nearestIndex = CheckpointGeometry.nearestIndex(session.centerline, center);
        Vector tangent = CheckpointGeometry.tangentAt(session.centerline, nearestIndex, session.loop);
        if (tangent == null) return false;

        Vector normal = CheckpointGeometry.snapToCardinal(tangent);

        // ID is a placeholder — saveTracedCheckpoints() reassigns IDs sequentially by list order at commit time.
        Checkpoint sector = new Checkpoint(-1, center, normal, halfWidth, halfHeight, Checkpoint.Type.SECTOR);

        double arcLength = session.centerlineArcLengths[nearestIndex];
        insertByArcLength(session, sector, arcLength);

        return true;
    }

    private void insertByArcLength(AutoTraceSession session, Checkpoint newCheckpoint, double newArcLength) {
        int insertAt = session.preview.size();

        for (int i = 0; i < session.preview.size(); i++) {
            Checkpoint existing = session.preview.get(i);
            int existingIndex = CheckpointGeometry.nearestIndex(session.centerline, existing.getCenter());
            double existingArcLength = session.centerlineArcLengths[existingIndex];

            if (existingArcLength > newArcLength) {
                insertAt = i;
                break;
            }
        }

        session.preview.add(insertAt, newCheckpoint);
    }

    private static final double MAX_EDIT_DISTANCE = 15.0;

    /**
     * Replaces the geometry of the nearest preview checkpoint to the given click points, using
     * their exact projected span for the new width (and center, if the click wasn't centered on
     * the original checkpoint). Keeps the checkpoint's original orientation, type, and ID slot —
     * only its width/position change. Use this to fix checkpoints where track-surface detection
     * got the width wrong (e.g. bridges with no ice for the scanner to measure).
     * @return true if a nearby checkpoint was found and resized
     */
    public boolean resizeNearest(Player p, Location l1, Location l2) {
        AutoTraceSession session = sessions.get(p);
        if (session == null || session.preview.isEmpty()) return false;

        Location clickMid = CheckpointGeometry.midpoint(l1, l2);
        int idx = nearestPreviewCheckpoint(session, clickMid);
        if (idx < 0) return false;

        Checkpoint original = session.preview.get(idx);
        Vector right = original.getRight();
        Vector normal = original.getNormal();

        double proj1 = l1.toVector().subtract(original.getCenter().toVector()).dot(right);
        double proj2 = l2.toVector().subtract(original.getCenter().toVector()).dot(right);

        double newHalfWidth = Math.max(Math.abs(proj1 - proj2) / 2.0, 0.5);
        double centerOffset = (proj1 + proj2) / 2.0;
        Location newCenter = original.getCenter().clone().add(right.clone().multiply(centerOffset));

        Checkpoint replacement = new Checkpoint(original.getId(), newCenter, normal, newHalfWidth, original.getHalfHeight(), original.getType());
        if (original.getType() == Checkpoint.Type.SECTOR) replacement.setSectorID(original.getSectorID());

        session.preview.set(idx, replacement);
        return true;
    }

    /**
     * Removes the preview checkpoint nearest to the given location (typically the player's current
     * position — walk up to the one you want gone and run the command). Useful for e.g. deleting a
     * redundant auto-generated checkpoint that ended up overlapping a manually-placed sector.
     * @return true if a nearby checkpoint was found and removed
     */
    public boolean deleteNearest(Player p, Location point) {
        AutoTraceSession session = sessions.get(p);
        if (session == null || session.preview.isEmpty()) return false;

        int idx = nearestPreviewCheckpoint(session, point);
        if (idx < 0) return false;

        session.preview.remove(idx);
        return true;
    }

    private int nearestPreviewCheckpoint(AutoTraceSession session, Location point) {
        int best = -1;
        double bestDist = MAX_EDIT_DISTANCE;
        for (int i = 0; i < session.preview.size(); i++) {
            double d = session.preview.get(i).getCenter().distance(point);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * Toggles the particle preview on/off. While on, redraws every second until toggled off,
     * the session ends, or the preview list changes shape drastically.
     * @return true if the preview is now ON, false if it's now OFF (or couldn't start)
     */
    public boolean togglePreview(Player p) {
        org.bukkit.scheduler.BukkitTask existing = previewTasks.remove(p);
        if (existing != null) {
            existing.cancel();
            return false;
        }

        AutoTraceSession session = sessions.get(p);
        if (session == null || session.preview.isEmpty()) return false;

        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(main, () -> {
            AutoTraceSession s = sessions.get(p);
            if (s == null || s.preview.isEmpty()) {
                org.bukkit.scheduler.BukkitTask t = previewTasks.remove(p);
                if (t != null) t.cancel();
                return;
            }
            for (Checkpoint cp : s.preview) drawCheckpointOutline(cp);
        }, 0L, 20L);

        previewTasks.put(p, task);
        return true;
    }

    private void drawCheckpointOutline(Checkpoint cp) {
        Location center = cp.getCenter();
        Vector right = cp.getRight();
        Vector up = cp.getUp();
        double hw = cp.getHalfWidth();
        double hh = cp.getHalfHeight();

        Vector rightHW = right.clone().multiply(hw);
        Vector upHH = up.clone().multiply(hh);

        Vector topRight = center.toVector().add(rightHW).add(upHH);
        Vector bottomRight = center.toVector().add(rightHW).subtract(upHH);
        Vector bottomLeft = center.toVector().subtract(rightHW).subtract(upHH);
        Vector topLeft = center.toVector().subtract(rightHW).add(upHH);

        Particle particle = switch (cp.getType()) {
            case START_FINISH -> Particle.FLAME;
            case SECTOR -> Particle.HAPPY_VILLAGER;
            default -> Particle.END_ROD;
        };

        drawLine(center.getWorld(), particle, topRight, bottomRight);
        drawLine(center.getWorld(), particle, bottomRight, bottomLeft);
        drawLine(center.getWorld(), particle, bottomLeft, topLeft);
        drawLine(center.getWorld(), particle, topLeft, topRight);
    }

    private void drawLine(org.bukkit.World world, Particle particle, Vector from, Vector to) {
        double length = from.distance(to);
        int steps = Math.max(1, (int) (length / 0.4));
        Vector step = to.clone().subtract(from).multiply(1.0 / steps);

        Vector cursor = from.clone();
        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(particle, cursor.getX(), cursor.getY(), cursor.getZ(), 1, 0, 0, 0, 0);
            cursor.add(step);
        }
    }

    /**
     * Commits the current preview to storage as real checkpoints. Clears the session and stops
     * the preview task (if any) on success.
     * @return true if the checkpoints were saved
     */
    public boolean accept(Player p) {
        AutoTraceSession session = sessions.get(p);
        if (session == null || session.preview.isEmpty()) return false;

        boolean success = checkpointManager.saveTracedCheckpoints(session.race, session.preview);
        if (success) {
            sessions.remove(p);
            org.bukkit.scheduler.BukkitTask previewTask = previewTasks.remove(p);
            if (previewTask != null) previewTask.cancel();
        }
        return success;
    }
}