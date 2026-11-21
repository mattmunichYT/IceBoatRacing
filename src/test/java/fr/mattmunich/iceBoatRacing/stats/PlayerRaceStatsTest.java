package fr.mattmunich.iceBoatRacing.stats;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerRaceStatsTest {

    @Test
    void testInitialState() {
        PlayerRaceStats stats = new PlayerRaceStats(5);
        assertEquals(0, stats.getCompletedRaces());
        assertEquals(0, stats.getCompletedLaps());
        assertTrue(stats.getPersonalBestLapMs().isEmpty());
        assertTrue(stats.getAverageLapMs().isEmpty());
        assertTrue(stats.getFastestRaceMs().isEmpty());
        assertTrue(stats.getLapHistory().isEmpty());
        assertNull(stats.getBestLapRecord());
    }

    @Test
    void testRecordLapAggregates() {
        PlayerRaceStats stats = new PlayerRaceStats(10);

        LapRecord lap1 = new LapRecord(60000, Instant.now(), "Monza", 1, SessionType.OFFICIAL);
        LapRecord lap2 = new LapRecord(50000, Instant.now(), "Monza", 2, SessionType.OFFICIAL);
        LapRecord lap3 = new LapRecord(55000, Instant.now(), "Monza", 3, SessionType.OFFICIAL);

        stats.recordLap(lap1);
        assertEquals(1, stats.getCompletedLaps());
        assertEquals(60000, stats.getPersonalBestLapMs().orElseThrow());
        assertEquals(60000, stats.getAverageLapMs().orElseThrow());

        stats.recordLap(lap2);
        assertEquals(2, stats.getCompletedLaps());
        assertEquals(50000, stats.getPersonalBestLapMs().orElseThrow());
        assertEquals(55000, stats.getAverageLapMs().orElseThrow());

        stats.recordLap(lap3);
        assertEquals(3, stats.getCompletedLaps());
        assertEquals(50000, stats.getPersonalBestLapMs().orElseThrow());
        assertEquals((60000 + 50000 + 55000) / 3, stats.getAverageLapMs().orElseThrow());
    }

    @Test
    void testLapHistoryCap() {
        int maxHistory = 3;
        PlayerRaceStats stats = new PlayerRaceStats(maxHistory);

        for (int i = 1; i <= 5; i++) {
            stats.recordLap(new LapRecord(60000 - i * 1000, Instant.now(), "Track", i, SessionType.OFFICIAL));
        }

        // All 5 laps counted in aggregate
        assertEquals(5, stats.getCompletedLaps());
        // History bounded to 3
        List<LapRecord> history = stats.getLapHistory();
        assertEquals(3, history.size());

        // Most recent first: lap 5, lap 4, lap 3
        assertEquals(5, history.get(0).lapNumber());
        assertEquals(4, history.get(1).lapNumber());
        assertEquals(3, history.get(2).lapNumber());

        // PB is lap 5 (55000)
        assertEquals(55000, stats.getPersonalBestLapMs().orElseThrow());
    }

    @Test
    void testRecordRaceFinished() {
        PlayerRaceStats stats = new PlayerRaceStats();

        stats.recordRaceFinished(180000);
        assertEquals(1, stats.getCompletedRaces());
        assertEquals(180000, stats.getFastestRaceMs().orElseThrow());

        stats.recordRaceFinished(200000); // Slower
        assertEquals(2, stats.getCompletedRaces());
        assertEquals(180000, stats.getFastestRaceMs().orElseThrow());

        stats.recordRaceFinished(150000); // Faster
        assertEquals(3, stats.getCompletedRaces());
        assertEquals(150000, stats.getFastestRaceMs().orElseThrow());
    }

    @Test
    void testCopySnapshotIsolation() {
        PlayerRaceStats original = new PlayerRaceStats(5);
        original.recordLap(new LapRecord(50000, Instant.now(), "Circuit", 1, SessionType.OFFICIAL));
        original.recordRaceFinished(200000);

        PlayerRaceStats snapshot = original.copy();

        // Mutate original
        original.recordLap(new LapRecord(40000, Instant.now(), "Circuit", 2, SessionType.OFFICIAL));
        original.recordRaceFinished(180000);

        // Snapshot remains untouched
        assertEquals(1, snapshot.getCompletedLaps());
        assertEquals(1, snapshot.getCompletedRaces());
        assertEquals(50000, snapshot.getPersonalBestLapMs().orElseThrow());
        assertEquals(200000, snapshot.getFastestRaceMs().orElseThrow());
        assertEquals(1, snapshot.getLapHistory().size());

        // Original has new values
        assertEquals(2, original.getCompletedLaps());
        assertEquals(2, original.getCompletedRaces());
        assertEquals(40000, original.getPersonalBestLapMs().orElseThrow());
    }

    @Test
    void testLapRecordReplayId() {
        LapRecord r1 = new LapRecord(45000, Instant.now(), "Track", 1, SessionType.OFFICIAL, "ghost_123");
        assertEquals("ghost_123", r1.replayId());

        LapRecord r2 = new LapRecord(46000, Instant.now(), "Track", 2, SessionType.OFFICIAL);
        assertNull(r2.replayId());
        assertTrue(r1.isFasterThan(r2));
        assertEquals(-1, r1.compareTimeTo(r2));
    }
}
