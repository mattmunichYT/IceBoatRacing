package fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.race.RaceData;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Messages.*;

public class CheckpointCommand implements Listener, BasicCommand {

    private final CheckpointManager  checkpointManager;

    private final Main main;

    public CheckpointCommand(CheckpointManager checkpointManager, Main main) {
        this.checkpointManager = checkpointManager;
        this.main = main;
    }

    private static final Map<Player, Location> pos1 = new HashMap<>();
    private static final Map<Player, Location> pos2 = new HashMap<>();

    @EventHandler
    public void onSelect(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getItem().getType() != Material.WOODEN_SHOVEL) return;

        Player p = event.getPlayer();

        if(!p.hasPermission("iceboatracing.command.checkpoint")) return;
        
        if(event.getClickedBlock() == null) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            assert event.getClickedBlock() != null;
            pos1.put(p, event.getClickedBlock().getLocation());
            p.sendMessage(getMessage("checkpoint.pos.1"));
            event.setCancelled(true);
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            assert event.getClickedBlock() != null;
            pos2.put(p, event.getClickedBlock().getLocation());
            p.sendMessage(getMessage("checkpoint.pos.2"));
            event.setCancelled(true);
        }
    }

    @Override
    public void execute(CommandSourceStack source, String @NonNull [] args) {
        if(!(source.getSender() instanceof Player player)) {
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("create")) {
            // Get positions first
            Location l1 = pos1.get(player);
            Location l2 = pos2.get(player);
            pos1.remove(player);
            pos2.remove(player);

            if (l1 == null || l2 == null) {
                player.sendMessage(getMessage("checkpoint.pos.notSet"));
                return;
            }

            // Check if creating a SECTOR
            if (args.length >= 3 && args[1].equalsIgnoreCase("SECTOR")) {
                int sectorIndex;
                try {
                    sectorIndex = Integer.parseInt(args[2]);
                } catch (Exception e) {
                    player.sendMessage(getMessage("error.invalidNumber"));
                    return;
                }

                checkpointManager.saveSectorCheckpoint(sectorIndex, l1, l2);
                player.sendMessage(getMessage("checkpoint.sectorSaved",formatArguments("index", String.valueOf(sectorIndex))));
                return;
            }

            // Otherwise normal checkpoint
            int nextIndex = checkpointManager.getAll().stream()
                    .mapToInt(Checkpoint::getIndex)
                    .max()
                    .orElse(-1) + 1;

            checkpointManager.saveCheckpoint(nextIndex, l1, l2, Checkpoint.Type.NORMAL);
            player.sendMessage(getMessage("checkpoint.saved",formatArguments("index", String.valueOf(nextIndex))));

        } else if (args.length == 1 && args[0].equalsIgnoreCase("setFinish")) {

            Location l1 = pos1.get(player);
            Location l2 = pos2.get(player);
            pos1.remove(player);
            pos2.remove(player);

            if (l1 == null || l2 == null) {
                player.sendMessage(getMessage("checkpoint.posNotSet"));
                return;
            }

            int nextIndex = checkpointManager.getAll().stream()
                    .mapToInt(Checkpoint::getIndex)
                    .max()
                    .orElse(-1) + 1;

            checkpointManager.saveCheckpoint(nextIndex, l1, l2, Checkpoint.Type.START_FINISH);
            player.sendMessage(getMessage("checkpoint.finishLineSaved"));


        } else if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {

            Checkpoint checkpoint =
                    checkpointManager.getAt(player.getLocation());

            if (checkpoint == null) {
                player.sendMessage(getMessage("checkpoint.notInCheckpoint"));
                return;
            }

            checkpointManager.remove(checkpoint);
            player.sendMessage(Messages.getMessage("checkpoint.removed",formatArguments("index","" + checkpoint.getIndex())));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {

            int checkpointNum;
            try {
                checkpointNum = Integer.parseInt(args[1]);
            }  catch (NumberFormatException e) {
                player.sendMessage(getMessage("error.invalidNumber"));
                return;
            }

            Checkpoint checkpoint = checkpointManager.get(checkpointNum);

            if (checkpoint == null) {
                player.sendMessage(getMessage("checkpoint.invalid"));
                return;
            }

            checkpointManager.remove(checkpoint);
            player.sendMessage(Messages.getMessage("checkpoint.removed",formatArguments("index","" + checkpointNum)));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("list")) {

            if (checkpointManager.getAll().isEmpty()) {
                player.sendMessage(getMessage("checkpoint.noCheckpoints"));
                return;
            }

            player.sendMessage(c("§bCheckpoints:"));

            for (Checkpoint cp : checkpointManager.getAll()) {
                Location min = cp.getMin();
                Location max = cp.getMax();

                Component removeText = Component.text("[x]");
                ClickEvent removeClickEvent = ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, ClickEvent.Payload.string("/checkpoint remove " + cp.getIndex()));
                removeText = removeText.clickEvent(removeClickEvent);

                Component tpText = Component.text("[→]");
                ClickEvent tpClickEvent = ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, ClickEvent.Payload.string("/tp " + cp.getMin().getBlockX() + " " + cp.getMin().getBlockY() + " " + cp.getMin().getBlockZ()));
                tpText = tpText.clickEvent(tpClickEvent);

                player.sendMessage(c(
                        "§e- #" + cp.getIndex()
                                + " §7[" + min.getWorld().getName() + "] "
                                + "§fX: " + min.getBlockX() + ", Y: " + min.getBlockY() + ", Z: " + min.getBlockZ()
                                + " §7-> "
                                + "§fX: " + max.getBlockX() + ", Y: " + max.getBlockY() + ", Z: " + max.getBlockZ()
                ).append(c("§8 ")).append(removeText).append(Component.text(" ").append(tpText)));
            }
        } else if (args.length == 1 && args[0].equalsIgnoreCase("normalize")) {

            checkpointManager.normalize();
            player.sendMessage(getMessage("checkpoint.normalized"));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("count")) {

            int count = checkpointManager.getAll().size();
            player.sendMessage(Messages.getMessage("checkpoint.count",formatArguments("count", "" + count)));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("resetData")) {
            Player target = Bukkit.getPlayer(args[1]);
            if(target == null) {
                player.sendMessage(getMessage("error.playerNotFound"));
                return;
            }
            main.racers.remove(target.getUniqueId());
            main.racers.put(target.getUniqueId(), new RaceData(target));
            main.liveSidebar.getScore(target.getName()).resetScore();
            player.sendMessage(Messages.getMessage("checkpoint.resetPlayerScore",formatArguments("player",player.getName())));
        } else {
            player.sendMessage(c("§eCheckpoint commands:"));
            player.sendMessage(c("§7- §f/checkpoint list"));
            player.sendMessage(c("§7- §f/checkpoint count"));
            player.sendMessage(c("§7- §f/checkpoint create"));
            player.sendMessage(c("§7- §f/checkpoint setFinish"));
            player.sendMessage(c("§7- §f/checkpoint remove"));
            player.sendMessage(c("§7- §f/checkpoint normalize"));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack source, String @NonNull [] args) {
        List<String> suggestions = new ArrayList<>();
        if(args.length==1 || args.length==0) {
            suggestions.add("list");
            suggestions.add("count");
            suggestions.add("create");
            suggestions.add("setFinish");
            suggestions.add("remove");
            suggestions.add("resetData");
            suggestions.add("normalize");
        }
        if(args.length==2 && args[0].equalsIgnoreCase("remove")) {
            for (int i = 0; i < checkpointManager.count(); i++) {
                suggestions.add("" + i);
            }
        }
        if(args.length==2 && args[0].equalsIgnoreCase("resetData")) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                suggestions.add(onlinePlayer.getName());
            }
        }
        return suggestions;
    }

    @Override
    public boolean canUse(@NonNull CommandSender sender) {
        return sender instanceof Player p && p.hasPermission("iceboatracing.command.checkpoint");
    }

    @Override
    public @Nullable String permission() {
        return "iceboatracing.command.checkpoint";
    }
}
