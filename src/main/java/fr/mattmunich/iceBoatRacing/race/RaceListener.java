package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.checkpoint.Checkpoint;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static fr.mattmunich.iceBoatRacing.Main.*;
import static fr.mattmunich.iceBoatRacing.Messages.*;

public class RaceListener implements Listener {

    private final Main main;
    private final RaceManager raceManager;

    public RaceListener(Main main, RaceManager raceManager) {
        this.main = main;
        this.raceManager = raceManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!player.isInsideVehicle()) return;
        if (!(player.getVehicle() instanceof Boat)) return;
        if(!player.getGameMode().equals(GameMode.ADVENTURE)) return;

        RaceData data = null;
        for(Race race : raceManager.activeRaces) {
            if(race.racers.containsKey(player.getUniqueId())) data = race.racers.get(player.getUniqueId());
        }

        if (data == null || data.race == null || data.car == null) {
            player.sendActionBar(Objects.requireNonNull(c("§e§oNo race data")));
            return;
        }

        Race race = data.race;

        //Test the whole movement segment (not just the current point) against checkpoints, and
        //loop rather than checking only one per tick. Looping matters: if two checkpoints sit close
        //together (e.g. a sector gate right next to an auto-generated checkpoint on a narrow bridge),
        //a single tick's movement could cross both — checking only the first would silently strand
        //the racer on the second one forever, since next tick they're already past it.
        Location from = event.getFrom();
        Location to = event.getTo();

        while (true) {
            int nextCheckpointID = data.checkpointIndex + 1;
            Checkpoint nextCheckpoint = race.getCheckpoint(nextCheckpointID);

            if (nextCheckpoint == null) {
//                main.log("Checkpoint with ID " + nextCheckpointID + " is null, going back to 0");
                data.checkpointIndex = 0;
                break;
            }

            if (!nextCheckpoint.crosses(from, to)) break;

            long now = System.currentTimeMillis();

            if (nextCheckpoint.getType().equals(Checkpoint.Type.START_FINISH)) {

                //When starting the race/crossing the start line
                if(data.lapCount==0) data.startTime=now;

                //When completing lap
                if (data.lapCount>0 && !(data.lapCount == race.getLapCount())) onCompleteLap(data, now);

                //When finishing race
                if(data.lapCount == race.getLapCount()) {
                    onCompleteLap(data, now);
                    onFinishRace(data, now);

                    //End race automatically
                    if(race.racing.isEmpty()) {
                        race.sendRanking();
                        race.end();
                    }

                    data.checkpointIndex = 0;
                    break; //racer just finished; nothing more to process for them this tick
                }

                data.lapTime = now;
                data.lapCount++;
                data.checkpointIndex = 0;
            }

            if (nextCheckpoint.getType().equals(Checkpoint.Type.SECTOR)) {
                data.addSectorTime(nextCheckpoint.getSectorID(), now, data.lapTime);
                Bukkit.broadcast(getMessage("race.onCrossSector",
                        formatArguments(
                                "player", l(player.displayName()),
                                "ID", String.valueOf(nextCheckpoint.getSectorID()),
                                "time", formatTime(now-data.lapTime)
                        )
                ));
            }

            data.checkpointIndex++;
            main.liveSidebar.getScore(player).setScore(((data.lapCount-1) * race.getCheckpoints().size()) + data.checkpointIndex);
        }
    }

    private void onCompleteLap(RaceData data, long now) {
        Player player = data.player;
        long lapDuration = now - data.lapTime;
        long result = data.race.isBestLapTimeYet(lapDuration);
        boolean bestLapTimeYet = result != -1;
        data.lapTimes.add(lapDuration);
        if(bestLapTimeYet) {
            Bukkit.broadcast(getMessage("race.onCompleteLap.messageBestLapTimeYet",
                    formatArguments(
                            "player", l(player.displayName()),
                            "ID",  "" + data.lapCount,
                            "time", formatTime(lapDuration)
                    )
            ));
        } else {
            Bukkit.broadcast(getMessage("race.onCompleteLap.message",
                    formatArguments(
                            "player", l(player.displayName()),
                            "ID",  "" + data.lapCount,
                            "time", formatTime(lapDuration)
                    )
            ));
        }
        Title title = Title.title(
                getMessage("race.onCompleteLap.title", formatArguments(
                        "currentLapCount", "" + data.lapCount,
                        "raceLapCount", "" + data.race.getLapCount()
                )), getMessage("race.onCompleteLap.subtitle"));

        player.showTitle(title);
    }

    private static void onFinishRace(RaceData data, long now) {
        Player player = data.player;
        Race race = data.race;
        Map<Integer, RaceData> finished = race.rankings;
        race.racing.remove(data);
        data.endTime= now;
        race.rankings.put(finished.size(), data);

        int ranking = finished.size();
        data.ranking = ranking; //Used for the final ranking later on
        boolean isWinner = ranking==1;

        long raceTime = now - data.startTime;
        long gapToWinner = isWinner ? -1 : finished.get(0).getRaceTime();
        //get(ranking-1) = self ; get(ranking-2) = player #(ranking-1) ; because ranking = size (starts @ 1) and get(x) starts @ 0
        long gapToNext = isWinner ? -1 : finished.get(ranking-2).getRaceTime();
        data.gapToNextTime = gapToNext;

        Map<Integer,Long> bestSectorsTimes = data.bestSectorsTimes();

        int players = race.racers.size();

        Bukkit.broadcast(getMessage("race.onFinish.message",
                formatArguments(
                        "player", l(player.displayName()),
                        "time", formatTime(raceTime),
                        "ranking", ranking + ""
                )
        ));

        Title title = Title.title(
                getMessage("race.onFinish.title"),
                getMessage("race.onFinish.subtitle", formatArguments("ranking", ranking + ""))
        );

        player.showTitle(title);

        Component bestSectorsTimesMessage = c("");
        for (Map.Entry<Integer, Long> entry : bestSectorsTimes.entrySet()) {
            bestSectorsTimesMessage = bestSectorsTimesMessage.append(getMessage("race.onFinish.sectorFormat",formatArguments(
                "sectorID", "" + entry.getKey(),
                    "time", formatTime(entry.getValue())
            )));
        }


        if(isWinner) {
            player.sendMessage(getMessage("race.onFinish.statsMessageWinner",
                    formatArguments(
                            "ranking", ranking + "",
                            "players",  players + "",
                            "raceTime", formatTime(raceTime),
                            "meanLapTime", formatTime(data.meanLapTime()),
                            "bestLapTime", formatTime(data.bestLapTime()),
                            "worstLapTime", formatTime(data.worstLapTime())
                    ),
                    formatComponentArguments(
                            "bestSectorsTimes", bestSectorsTimesMessage
                    )
            ));
        } else {
            player.sendMessage(getMessage("race.onFinish.statsMessage",
                    formatArguments(
                            "ranking", ranking + "",
                            "players",  players + "",
                            "raceTime", formatTime(raceTime),
                            "meanLapTime", formatTime(data.meanLapTime()),
                            "bestLapTime", formatTime(data.bestLapTime()),
                            "worstLapTime", formatTime(data.worstLapTime()),
                            "gapToWinner", formatTime(gapToWinner),
                            "gapToNext", formatTime(gapToNext)
                    ),
                    formatComponentArguments(
                            "bestSectorsTimes", bestSectorsTimesMessage
                    )
            ));
        }

        //TODO make optional via config
        player.setGameMode(GameMode.SPECTATOR);
        try { data.car.destroy(); } catch (Exception ignored) {}
    }
}