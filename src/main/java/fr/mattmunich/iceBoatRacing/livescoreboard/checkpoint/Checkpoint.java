package fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint;

import org.bukkit.Location;

public class Checkpoint {

    public enum Type {
        NORMAL,
        START_FINISH,
        SECTOR
    }

    int index; // checkpoint index in the race order
    int sectorIndex = -1; // only used for sectors
    Location min;
    Location max;
    Type type;

    /**
     * Type will be set to NORMAL
     * @param index the checkpoint ID
     * @param min position 1
     * @param max position 2
     */
    public Checkpoint(int index, Location min, Location max) {
        this.index = index;
        this.min = min;
        this.max = max;
        this.type = Type.NORMAL;
    }

    /**
     * @param index the checkpoint ID
     * @param min position 1
     * @param max position 2
     * @param type the checkpoint type from the Checkpoint.Type enum
     */
    public Checkpoint(int index, Location min, Location max, Type type) {
        this.index = index;
        this.min = min;
        this.max = max;
        this.type = type;
    }

    /**
     *  Use for new SECTOR checkpoint only
     * @param index the checkpoint ID
     * @param sectorIndex the sector ID
     * @param min position 1
     * @param max position 2
     */
    public Checkpoint(int index, int sectorIndex, Location min, Location max) {
        this.index = index;
        this.sectorIndex = sectorIndex;
        this.min = min;
        this.max = max;
        this.type = Type.SECTOR;
    }
    /**
     * Check if the checkpoint contains the location loc
     * @param loc the location to check
     */
    public boolean contains(Location loc) {
        return loc.getWorld().equals(min.getWorld()) &&
                loc.getX() >= min.getX() && loc.getX() <= max.getX() &&
                loc.getY() >= min.getY() && loc.getY() <= max.getY() &&
                loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ();
    }

    // Getters
    public int getIndex() { return index; }
    public int getSectorIndex() { return sectorIndex; }
    public Location getMin() { return min; }
    public Location getMax() { return max; }
    public Type getType() { return type; }
}