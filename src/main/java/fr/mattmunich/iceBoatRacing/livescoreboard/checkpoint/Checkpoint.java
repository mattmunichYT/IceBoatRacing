package fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint;

import org.bukkit.Location;

public class Checkpoint {

    public enum Type {
        NORMAL,
        START_FINISH,
        SECTOR
    }

    int id; // checkpoint index in the race order
    int sectorID = -1; // only used for sectors
    Location min;
    Location max;
    Type type;

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
    public int getId() { return id; }
    public int getSectorID() { return sectorID; }
    public Location getMin() { return min; }
    public Location getMax() { return max; }
    public Type getType() { return type; }
}