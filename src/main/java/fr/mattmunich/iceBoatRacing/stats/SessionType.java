package fr.mattmunich.iceBoatRacing.stats;

/**
 * Distinguishes official competitive race laps from training-mode laps.
 *
 * <p>This enum is the single source of truth for session-type separation.
 * Statistics keyed on {@code OFFICIAL} must never be contaminated by
 * {@code TRAINING} data, and vice-versa.
 *
 * <p>Future training-mode work should use {@code TRAINING} when recording laps;
 * the statistics system will automatically keep them in a separate bucket.
 */
public enum SessionType {
    /** A lap completed during a live, official race. */
    OFFICIAL,

    /**
     * A lap completed during a solo training session.
     * Reserved for future use — no production code currently writes this value.
     */
    TRAINING
}
