package fr.mattmunich.iceBoatRacing.stats;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class StatsComparisonTest {

    @Test
    void testOfficialAndTrainingSeparation() {
        PlayerRaceStats official = new PlayerRaceStats(10);
        PlayerRaceStats training = new PlayerRaceStats(10);

        official.recordLap(new LapRecord(50000, Instant.now(), "Monza", 1, SessionType.OFFICIAL));
        training.recordLap(new LapRecord(45000, Instant.now(), "Monza", 1, SessionType.TRAINING));

        assertEquals(50000, official.getPersonalBestLapMs().orElseThrow());
        assertEquals(45000, training.getPersonalBestLapMs().orElseThrow());

        // Official should never be affected by training
        assertNotEquals(official.getPersonalBestLapMs().orElseThrow(), training.getPersonalBestLapMs().orElseThrow());
    }

    @Test
    void testCompareLapTimes() {
        LapRecord lapA = new LapRecord(50000, Instant.now(), "Monza", 1, SessionType.OFFICIAL);
        LapRecord lapB = new LapRecord(55000, Instant.now(), "Monza", 2, SessionType.OFFICIAL);

        assertTrue(lapA.isFasterThan(lapB));
        assertFalse(lapB.isFasterThan(lapA));
        assertTrue(lapA.isFasterThan(52000));
        assertFalse(lapB.isFasterThan(52000));

        assertEquals(-1, lapA.compareTimeTo(lapB));
        assertEquals(1, lapB.compareTimeTo(lapA));
        assertEquals(0, lapA.compareTimeTo(new LapRecord(50000, Instant.now(), "Monza", 3, SessionType.OFFICIAL)));
    }

    @Test
    void testIsPersonalBest() {
        PlayerRaceStats stats = new PlayerRaceStats(10);
        assertTrue(stats.isPersonalBest(60000)); // First lap is always PB

        stats.recordLap(new LapRecord(60000, Instant.now(), "Monza", 1, SessionType.OFFICIAL));
        assertFalse(stats.isPersonalBest(65000));
        assertFalse(stats.isPersonalBest(60000)); // Not strictly faster
        assertTrue(stats.isPersonalBest(59999));
    }
}
