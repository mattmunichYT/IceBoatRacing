package fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceData;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import static fr.mattmunich.iceBoatRacing.race.RaceCreator.creatingRace;

public class CheckpointCommand implements Listener, BasicCommand {

    private final CheckpointManager checkpointManager;
    private final RaceManager raceManager;

    private final Main main;

    public CheckpointCommand(CheckpointManager checkpointManager, RaceManager raceManager, Main main) {
        this.checkpointManager = checkpointManager;
        this.raceManager = raceManager;
        this.main = main;
    }

    public static final Map<Player, Location> pos1 = new HashMap<>();
    public static final Map<Player, Location> pos2 = new HashMap<>();

    @EventHandler
    public void onSelect(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getItem().getType() != Material.WOODEN_SHOVEL) return;
        if(creatingRace.get(event.getPlayer()) != null && creatingRace.get(event.getPlayer()) == 3) return;

        Player p = event.getPlayer();

        if(!p.hasPermission("iceboatracing.command.checkpoint")) return;

        Block clicked = event.getClickedBlock();

        if(clicked == null) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(p, clicked.getLocation());
            p.sendMessage(getMessage("checkpoint.pos.1",formatArguments("x",""+clicked.getX(),"y",""+clicked.getY(),"z",""+clicked.getZ())));
            event.setCancelled(true);
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2.put(p, clicked.getLocation());
            p.sendMessage(getMessage("checkpoint.pos.2",formatArguments("x",""+clicked.getX(),"y",""+clicked.getY(),"z",""+clicked.getZ())));
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

            String raceName = args[1];
            Race race = raceManager.getRace(raceName);
            if(race == null) {
                source.getSender().sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            // Check if creating a SECTOR
            if (args.length >= 3 && args[2].equalsIgnoreCase("SECTOR")) {
                Checkpoint checkpoint = checkpointManager.saveSectorCheckpoint(race, l1, l2);
                if(checkpoint == null) {
                    player.sendMessage(getMessage("error.unknown"));
                    return;
                }
                player.sendMessage(getMessage("checkpoint.sectorSaved",
                        formatArguments("sectorID", "" + checkpoint.getSectorID())
                ));
                return;
            }

            // Otherwise normal checkpoint
            if(race.getCheckpoints().isEmpty()) {
                checkpointManager.saveCheckpoint(race, l1, l2, Checkpoint.Type.START_FINISH);
                player.sendMessage(getMessage("checkpoint.startLineSaved"));
            }

            Checkpoint checkpoint = checkpointManager.saveCheckpoint(race, l1, l2, Checkpoint.Type.NORMAL);
            if(checkpoint == null) {
                player.sendMessage(getMessage("error.unknown"));
                return;
            }
            player.sendMessage(getMessage("checkpoint.saved",
                    formatArguments("id", String.valueOf(checkpoint.getId()))
            ));

        } else if (args.length == 2 && args[0].equalsIgnoreCase("setFinish")) {

            Location l1 = pos1.get(player);
            Location l2 = pos2.get(player);

            if (l1 == null || l2 == null) {
                player.sendMessage(getMessage("checkpoint.posNotSet"));
                return;
            }

            pos1.remove(player);
            pos2.remove(player);

            String raceName = args[1];
            Race race = raceManager.getRace(raceName);
            if(race == null) {
                source.getSender().sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            Checkpoint checkpoint = checkpointManager.saveCheckpoint(race, l1, l2, Checkpoint.Type.START_FINISH);

            if(checkpoint == null) {
                player.sendMessage(getMessage("error.unknown"));
                return;
            }

            player.sendMessage(getMessage("checkpoint.finishLineSaved"));


        } else if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {

            Map<Race,Checkpoint> check = checkpointManager.getAt(player.getLocation());
            Race race = check.keySet().iterator().next();
            Checkpoint checkpoint = check.values().iterator().next();

            if (checkpoint == null) {
                player.sendMessage(getMessage("checkpoint.notInCheckpoint"));
                return;
            }

            checkpointManager.remove(race, checkpoint);
            player.sendMessage(Messages.getMessage("checkpoint.removed",formatArguments("index","" + checkpoint.getId())));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("remove")) {

            int checkpointNum;
            try {
                checkpointNum = Integer.parseInt(args[1]);
            }  catch (NumberFormatException e) {
                player.sendMessage(getMessage("error.invalidNumber"));
                return;
            }

            String raceName = args[2];
            Race race = raceManager.getRace(raceName);
            if(race == null) {
                source.getSender().sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            Checkpoint checkpoint = checkpointManager.get(race, checkpointNum);

            if (checkpoint == null) {
                player.sendMessage(getMessage("checkpoint.invalid"));
                return;
            }

            boolean success = checkpointManager.remove(race, checkpoint);
            if (!success) {
                player.sendMessage(getMessage("error.unknown"));
                return;
            }
            player.sendMessage(Messages.getMessage("checkpoint.removed",
                    formatArguments("id","" + checkpointNum)
            ));
        } else if (args.length <= 2 && args[0].equalsIgnoreCase("list")) {
            //Generated by Claude
            //TODO check if it works

            Map<Race, List<Checkpoint>> all = checkpointManager.getAll();

            if (all.values().stream().allMatch(List::isEmpty)) {
                player.sendMessage(getMessage("checkpoint.noCheckpoints"));
                return;
            }

            // Flatten into ordered (Race, Checkpoint) pairs for pagination
            List<Map.Entry<Race, Checkpoint>> entries = new ArrayList<>();
            for (Map.Entry<Race, List<Checkpoint>> entry : all.entrySet()) {
                for (Checkpoint cp : entry.getValue()) {
                    entries.add(Map.entry(entry.getKey(), cp));
                }
            }

            final int PAGE_SIZE = 5;
            int totalPages = (int) Math.ceil((double) entries.size() / PAGE_SIZE);

            int page = 1;
            if (args.length == 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(getMessage("error.invalidNumber"));
                    return;
                }
            }

            if (page < 1 || page > totalPages) {
                player.sendMessage(c("§cInvalid page (1-" + totalPages + ")"));
                return;
            }

            int start = (page - 1) * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, entries.size());

            player.sendMessage(c("§bCheckpoints §7(page §f" + page + "§7/§f" + totalPages + "§7):"));

            String lastRaceName = null;
            for (int i = start; i < end; i++) {
                Race race = entries.get(i).getKey();
                Checkpoint checkpoint = entries.get(i).getValue();

                // Print race header when the race changes
                if (!race.getName().equals(lastRaceName)) {
                    lastRaceName = race.getName();
                    player.sendMessage(c("§3▌ §b" + race.getName()));
                }

                Location min = checkpoint.getMin();
                Location max = checkpoint.getMax();

                Component removeText = Component.text("[x]")
                        .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND,
                                ClickEvent.Payload.string("/checkpoint remove " + checkpoint.getId() + " " + race.getName())));

                Component tpText = Component.text("[→]")
                        .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND,
                                ClickEvent.Payload.string("/tp " + min.getBlockX() + " " + min.getBlockY() + " " + min.getBlockZ())));

                player.sendMessage(c(
                        "§e  #" + checkpoint.getId()
                                + " §7[" + min.getWorld().getName() + "]"
                                + " §fX:" + min.getBlockX() + " Y:" + min.getBlockY() + " Z:" + min.getBlockZ()
                                + " §7→"
                                + " §fX:" + max.getBlockX() + " Y:" + max.getBlockY() + " Z:" + max.getBlockZ()
                                + " "
                ).append(removeText).append(c(" ")).append(tpText));
            }

            // Page navigation row
            if (totalPages > 1) {
                Component prev = page > 1
                        ? c("§a[←]").clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND,
                        ClickEvent.Payload.string("/checkpoint list " + (page - 1))))
                        : c("§8[←]");
                Component next = page < totalPages
                        ? c("§a[→]").clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND,
                        ClickEvent.Payload.string("/checkpoint list " + (page + 1))))
                        : c("§8[→]");

                player.sendMessage(prev.append(c("  §7Page §f" + page + " §7of §f" + totalPages + "  ")).append(next));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("normalize")) {
            String raceName = args[1];
            Race race = raceManager.getRace(raceName);
            if(race == null) {
                source.getSender().sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            checkpointManager.normalize(race);
            player.sendMessage(getMessage("checkpoint.normalized"));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("count")) {

            int count = checkpointManager.getAllNoRaceInfo().size();
            player.sendMessage(Messages.getMessage("checkpoint.count",formatArguments("count", "" + count)));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("resetData")) {
            Player target = Bukkit.getPlayer(args[1]);
            if(target == null) {
                player.sendMessage(getMessage("error.playerNotFound"));
                return;
            }

            String raceName = args[2];
            Race race = raceManager.getRace(raceName);
            if(race == null) {
                source.getSender().sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            //Remove data
            race.racers.remove(target.getUniqueId());
            //Replace with some new clear data
            race.racers.put(target.getUniqueId(), new RaceData(target));

            //Reset player's data on live scoreboard
            main.liveSidebar.getScore(target.getName()).resetScore();
            player.sendMessage(Messages.getMessage("checkpoint.resetPlayerScore",formatArguments("player",player.getName())));
        } else {
            player.sendMessage(c("§eCheckpoint commands:"));
            player.sendMessage(c("§7- §f/checkpoint list"));
            player.sendMessage(c("§7- §f/checkpoint count"));
            player.sendMessage(c("§7- §f/checkpoint create <raceName> [\"SECTOR\"]"));
            player.sendMessage(c("§7- §f/checkpoint setFinish <raceName>"));
            player.sendMessage(c("§7- §f/checkpoint remove [raceName] [checkpointID]"));
            player.sendMessage(c("§7- §f/checkpoint normalize [raceName]"));
            player.sendMessage(c("§7- §f/checkpoint resetData <player> <raceName>"));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack source, String @NonNull [] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length <= 1) {
            suggestions.add("list");
            suggestions.add("count");
            suggestions.add("create");
            suggestions.add("setFinish");
            suggestions.add("remove");
            suggestions.add("resetData");
            suggestions.add("normalize");
            return suggestions;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                if (args.length == 2) {
                    int total = (int) Math.ceil((double) checkpointManager.getAllNoRaceInfo().size() / 5.0);
                    for (int i = 1; i <= total; i++) suggestions.add("" + i);
                }
            }
            case "create" -> {
                if (args.length == 2) {
                    for (Race race : raceManager.races) suggestions.add(race.getName());
                } else if (args.length == 3) {
                    suggestions.add("SECTOR");
                }
            }
            case "setfinish", "normalize" -> {
                if (args.length == 2) {
                    for (Race race : raceManager.races) suggestions.add(race.getName());
                }
            }
            case "remove" -> {
                if (args.length == 2) {
                    for (int i = 0; i < checkpointManager.count(); i++) suggestions.add("" + i);
                } else if (args.length == 3) {
                    for (Race race : raceManager.races) suggestions.add(race.getName());
                }
            }
            case "resetdata" -> {
                if (args.length == 2) {
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) suggestions.add(onlinePlayer.getName());
                } else if (args.length == 3) {
                    for (Race race : raceManager.races) suggestions.add(race.getName());
                }
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
