package fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.*;

public class CheckpointManager {

    private final Main main;
    private  RaceManager raceManager;

    public CheckpointManager(Main main) {
        this.main = main;
    }

    public void setRaceManager(RaceManager raceManager) {
        this.raceManager = raceManager;
    }

    /**
     * Get the checkpoint with a specific ID from a specific race
     * @param race The race that contains the checkpoint
     * @param ID The ID of the checkpoint
     */
    public Checkpoint get(Race race, int ID) {
        return ID < race.getCheckpoints().size() ? race.getCheckpoints().get(ID) : null;
    }

    /**
     * Get all the checkpoints
     * @return a map of all the races as keys and their checkpoint list as values
     */
    public Map<Race, List<Checkpoint>> getAll() {
        Map<Race, List<Checkpoint>> checkpoints = new HashMap<>();
        for (Race race : raceManager.races) {
            checkpoints.put(race, race.getCheckpoints());
        }
        return checkpoints;
    }

    /**
    * Get all the checkpoints in the form of a list
     *@return a list of all the checkpoints
    */
    public List<Checkpoint> getAllNoRaceInfo() {
        List<Checkpoint> checkpoints = new ArrayList<>();
        for (Race race : raceManager.races) {
            checkpoints.addAll(race.getCheckpoints());
        }
        return checkpoints;
    }

    /**
     * Will return a checkpoint found at the location
     * @param loc The location where to look for checkpoints
     * @return Returns in the form of a map of the race and the checkpoint found
     *
     */
    public Map<Race, Checkpoint> getAt(Location loc) {
        for(Map.Entry<Race, List<Checkpoint>> entry : getAll().entrySet()) {
            for (Checkpoint checkpoint : entry.getValue()) {
                if (checkpoint.contains(loc)) {
                    Map<Race,Checkpoint> result = new  HashMap<>();
                    result.put(entry.getKey(), checkpoint);
                    return result;
                }
            }
        }


        return null;
    }

    public boolean remove(Race race, Checkpoint checkpoint) {
        if (checkpoint == null) return false;

        YamlConfiguration config = raceManager.getRaceConfig(race);
        if(config == null) {
            main.log("§cCheckpoint " + checkpoint.getId() + " for race " + race.getName() + " wasn't removed, see cause above.");
            return false;
        }

        config.set("checkpoints." + checkpoint.getId(), null);
        try {
            raceManager.saveRaceConfig(race, config);
        } catch (IOException e) {
            main.err("Couldn't remove checkpoint " + checkpoint.getId() + " for race " + race.getName() + " because the it's config threw an error on saving. ",e);
            return false;
        }
        return race.removeCheckpoint(checkpoint);
    }

    /**
     * Saves a checkpoint for a race
     * @param race The race that the checkpoint is assigned to
     * @param l1 The 1st location of the checkpoint (pos 1)
     * @param l2 The 2nd location of the checkpoint (pos 2)
     * @param type The checkpoint's type (NORMAL or START_FINISH)
     */
    public Checkpoint saveCheckpoint(Race race, Location l1, Location l2, Checkpoint.Type type) {
        Location min = min(l1, l2);
        Location max = max(l1, l2);

        int ID = race.getCheckpoints().stream()
                .mapToInt(Checkpoint::getId)
                .max()
                .orElse(0) + 1;


        YamlConfiguration config = raceManager.getRaceConfig(race);
        if(config == null) {
            main.log("§cCheckpoint " + ID + " for race " + race.getName() + " wasn't saved, see cause above.");
            return null;
        }

        String path = "checkpoints." + ID;

        config.set(path + ".world", min.getWorld().getName());
        config.set(path + ".min", serialize(min));
        config.set(path + ".max", serialize(max));
        config.set(path + ".type", type.name());

        try {
            raceManager.saveRaceConfig(race, config);
        } catch (IOException e) {
            main.err("Couldn't save checkpoint " + ID + " for race " + race.getName() + " because the it's config threw an error on saving. ",e);
            return null;
        }

        Checkpoint checkpoint = new Checkpoint(ID, min, max, type);
        race.addCheckpoint(checkpoint);
        return checkpoint;
    }

    /**
     * Saves a checkpoint for a race
     * @param race The race that the checkpoint is assigned to
     * @param l1 The 1st location of the checkpoint (pos 1)
     * @param l2 The 2nd location of the checkpoint (pos 2)
     * @return The type of checkpoint that was saved, to figure out which feedback to give to the player
     */
    public Checkpoint saveCheckpoint(Race race, Location l1, Location l2) {
        Location min = min(l1, l2);
        Location max = max(l1, l2);

        int ID = race.getCheckpoints().stream()
                .mapToInt(Checkpoint::getId)
                .max()
                .orElse(0) + 1;

        Checkpoint.Type type = Checkpoint.Type.NORMAL;

        if(ID == 1) {
            type = Checkpoint.Type.START_FINISH;
        }

        YamlConfiguration config = raceManager.getRaceConfig(race);
        if(config == null) {
            main.log("§cCheckpoint " + ID + " for race " + race.getName() + " wasn't saved, see cause above.");
            return null;
        }

        String path = "checkpoints." + ID;

        config.set(path + ".world", min.getWorld().getName());
        config.set(path + ".min", serialize(min));
        config.set(path + ".max", serialize(max));
        config.set(path + ".type", type.name());

        try {
            raceManager.saveRaceConfig(race, config);
        } catch (IOException e) {
            main.err("Couldn't save checkpoint " + ID + " for race " + race.getName() + " because the it's config threw an error on saving. ",e);
            return null;
        }

        Checkpoint checkpoint = new Checkpoint(ID, min, max, type);
        race.addCheckpoint(checkpoint);
        return checkpoint;
    }

    public Checkpoint saveSectorCheckpoint(Race race, Location l1, Location l2) {
        Location min = min(l1, l2);
        Location max = max(l1, l2);

        int ID = race.getCheckpoints().stream()
                .mapToInt(Checkpoint::getId)
                .max()
                .orElse(0) + 1;

        int sectorID = 1;
        for(Checkpoint check : race.getCheckpoints()) {
            //Add 1 for each existing sector
            if(check.getType().equals(Checkpoint.Type.SECTOR)) sectorID++;
        }

        YamlConfiguration config = raceManager.getRaceConfig(race);
        if(config == null) {
            main.log("§cCheckpoint " + ID + " for race " + race.getName() + " wasn't saved, see cause above.");
            return null;
        }

        String path = "checkpoints." + ID;

        config.set(path + ".world", min.getWorld().getName());
        config.set(path + ".min", serialize(min));
        config.set(path + ".max", serialize(max));
        config.set(path + ".type", Checkpoint.Type.SECTOR.name());
        config.set(path + ".sectorID", sectorID);

        try {
            raceManager.saveRaceConfig(race, config);
        } catch (IOException e) {
            main.err("Couldn't save checkpoint " + ID + " for race " + race.getName() + " because the it's config threw an error on saving. ",e);
            return null;
        }

        Checkpoint checkpoint = new Checkpoint(ID, sectorID, min, max);
        race.addCheckpoint(checkpoint);
        return checkpoint;
    }

    public void loadRaceCheckpoints(Race race) {
        YamlConfiguration config = raceManager.getRaceConfig(race);
        if(config == null) {
            main.log("§cCheckpoints for race " + race.getName() + " were not loaded, see cause above.");
            return;
        }

        race.clearCheckpoints();
        if (!config.isConfigurationSection("checkpoints")) {
            main.log("§eNo checkpoints to load for race " + race.getName());
            return;
        }

        for (String key : Objects.requireNonNull(
                config.getConfigurationSection("checkpoints")
        ).getKeys(false)) {

            int ID = Integer.parseInt(key);
            String worldName = config.getString("checkpoints." + key + ".world");

            Location min = deserialize(worldName,
                    config.getString("checkpoints." + key + ".min"));
            Location max = deserialize(worldName,
                    config.getString("checkpoints." + key + ".max"));

            if (min == null || max == null) continue;

            String typeString = config.getString("checkpoints." + key + ".type");
            Checkpoint.Type type = Checkpoint.Type.NORMAL;

            if (typeString != null) {
                type = Checkpoint.Type.valueOf(typeString);
            }

            if (type == Checkpoint.Type.SECTOR) {
                int sectorID = config.getInt("checkpoints." + key + ".sectorID");
                race.addCheckpoint(new Checkpoint(ID, sectorID, min, max));
            } else if (type == Checkpoint.Type.START_FINISH) {
                race.addCheckpoint(new Checkpoint(ID, min, max, Checkpoint.Type.START_FINISH));
            } else {
                race.addCheckpoint(new Checkpoint(ID, min, max));
            }
        }

        race.getCheckpoints().sort(Comparator.comparingInt(Checkpoint::getId));
    }

    private String serialize(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location deserialize(String worldName, String value) {
        if (value == null) return null;

        World world = Bukkit.getWorld(worldName);
//        if (world == null) return null;

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

    public void normalize(Race race) {
        YamlConfiguration config = raceManager.getRaceConfig(race);
        if(config == null) {
            main.log("§cCheckpoints for race " + race.getName() + " were not normalized, see cause above.");
            return;
        }

        List<Checkpoint> checkpoints = race.getCheckpoints();
        // Sort by current ID first
        checkpoints.sort(Comparator.comparingInt(Checkpoint::getId));

        // Clear old config checkpoints section
        config.set("checkpoints", null);

        // Renumber checkpoints sequentially
        for (int i = 0; i < checkpoints.size(); i++) {
            Checkpoint old = checkpoints.get(i);

            // Create a new checkpoint with updated ID
            Checkpoint updated = new Checkpoint(i, old.getMin(), old.getMax());
            checkpoints.set(i, updated);

            // Save to config
            String path = "checkpoints." + i;
            config.set(path + ".world", updated.getMin().getWorld().getName());
            config.set(path + ".min", serialize(updated.getMin()));
            config.set(path + ".max", serialize(updated.getMax()));
        }

        try {
            raceManager.saveRaceConfig(race, config);
        } catch (IOException e) {
            main.err("Couldn't normalize checkpoints for race " + race.getName() + " because the it's config threw an error on saving. ",e);
        }
    }
}