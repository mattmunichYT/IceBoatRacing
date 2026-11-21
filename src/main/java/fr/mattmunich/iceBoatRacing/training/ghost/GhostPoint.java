package fr.mattmunich.iceBoatRacing.training.ghost;

/**
 * Immutable recorded movement sample along a track lap.
 *
 * @param x         World X coordinate
 * @param y         World Y coordinate
 * @param z         World Z coordinate
 * @param yaw       Yaw angle in degrees
 * @param pitch     Pitch angle in degrees
 * @param elapsedMs Milliseconds elapsed since the start of the lap
 */
public record GhostPoint(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long elapsedMs
) {}
