package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.CheckpointManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Main.formatTime;
import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public class RaceListener implements Listener {

    private final Main main;
    private final CheckpointManager checkpointManager;

    public RaceListener(Main main, CheckpointManager checkpointManager) {
        this.main = main;
        this.checkpointManager = checkpointManager;
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

        RaceData data = main.racers.get(player.getUniqueId());
        if (data == null) {
            player.sendActionBar(c("§cNo race data."));
            return;
        }
        Checkpoint next = checkpointManager.get(data.checkpointIndex+1);

        if (next==null) next = checkpointManager.get(0);
        if (!next.contains(player.getLocation())) return;

        // Start/finish checkpoint handling
        if (next.getType().equals(Checkpoint.Type.START_FINISH)) {
            long now = System.currentTimeMillis();
            if(data.lapCount==0) data.startTime=now;
            if (data.lapCount>0) {
                long lapDuration = now-data.lapTime;
                Bukkit.broadcast(getMessage("completedLaps",
                        formatArguments(
                                "player", LegacyComponentSerializer.legacySection().serialize(player.displayName()),
                                "count",  "" + data.lapCount,
                                "time", formatTime(lapDuration)
                        )
                ));
            }
            if(data.lapCount == 20) {
                Bukkit.broadcast(Messages.getMessage("prefix").append(c("§3")).append(player.displayName()).append(c("§b a terminé la course!")));
            }

            data.lapTime = now;
            data.lapCount++;
            data.checkpointIndex = -1;
        }

        if (next.getType().equals(Checkpoint.Type.SECTOR)) {
            Bukkit.broadcast(getMessage("crossedSector",
                    formatArguments(
                            "player", LegacyComponentSerializer.legacySection().serialize(player.displayName()),
                            "count", String.valueOf(next.getSectorIndex()),
                            "time", formatTime(System.currentTimeMillis()-data.lapTime)
                    )
            ));
        }

        data.checkpointIndex++;
        main.liveSidebar.getScore(player).setScore((data.lapCount*checkpointManager.getAll().size()) + data.checkpointIndex);
    }
}
