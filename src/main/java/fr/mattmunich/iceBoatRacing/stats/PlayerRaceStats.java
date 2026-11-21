package fr.mattmunich.iceBoatRacing.stats;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

/**
 * In-memory statistics for a single player on a single race, for a single {@link SessionType}.
 *
 * <p>This class is intentionally decoupled from Bukkit/Paper, storage, and presentation.
 * It holds only plain data and provides read accessors and a single write method
 * ({@link #recordLap}).
 *
 * <h3>History limit</h3>
 * The lap history is capped at {@link #maxHistory} entries (oldest dropped first) to
 * prevent unbounded accumulation. The cap is configurable via
 * {@link PlayerRaceStats#PlayerRaceStats(int)} so it can be driven from {@code config.yml}.
 */
public class PlayerRaceStats {

    /** Default history cap when no value is configured. */
    public static final int DEFAULT_MAX_HISTORY = 100;

    private final int maxHistory;

    // --- aggregate counters ---
    private int completedRaces  = 0;
    private int completedLaps   = 0;

    /** Best (lowest) lap time ever recorded, or {@code Long.MAX_VALUE} when no laps exist. */
    private long personalBestLapMs = Long.MAX_VALUE;

    /** Sum of all recorded lap times (used to compute the running average). */
    private long totalLapTimeMs = 0;

    /** Fastest full-race completion time, or {@code Long.MAX_VALUE} when no race was finished. */
    private long fastestRaceMs = Long.MAX_VALUE;

    /** Bounded lap history (most-recent first after each insertion). */
    private final List<LapRecord> lapHistory = new ArrayList<>();

    // -------------------------------------------------------------------------

    public PlayerRaceStats() {
        this(DEFAULT_MAX_HISTORY);
    }

    public PlayerRaceStats(int maxHistory) {
        this.maxHistory = Math.max(1, maxHistory);
    }

    /**
     * Creates an independent deep-copy snapshot of this stats object.
     * Safe to pass to asynchronous background threads for disk persistence.
     */
    public PlayerRaceStats copy() {
        PlayerRaceStats snapshot = new PlayerRaceStats(this.maxHistory);
        snapshot.completedRaces = this.completedRaces;
        snapshot.completedLaps = this.completedLaps;
        snapshot.personalBestLapMs = this.personalBestLapMs;
        snapshot.totalLapTimeMs = this.totalLapTimeMs;
        snapshot.fastestRaceMs = this.fastestRaceMs;
        snapshot.lapHistory.addAll(this.lapHistory);
        return snapshot;
    }

    // -------------------------------------------------------------------------
    // Write API (called by StatsManager)
    // -------------------------------------------------------------------------

    /**
     * Records a completed lap.
     *
     * <p>This is the <em>only</em> method that mutates aggregate state, so the
     * invariant «completedLaps == lapHistory.size() (up to cap)» is preserved.
     *
     * @param record The lap to register (must not be null).
     */
    public void recordLap(LapRecord record) {
        completedLaps++;
        totalLapTimeMs += record.lapTimeMs();

        if (record.lapTimeMs() < personalBestLapMs) {
            personalBestLapMs = record.lapTimeMs();
        }

        // Insert at front so index 0 is always the most-recent lap.
        lapHistory.addFirst(record);
        if (lapHistory.size() > maxHistory) {
            lapHistory.removeLast();
        }
    }

    /**
     * Marks one more race as completed and optionally updates the fastest race time.
     *
     * @param raceTimeMs Full race duration in milliseconds; pass {@code -1} to skip time update.
     */
    public void recordRaceFinished(long raceTimeMs) {
        completedRaces++;
        if (raceTimeMs > 0 && raceTimeMs < fastestRaceMs) {
            fastestRaceMs = raceTimeMs;
        }
    }

    // -------------------------------------------------------------------------
    // Read API
    // -------------------------------------------------------------------------

    /** Configured max lap history size. */
    public int getMaxHistory() {
        return maxHistory;
    }

    /** Total number of races the player finished (regardless of ranking). */
    public int getCompletedRaces() {
        return completedRaces;
    }

    /** Total number of laps ever completed. */
    public int getCompletedLaps() {
        return completedLaps;
    }

    /**
     * Personal-best lap time in milliseconds.
     *
     * @return {@link OptionalLong#empty()} when no laps have been recorded.
     */
    public OptionalLong getPersonalBestLapMs() {
        return completedLaps == 0 ? OptionalLong.empty() : OptionalLong.of(personalBestLapMs);
    }

    /**
     * Average lap time in milliseconds, computed from all recorded laps
     * (including laps that dropped out of the capped history).
     *
     * @return {@link OptionalLong#empty()} when no laps have been recorded.
     */
    public OptionalLong getAverageLapMs() {
        if (completedLaps == 0) return OptionalLong.empty();
        return OptionalLong.of(totalLapTimeMs / completedLaps);
    }

    /**
     * Fastest full race completion time.
     *
     * @return {@link OptionalLong#empty()} when no race was ever finished.
     */
    public OptionalLong getFastestRaceMs() {
        return completedRaces == 0 ? OptionalLong.empty() : OptionalLong.of(fastestRaceMs);
    }

    /**
     * Total duration spent across all recorded laps in milliseconds.
     */
    public long getTotalLapTimeMs() {
        return totalLapTimeMs;
    }

    /** Raw personal best in ms or Long.MAX_VALUE if none. */
    public long getRawPersonalBestLapMs() {
        return personalBestLapMs;
    }

    /** Raw fastest race time in ms or Long.MAX_VALUE if none. */
    public long getRawFastestRaceMs() {
        return fastestRaceMs;
    }

    /**
     * Lap history (most-recent first), capped at {@link #maxHistory} entries.
     * The returned list is an unmodifiable view.
     */
    public List<LapRecord> getLapHistory() {
        return Collections.unmodifiableList(lapHistory);
    }

    /**
     * Retrieves the personal-best lap record from the history.
     *
     * <p>Note: this searches the capped history list, so when the history was
     * trimmed the result is the best of the <em>retained</em> records.
     * The aggregate {@link #getPersonalBestLapMs()} always reflects the true
     * all-time best.
     *
     * @return The best-time {@link LapRecord} in the retained history, or
     *         {@code null} when history is empty.
     */
    @Nullable
    public LapRecord getBestLapRecord() {
        return lapHistory.stream()
                .min(LapRecord::compareTimeTo)
                .orElse(null);
    }

    /**
     * Compares {@code candidateMs} against the current personal best.
     *
     * @param candidateMs Lap time to test.
     * @return {@code true} if {@code candidateMs} is strictly better than the current PB,
     *         or if no PB exists yet.
     */
    public boolean isPersonalBest(long candidateMs) {
        return candidateMs < personalBestLapMs;
    }

    // -------------------------------------------------------------------------
    // Internal setters (used only by PlayerStatsStore during deserialization)
    // -------------------------------------------------------------------------

    void setCompletedRaces(int v)     { this.completedRaces   = v; }
    void setCompletedLaps(int v)      { this.completedLaps    = v; }
    void setPersonalBestLapMs(long v) { this.personalBestLapMs = v; }
    void setTotalLapTimeMs(long v)    { this.totalLapTimeMs   = v; }
    void setFastestRaceMs(long v)     { this.fastestRaceMs    = v; }
    void addLapHistoryRecord(LapRecord r) { lapHistory.add(r); }
}
