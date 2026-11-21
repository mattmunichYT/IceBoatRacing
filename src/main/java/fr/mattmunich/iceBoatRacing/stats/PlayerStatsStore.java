package fr.mattmunich.iceBoatRacing.stats;

import fr.mattmunich.iceBoatRacing.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static fr.mattmunich.iceBoatRacing.Main.*;

/**
 * Handles reading and writing {@link PlayerRaceStats} to disk.
 *
 * <h3>File layout</h3>
 * One YAML file per player, stored under {@code plugins/IceBoatRacing/stats/<uuid>.yml}.
 *
 * <pre>
 * # <uuid>.yml
 * version: 1
 * official:
 *   MonzaGP:
 *     completedRaces: 3
 *     completedLaps: 30
 *     personalBestLapMs: 54321
 *     totalLapTimeMs: 1800000
 *     fastestRaceMs: 600000
 *     lapHistory:
 *       - lapTimeMs: 54321
 *         timestamp: 2026-11-21T10:00:00Z
 *         raceName: MonzaGP
 *         lapNumber: 3
 *         replayId: optional-trajectory-id
 * training:
 *   MonzaGP:
 *     ...same structure...
 * </pre>
 *
 * <h3>Corruption handling</h3>
 * Any field that cannot be parsed is silently skipped; the remaining data is preserved.
 * A file that cannot be loaded at all is treated as empty (a warning is logged).
 */
public class PlayerStatsStore {

    private static final int FORMAT_VERSION = 1;

    private final Main main;
    private final int maxHistory;

    public PlayerStatsStore(Main main, int maxHistory) {
        this.main = main;
        this.maxHistory = maxHistory;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Saves all stats for a single player.
     *
     * @param uuid  The player's UUID.
     * @param stats Map keyed by {@code SessionType → (raceName → stats)}.
     */
    public void save(UUID uuid, Map<SessionType, Map<String, PlayerRaceStats>> stats) {
        File file = getPlayerFile(uuid);
        file.getParentFile().mkdirs();

        YamlConfiguration config = new YamlConfiguration();
        config.set("version", FORMAT_VERSION);

        for (SessionType type : SessionType.values()) {
            Map<String, PlayerRaceStats> byRace = stats.get(type);
            if (byRace == null || byRace.isEmpty()) continue;

            String typeKey = type.name().toLowerCase();
            for (Map.Entry<String, PlayerRaceStats> entry : byRace.entrySet()) {
                String path = typeKey + "." + sanitize(entry.getKey());
                writeStats(config, path, entry.getValue());
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            err("Could not save stats for player " + uuid, e);
        }
    }

    /**
     * Loads all stats for a single player.
     *
     * @param uuid The player's UUID.
     * @return Map keyed by {@code SessionType → (raceName → stats)}, never null.
     */
    public Map<SessionType, Map<String, PlayerRaceStats>> load(UUID uuid) {
        Map<SessionType, Map<String, PlayerRaceStats>> result = new HashMap<>();
        for (SessionType t : SessionType.values()) result.put(t, new HashMap<>());

        File file = getPlayerFile(uuid);
        if (!file.exists()) return result;

        YamlConfiguration config;
        try {
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            warn("Could not load stats file for player " + uuid + ": " + e.getMessage());
            return result;
        }

        for (SessionType type : SessionType.values()) {
            String typeKey = type.name().toLowerCase();
            ConfigurationSection typeSection = config.getConfigurationSection(typeKey);
            if (typeSection == null) continue;

            for (String raceKey : typeSection.getKeys(false)) {
                ConfigurationSection section = typeSection.getConfigurationSection(raceKey);
                if (section == null) continue;

                PlayerRaceStats s = readStats(section, raceKey, type);
                result.get(type).put(raceKey, s);
            }
        }

        return result;
    }

    /**
     * Deletes the stats file for the given player.
     *
     * @return {@code true} if the file was deleted, {@code false} if it didn't exist or deletion failed.
     */
    public boolean delete(UUID uuid) {
        return getPlayerFile(uuid).delete();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private File getPlayerFile(UUID uuid) {
        return new File(new File(main.getDataFolder(), "stats"), uuid + ".yml");
    }

    /** Sanitizes a race name for use as a YAML key (spaces → underscores). */
    private static String sanitize(String raceName) {
        return raceName.replace(" ", "_");
    }

    private void writeStats(YamlConfiguration config, String path, PlayerRaceStats s) {
        config.set(path + ".completedRaces",   s.getCompletedRaces());
        config.set(path + ".completedLaps",    s.getCompletedLaps());
        config.set(path + ".personalBestLapMs", s.getRawPersonalBestLapMs());
        config.set(path + ".totalLapTimeMs",   s.getTotalLapTimeMs());
        config.set(path + ".fastestRaceMs",    s.getRawFastestRaceMs());

        for (int i = 0; i < s.getLapHistory().size(); i++) {
            LapRecord r = s.getLapHistory().get(i);
            String base = path + ".lapHistory." + i;
            config.set(base + ".lapTimeMs",  r.lapTimeMs());
            config.set(base + ".timestamp",  r.timestamp().toString());
            config.set(base + ".raceName",   r.raceName());
            config.set(base + ".lapNumber",  r.lapNumber());
            if (r.replayId() != null) {
                config.set(base + ".replayId", r.replayId());
            }
        }
    }

    private PlayerRaceStats readStats(ConfigurationSection section, String raceKey, SessionType sessionType) {
        PlayerRaceStats s = new PlayerRaceStats(maxHistory);

        // Aggregates
        s.setCompletedRaces(section.getInt("completedRaces", 0));
        s.setCompletedLaps(section.getInt("completedLaps", 0));
        s.setPersonalBestLapMs(section.getLong("personalBestLapMs", Long.MAX_VALUE));
        s.setTotalLapTimeMs(section.getLong("totalLapTimeMs", 0));
        s.setFastestRaceMs(section.getLong("fastestRaceMs", Long.MAX_VALUE));

        // Lap history
        ConfigurationSection histSection = section.getConfigurationSection("lapHistory");
        if (histSection != null) {
            for (String idx : histSection.getKeys(false)) {
                ConfigurationSection lapSec = histSection.getConfigurationSection(idx);
                if (lapSec == null) continue;
                try {
                    long lapTimeMs = lapSec.getLong("lapTimeMs");
                    Instant timestamp = Instant.parse(lapSec.getString("timestamp", Instant.EPOCH.toString()));
                    String raceName   = lapSec.getString("raceName", raceKey);
                    int lapNumber     = lapSec.getInt("lapNumber", 1);
                    String replayId   = lapSec.getString("replayId", null);
                    s.addLapHistoryRecord(new LapRecord(lapTimeMs, timestamp, raceName, lapNumber, sessionType, replayId));
                } catch (Exception e) {
                    warn("Skipping corrupt lap history entry " + idx + " for race " + raceKey + ": " + e.getMessage());
                }
            }
        }

        return s;
    }
}
