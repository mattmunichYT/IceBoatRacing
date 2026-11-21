package fr.mattmunich.iceBoatRacing.training.ghost;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GhostTrajectoryTest {

    @Test
    void testEmptyTrajectory() {
        GhostTrajectory trajectory = new GhostTrajectory(UUID.randomUUID(), "TestRace", 0L, List.of());
        assertTrue(trajectory.isEmpty());
        assertNull(trajectory.getStartPoint());
        assertNull(trajectory.getEndPoint());
        assertNull(trajectory.interpolate(100L));
        assertEquals(-1, trajectory.findClosestPointIndex(0, 0, 0, 0));
    }

    @Test
    void testSinglePointTrajectory() {
        GhostPoint p = new GhostPoint(10.0, 64.0, 20.0, 90.0f, 0.0f, 0L);
        GhostTrajectory trajectory = new GhostTrajectory(UUID.randomUUID(), "TestRace", 5000L, List.of(p));

        assertFalse(trajectory.isEmpty());
        assertEquals(p, trajectory.getStartPoint());
        assertEquals(p, trajectory.getEndPoint());

        GhostPoint interpolated = trajectory.interpolate(2500L);
        assertNotNull(interpolated);
        assertEquals(10.0, interpolated.x());
        assertEquals(64.0, interpolated.y());
        assertEquals(20.0, interpolated.z());
        assertEquals(90.0f, interpolated.yaw());
    }

    @Test
    void testInterpolationLinear() {
        GhostPoint p1 = new GhostPoint(0.0, 64.0, 0.0, 0.0f, 0.0f, 0L);
        GhostPoint p2 = new GhostPoint(100.0, 64.0, 200.0, 180.0f, 10.0f, 1000L);

        GhostTrajectory trajectory = new GhostTrajectory(UUID.randomUUID(), "Monza", 1000L, List.of(p1, p2));

        // Exactly at start
        GhostPoint at0 = trajectory.interpolate(0L);
        assertEquals(0.0, at0.x(), 0.001);
        assertEquals(0.0, at0.z(), 0.001);

        // Halfway (500ms)
        GhostPoint atHalf = trajectory.interpolate(500L);
        assertEquals(50.0, atHalf.x(), 0.001);
        assertEquals(64.0, atHalf.y(), 0.001);
        assertEquals(100.0, atHalf.z(), 0.001);
        assertEquals(5.0f, atHalf.pitch(), 0.001f);

        // 25% (250ms)
        GhostPoint atQuarter = trajectory.interpolate(250L);
        assertEquals(25.0, atQuarter.x(), 0.001);
        assertEquals(50.0, atQuarter.z(), 0.001);

        // Beyond end
        GhostPoint atEnd = trajectory.interpolate(1500L);
        assertEquals(100.0, atEnd.x(), 0.001);
        assertEquals(200.0, atEnd.z(), 0.001);
    }

    @Test
    void testYawWrapAroundInterpolation() {
        // Turning through 180° boundary (e.g. from 170° to -170°, a 20° right turn)
        GhostPoint p1 = new GhostPoint(0.0, 64.0, 0.0, 170.0f, 0.0f, 0L);
        GhostPoint p2 = new GhostPoint(10.0, 64.0, 0.0, -170.0f, 0.0f, 1000L);

        GhostTrajectory trajectory = new GhostTrajectory(UUID.randomUUID(), "Curve", 1000L, List.of(p1, p2));

        GhostPoint mid = trajectory.interpolate(500L);
        // Halfway between 170° and 190° (-170°) should be 180° or -180°
        float yaw = Math.abs(mid.yaw());
        assertEquals(180.0f, yaw, 0.5f);
    }

    @Test
    void testFindClosestPointIndex() {
        GhostPoint p1 = new GhostPoint(0.0, 64.0, 0.0, 0.0f, 0.0f, 0L);
        GhostPoint p2 = new GhostPoint(10.0, 64.0, 0.0, 0.0f, 0.0f, 100L);
        GhostPoint p3 = new GhostPoint(20.0, 64.0, 0.0, 0.0f, 0.0f, 200L);
        GhostPoint p4 = new GhostPoint(30.0, 64.0, 0.0, 0.0f, 0.0f, 300L);

        GhostTrajectory trajectory = new GhostTrajectory(UUID.randomUUID(), "Straight", 300L, List.of(p1, p2, p3, p4));

        // Near p2
        int idx = trajectory.findClosestPointIndex(9.8, 64.0, 0.1, 0);
        assertEquals(1, idx);

        // Continuing forward near p3
        idx = trajectory.findClosestPointIndex(20.2, 64.0, -0.1, 1);
        assertEquals(2, idx);
    }
}
