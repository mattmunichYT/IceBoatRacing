package fr.mattmunich.iceBoatRacing.stats;

import fr.mattmunich.iceBoatRacing.Main;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.*;

/**
 * Central statistics manager for the IceBoatRacing plugin.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Keep an in-memory cache of player statistics (loaded lazily on first access).</li>
 *   <li>Expose a comprehensive API for recording and querying statistics.</li>
 *   <li>Keep {@link SessionType#OFFICIAL} and {@link SessionType#TRAINING} data strictly
 *       separated so training never contaminates competitive statistics.</li>
 *   <li>Delegate file persistence to {@link PlayerStatsStore}.</li>
 *   <li>Avoid unnecessary disk operations during races by debouncing saves and flushing on
 *       race completion, player disconnect, or server shutdown.</li>
 * </ul>
 *
 * <h3>Thread Safety & Persistence</h3>
 * Mutations occur on the server thread. When writing to disk, an immutable deep-copy snapshot
 * of the player's data is created synchronously before dispatching to an asynchronous task,
 * guaranteeing zero {@link java.util.ConcurrentModificationException}s and non-blocking I/O.
 */
public class StatsManager {

    private final Main main;
    private final PlayerStatsStore store;

    /**
     * Cache: UUID → (SessionType → (raceName → stats)).
     * Populated lazily via {@link #getOrLoad(UUID)}.
     */
    private final Map<UUID, Map<SessionType, Map<String, PlayerRaceStats>>> cache = new HashMap<>();

    /** Set of player UUIDs that have unsaved changes in memory. */
    private final Set<UUID> dirtyPlayers = new HashSet<>();

    /** Tracks player UUIDs currently being written to disk to avoid overlapping writes. */
    private final Set<UUID> savingPlayers = Collections.synchronizedSet(new HashSet<>());

    /** Periodic task flushing dirty player statistics. */
    private final BukkitTask autoSaveTask;

    public StatsManager(Main main, int maxHistoryPerRace) {
        this.main  = main;
        this.store = new PlayerStatsStore(main, maxHistoryPerRace);

        // Periodic flush every 30 seconds (600 ticks) to avoid frequent disk operations during racing
        this.autoSaveTask = Bukkit.getScheduler().runTaskTimer(main, this::saveDirtyAsync, 600L, 600L);
    }

    // =========================================================================
    // Write API
    // =========================================================================

    /**
     * Records a completed official lap.
     *
     * @param uuid      UUID of the racer.
     * @param raceName  Name of the race.
     * @param lapTimeMs Duration of the lap in milliseconds.
     * @param lapNumber 1-based lap number within this race.
     */
    public void recordOfficialLap(UUID uuid, String raceName, long lapTimeMs, int lapNumber) {
        recordOfficialLap(uuid, raceName, lapTimeMs, lapNumber, null);
    }

    /**
     * Records a completed official lap with an optional trajectory/replay identifier.
     *
     * @param uuid      UUID of the racer.
     * @param raceName  Name of the race.
     * @param lapTimeMs Duration of the lap in milliseconds.
     * @param lapNumber 1-based lap number within this race.
     * @param replayId  Optional identifier for future ghost replay data.
     */
    public void recordOfficialLap(UUID uuid, String raceName, long lapTimeMs, int lapNumber, @Nullable String replayId) {
        recordLap(uuid, raceName, lapTimeMs, lapNumber, SessionType.OFFICIAL, replayId);
    }

    /**
     * Records a completed training lap.
     *
     * <p>Official race stats are never touched when this method is called.
     *
     * @param uuid      UUID of the racer.
     * @param raceName  Name of the race / training circuit.
     * @param lapTimeMs Duration of the lap in milliseconds.
     * @param lapNumber 1-based lap number within this training session.
     */
    public void recordTrainingLap(UUID uuid, String raceName, long lapTimeMs, int lapNumber) {
        recordTrainingLap(uuid, raceName, lapTimeMs, lapNumber, null);
    }

    /**
     * Records a completed training lap with an optional trajectory/replay identifier.
     *
     * @param uuid      UUID of the racer.
     * @param raceName  Name of the race / training circuit.
     * @param lapTimeMs Duration of the lap in milliseconds.
     * @param lapNumber 1-based lap number within this training session.
     * @param replayId  Optional identifier for future ghost replay data.
     */
    public void recordTrainingLap(UUID uuid, String raceName, long lapTimeMs, int lapNumber, @Nullable String replayId) {
        recordLap(uuid, raceName, lapTimeMs, lapNumber, SessionType.TRAINING, replayId);
    }

    /**
     * Records a completed official race finish for a player.
     *
     * <p>Increments the «completedRaces» counter and optionally updates the
     * fastest-race-time record. Disqualified racers should not be passed here.
     *
     * @param uuid       UUID of the racer.
     * @param raceName   Name of the race.
     * @param raceTimeMs Full race duration in milliseconds (start → finish).
     */
    public void recordOfficialRaceFinished(UUID uuid, String raceName, long raceTimeMs) {
        PlayerRaceStats stats = getOrCreate(uuid, raceName, SessionType.OFFICIAL);
        stats.recordRaceFinished(raceTimeMs);
        markDirty(uuid);
    }

    /**
     * Resets statistics for a player on a specific race (or all races if null).
     *
     * @param uuid     The player's UUID.
     * @param raceName The race name, or {@code null} to reset all races.
     */
    public void resetStats(UUID uuid, @Nullable String raceName) {
        Map<SessionType, Map<String, PlayerRaceStats>> all = getOrLoad(uuid);
        if (raceName == null) {
            for (SessionType t : SessionType.values()) all.get(t).clear();
        } else {
            for (SessionType t : SessionType.values()) all.get(t).remove(raceName);
        }
        markDirty(uuid);
        savePlayerAsync(uuid);
        log("Stats reset for player " + uuid + (raceName == null ? " (all races)" : " on race " + raceName));
    }

    /**
     * Resets statistics for a player on a specific race and specific session type.
     *
     * @param uuid        The player's UUID.
     * @param raceName    The race name, or {@code null} to reset all races for that type.
     * @param sessionType The session type (OFFICIAL or TRAINING).
     */
    public void resetStats(UUID uuid, @Nullable String raceName, SessionType sessionType) {
        Map<SessionType, Map<String, PlayerRaceStats>> all = getOrLoad(uuid);
        Map<String, PlayerRaceStats> typeMap = all.get(sessionType);
        if (typeMap != null) {
            if (raceName == null) {
                typeMap.clear();
            } else {
                typeMap.remove(raceName);
            }
        }
        markDirty(uuid);
        savePlayerAsync(uuid);
        log("Stats (" + sessionType + ") reset for player " + uuid + (raceName == null ? " (all races)" : " on race " + raceName));
    }

    /**
     * Resets statistics for all cached players on a specific race (e.g. when a race is deleted).
     *
     * @param raceName The race name.
     */
    public void resetRaceStats(String raceName) {
        for (Map.Entry<UUID, Map<SessionType, Map<String, PlayerRaceStats>>> entry : cache.entrySet()) {
            boolean changed = false;
            for (SessionType t : SessionType.values()) {
                if (entry.getValue().get(t).remove(raceName) != null) {
                    changed = true;
                }
            }
            if (changed) {
                markDirty(entry.getKey());
                savePlayerAsync(entry.getKey());
            }
        }
    }

    // =========================================================================
    // Read API
    // =========================================================================

    /**
     * Returns the official stats for the given player and race, or {@code null} if none exist.
     */
    @Nullable
    public PlayerRaceStats getOfficialStats(UUID uuid, String raceName) {
        return getStats(uuid, raceName, SessionType.OFFICIAL);
    }

    /**
     * Returns the training stats for the given player and race, or {@code null} if none exist.
     */
    @Nullable
    public PlayerRaceStats getTrainingStats(UUID uuid, String raceName) {
        return getStats(uuid, raceName, SessionType.TRAINING);
    }

    /**
     * Returns the stats for the given player, race and session type, or {@code null} if none exist.
     */
    @Nullable
    public PlayerRaceStats getStats(UUID uuid, String raceName, SessionType sessionType) {
        return getOrLoad(uuid).get(sessionType).get(raceName);
    }

    /**
     * Returns all official stats for the player, keyed by race name.
     */
    public Map<String, PlayerRaceStats> getAllOfficialStats(UUID uuid) {
        return getAllStats(uuid, SessionType.OFFICIAL);
    }

    /**
     * Returns all training stats for the player, keyed by race name.
     */
    public Map<String, PlayerRaceStats> getAllTrainingStats(UUID uuid) {
        return getAllStats(uuid, SessionType.TRAINING);
    }

    /**
     * Returns all stats for the player for the specified session type.
     */
    public Map<String, PlayerRaceStats> getAllStats(UUID uuid, SessionType sessionType) {
        return Collections.unmodifiableMap(getOrLoad(uuid).get(sessionType));
    }

    /**
     * Personal best lap time (official) for the given player and race.
     */
    public OptionalLong getPersonalBestLap(UUID uuid, String raceName) {
        return getPersonalBestLap(uuid, raceName, SessionType.OFFICIAL);
    }

    /**
     * Personal best lap time for the given player, race, and session type.
     */
    public OptionalLong getPersonalBestLap(UUID uuid, String raceName, SessionType sessionType) {
        PlayerRaceStats s = getStats(uuid, raceName, sessionType);
        return s == null ? OptionalLong.empty() : s.getPersonalBestLapMs();
    }

    /**
     * Personal best lap time (training) for the given player and race.
     */
    public OptionalLong getTrainingPersonalBestLap(UUID uuid, String raceName) {
        return getPersonalBestLap(uuid, raceName, SessionType.TRAINING);
    }

    /**
     * Retrieves the best {@link LapRecord} (including replayId if present) from the retained
     * lap history.
     */
    @Nullable
    public LapRecord getBestLapRecord(UUID uuid, String raceName, SessionType sessionType) {
        PlayerRaceStats s = getStats(uuid, raceName, sessionType);
        return s == null ? null : s.getBestLapRecord();
    }

    /**
     * Full lap history (official, most-recent first) for the given player and race.
     */
    public List<LapRecord> getLapHistory(UUID uuid, String raceName) {
        return getLapHistory(uuid, raceName, SessionType.OFFICIAL);
    }

    /**
     * Full lap history for the given player, race, and session type.
     */
    public List<LapRecord> getLapHistory(UUID uuid, String raceName, SessionType sessionType) {
        PlayerRaceStats s = getStats(uuid, raceName, sessionType);
        return s == null ? Collections.emptyList() : s.getLapHistory();
    }

    /**
     * Full lap history (training) for the given player and race.
     */
    public List<LapRecord> getTrainingLapHistory(UUID uuid, String raceName) {
        return getLapHistory(uuid, raceName, SessionType.TRAINING);
    }

    /**
     * Compares two lap times.
     *
     * @param lapAMs First lap time in milliseconds.
     * @param lapBMs Second lap time in milliseconds.
     * @return Negative if {@code lapAMs} is faster, positive if slower, 0 if equal.
     */
    public int compareLapTimes(long lapAMs, long lapBMs) {
        return Long.compare(lapAMs, lapBMs);
    }

    /**
     * Compares a candidate lap time against the player's personal best for that session type.
     *
     * @return Difference in milliseconds (candidate - PB). Negative means candidate is faster than PB.
     *         Returns {@link OptionalLong#empty()} if no prior PB exists.
     */
    public OptionalLong compareWithPersonalBest(UUID uuid, String raceName, long lapTimeMs, SessionType sessionType) {
        OptionalLong pb = getPersonalBestLap(uuid, raceName, sessionType);
        if (pb.isEmpty()) return OptionalLong.empty();
        return OptionalLong.of(lapTimeMs - pb.getAsLong());
    }

    // =========================================================================
    // Persistence & Snapshotting
    // =========================================================================

    /**
     * Saves all dirty player statistics asynchronously.
     * Can be invoked on race completion, periodic task, etc.
     */
    public void saveDirtyAsync() {
        if (dirtyPlayers.isEmpty()) return;

        List<UUID> toSave;
        synchronized (dirtyPlayers) {
            toSave = new ArrayList<>(dirtyPlayers);
            dirtyPlayers.clear();
        }

        for (UUID uuid : toSave) {
            savePlayerAsync(uuid);
        }
    }

    /**
     * Saves a specific player's statistics asynchronously.
     */
    public void savePlayerAsync(UUID uuid) {
        Map<SessionType, Map<String, PlayerRaceStats>> snapshot = createSnapshot(uuid);
        if (snapshot.isEmpty()) return;

        // Skip if already in the middle of being written to disk
        if (!savingPlayers.add(uuid)) {
            markDirty(uuid);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(main, () -> {
            try {
                store.save(uuid, snapshot);
            } finally {
                savingPlayers.remove(uuid);
            }
        });
    }

    /**
     * Saves a specific player synchronously.
     */
    public void savePlayerSync(UUID uuid) {
        Map<SessionType, Map<String, PlayerRaceStats>> snapshot = createSnapshot(uuid);
        if (!snapshot.isEmpty()) {
            store.save(uuid, snapshot);
        }
        dirtyPlayers.remove(uuid);
    }

    /**
     * Saves all cached stats synchronously. Called on plugin shutdown or reload.
     */
    public void saveAll() {
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }
        log("Saving all player statistics...");
        int count = 0;
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            savePlayerSync(uuid);
            count++;
        }
        log("Saved statistics for " + count + " player(s).");
    }

    /**
     * Evicts a player from the in-memory cache after saving their data.
     */
    public void saveAndEvict(UUID uuid) {
        savePlayerSync(uuid);
        cache.remove(uuid);
    }

    /**
     * Evicts a player from the in-memory cache without saving.
     */
    public void evict(UUID uuid) {
        cache.remove(uuid);
        dirtyPlayers.remove(uuid);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void recordLap(UUID uuid, String raceName, long lapTimeMs, int lapNumber, SessionType type, @Nullable String replayId) {
        LapRecord record = new LapRecord(lapTimeMs, Instant.now(), raceName, lapNumber, type, replayId);
        PlayerRaceStats stats = getOrCreate(uuid, raceName, type);
        stats.recordLap(record);
        markDirty(uuid);
    }

    private void markDirty(UUID uuid) {
        synchronized (dirtyPlayers) {
            dirtyPlayers.add(uuid);
        }
    }

    private PlayerRaceStats getOrCreate(UUID uuid, String raceName, SessionType type) {
        return getOrLoad(uuid)
                .get(type)
                .computeIfAbsent(raceName, k -> new PlayerRaceStats(storeMaxHistory()));
    }

    private int storeMaxHistory() {
        return Main.STATS_MAX_HISTORY > 0 ? Main.STATS_MAX_HISTORY : PlayerRaceStats.DEFAULT_MAX_HISTORY;
    }

    private Map<SessionType, Map<String, PlayerRaceStats>> getOrLoad(UUID uuid) {
        return cache.computeIfAbsent(uuid, store::load);
    }

    /**
     * Creates an isolated, thread-safe snapshot of all stats for a player on the main thread.
     */
    private Map<SessionType, Map<String, PlayerRaceStats>> createSnapshot(UUID uuid) {
        Map<SessionType, Map<String, PlayerRaceStats>> original = cache.get(uuid);
        if (original == null) return Collections.emptyMap();

        Map<SessionType, Map<String, PlayerRaceStats>> copy = new EnumMap<>(SessionType.class);
        for (Map.Entry<SessionType, Map<String, PlayerRaceStats>> typeEntry : original.entrySet()) {
            Map<String, PlayerRaceStats> raceMapCopy = new HashMap<>();
            for (Map.Entry<String, PlayerRaceStats> raceEntry : typeEntry.getValue().entrySet()) {
                raceMapCopy.put(raceEntry.getKey(), raceEntry.getValue().copy());
            }
            copy.put(typeEntry.getKey(), raceMapCopy);
        }
        return copy;
    }
}
