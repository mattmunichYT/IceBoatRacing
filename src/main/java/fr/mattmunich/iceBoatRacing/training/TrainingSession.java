package fr.mattmunich.iceBoatRacing.training;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceListener;
import fr.mattmunich.iceBoatRacing.stats.StatsManager;
import fr.mattmunich.iceBoatRacing.training.ghost.GhostManager;
import fr.mattmunich.iceBoatRacing.training.ghost.GhostRecorder;
import fr.mattmunich.iceBoatRacing.training.ghost.GhostReplay;
import fr.mattmunich.iceBoatRacing.training.ghost.GhostTrajectory;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.OptionalLong;

import static fr.mattmunich.iceBoatRacing.Main.*;
import static fr.mattmunich.iceBoatRacing.Messages.*;

/**
 * Manages an individual player's training session on a race track.
 */
public class TrainingSession {

    private final Main main;
    private final Player player;
    private final Race race;
    private final StatsManager statsManager;
    private final GhostManager ghostManager;
    private final Scoreboard trainingScoreboard;

    private Boat boat;
    private final Location startLocation;
    private final float startRotation;

    private boolean preparing = false;
    private boolean started = false;

    private int checkpointIndex = -1;
    private int lapCount = 0;
    private long startTime = 0L;
    private long lapTime = 0L;
    private Location from = null;

    private GhostTrajectory ghostTrajectory;
    private GhostReplay ghostReplay;
    private GhostRecorder currentRecorder;
    private int lastMatchedGhostIndex = 0;
    private boolean ghostEnabled = true;

    private BukkitTask countdownTask = null;
    private BukkitTask tickTask = null;

    public TrainingSession(Main main, Player player, Race race, Boat boat,
                           Location startLocation, float startRotation,
                           StatsManager statsManager, GhostManager ghostManager,
                           Scoreboard trainingScoreboard) {
        this.main = main;
        this.player = player;
        this.race = race;
        this.boat = boat;
        this.startLocation = startLocation;
        this.startRotation = startRotation;
        this.statsManager = statsManager;
        this.ghostManager = ghostManager;
        this.trainingScoreboard = trainingScoreboard;

        // Load personal best ghost trajectory if available
        this.ghostTrajectory = ghostManager.getTrajectory(player.getUniqueId(), race.getName());
        if (this.ghostTrajectory != null) {
            this.ghostReplay = new GhostReplay(main, player, race.getWorld(), ghostTrajectory);
        }
    }

    // -------------------------------------------------------------------------
    // Session Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the 3-2-1-GO! countdown before releasing the player.
     */
    public void startCountdown() {
        preparing = true;
        started = false;

        final int[] step = {3};
        countdownTask = Bukkit.getScheduler().runTaskTimer(main, () -> {
            if (!player.isOnline() || boat == null || !boat.isValid()) {
                cancelTasks();
                return;
            }

            if (step[0] > 0) {
                String color = switch (step[0]) {
                    case 3 -> "§c";
                    case 2 -> "§e";
                    case 1 -> "§a";
                    default -> "§f";
                };
                Title title = Title.title(c(color + "§l" + step[0]), c(""));
                player.showTitle(title);
                player.playSound(Sound.sound(Key.key("minecraft:block.note_block.pling"), Sound.Source.NEUTRAL, 1.0f, 1.0f));
                step[0]--;
            } else {
                Title title = Title.title(c("§3§lGO!"), c(""));
                player.showTitle(title);
                player.playSound(Sound.sound(Key.key("minecraft:block.note_block.pling"), Sound.Source.NEUTRAL, 1.0f, 2.0f));

                preparing = false;
                started = true;
                if (countdownTask != null) {
                    countdownTask.cancel();
                    countdownTask = null;
                }
            }
        }, 0L, 20L);

        startTickTask();
    }

    /**
     * Periodic 1-tick task updating ghost playback, delta calculation, and keeping the boat pinned
     * during countdown.
     */
    private void startTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(main, this::tick, 1L, 1L);
    }

    private void tick() {
        if (!player.isOnline() || boat == null || !boat.isValid()) {
            cleanup();
            return;
        }

        // Hold player at start position during countdown
        if (preparing) {
            Location holdLoc = startLocation.clone();
            holdLoc.setRotation(startRotation, 0f);
            boat.teleport(holdLoc);
            boat.setVelocity(new Vector(0, 0, 0));
            return;
        }

        if (!started) return;

        // When racing a timed lap
        if (lapCount > 0 && lapTime > 0) {
            long now = System.currentTimeMillis();
            long elapsed = now - lapTime;

            // Record movement sample into current lap recorder
            if (currentRecorder != null) {
                currentRecorder.record(boat.getLocation(), elapsed);
            }

            // Update ghost playback
            if (ghostEnabled && ghostReplay != null) {
                ghostReplay.update(elapsed);
            }

            // Delta against personal best ghost
            updateDeltaActionBar(elapsed);
        }
    }

    /**
     * Computes the real-time time difference against the ghost trajectory and updates the action bar.
     */
    private void updateDeltaActionBar(long elapsedMs) {
        if (ghostTrajectory != null && !ghostTrajectory.isEmpty()) {
            Location loc = boat.getLocation();
            int matchedIdx = ghostTrajectory.findClosestPointIndex(loc.getX(), loc.getY(), loc.getZ(), lastMatchedGhostIndex);
            lastMatchedGhostIndex = matchedIdx;

            long ghostElapsed = ghostTrajectory.getPoints().get(matchedIdx).elapsedMs();
            long delta = elapsedMs - ghostElapsed;

            String deltaStr;
            if (delta < 0) {
                deltaStr = "§a▲ -" + formatDelta(Math.abs(delta));
            } else if (delta > 0) {
                deltaStr = "§c▼ +" + formatDelta(delta);
            } else {
                deltaStr = "§e■ ±0.00s";
            }

            player.sendActionBar(getMessage("race.train.actionBar.withGhost", formatArguments(
                    "currentLap", lapCount,
                    "time", formatTime(elapsedMs),
                    "delta", deltaStr,
                    "pb", formatTime(ghostTrajectory.getLapTimeMs())
            )));
        } else {
            OptionalLong pbOpt = statsManager.getTrainingPersonalBestLap(player.getUniqueId(), race.getName());
            String pbStr = pbOpt.isPresent() ? formatTime(pbOpt.getAsLong()) : "None";

            player.sendActionBar(getMessage("race.train.actionBar.noGhost", formatArguments(
                    "currentLap", lapCount,
                    "time", formatTime(elapsedMs),
                    "pb", pbStr
            )));
        }
    }

    // -------------------------------------------------------------------------
    // Checkpoint & Lap Events
    // -------------------------------------------------------------------------

    /**
     * Handles crossing the START_FINISH line.
     */
    public void crossStartFinish(long now) {
        if (!started) return;

        // 1. First crossing: lap 1 begins
        if (lapCount == 0) {
            lapCount = 1;
            startTime = now;
            lapTime = now;
            checkpointIndex = RaceListener.firstCheckpointId(race);
            from = null;
            lastMatchedGhostIndex = 0;

            startNewLapRecording();
            restartGhostReplay();

            player.playSound(Sound.sound(Key.key("minecraft:block.note_block.bell"), Sound.Source.NEUTRAL, 1.0f, 1.2f));
            return;
        }

        // 2. Lap completion
        long lapDuration = now - lapTime;
        if (lapDuration <= 0) return;

        GhostTrajectory newTrajectory = null;
        if (currentRecorder != null) {
            newTrajectory = currentRecorder.finish(lapDuration);
        }

        // Query previous PB
        OptionalLong pbOpt = statsManager.getTrainingPersonalBestLap(player.getUniqueId(), race.getName());
        boolean isNewPb = pbOpt.isEmpty() || lapDuration < pbOpt.getAsLong();

        // Record in training statistics
        String replayId = isNewPb ? "ghost_" + player.getUniqueId() + "_" + race.getName().replace(" ", "_") : null;
        statsManager.recordTrainingLap(player.getUniqueId(), race.getName(), lapDuration, lapCount, replayId);

        if (isNewPb && newTrajectory != null) {
            // Save and become new ghost
            ghostManager.saveTrajectory(player.getUniqueId(), race.getName(), newTrajectory);
            setGhostTrajectory(newTrajectory);

            long delta = pbOpt.isPresent() ? pbOpt.getAsLong() - lapDuration : 0;
            String deltaStr = pbOpt.isPresent() ? formatDelta(delta) : "first lap";

            player.sendMessage(getMessage("race.train.onCompleteLap.messagePB", formatArguments(
                    "ID", lapCount,
                    "time", formatTime(lapDuration),
                    "delta", deltaStr
            )));

            Title title = Title.title(
                    getMessage("race.train.onCompleteLap.titlePB"),
                    getMessage("race.train.onCompleteLap.subtitlePB", formatArguments("time", formatTime(lapDuration)))
            );
            player.showTitle(title);
            player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.NEUTRAL, 1.0f, 1.0f));
        } else {
            long delta = pbOpt.isPresent() ? lapDuration - pbOpt.getAsLong() : 0;
            String deltaStr = "+" + formatDelta(delta);
            String pbStr = pbOpt.isPresent() ? formatTime(pbOpt.getAsLong()) : "N/A";

            player.sendMessage(getMessage("race.train.onCompleteLap.message", formatArguments(
                    "ID", lapCount,
                    "time", formatTime(lapDuration),
                    "pb", pbStr,
                    "delta", deltaStr
            )));

            Title title = Title.title(
                    getMessage("race.train.onCompleteLap.title", formatArguments("lap", lapCount)),
                    getMessage("race.train.onCompleteLap.subtitle", formatArguments("time", formatTime(lapDuration)))
            );
            player.showTitle(title);
            player.playSound(Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.NEUTRAL, 1.0f, 1.0f));
        }

        // Advance to next lap
        lapCount++;
        lapTime = now;
        checkpointIndex = RaceListener.firstCheckpointId(race);
        from = null;
        lastMatchedGhostIndex = 0;

        startNewLapRecording();
        restartGhostReplay();
    }

    /**
     * Handles crossing a SECTOR checkpoint.
     */
    public void crossSector(Checkpoint cp, long now) {
        if (!started || lapTime <= 0) return;
        long sectorTime = now - lapTime;
        player.sendMessage(getMessage("race.onCrossSector", formatArguments(
                "player", player.getName(),
                "ID", cp.getSectorID(),
                "time", formatTime(sectorTime)
        )));
        player.playSound(Sound.sound(Key.key("minecraft:block.note_block.hat"), Sound.Source.NEUTRAL, 0.8f, 1.2f));
    }

    // -------------------------------------------------------------------------
    // Control Commands
    // -------------------------------------------------------------------------

    /**
     * Restarts the training session back at the starting grid.
     */
    public void restart() {
        cancelTasks();

        if (ghostReplay != null) {
            ghostReplay.reset();
        }

        lapCount = 0;
        lapTime = 0L;
        startTime = 0L;
        checkpointIndex = -1;
        from = null;
        lastMatchedGhostIndex = 0;
        currentRecorder = null;

        // Teleport boat and player to start
        if (boat != null && boat.isValid()) {
            Location holdLoc = startLocation.clone();
            holdLoc.setRotation(startRotation, 0f);
            boat.teleport(holdLoc);
            boat.setVelocity(new Vector(0, 0, 0));
            player.teleport(holdLoc);
            boat.addPassenger(player);
        }

        player.sendMessage(getMessage("race.train.restart", formatArguments("race", race.getName())));
        startCountdown();
    }

    /**
     * Toggles ghost replay playback for this session.
     *
     * @return New ghost replay state (true if enabled, false if disabled)
     */
    public boolean toggleGhost() {
        ghostEnabled = !ghostEnabled;
        if (!ghostEnabled && ghostReplay != null) {
            ghostReplay.cleanup();
        } else if (ghostEnabled && ghostTrajectory != null) {
            if (ghostReplay == null) {
                ghostReplay = new GhostReplay(main, player, race.getWorld(), ghostTrajectory);
            }
            if (started && lapCount > 0) {
                ghostReplay.reset();
            }
        }
        return ghostEnabled;
    }

    /**
     * Sets a new ghost trajectory (e.g. after beating the personal best).
     */
    public void setGhostTrajectory(GhostTrajectory newTrajectory) {
        this.ghostTrajectory = newTrajectory;
        if (ghostReplay == null) {
            ghostReplay = new GhostReplay(main, player, race.getWorld(), newTrajectory);
        } else {
            ghostReplay.setTrajectory(newTrajectory);
        }
    }

    public void startNewLapRecording() {
        currentRecorder = new GhostRecorder(player.getUniqueId(), race.getName());
    }

    public void restartGhostReplay() {
        if (ghostEnabled && ghostReplay != null) {
            ghostReplay.reset();
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    public void cancelTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public void cleanup() {
        cancelTasks();

        if (ghostReplay != null) {
            ghostReplay.cleanup();
            ghostReplay = null;
        }

        if (boat != null) {
            try {
                boat.remove();
            } catch (Exception ignored) {}
            boat = null;
        }
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public Player getPlayer() { return player; }
    public Race getRace() { return race; }
    public Boat getBoat() { return boat; }
    public Location getStartLocation() { return startLocation; }
    public float getStartRotation() { return startRotation; }
    public boolean isPreparing() { return preparing; }
    public boolean isStarted() { return started; }
    public int getCheckpointIndex() { return checkpointIndex; }
    public void setCheckpointIndex(int idx) { this.checkpointIndex = idx; }
    public int getLapCount() { return lapCount; }
    public void setLapCount(int v) { this.lapCount = v; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long v) { this.startTime = v; }
    public long getLapTime() { return lapTime; }
    public void setLapTime(long v) { this.lapTime = v; }
    public Location getFrom() { return from; }
    public void setFrom(Location from) { this.from = from; }
    public GhostTrajectory getGhostTrajectory() { return ghostTrajectory; }
    public GhostReplay getGhostReplay() { return ghostReplay; }
    public boolean isGhostEnabled() { return ghostEnabled; }
    public Scoreboard getTrainingScoreboard() { return trainingScoreboard; }

    @Nullable
    public Boat getGhostBoat() {
        return ghostReplay != null ? ghostReplay.getGhostBoat() : null;
    }

    public static String formatDelta(long ms) {
        if (ms < 60000) {
            long seconds = ms / 1000;
            long centis = (ms % 1000) / 10;
            return seconds + "." + String.format("%02d", centis) + "s";
        } else {
            return formatTime(ms);
        }
    }
}
