package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.CheckpointManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Objects;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Main.formatTime;
import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public class RaceListener implements Listener {

    private final Main main;
    private final CheckpointManager checkpointManager;
    private final RaceManager raceManager;

    public RaceListener(Main main, CheckpointManager checkpointManager, RaceManager raceManager) {
        this.main = main;
        this.checkpointManager = checkpointManager;
        this.raceManager = raceManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!player.isInsideVehicle()) {
            return;
        }
        if (!(player.getVehicle() instanceof Boat)) {
            return;
        }

        RaceData data = null;
        for(Race race : raceManager.activeRaces) {
            if(race.racers.containsKey(player.getUniqueId())) data = race.racers.get(player.getUniqueId());
        }

        if (data == null || data.race == null || data.car == null) {
            player.sendActionBar(Objects.requireNonNull(c("§e§oNo race data")));
            return;
        }

        Checkpoint nextCheckpoint = checkpointManager.get(data.race, data.checkpointIndex+1);

        if (nextCheckpoint == null) nextCheckpoint = checkpointManager.get(data.race,1);

        main.log("[DEBUG] Checking for " + player.getName() + " in checkpoint " + nextCheckpoint.getId());

        //Actually check if the player is crossing the checkpoint
        if (!nextCheckpoint.contains(player.getLocation())) return;

        main.log("[DEBUG] Checkpoint crossed.");

        // Start/finish checkpoint handling
        if (nextCheckpoint.getType().equals(Checkpoint.Type.START_FINISH)) {
            long now = System.currentTimeMillis();
            if(data.lapCount==0) data.startTime=now;
            if (data.lapCount>0) {
                long lapDuration = now-data.lapTime;
                Bukkit.broadcast(getMessage("race.onCompleteLap.message",
                        formatArguments(
                                "player", LegacyComponentSerializer.legacySection().serialize(player.displayName()),
                                "count",  "" + data.lapCount,
                                "time", formatTime(lapDuration)
                        )
                ));
                Title title = Title.title(
                    Messages.getMessage("race.onCompleteLap.title", formatArguments(
                        "currentLapCount", "" + data.lapCount,
                        "raceLapCount", "" + main.raceLapCount
                )), Messages.getMessage("race.onCompleteLap.subtitle"));

                player.showTitle(title);
            }
            if(data.lapCount == main.raceLapCount) {
                Bukkit.broadcast(Messages.getMessage("prefix").append(Objects.requireNonNull(c("§3"))).append(player.displayName()).append(Objects.requireNonNull(c("§b a terminé la course!"))));
            }

            data.lapTime = now;
            data.lapCount++;
            data.checkpointIndex = -1;
        }

        if (nextCheckpoint.getType().equals(Checkpoint.Type.SECTOR)) {
            Bukkit.broadcast(getMessage("race.onCrossSector",
                    formatArguments(
                            "player", LegacyComponentSerializer.legacySection().serialize(player.displayName()),
                            "count", String.valueOf(nextCheckpoint.getSectorID()),
                            "time", formatTime(System.currentTimeMillis()-data.lapTime)
                    )
            ));
        }

        data.checkpointIndex++;
        main.liveSidebar.getScore(player).setScore((data.lapCount*checkpointManager.getAll().size()) + data.checkpointIndex);
    }
}
