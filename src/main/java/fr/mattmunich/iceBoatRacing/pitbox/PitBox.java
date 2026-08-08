package fr.mattmunich.iceBoatRacing.pitbox;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;

public class PitBox {

    public enum TaskType {
        TIMED
        // Future: CREATIVE, etc. — kept as an enum now so config doesn't need to migrate later.
    }

    private final int id;
    private final String name;
    private final Location min;
    private final Location max;
    private final TaskType taskType;
    private final int duration; // seconds, only meaningful for TIMED
    private List<String> allowed; // player names, or "*" for anyone racing

    // Runtime-only occupancy state — never persisted, rebuilt as empty on load.
    private UUID occupant;

    public PitBox(int id, String name, Location min, Location max, TaskType taskType, int duration, List<String> allowed) {
        this.id = id;
        this.name = name;
        this.min = min;
        this.max = max;
        this.taskType = taskType;
        this.duration = duration;
        this.allowed = allowed;
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(min.getWorld())) return false;
        return loc.getX() >= min.getX() && loc.getX() <= max.getX() &&
                loc.getY() >= min.getY() && loc.getY() <= max.getY() &&
                loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ();
    }

    public String getName() {
        return name;
    }

    public void setAllowed(List<String> allowed) {
        this.allowed = allowed;
    }

    public boolean isAllowed(String playerName) {
        for (String name : allowed) {
            if (name.equals("*") || name.equalsIgnoreCase(playerName)) return true;
        }
        return false;
    }

    public boolean isOccupied() {
        return occupant != null;
    }

    public UUID getOccupant() {
        return occupant;
    }

    public void setOccupant(UUID occupant) {
        this.occupant = occupant;
    }

    public int getId() {
        return id;
    }

    public Location getMin() {
        return min;
    }

    public Location getMax() {
        return max;
    }

    public Location getCenter() {
        return min.clone().add(max).multiply(0.5);
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public int getDuration() {
        return duration;
    }

    public List<String> getAllowed() {
        return allowed;
    }
}