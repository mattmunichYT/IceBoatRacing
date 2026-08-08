package fr.mattmunich.iceBoatRacing.checkpoint;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public class Checkpoint {

    public enum Type {
        NORMAL,
        START_FINISH,
        SECTOR
    }

    public enum Shape {
        BOX,
        PLANE
    }

    /**
     * A secondary physical gate that counts as crossing this same logical checkpoint.
     * Used for alternate routes that rejoin the track without physically crossing the
     * primary checkpoint plane (e.g. a pit/stands lane that runs past the start/finish line).
     */
    public static class AlternateRoute {
        public final Checkpoint parent;
        public final Location center;
        public final Vector normal;
        public final Vector right;
        public final Vector up;
        public final double halfWidth;
        public final double halfHeight;

        public AlternateRoute(Checkpoint parent, Location center, Vector normal, double halfWidth, double halfHeight) {
            this.parent = parent;
            this.center = center;
            this.normal = normal.clone().normalize();

            Vector worldUp = new Vector(0, 1, 0);
            Vector r = this.normal.clone().crossProduct(worldUp);
            if (r.lengthSquared() < 1e-6) r = new Vector(1, 0, 0);
            this.right = r.normalize();
            this.up = this.right.clone().crossProduct(this.normal).normalize();

            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
        }

        public Checkpoint getParent() {
            return parent;
        }

        public double getHalfHeight() {
            return halfHeight;
        }

        public double getHalfWidth() {
            return halfWidth;
        }

        public Location getCenter() {
            return center;
        }

// --Commented out by Inspection START (05/08/2026, 16:51):
//        public Vector getNormal() {
//            return normal;
//        }
// --Commented out by Inspection STOP (05/08/2026, 16:51)

        public Vector getRight() {
            return right;
        }

        public Vector getUp() {
            return up;
        }

        public void remove() {
            parent.removeAlternate(this);
        }
    }

    final int id; // checkpoint ID in the race order
    int sectorID = -1; // only used for sectors
    final Location min;
    final Location max;
    final Type type;
    Shape shape = Shape.BOX;

    // PLANE-only fields
    Location center;
    Vector normal;
    Vector right;
    Vector up;
    double halfWidth;
    double halfHeight;

    final List<AlternateRoute> alternates = new ArrayList<>();

    /**
     * Type will be set to NORMAL
     * @param id the checkpoint ID
     * @param min position 1
     * @param max position 2
     */
    public Checkpoint(int id, Location min, Location max) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.type = Type.NORMAL;
    }

    /**
     * @param id the checkpoint ID
     * @param min position 1
     * @param max position 2
     * @param type the checkpoint type from the Checkpoint.Type enum
     */
    public Checkpoint(int id, Location min, Location max, Type type) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.type = type;
    }

    /**
     *  Use for new SECTOR checkpoint only
     * @param id the checkpoint ID
     * @param sectorID the sector ID
     * @param min position 1
     * @param max position 2
     */
    public Checkpoint(int id, int sectorID, Location min, Location max) {
        this.id = id;
        this.sectorID = sectorID;
        this.min = min;
        this.max = max;
        this.type = Type.SECTOR;
    }

    /**
     * Oriented plane checkpoint. Used by the auto-trace generator, and by anything else that
     * wants a checkpoint that isn't forced to be axis-aligned (curved/diagonal track sections).
     * @param id the checkpoint ID
     * @param center the center point of the checkpoint plane
     * @param normal direction of travel through the checkpoint (does not need to be normalized)
     * @param halfWidth half-width of the checkpoint along its horizontal axis
     * @param halfHeight half-height of the checkpoint along its vertical axis
     * @param type the checkpoint type
     */
    public Checkpoint(int id, Location center, Vector normal, double halfWidth, double halfHeight, Type type) {
        this.id = id;
        this.type = type;
        this.shape = Shape.PLANE;
        this.center = center;
        this.normal = normal.clone().normalize();

        Vector worldUp = new Vector(0, 1, 0);
        Vector r = this.normal.clone().crossProduct(worldUp);
        if (r.lengthSquared() < 1e-6) {
            // normal is (near) vertical, fall back to a fixed horizontal right axis
            r = new Vector(1, 0, 0);
        }
        this.right = r.normalize();
        this.up = this.right.clone().crossProduct(this.normal).normalize();

        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;

        // Loose AABB around the plane, purely so getAt()/teleport/list still have something to work with.
        double r2 = Math.max(halfWidth, halfHeight) + 1;
        this.min = center.clone().subtract(r2, r2, r2);
        this.max = center.clone().add(r2, r2, r2);
    }

    public void addAlternate(Location center, Vector normal, double halfWidth, double halfHeight) {
        alternates.add(new AlternateRoute(this, center, normal, halfWidth, halfHeight));
    }

    public List<AlternateRoute> getAlternates() {
        return alternates;
    }

    public void removeAlternate(AlternateRoute alternateRoute) {
        alternates.remove(alternateRoute);
    }

    /**
     * True if the movement segment from -> to crosses this checkpoint, including any alternate
     * routes (e.g. a bypass lane that rejoins the track without crossing the primary plane).
     * Use this for race crossing detection instead of contains().
     */
    public boolean crosses(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) return false;

        boolean primaryCrossed = shape == Shape.PLANE ? crossesPlane(from, to) : crossesBox(from, to);
        if (primaryCrossed) return true;

        for (AlternateRoute alt : alternates) {
            if (planeCrossTest(from, to, alt.center, alt.normal, alt.right, alt.up, alt.halfWidth, alt.halfHeight)) {
                return true;
            }
        }
        return false;
    }

    private boolean crossesPlane(Location from, Location to) {
        return planeCrossTest(from, to, center, normal, right, up, halfWidth, halfHeight);
    }

    private static boolean planeCrossTest(Location from, Location to, Location planeCenter, Vector planeNormal,
                                          Vector planeRight, Vector planeUp, double hw, double hh) {
        if (!from.getWorld().equals(planeCenter.getWorld())) return false;

        Vector fromRel = from.toVector().subtract(planeCenter.toVector());
        Vector toRel = to.toVector().subtract(planeCenter.toVector());

        double d1 = fromRel.dot(planeNormal);
        double d2 = toRel.dot(planeNormal);

        if ((d1 > 0) == (d2 > 0)) return false; // no sign change -> didn't cross the plane this tick

        double t = d1 / (d1 - d2);
        Vector intersection = fromRel.clone().add(toRel.clone().subtract(fromRel).multiply(t));

        double w = intersection.dot(planeRight);
        double h = intersection.dot(planeUp);
        return Math.abs(w) <= hw && Math.abs(h) <= hh;
    }

    private boolean crossesBox(Location from, Location to) {
        if (!to.getWorld().equals(min.getWorld())) return false;
        if (containsBoxPoint(to)) return true;
        return segmentIntersectsBox(from.toVector(), to.toVector());
    }

    private boolean containsBoxPoint(Location loc) {
        return loc.getX() >= min.getX() && loc.getX() <= max.getX() &&
                loc.getY() >= min.getY() && loc.getY() <= max.getY() &&
                loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ();
    }

    private boolean segmentIntersectsBox(Vector from, Vector to) {
        Vector dir = to.clone().subtract(from);
        double tMin = 0.0, tMax = 1.0;
        double[] fromArr = {from.getX(), from.getY(), from.getZ()};
        double[] dirArr = {dir.getX(), dir.getY(), dir.getZ()};
        double[] minArr = {min.getX(), min.getY(), min.getZ()};
        double[] maxArr = {max.getX(), max.getY(), max.getZ()};

        for (int i = 0; i < 3; i++) {
            if (Math.abs(dirArr[i]) < 1e-9) {
                if (fromArr[i] < minArr[i] || fromArr[i] > maxArr[i]) return false;
                continue;
            }
            double t1 = (minArr[i] - fromArr[i]) / dirArr[i];
            double t2 = (maxArr[i] - fromArr[i]) / dirArr[i];
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }
        return true;
    }

    // Getters
    public int getId() { return id; }
    public int getSectorID() { return sectorID; }
    public void setSectorID(int sectorID) { this.sectorID = sectorID; }
    public Location getMin() { return min; }
    public Location getMax() { return max; }
    public Type getType() { return type; }
    public Shape getShape() { return shape; }
    public Location getCenter() {
        if (shape == Shape.PLANE) return center;
        return min.clone().add(max).multiply(0.5);
    }
    public Vector getNormal() { return normal; }
    public Vector getRight() { return right; }
    public Vector getUp() { return up; }
    public double getHalfWidth() { return halfWidth; }
    public double getHalfHeight() { return halfHeight; }

    @Override
    public String toString() {
        return """
                Checkpoint %s:
                - Type: %s (sector %s)
                - Shape: %s
                - Min: %s ; Max : %s
                - Center: %s ; Normal: %s
                - Right: %s ; Up: %s
                - HalfWidth: %s ; HalfHeight: %s
                """
                .formatted(
                        getId(),
                        getType(), getSectorID(),
                        getShape(),
                        getMin(), getMax(),
                        getCenter(), getNormal(),
                        getRight(), getUp(),
                        getHalfWidth(), getHalfHeight()
                );
    }
}