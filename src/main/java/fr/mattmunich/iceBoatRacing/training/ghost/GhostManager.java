package fr.mattmunich.iceBoatRacing.training.ghost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.mattmunich.iceBoatRacing.Main;
import org.bukkit.Bukkit;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static fr.mattmunich.iceBoatRacing.Main.*;

/**
 * Manages storage, persistence, and caching of player ghost trajectories.
 *
 * <p>Persists ghost data to {@code plugins/IceBoatRacing/ghosts/<uuid>/<raceName>.json}.
 */
public class GhostManager {

    private final Main main;
    private final Gson gson;
    private final Map<UUID, Map<String, GhostTrajectory>> cache = new ConcurrentHashMap<>();

    public GhostManager(Main main) {
        this.main = main;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Retrieves the ghost trajectory for the given player on the given race.
     * Loads from disk if not already cached.
     *
     * @param uuid     Player UUID
     * @param raceName Name of the race
     * @return Ghost trajectory, or null if no recorded trajectory exists
     */
    @Nullable
    public GhostTrajectory getTrajectory(UUID uuid, String raceName) {
        Map<String, GhostTrajectory> playerRaces = cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        return playerRaces.computeIfAbsent(raceName, r -> loadFromDisk(uuid, r));
    }

    /**
     * Stores and persists a new personal-best trajectory for a player.
     *
     * @param uuid       Player UUID
     * @param raceName   Name of the race
     * @param trajectory Completed trajectory
     */
    public void saveTrajectory(UUID uuid, String raceName, GhostTrajectory trajectory) {
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(raceName, trajectory);

        // Save to disk asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(main, () -> {
            File file = getGhostFile(uuid, raceName);
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(trajectory, writer);
            } catch (IOException e) {
                err("Could not save ghost trajectory for player " + uuid + " on race " + raceName, e);
            }
        });
    }

    /**
     * Deletes the ghost trajectory for the given player and race.
     */
    public void deleteTrajectory(UUID uuid, String raceName) {
        Map<String, GhostTrajectory> playerRaces = cache.get(uuid);
        if (playerRaces != null) {
            playerRaces.remove(raceName);
        }

        File file = getGhostFile(uuid, raceName);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Clears all cached trajectories (e.g. on plugin reload).
     */
    public void clearCache() {
        cache.clear();
    }

    private File getGhostFile(UUID uuid, String raceName) {
        File folder = new File(new File(main.getDataFolder(), "ghosts"), uuid.toString());
        return new File(folder, sanitize(raceName) + ".json");
    }

    private static String sanitize(String raceName) {
        return raceName.replace(" ", "_");
    }

    @Nullable
    private GhostTrajectory loadFromDisk(UUID uuid, String raceName) {
        File file = getGhostFile(uuid, raceName);
        if (!file.exists()) return null;

        try (FileReader reader = new FileReader(file)) {
            GhostTrajectory trajectory = gson.fromJson(reader, GhostTrajectory.class);
            if (trajectory != null && !trajectory.isEmpty()) {
                return trajectory;
            }
        } catch (Exception e) {
            warn("Could not load ghost file for player " + uuid + " on race " + raceName + ": " + e.getMessage());
        }
        return null;
    }
}
