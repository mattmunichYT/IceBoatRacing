package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.LiveScoreManager;
import fr.mattmunich.iceBoatRacing.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.pitbox.PitBox;
import fr.mattmunich.iceBoatRacing.pitbox.PitBoxManager;
import fr.mattmunich.iceBoatRacing.stats.StatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;

import static fr.mattmunich.iceBoatRacing.Main.*;
import static fr.mattmunich.iceBoatRacing.Messages.*;

public class RaceListener implements Listener {

    private final RaceManager raceManager;
    private final PitBoxManager pitBoxManager;
    private final LiveScoreManager liveScoreManager;
    private final StatsManager statsManager;

    public RaceListener(RaceManager raceManager, PitBoxManager pitBoxManager, LiveScoreManager liveScoreManager) {
        this(raceManager, pitBoxManager, liveScoreManager, null);
    }

    public RaceListener(RaceManager raceManager, PitBoxManager pitBoxManager, LiveScoreManager liveScoreManager, StatsManager statsManager) {
        this.raceManager = raceManager;
        this.pitBoxManager = pitBoxManager;
        this.liveScoreManager = liveScoreManager;
        this.statsManager = statsManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!player.isInsideVehicle()) return;
        if (!(player.getVehicle() instanceof Boat)) return;

        Racer racer = null;
        for (Race r : raceManager.activeRaces) {
            Racer candidate = r.racers.get(player.getUniqueId());
            if (candidate != null) racer = candidate;
        }

        // Not a racer: stay silent (this event fires for every player in any boat on the server).
        if (racer == null || racer.race == null || racer.car == null) return;

        // Always keep the previous position up to date, even when we return early below.
        // Otherwise, the next segment would span the whole gap and could "cross" checkpoints
        // the racer never actually drove through.
        Location from = racer.from == null ? event.getFrom() : racer.from;
        Location to = event.getTo();
        racer.from = to.clone();

        // Finished racers (SPECTATE_ON_FINISH = false) stay in their boat: ignore them, otherwise
        // crossing the line again would finish the race a second time.
        if (!racer.racing) return;

        Race race = racer.race;

        // Nothing counts during the countdown.
        if (!race.hasStarted()) return;

        if (ENFORCE_RACING_GAMEMODE && !player.getGameMode().equals(RACING_GAMEMODE)) {
            player.sendActionBar(c("§cIllegal GameMode!"));
            return;
        }

        // Test the whole movement segment (not just the current point) against checkpoints, and
        // loop rather than checking only one per tick. Looping matters: if two checkpoints sit close
        // together (e.g. a sector gate right next to an auto-generated checkpoint on a narrow bridge),
        // a single tick's movement could cross both — checking only the first would silently strand
        // the racer on the second one forever, since next tick they're already past it.
        while (true) {
            Checkpoint nextCheckpoint = checkpointAfter(race, racer.checkpointIndex);
            if (nextCheckpoint == null) break; // race has no usable checkpoints

            if (!nextCheckpoint.crosses(from, to)) {
                // Allow skipping 1 checkpoint if detection fails.
                // Often happens when the checkpoints are misplaced.
                Checkpoint afterNext = checkpointAfter(race, nextCheckpoint.getId());
                if (afterNext == null || afterNext == nextCheckpoint || !afterNext.crosses(from, to)) break;

                warn("Allowed " + player.getName() + " to skip checkpoint " + nextCheckpoint.getId() + " as they crossed checkpoint " + afterNext.getId() + " on the racetrack " + race.getName()
                        + "\nIf this happens often, please check the checkpoint placement, how your server is performing and if the player has an unstable connection or some kind of cheats.");
                long now = System.currentTimeMillis();

                if (handleCrossing(racer, nextCheckpoint, now, race, player)) break; // skipped one
                if (handleCrossing(racer, afterNext, now, race, player)) break;      // actually crossed one

                racer.checkpointIndex = afterNext.getId();
                racer.recordProgress(now);
                liveScoreManager.updateRacer(racer);
                break;
            }

            long now = System.currentTimeMillis();
            if (handleCrossing(racer, nextCheckpoint, now, race, player)) break;

            racer.checkpointIndex = nextCheckpoint.getId();
            racer.recordProgress(now);
            liveScoreManager.updateRacer(racer);
        }

        // A racer who just finished must not be able to enter a pit box
        if (racer.racing) {
            handlePitBox(player, racer, race, to);
            sendLapActionBar(player, racer);
        }
    }

    /** Shows the current lap and running lap time (message: race.actionBar.lapInfo in the lang files). */
    private void sendLapActionBar(Player player, Racer racer) {
        if (racer.lapCount <= 0 || racer.lapTime <= 0) return; // hasn't crossed the start line yet
        player.sendActionBar(getMessage("race.actionBar.lapInfo", formatArguments(
                "currentLap", racer.lapCount,
                "lapCount", racer.race.getLapCount(),
                "time", formatTime(System.currentTimeMillis() - racer.lapTime)
        )));
    }

    /**
     * Finds the checkpoint a racer has to cross next: the one with the smallest ID greater than
     * {@code afterId}. Looking up by "smallest bigger ID" (instead of {@code afterId + 1}) keeps
     * working when IDs have gaps from removed checkpoints, and past the last checkpoint it wraps
     * around to the start/finish line so the lap can actually be closed.
     * @return the next checkpoint, or null if the race has no checkpoints / no start/finish line
     */
    public static Checkpoint checkpointAfter(Race race, int afterId) {
        Checkpoint next = null;
        Checkpoint first = null;
        for (Checkpoint cp : race.getCheckpoints()) {
            if (first == null || cp.getId() < first.getId()) first = cp;
            if (cp.getId() > afterId && (next == null || cp.getId() < next.getId())) next = cp;
        }
        if (next != null) return next;
        return first != null && first.getType() == Checkpoint.Type.START_FINISH ? first : null;
    }

    public static int firstCheckpointId(Race race) {
        return race.getCheckpoints().stream().mapToInt(Checkpoint::getId).min().orElse(1);
    }

    /**
     * @return true if the checkpoint was a start/finish line, which stops the checkpoint loop for this tick
     */
    private boolean handleCrossing(Racer racer, Checkpoint checkpoint, long now, Race race, Player player) {
        switch (checkpoint.getType()) {
            case START_FINISH -> {
                crossStartFinish(racer, now, race);
                return true;
            }
            case SECTOR -> crossSector(racer, checkpoint, now, player);
            default -> { }
        }
        return false;
    }

    /// # Cross start finish
    /// _The method that handles the event of a player crossing the start/finish line._
    /// ---
    /// The system resembles to how Mario Cart handles laps, but it is in no way a copy, this is code is
    /// in no way the code Mario Cart and has been written by me (mattmunich).
    ///
    /// What I mean by that is: when you start your final lap, a title saying "3/3" (for example) will be sent,
    /// indicating that you are starting the 3rd lap out of 3 (in this example), which is the final lap.
    ///
    /// #### 1. Handles when a player starts the race: they cross the start line for the first time
    /// -> Lap count is set to 1 below.
    ///
    /// #### 2. Complete lap is handles by [onCompleteLap]
    ///
    /// #### 3. Handles when a player finishes the race
    /// > -> run [onCompleteLap] <br/>
    /// > -> run [onFinishRace] <br/>
    /// > -> Check if there are no more racers <br/>
    /// >   -> If so: send the ranking with [Race#sendRanking] and end the race using [Race#end]
    ///
    /// #### Finally:
    /// - update {@code lapTime} to `now`
    /// - update {@code lapCount} to += 1
    /// - update {@code checkpointIndex} to the first checkpoint's ID (so that we handle the checkpoint after it on next {@link PlayerMoveEvent})
    /// - update the SideBar
    ///
    /// @param racer The racer's data
    /// @param now The current time in millis
    /// @param race The race (like what else am I meant to say)
    private void crossStartFinish(Racer racer, long now, Race race) {
        if (!racer.racing) return;

        //1. When starting the race = crossing the start line for the first time
        // -> lapCount is set to 1 below
        if (racer.lapCount == 0) racer.startTime = now;

        //2. Complete lap
        if (racer.lapCount > 0 && racer.lapCount != race.getLapCount()) onCompleteLap(racer, now);

        //3. Finish race
        if (racer.lapCount == race.getLapCount()) {
            onCompleteLap(racer, now);
            onFinishRace(racer, now);

            //End race automatically
            if (race.racing.isEmpty()) {
                try {
                    race.end(true);
                } catch (Exception e) {
                    switch (String.valueOf(e.getMessage())) {
                        case "NO_RANKING" -> Bukkit.getConsoleSender().sendMessage(getMessage("race.onEnd.error.noRanking"));
                        case "NO_RACERS" -> Bukkit.getConsoleSender().sendMessage(getMessage("race.onEnd.error.noRacers"));
                        default -> {
                            Bukkit.getConsoleSender().sendMessage(getMessage("error.unknown"));
                            err("Could not send ranking on race end", e);
                        }
                    }
                }
            }

            racer.checkpointIndex = 0;
            return;  //racer just finished; nothing more to process for them this tick
        }

        racer.lapTime = now;
        racer.lapCount++;
        racer.checkpointIndex = firstCheckpointId(race);
        racer.recordProgress(now);
        liveScoreManager.updateRacer(racer);
    }

    private static void crossSector(Racer racer, Checkpoint nextCheckpoint, long now, Player player) {
        racer.addSectorTime(nextCheckpoint.getSectorID(), now, racer.lapTime);
        Bukkit.broadcast(getMessage("race.onCrossSector",
                formatArguments(
                        "player", l(player.displayName()),
                        "ID", String.valueOf(nextCheckpoint.getSectorID()),
                        "time", formatTime(now - racer.lapTime)
                )
        ));
    }

    private void onCompleteLap(Racer racer, long now) {
        Player player = racer.player;
        long lapDuration = now - racer.lapTime;
        if (lapDuration <= 0) return;
        long result = racer.race.isBestLapTimeYet(lapDuration);
        boolean bestLapTimeYet = result != -1;
        racer.lapTimes.add(lapDuration);

        // --- Statistics recording (official only; guard against missing manager) ---
        if (statsManager != null && player != null) {
            statsManager.recordOfficialLap(
                    player.getUniqueId(),
                    racer.race.getName(),
                    lapDuration,
                    racer.lapCount   // 1-based lap number being completed
            );
        }
        // -------------------------------------------------------------------------

        if (bestLapTimeYet) {
            Bukkit.broadcast(getMessage("race.onCompleteLap.messageBestLapTimeYet",
                    formatArguments(
                            "player", l(player.displayName()),
                            "ID", "" + racer.lapCount,
                            "time", formatTime(lapDuration)
                    )
            ));
        } else {
            Bukkit.broadcast(getMessage("race.onCompleteLap.message",
                    formatArguments(
                            "player", l(player.displayName()),
                            "ID", "" + racer.lapCount,
                            "time", formatTime(lapDuration)
                    )
            ));
        }
        Title title = Title.title(
                getMessage("race.onCompleteLap.title", formatArguments(
                        "currentLapCount", "" + racer.lapCount,
                        "raceLapCount", "" + racer.race.getLapCount()
                )), getMessage("race.onCompleteLap.subtitle"));

        player.showTitle(title);
    }

    private void onFinishRace(Racer racer, long now) {
        Player player = racer.player;
        Race race = racer.race;

        liveScoreManager.removeRacer(racer); //make sure that the last players are visible

        Map<Integer, Racer> finished = race.rankings;
        race.racing.remove(racer);
        racer.endTime = now;
        racer.racing = false;
        race.rankings.put(finished.size(), racer);

        int ranking = finished.size();
        racer.ranking = ranking; //Used for the final ranking later on
        racer.disqualified = racer.pitStopsCompleted < race.getRequiredPitStops();
        boolean isWinner = ranking == 1;

        long raceTime = now - racer.startTime;

        // --- Statistics recording (only for non-disqualified finishers) ----------
        if (statsManager != null && player != null && !racer.disqualified) {
            statsManager.recordOfficialRaceFinished(player.getUniqueId(), race.getName(), raceTime);
        }
        // -------------------------------------------------------------------------

        long gapToWinner = isWinner ? -1 : (raceTime - finished.get(0).getRaceTime());
        //get(ranking-1) = self ; get(ranking-2) = player #(ranking-1) ; because ranking = size (starts @ 1) and get(x) starts @ 0
        long gapToNext = isWinner ? -1 : (raceTime - finished.get(ranking - 2).getRaceTime());
        racer.gapToNextTime = gapToNext;

        Map<Integer, Long> bestSectorsTimes = racer.bestSectorsTimes();

        int players = race.racers.size();

        Bukkit.broadcast(getMessage("race.onFinish.message",
                formatArguments(
                        "time", formatTime(raceTime),
                        "ranking", ranking + ""
                ),
                formatComponentArguments("player", player.displayName())
        ));

        if (racer.disqualified) {
            Bukkit.broadcast(getMessage("race.onFinish.disqualifiedNotice", formatArguments(
                    "player", player.getName(),
                    "completed", racer.pitStopsCompleted,
                    "required", race.getRequiredPitStops()
            )));
        }

        Title title = Title.title(
                getMessage("race.onFinish.title"),
                getMessage("race.onFinish.subtitle", formatArguments("ranking", ranking + ""))
        );

        player.showTitle(title);

        Component bestSectorsTimesMessage = c("§cNo data");
        if (!bestSectorsTimes.isEmpty()) {
            bestSectorsTimesMessage = c("");
            for (Map.Entry<Integer, Long> entry : bestSectorsTimes.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                bestSectorsTimesMessage = bestSectorsTimesMessage.append(getMessage("race.onFinish.sectorFormat", formatArguments(
                        "sectorID", "" + entry.getKey(),
                        "time", formatTime(entry.getValue())
                )));
            }
        }

        if (isWinner) {
            player.sendMessage(getMessage("race.onFinish.statsMessageWinner",
                    formatArguments(
                            "ranking", ranking + "",
                            "players", players + "",
                            "raceTime", formatTime(raceTime),
                            "meanLapTime", formatTime(racer.meanLapTime()),
                            "bestLapTime", formatTime(racer.bestLapTime()),
                            "worstLapTime", formatTime(racer.worstLapTime())
                    ),
                    formatComponentArguments(
                            "bestSectorsTimes", bestSectorsTimesMessage
                    )
            ));
        } else {
            player.sendMessage(getMessage("race.onFinish.statsMessage",
                    formatArguments(
                            "ranking", ranking + "",
                            "players", players + "",
                            "raceTime", formatTime(raceTime),
                            "meanLapTime", formatTime(racer.meanLapTime()),
                            "bestLapTime", formatTime(racer.bestLapTime()),
                            "worstLapTime", formatTime(racer.worstLapTime()),
                            "gapToWinner", formatTime(gapToWinner),
                            "gapToNext", formatTime(gapToNext)
                    ),
                    formatComponentArguments(
                            "bestSectorsTimes", bestSectorsTimesMessage
                    )
            ));
        }

        if (SPECTATE_ON_FINISH) {
            player.setGameMode(GameMode.SPECTATOR);
            boolean success = racer.car.destroy();
            if (!success && player.getVehicle() != null) player.getVehicle().remove();
        }
    }

    private void handlePitBox(Player player, Racer racer, Race race, Location to) {
        if (racer.pitStopsCompleted >= race.getRequiredPitStops()) return; // done for this race
        if (racer.pittingBox != null) return; // already serving a stop
        long sinceLastPitBox = System.currentTimeMillis() - racer.lastPitBox;
        if (sinceLastPitBox < 30000) return; //10 secs delay before pitting again -> prevent double pitting

        for (PitBox box : race.getPitBoxes()) {
            if (!box.contains(to)) continue;
            if (box.isOccupied()) continue;
            if (!box.isAllowed(player)) continue;

            pitBoxManager.startSession(player, racer, box);
            return;
        }
    }
}