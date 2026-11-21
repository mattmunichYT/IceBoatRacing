package fr.mattmunich.iceBoatRacing.pitbox;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.mattmunich.iceBoatRacing.CommandUtil;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.CommandUtil.*;
import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Messages.*;

/**
 * /pitbox command (Brigadier).
 * <pre>
 * /pitbox create <race>
 * /pitbox list <race>
 * /pitbox remove|delete <race> <box>
 * /pitbox setAllowed <race> <box> <players... | *>
 * /pitbox setColor <race> <box> <color>
 * </pre>
 * &lt;box&gt; is either the pit box's ID or its name.
 */
public class PitBoxCommand {
    public static final String PERM_BASE = "iceboatracing.command.pitbox";
    public static final String PERM_CREATE = PERM_BASE + ".create";
    public static final String PERM_LIST = PERM_BASE + ".list";
    public static final String PERM_REMOVE = PERM_BASE + ".remove";
    public static final String PERM_MODIFY = PERM_BASE + ".modify"; // setAllowed, setColor

    private final RaceManager raceManager;
    private final PitBoxManager pitBoxManager;
    private final PitBoxCreator pitBoxCreator;

    public PitBoxCommand(RaceManager raceManager, PitBoxManager pitBoxManager, PitBoxCreator pitBoxCreator) {
        this.raceManager = raceManager;
        this.pitBoxManager = pitBoxManager;
        this.pitBoxCreator = pitBoxCreator;
    }

    // ------------------------------------------------------------------
    // Command tree
    // ------------------------------------------------------------------

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pitbox")
                .requires(playerPerm(PERM_BASE, PERM_CREATE, PERM_LIST, PERM_REMOVE, PERM_MODIFY))
                .executes(ctx -> help(ctx, "pitbox.help"))
                .then(Commands.literal("create")
                        .requires(perm(PERM_CREATE))
                        .then(raceArgument("race", raceManager).executes(this::create)))
                .then(Commands.literal("list")
                        .requires(perm(PERM_LIST))
                        .then(raceArgument("race", raceManager).executes(this::list)))
                .then(removeNode("remove"))
                .then(removeNode("delete"))
                .then(Commands.literal("setAllowed")
                        .requires(perm(PERM_MODIFY))
                        .then(raceArgument("race", raceManager)
                                .then(boxArgument()
                                        .then(Commands.argument("players", StringArgumentType.greedyString())
                                                .suggests(this::suggestPlayers)
                                                .executes(this::setAllowed)))))
                .then(Commands.literal("setColor")
                        .requires(perm(PERM_MODIFY))
                        .then(raceArgument("race", raceManager)
                                .then(boxArgument()
                                        .then(Commands.argument("color", StringArgumentType.word())
                                                .suggests((ctx, builder) -> CommandUtil.suggest(Arrays.asList(PitBoxColor.names()), builder))
                                                .executes(this::setColor)))))
                .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> removeNode(String literal) {
        return Commands.literal(literal)
                .requires(perm(PERM_REMOVE))
                .then(raceArgument("race", raceManager)
                        .then(boxArgument().executes(this::remove)));
    }

    /** Pit box ID or name, completed from the race typed just before. */
    private RequiredArgumentBuilder<CommandSourceStack, String> boxArgument() {
        return Commands.argument("box", StringArgumentType.word()).suggests((ctx, builder) -> {
            List<String> options = new ArrayList<>();
            Race race = raceManager.getRace(StringArgumentType.getString(ctx, "race"));
            if (race != null) {
                for (PitBox box : race.getPitBoxes()) {
                    options.add(box.getName());
                    options.add(String.valueOf(box.getId()));
                }
            }
            return CommandUtil.suggest(options, builder);
        });
    }

    /** Completes the LAST word of the greedy "players" argument. */
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        SuggestionsBuilder lastWord = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);

        List<String> options = new ArrayList<>();
        options.add("*");
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null) options.add(offlinePlayer.getName());
        }
        return CommandUtil.suggest(options, lastWord);
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    private int create(CommandContext<CommandSourceStack> ctx) {
        Player p = player(ctx);
        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        pitBoxCreator.createPitBox(p, race);
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        List<PitBox> boxes = race.getPitBoxes();
        if (boxes.isEmpty()) {
            sender.sendMessage(getMessage("pitbox.noPitBoxes"));
            return 0;
        }

        sender.sendMessage(c("§bPit boxes §7— §f" + race.getName() + "§7:"));
        for (PitBox box : boxes) {
            Component editText = Component.text("[✎]")
                    .hoverEvent(HoverEvent.showText(c("Click to define allowed players")))
                    .clickEvent(ClickEvent.suggestCommand("/pitbox setAllowed " + race.getName() + " " + box.getId() + " "));

            Component colorText = Component.text("[🎨]")
                    .hoverEvent(HoverEvent.showText(c("Click to copy to change color")))
                    .clickEvent(ClickEvent.suggestCommand("/pitbox setColor " + race.getName() + " " + box.getId() + " "));

            Component removeText = Component.text("[x]")
                    .hoverEvent(HoverEvent.showText(c("Click to delete")))
                    .clickEvent(ClickEvent.runCommand("/pitbox remove " + race.getName() + " " + box.getId()));

            Component tpText = Component.text("[→]")
                    .hoverEvent(HoverEvent.showText(c("Click to teleport")))
                    .clickEvent(ClickEvent.runCommand("/tp " + box.getCenter().getBlockX() + " "
                            + box.getCenter().getBlockY() + " " + box.getCenter().getBlockZ()));

            List<String> allowedNames = new ArrayList<>();
            for (UUID allowed : box.getAllowed()) {
                String name = Bukkit.getOfflinePlayer(allowed).getName();
                allowedNames.add(name == null ? getStringMessage("playerNotFound") : name);
            }

            sender.sendMessage(c(
                    "§3  #" + box.getId() + " §b" + box.getName()
                            + " §7— §f" + box.getDuration() + "s"
                            + " §7— §f" + String.join(",", allowedNames)
                            + " §7— §f" + box.getColor().name()
                            + " §7 | §f"
            ).append(editText).append(c(" ")).append(colorText).append(c(" ")).append(removeText).append(c(" ")).append(tpText));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        PitBox box = getBox(ctx, race, sender);
        if (box == null) return 0;
        String name = box.getName();

        if (!pitBoxManager.remove(race, box)) {
            sender.sendMessage(getMessage("error.unknown"));
            return 0;
        }
        sender.sendMessage(getMessage("pitbox.removed", formatArguments("name", name)));
        return Command.SINGLE_SUCCESS;
    }

    private int setAllowed(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        PitBox box = getBox(ctx, race, sender);
        if (box == null) return 0;

        List<String> allowedNames = new ArrayList<>(Arrays.asList(
                StringArgumentType.getString(ctx, "players").trim().split("\\s+")));
        if (allowedNames.getFirst().equals("*")) allowedNames = new ArrayList<>();

        List<UUID> allowedUUIDs = new ArrayList<>();
        OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();
        for (String allowedName : allowedNames) {
            Optional<OfflinePlayer> result = Arrays.stream(offlinePlayers)
                    .filter(off -> allowedName.equalsIgnoreCase(off.getName()))
                    .findFirst();
            if (result.isEmpty()) {
                sender.sendMessage(getMessage("playerNeverJoined", formatArguments("name", allowedName)));
                return 0;
            }
            allowedUUIDs.add(result.get().getUniqueId());
        }

        if (!pitBoxManager.setAllowed(race, box, allowedUUIDs)) {
            sender.sendMessage(getMessage("error.unknown"));
            return 0;
        }

        sender.sendMessage(getMessage("pitbox.setAllowed.updated", formatArguments(
                "name", box.getName(),
                "players", allowedNames.isEmpty() ? "*" : String.join(", ", allowedNames))
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int setColor(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        PitBox box = getBox(ctx, race, sender);
        if (box == null) return 0;

        PitBoxColor color;
        try {
            color = PitBoxColor.valueOf(StringArgumentType.getString(ctx, "color").toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(getMessage("pitbox.setColor.invalid",
                    formatArguments("availableColors", String.join(", ", PitBoxColor.names()))));
            return 0;
        }

        if (!pitBoxManager.setColor(race, box, color)) {
            sender.sendMessage(getMessage("error.unknown"));
            return 0;
        }

        sender.sendMessage(getMessage("pitbox.setColor.updated", formatArguments("name", box.getName(), "color", color.name())));
        return Command.SINGLE_SUCCESS;
    }

    /** The "box" argument is an ID if it's a number, a name otherwise. */
    private static PitBox getBox(CommandContext<CommandSourceStack> ctx, Race race, CommandSender sender) {
        String value = StringArgumentType.getString(ctx, "box");
        PitBox box;
        try {
            box = race.getPitBox(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            box = race.getPitBox(value);
        }

        if (box == null) sender.sendMessage(getMessage("pitbox.invalid"));
        return box;
    }
}