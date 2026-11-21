package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.pitbox.PitBox;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class Racer {
    public Player player;

    public boolean racing = false;
    public int ranking = -1;
    public boolean disqualified = false;
    public int checkpointIndex = 0;
    public int lapCount = -1;
    public Location from;

    public Car car;

    public int pitStopsCompleted = 0;
    public PitBox pittingBox = null;
    public long lastPitBox = 0;

    public long startTime = 0;
    public long lapTime = 0;
    public long endTime = 0;
    public long gapToNextTime = 0;
    public final List<Long> lapTimes = new ArrayList<>();
    public final Map<Integer,List<Long>> sectorsTimes = new HashMap<>();

    /**
     * Position on the starting grid (1 = pole), 0 until it has been assigned.
     * Used by the live score as the baseline for {@code positionChange}.
     */
    public int gridPosition = 0;

    /**
     * Wall-clock time ({@link System#currentTimeMillis()}) at which this racer passed each checkpoint,
     * keyed by {@link #getProgress()}. Used by the live score to compute gaps between racers.
     */
    private final Map<Integer, Long> progressTimes = new HashMap<>();

    public Race race;
    public Location logoutLocation;

    public Racer(Player player) {
        this.player = player;
    }

    public Player getPlayer() { return player; }

    public Race getRace() { return race; }

    public int getCompletedLapCount() {
        return lapCount;
    }

    public int getCompletedPitStops() {
        return pitStopsCompleted;
    }

    public int getScore() {
        return ((lapCount-1) * getRace().getCheckpoints().size()) + checkpointIndex;
    }

    /**
     * How many of the race's checkpoints (by ID order) the racer has passed in the current lap:
     * 0 = none yet, 1 = the start/finish line, ... Unlike {@link #checkpointIndex} this has no gaps
     * when checkpoint IDs have been removed.
     */
    public int getCheckpointOrdinal() {
        int ordinal = 0;
        for (Checkpoint cp : race.getCheckpoints()) {
            if (cp.getId() <= checkpointIndex) ordinal++;
        }
        return ordinal;
    }

    /**
     * Total number of checkpoints passed since the start line, across all laps. Strictly increases
     * as the racer drives; 0 until they have crossed the start line.
     */
    public int getProgress() {
        if (lapCount < 1) return 0;
        return (lapCount - 1) * race.getCheckpoints().size() + getCheckpointOrdinal();
    }

    /**
     * Remembers when the racer reached their current {@link #getProgress()}.
     * Call right after {@link #checkpointIndex} / {@link #lapCount} were updated.
     */
    public void recordProgress(long now) {
        int progress = getProgress();
        if (progress > 0) progressTimes.put(progress, now);
    }

    /** @return when the racer reached the given {@link #getProgress()} value, or null if never recorded */
    public Long getProgressTime(int progress) {
        return progressTimes.get(progress);
    }

    public long bestLapTime() {
        return Collections.min(lapTimes);
    }

    public long worstLapTime() {
        return Collections.max(lapTimes);
    }

    public Map<Integer,Long> bestSectorsTimes() {
        Map<Integer,Long> bestSectorsTimes = new HashMap<>();
        for (Integer s : sectorsTimes.keySet()) {
            List<Long> sTimes = sectorsTimes.get(s);
            bestSectorsTimes.put(s, Collections.min(sTimes));
        }
        return bestSectorsTimes;
    }

    public long meanLapTime() {
        return (long) lapTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
    }

    public long getRaceTime() {
        return endTime - startTime;
    }

    public List<Long> getLapTimes() {
        return lapTimes;
    }

    public Map<Integer,List<Long>> getSectorsTimes() {
        return sectorsTimes;
    }

    public void addSectorTime(int sectorID, long now, long lapTime) {
        List<Long> sectorTimes = sectorsTimes.getOrDefault(sectorID, new ArrayList<>());
        sectorTimes.add(now-lapTime);
        sectorsTimes.put(sectorID, sectorTimes);
    }

    public Location getLogoutLocation() {
        return logoutLocation;
    }

    public void setLogoutLocation(Location logoutLocation) {
        this.logoutLocation = logoutLocation;
    }
}