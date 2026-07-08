package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.Checkpoint;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;
import java.util.Objects;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Main.formatTime;
import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

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

        int nextCheckpointID = data.checkpointIndex+1;
        Checkpoint nextCheckpoint = race.getCheckpoint(nextCheckpointID);

        if (nextCheckpoint == null) {
//            main.log("Checkpoint with ID " + nextCheckpointID + " is null, going back to 0");
            data.checkpointIndex=0;
            return;
        }

        //Actually check if the player is crossing the checkpoint
        if (!nextCheckpoint.contains(player.getLocation())) return;

        // Start/finish checkpoint handling
        long now = System.currentTimeMillis();
        if (nextCheckpoint.getType().equals(Checkpoint.Type.START_FINISH)) {

            //When starting the race/crossing the start line
            if(data.lapCount==0) data.startTime=now;

            //When completing lap
            if (data.lapCount>0 && !(data.lapCount == main.raceLapCount)) onCompleteLap(data, now);

            //When finishing race
            if(data.lapCount == main.raceLapCount) {
                onCompleteLap(data, now);
                onFinishRace(data, now);

                //End race automatically
                if(race.racing.isEmpty()) {
                    race.sendRanking();
                    race.end();
                }
            }

            data.lapTime = now;
            data.lapCount++;
            data.checkpointIndex = 0;
        }

        if (nextCheckpoint.getType().equals(Checkpoint.Type.SECTOR)) {
            data.sectorTimes.put(nextCheckpoint.getSectorID(), now-data.lapTime);
            Bukkit.broadcast(getMessage("race.onCrossSector",
                    formatArguments(
                            "player", LegacyComponentSerializer.legacySection().serialize(player.displayName()),
                            "ID", String.valueOf(nextCheckpoint.getSectorID()),
                            "time", formatTime(now-data.lapTime)
                    )
            ));
        }

        data.checkpointIndex++;
        main.liveSidebar.getScore(player).setScore(((data.lapCount-1) * race.getCheckpoints().size()) + data.checkpointIndex);
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
                            "player", LegacyComponentSerializer.legacySection().serialize(player.displayName()),
                            "ID",  "" + data.lapCount,
                            "time", formatTime(lapDuration)
                    )
            ));
        } else {
            Bukkit.broadcast(getMessage("race.onCompleteLap.message",
                    formatArguments(
                            "player", LegacyComponentSerializer.legacySection().serialize(player.displayName()),
                            "ID",  "" + data.lapCount,
                            "time", formatTime(lapDuration)
                    )
            ));
        }
        Title title = Title.title(
            Messages.getMessage("race.onCompleteLap.title", formatArguments(
                "currentLapCount", "" + data.lapCount,
                "raceLapCount", "" + main.raceLapCount
        )), Messages.getMessage("race.onCompleteLap.subtitle"));

        player.showTitle(title);
    }

    private static void onFinishRace(RaceData data, long now) {
        Player player = data.player;
        Race race = data.race;
        List<RaceData> finished = race.rankings;
        race.racing.remove(data);
        data.endTime= now;
        race.rankings.add(data);

        int ranking = finished.size();
        data.ranking = ranking; //Used for the final ranking later on
        boolean isWinner = ranking==1;

        long raceTime = now - data.startTime;
        long gapToWinner = isWinner ? -1 : finished.getFirst().getRaceTime();
        //get(ranking-1) = self ; get(ranking-2) = player #(ranking-1) ; because ranking = size (starts @ 1) and get(x) starts @ 0
        long gapToNext = isWinner ? -1 : finished.get(ranking-2).getRaceTime();

        int players = race.racers.size();

        Bukkit.broadcast(getMessage("race.onFinish.message",
                formatArguments(
                        "player", LegacyComponentSerializer.legacySection().serialize(player.displayName()),
                        "time", formatTime(raceTime),
                        "ranking", ranking + ""
                )
        ));

        Title title = Title.title(
                Messages.getMessage("race.onFinish.title"),
                Messages.getMessage("race.onFinish.subtitle", formatArguments("ranking", ranking + ""))
        );

        player.showTitle(title);

        if(isWinner) {
            player.sendMessage(getMessage("race.onFinish.statsMessageWinner",
                    formatArguments(
                            "ranking", ranking + "",
                            "players",  players + "",
                            "raceTime", formatTime(raceTime),
                            "meanLapTime", formatTime(data.meanLapTime()),
                            "bestLapTime", formatTime(data.bestLapTime()),
                            "worstLapTime", formatTime(data.worstLapTime())
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

                    )
            ));
        }

        //TODO make optional via config
        player.setGameMode(GameMode.SPECTATOR);
        try { data.car.destroy(); } catch (Exception ignored) {}
    }
}
