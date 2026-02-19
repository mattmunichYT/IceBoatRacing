package fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint;

import fr.mattmunich.iceBoatRacing.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class CheckpointManager {

    private final Main main;
    private final List<Checkpoint> checkpoints = new ArrayList<>();

    public CheckpointManager(Main main) {
        this.main = main;
    }

    public void add(Checkpoint checkpoint) {
        checkpoints.removeIf(c -> c.getIndex() == checkpoint.getIndex());
        checkpoints.add(checkpoint);
        checkpoints.sort(Comparator.comparingInt(Checkpoint::getIndex));
    }

    public Checkpoint get(int index) {
        return index < checkpoints.size() ? checkpoints.get(index) : null;
    }

    public List<Checkpoint> getAll() {
        return checkpoints;
    }

    public Checkpoint getAt(Location loc) {
        for (Checkpoint checkpoint : getAll()) {
            if (checkpoint.contains(loc)) {
                return checkpoint;
            }
        }
        return null;
    }

    public void remove(Checkpoint checkpoint) {
        if (checkpoint == null) return;

        checkpoints.remove(checkpoint);
        main.getConfig().set("checkpoints." + checkpoint.getIndex(), null);
        main.saveConfig();
    }

    public void saveCheckpoint(int index, Location l1, Location l2, Checkpoint.Type type) {
        Location min = min(l1, l2);
        Location max = max(l1, l2);

        String path = "checkpoints." + index;

        main.getConfig().set(path + ".world", min.getWorld().getName());
        main.getConfig().set(path + ".min", serialize(min));
        main.getConfig().set(path + ".max", serialize(max));
        main.getConfig().set(path + ".type", type.name()); // store type

        main.saveConfig();

        add(new Checkpoint(index, min, max, type));
    }

    public void saveSectorCheckpoint(int sectorIndex, Location l1, Location l2) {
        int nextIndex = getAll().stream()
                .mapToInt(Checkpoint::getIndex)
                .max().orElse(-1) + 1;

        String path = "checkpoints." + nextIndex;

        main.getConfig().set(path + ".world", l1.getWorld().getName());
        main.getConfig().set(path + ".min", serialize(min(l1, l2)));
        main.getConfig().set(path + ".max", serialize(max(l1, l2)));
        main.getConfig().set(path + ".type", "SECTOR");
        main.getConfig().set(path + ".sectorIndex", sectorIndex);

        main.saveConfig();

        add(new Checkpoint(nextIndex, sectorIndex, min(l1, l2), max(l1, l2)));
    }

    public void loadCheckpoints() {
        checkpoints.clear();

        if (!main.getConfig().isConfigurationSection("checkpoints")) {
            main.log("§eNo checkpoints to load.");
            return;
        }
        int checkpointCount = 0;

        for (String key : Objects.requireNonNull(
                main.getConfig().getConfigurationSection("checkpoints")
        ).getKeys(false)) {

            int index = Integer.parseInt(key);
            String worldName = main.getConfig().getString("checkpoints." + key + ".world");

            Location min = deserialize(worldName,
                    main.getConfig().getString("checkpoints." + key + ".min"));
            Location max = deserialize(worldName,
                    main.getConfig().getString("checkpoints." + key + ".max"));

            if (min == null || max == null) continue;

            String typeString = main.getConfig().getString("checkpoints." + key + ".type");
            Checkpoint.Type type = Checkpoint.Type.NORMAL;

            if (typeString != null) {
                type = Checkpoint.Type.valueOf(typeString);
            }

            if (type == Checkpoint.Type.SECTOR) {
                int sectorIndex = main.getConfig().getInt("checkpoints." + key + ".sectorIndex");
                add(new Checkpoint(index, sectorIndex, min, max));
            } else if (type == Checkpoint.Type.START_FINISH) {
                add(new Checkpoint(index, min, max, Checkpoint.Type.START_FINISH));
            } else {
                add(new Checkpoint(index, min, max));
            }
            checkpointCount++;
        }

        checkpoints.sort(Comparator.comparingInt(Checkpoint::getIndex));
        main.log("Loaded and sorted " + checkpointCount + " checkpoints.");
    }

    private String serialize(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location deserialize(String worldName, String value) {
        if (value == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        String[] parts = value.split(",");
        return new Location(
                world,
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2])
        );
    }

    public int count() {
        return getAll().size();
    }

    private Location min(Location a, Location b) {
        return new Location(a.getWorld(),
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
    }

    private Location max(Location a, Location b) {
        return new Location(a.getWorld(),
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
    }

    public void normalize() {
        // Sort by current index first
        checkpoints.sort(Comparator.comparingInt(Checkpoint::getIndex));

        // Clear old config checkpoints section
        main.getConfig().set("checkpoints", null);

        // Renumber checkpoints sequentially
        for (int i = 0; i < checkpoints.size(); i++) {
            Checkpoint old = checkpoints.get(i);

            // Create a new checkpoint with updated index
            Checkpoint updated = new Checkpoint(i, old.getMin(), old.getMax());
            checkpoints.set(i, updated);

            // Save to config
            String path = "checkpoints." + i;
            main.getConfig().set(path + ".world", updated.getMin().getWorld().getName());
            main.getConfig().set(path + ".min", serialize(updated.getMin()));
            main.getConfig().set(path + ".max", serialize(updated.getMax()));
        }

        main.saveConfig();
    }
}