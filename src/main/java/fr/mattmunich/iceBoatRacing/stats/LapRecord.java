package fr.mattmunich.iceBoatRacing.stats;

import javax.annotation.Nullable;
import java.time.Instant;

/**
 * Immutable record of a single completed lap.
 *
 * <p>Both official competitive laps and training laps are stored using this class;
 * the {@link SessionType} field distinguishes them. Never mix the two: callers must
 * supply the correct type at construction time.
 *
 * <p>Future ghost replay functionality:
 * The optional {@link #replayId()} field links this lap to an external recorded trajectory
 * or ghost file (e.g., recorded movement samples stored on disk). A future ghost system
 * can query the best {@link LapRecord} for a player/track, retrieve its {@code replayId},
 * and replay the recorded movement data.
 *
 * @param lapTimeMs  Duration of the lap in milliseconds.
 * @param timestamp  Wall-clock instant at which the lap was completed.
 * @param raceName   Name of the race this lap belongs to.
 * @param lapNumber  1-based lap number within the race (or training session).
 * @param type       Whether this lap was part of an official race or a training session.
 * @param replayId   Optional identifier or file name of the movement trajectory for ghost replays.
 */
public record LapRecord(
        long lapTimeMs,
        Instant timestamp,
        String raceName,
        int lapNumber,
        SessionType type,
        @Nullable String replayId
) {

    /**
     * Backward-compatible constructor without a replayId.
     */
    public LapRecord(long lapTimeMs, Instant timestamp, String raceName, int lapNumber, SessionType type) {
        this(lapTimeMs, timestamp, raceName, lapNumber, type, null);
    }

    /** Convenience: compare two lap times. Returns negative if {@code this} is faster. */
    public int compareTimeTo(LapRecord other) {
        return Long.compare(this.lapTimeMs, other.lapTimeMs);
    }

    /** True when this record is faster than {@code other}. */
    public boolean isFasterThan(LapRecord other) {
        return this.lapTimeMs < other.lapTimeMs;
    }

    /** True when this record is faster than the given raw time in ms. */
    public boolean isFasterThan(long otherMs) {
        return this.lapTimeMs < otherMs;
    }
}
