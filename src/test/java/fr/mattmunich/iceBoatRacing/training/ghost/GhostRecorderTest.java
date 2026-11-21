package fr.mattmunich.iceBoatRacing.training.ghost;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GhostRecorderTest {

    @Test
    void testRecordingThrottling() {
        UUID uuid = UUID.randomUUID();
        GhostRecorder recorder = new GhostRecorder(uuid, "TestRace");

        recorder.record(10.0, 64.0, 20.0, 0f, 0f, 0L);
        assertEquals(1, recorder.getPointCount());

        // Same position within 20ms (< 50ms) -> throttled
        recorder.record(10.0, 64.0, 20.0, 0f, 0f, 20L);
        assertEquals(1, recorder.getPointCount());

        // Same position after 50ms -> recorded
        recorder.record(10.0, 64.0, 20.0, 0f, 0f, 60L);
        assertEquals(2, recorder.getPointCount());

        // Next position at 120ms
        recorder.record(15.0, 64.0, 20.0, 45f, 0f, 120L);
        assertEquals(3, recorder.getPointCount());

        // Finish lap at 5000ms
        GhostTrajectory trajectory = recorder.finish(5000L);
        assertNotNull(trajectory);
        assertEquals(uuid, trajectory.getPlayerUuid());
        assertEquals("TestRace", trajectory.getRaceName());
        assertEquals(5000L, trajectory.getLapTimeMs());

        // Terminal point added at 5000ms
        GhostPoint endPoint = trajectory.getEndPoint();
        assertNotNull(endPoint);
        assertEquals(5000L, endPoint.elapsedMs());
    }
}
