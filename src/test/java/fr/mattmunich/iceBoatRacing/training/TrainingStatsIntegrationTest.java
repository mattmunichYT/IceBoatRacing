package fr.mattmunich.iceBoatRacing.training;

import fr.mattmunich.iceBoatRacing.stats.LapRecord;
import fr.mattmunich.iceBoatRacing.stats.PlayerRaceStats;
import fr.mattmunich.iceBoatRacing.stats.SessionType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class TrainingStatsIntegrationTest {

    @Test
    void testTrainingAndOfficialSeparation() {
        PlayerRaceStats officialStats = new PlayerRaceStats(10);
        PlayerRaceStats trainingStats = new PlayerRaceStats(10);

        // Player completes an official lap of 55000ms
        officialStats.recordLap(new LapRecord(55000L, Instant.now(), "Monza", 1, SessionType.OFFICIAL));

        // Player completes training laps of 48000ms and 45000ms
        trainingStats.recordLap(new LapRecord(48000L, Instant.now(), "Monza", 1, SessionType.TRAINING, "ghost_1"));
        trainingStats.recordLap(new LapRecord(45000L, Instant.now(), "Monza", 2, SessionType.TRAINING, "ghost_2"));

        // Official stats must remain completely uncontaminated
        assertEquals(1, officialStats.getCompletedLaps());
        assertEquals(55000L, officialStats.getPersonalBestLapMs().orElseThrow());
        assertEquals(55000L, officialStats.getAverageLapMs().orElseThrow());

        // Training stats reflect the training laps
        assertEquals(2, trainingStats.getCompletedLaps());
        assertEquals(45000L, trainingStats.getPersonalBestLapMs().orElseThrow());
        assertEquals((48000L + 45000L) / 2, trainingStats.getAverageLapMs().orElseThrow());

        // Best lap record has replayId
        LapRecord bestTraining = trainingStats.getBestLapRecord();
        assertNotNull(bestTraining);
        assertEquals(45000L, bestTraining.lapTimeMs());
        assertEquals("ghost_2", bestTraining.replayId());
        assertEquals(SessionType.TRAINING, bestTraining.type());
    }

    @Test
    void testDeltaFormat() {
        assertEquals("0.45s", TrainingSession.formatDelta(450L));
        assertEquals("1.20s", TrainingSession.formatDelta(1200L));
        assertEquals("12.50s", TrainingSession.formatDelta(12500L));
        assertEquals("1m05,00", TrainingSession.formatDelta(65000L));
    }
}
