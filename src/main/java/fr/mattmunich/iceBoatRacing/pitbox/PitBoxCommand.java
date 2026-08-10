package fr.mattmunich.iceBoatRacing.pitbox;

import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public class PitBoxCommand implements BasicCommand {

    private final RaceManager raceManager;
    private final PitBoxManager pitBoxManager;
    private final PitBoxCreator pitBoxCreator;

    public PitBoxCommand(RaceManager raceManager, PitBoxManager pitBoxManager, PitBoxCreator pitBoxCreator) {
        this.raceManager = raceManager;
        this.pitBoxManager = pitBoxManager;
        this.pitBoxCreator = pitBoxCreator;
    }

    @Override
    public void execute(@NonNull CommandSourceStack source, String @NonNull [] args) {
        CommandSender sender = source.getSender();

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(getMessage("error.playerToExecuteCommand"));
                return;
            }

            Race race = raceManager.getRace(args[1]);
            if (race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            pitBoxCreator.createPitBox(p, race);

        } else if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            Race race = raceManager.getRace(args[1]);
            if (race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            List<PitBox> boxes = race.getPitBoxes();
            if (boxes.isEmpty()) {
                sender.sendMessage(getMessage("pitbox.noPitBoxes"));
                return;
            }

            sender.sendMessage(c("§bPit boxes §7— §f" + race.getName() + "§7:"));
            for (PitBox box : boxes) {
                Component editText = Component.text("[✎]")
                        .clickEvent(ClickEvent.suggestCommand("/pitbox setAllowed " + race.getName() + " " + box.getId() + " "));

                Component colorText = Component.text("[🎨]")
                        .clickEvent(ClickEvent.suggestCommand("/pitbox setColor " + race.getName() + " " + box.getId() + " "));

                Component removeText = Component.text("[x]")
                        .clickEvent(ClickEvent.runCommand("/pitbox remove " + race.getName() + " " + box.getId()));

                Component tpText = Component.text("[→]")
                        .clickEvent(ClickEvent.runCommand("/tp " + box.getCenter().getBlockX() + " "
                                + box.getCenter().getBlockY() + " " + box.getCenter().getBlockZ()));

                sender.sendMessage(c(
                        "§e  #" + box.getId() + " §f" + box.getName()
                                + " §7— §f" + box.getDuration() + "s"
                                + " §7— §f" + String.join(",", box.getAllowed())
                                + " §7— §f" + box.getColor().name()
                                + " "
                ).append(editText).append(c(" ")).append(colorText).append(c(" ")).append(removeText).append(c(" ")).append(tpText));
            }

        } else if (args.length == 3 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete"))) {
            Race race = raceManager.getRace(args[1]);
            if (race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            int id;
            try {
                id = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("error.invalidNumber"));
                return;
            }

            PitBox box = race.getPitBox(id);
            if (box == null) {
                sender.sendMessage(getMessage("pitbox.invalid"));
                return;
            }

            boolean success = pitBoxManager.remove(race, box);
            if (!success) {
                sender.sendMessage(getMessage("error.unknown"));
                return;
            }
            sender.sendMessage(getMessage("pitbox.removed", formatArguments("id", "" + id)));

        } else if (args.length >= 4 && args[0].equalsIgnoreCase("setAllowed")) {
            Race race = raceManager.getRace(args[1]);
            if (race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            int id;
            try {
                id = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("error.invalidNumber"));
                return;
            }

            PitBox box = race.getPitBox(id);
            if (box == null) {
                sender.sendMessage(getMessage("pitbox.invalid"));
                return;
            }

            List<String> allowed = new ArrayList<>(Arrays.asList(args).subList(3, args.length));

            boolean success = pitBoxManager.setAllowed(race, box, allowed);
            if (!success) {
                sender.sendMessage(getMessage("error.unknown"));
                return;
            }

            sender.sendMessage(getMessage("pitbox.setAllowed.updated", formatArguments(
                    "id", "" + id,
                    "players", String.join(", ", allowed)
            )));

        } else if (args.length == 4 && args[0].equalsIgnoreCase("setColor")) {
            Race race = raceManager.getRace(args[1]);
            if (race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            int id;
            try {
                id = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("error.invalidNumber"));
                return;
            }

            PitBox box = race.getPitBox(id);
            if (box == null) {
                sender.sendMessage(getMessage("pitbox.invalid"));
                return;
            }

            PitBoxColor color;
            try {
                color = PitBoxColor.valueOf(args[3].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(getMessage("pitbox.setColor.invalid", formatArguments("availableColors", PitBoxColor.values())));
                return;
            }

            boolean success = pitBoxManager.setColor(race, box, color);
            if (!success) {
                sender.sendMessage(getMessage("error.unknown"));
                return;
            }

            sender.sendMessage(getMessage("pitbox.setColor.updated", formatArguments("id", "" + id, "color", color.name())));

        } else {
            sender.sendMessage(getMessage("pitbox.help"));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack source, String @NonNull [] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length <= 1) {
            suggestions.add("create");
            suggestions.add("list");
            suggestions.add("remove");
            suggestions.add("setAllowed");
            suggestions.add("setColor");
            return suggestions;
        }

        switch (args[0].toLowerCase()) {
            case "create", "list" -> {
                if (args.length == 2) for (Race race : raceManager.races) suggestions.add(race.getName());
            }
            case "remove", "delete" -> {
                if (args.length == 2) {
                    for (Race race : raceManager.races) suggestions.add(race.getName());
                } else if (args.length == 3) {
                    Race race = raceManager.getRace(args[1]);
                    if (race != null) for (PitBox box : race.getPitBoxes()) suggestions.add("" + box.getId());
                }
            }
            case "setallowed" -> {
                if (args.length == 2) {
                    for (Race race : raceManager.races) suggestions.add(race.getName());
                } else if (args.length == 3) {
                    Race race = raceManager.getRace(args[1]);
                    if (race != null) for (PitBox box : race.getPitBoxes()) suggestions.add("" + box.getId());
                } else {
                    suggestions.add("*");
                    for (Player online : Bukkit.getOnlinePlayers()) suggestions.add(online.getName());
                }
            }
            case "setcolor" -> {
                if (args.length == 2) {
                    for (Race race : raceManager.races) suggestions.add(race.getName());
                } else if (args.length == 3) {
                    Race race = raceManager.getRace(args[1]);
                    if (race != null) for (PitBox box : race.getPitBoxes()) suggestions.add("" + box.getId());
                } else if (args.length == 4) {
                    for (PitBoxColor color : PitBoxColor.values()) suggestions.add(color.name());
                }
            }
        }

        return suggestions.isEmpty() ? Collections.emptyList() : suggestions;
    }

    @Override
    public boolean canUse(@NonNull CommandSender sender) {
        return sender instanceof Player && sender.hasPermission("iceboatracing.command.pitbox");
    }

    @Override
    public @Nullable String permission() {
        return "iceboatracing.command.pitbox";
    }
}