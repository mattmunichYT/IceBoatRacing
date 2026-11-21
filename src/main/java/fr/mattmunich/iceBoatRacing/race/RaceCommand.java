package fr.mattmunich.iceBoatRacing.race;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.Result;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static fr.mattmunich.iceBoatRacing.CommandUtil.*;
import static fr.mattmunich.iceBoatRacing.Main.err;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;
import static fr.mattmunich.iceBoatRacing.PosSelection.pos1;
import static fr.mattmunich.iceBoatRacing.PosSelection.pos2;

/**
 * /race command, built with Paper's Brigadier API.
 * <p>
 * Every subcommand accepts an optional race name. When it is omitted and exactly one race
 * exists, that race is used (same behaviour as the old BasicCommand).
 * <p>
 * Register it from Main with:
 * <pre>
 * getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
 *     event.registrar().register(new RaceCommand(this, raceManager, raceCreator).build(),
 *         "Command to manage the race"));
 * </pre>
 */
public class RaceCommand {
    private static final String PERM_BASE = "iceboatracing.command.race";
    private static final String PERM_START = PERM_BASE + ".start";
    private static final String PERM_END = PERM_BASE + ".end";
    private static final String PERM_DELETE = PERM_BASE + ".delete";
    private static final String PERM_MODIFY = PERM_BASE + ".modify";

    private static final Map<String, Float> DIRECTIONS = Map.of(
            "north", 180f, "n", 180f,
            "west", 90f, "w", 90f,
            "south", 0f, "s", 0f,
            "east", 270f, "e", 270f
    );

    private final RaceManager raceManager;
    private final RaceCreator raceCreator;

    public RaceCommand(RaceManager raceManager, RaceCreator raceCreator) {
        this.raceManager = raceManager;
        this.raceCreator = raceCreator;
    }

    // ------------------------------------------------------------------
    // Functional helpers
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface RaceAction {
        void run(CommandSender sender, Race race);
    }

    @FunctionalInterface
    private interface RaceValueAction<T> {
        void run(CommandSender sender, Race race, T value);
    }

    // ------------------------------------------------------------------
    // Command tree
    // ------------------------------------------------------------------

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("race")
                .requires(perm(PERM_BASE, PERM_START, PERM_END, PERM_DELETE, PERM_MODIFY))
                .executes(ctx -> help(ctx, "race.help"))
                .then(raceAction("start", PERM_START, this::start))
                .then(raceAction("prepare", PERM_START, (sender, race) -> race.togglePrepare(sender)))
                .then(raceAction("end", PERM_END, this::end))
                .then(raceAction("delete", PERM_DELETE, this::delete))
                .then(create())
                .then(setting("setLapCount", IntegerArgumentType.integer(1), Integer.class,
                        this::showLapCount, this::setLapCount))
                .then(setting("setPitStops", IntegerArgumentType.integer(0), Integer.class,
                        this::showPitStops, this::setPitStops))
                .then(startRotation())
                .then(raceAction("setLights", PERM_MODIFY, this::lights))
                .build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> raceArg() {
        return raceArgument("race", raceManager);
    }

    /** /race <name> [race] */
    private LiteralArgumentBuilder<CommandSourceStack> raceAction(String name, String permission, RaceAction action) {
        return Commands.literal(name)
                .requires(perm(permission))
                .executes(ctx -> run(ctx, false, action))
                .then(raceArg().executes(ctx -> run(ctx, true, action)));
    }

    /**
     * /race <name> [race] [value]
     * <p>
     * The value argument is registered BEFORE the race argument on purpose: Brigadier tries
     * arguments in registration order, so "/race setLapCount 3" is read as a value for the
     * only race, while "/race setLapCount myRace" falls through to the race name
     * (an integer can't parse "myRace").
     */
    private <T> LiteralArgumentBuilder<CommandSourceStack> setting(String name, ArgumentType<T> type, Class<T> clazz,
                                                                   RaceAction query, RaceValueAction<T> setter) {
        return Commands.literal(name)
                .requires(perm(PERM_MODIFY))
                .executes(ctx -> run(ctx, false, query))
                .then(Commands.argument("value", type)
                        .executes(ctx -> run(ctx, false,
                                (sender, race) -> setter.run(sender, race, ctx.getArgument("value", clazz)))))
                .then(raceArg()
                        .executes(ctx -> run(ctx, true, query))
                        .then(Commands.argument("value", type)
                                .executes(ctx -> run(ctx, true,
                                        (sender, race) -> setter.run(sender, race, ctx.getArgument("value", clazz))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("create")
                .requires(perm(PERM_MODIFY))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(getMessage("error.playerToExecuteCommand"));
                        return 0;
                    }
                    raceCreator.createRace(p);
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> startRotation() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("setStartRotation")
                .requires(perm(PERM_MODIFY))
                .executes(ctx -> run(ctx, false, this::showStartRotation));
        addRotationValues(root, false);

        RequiredArgumentBuilder<CommandSourceStack, String> raceNode = raceArg()
                .executes(ctx -> run(ctx, true, this::showStartRotation));
        addRotationValues(raceNode, true);

        return root.then(raceNode);
    }

    private void addRotationValues(ArgumentBuilder<CommandSourceStack, ?> parent, boolean named) {
        parent.then(Commands.argument("rotation", FloatArgumentType.floatArg(-180, 360))
                .executes(ctx -> run(ctx, named,
                        (sender, race) -> setStartRotation(sender, race, ctx.getArgument("rotation", Float.class)))));

        DIRECTIONS.forEach((label, rotation) -> parent.then(Commands.literal(label)
                .executes(ctx -> run(ctx, named, (sender, race) -> setStartRotation(sender, race, rotation)))));
    }

    // ------------------------------------------------------------------
    // Race resolution & suggestions
    // ------------------------------------------------------------------

    private int run(CommandContext<CommandSourceStack> ctx, boolean named, RaceAction action) {
        CommandSender sender = ctx.getSource().getSender();
        Race race = resolveRace(ctx, sender, named);
        if (race == null) return 0;
        action.run(sender, race);
        return Command.SINGLE_SUCCESS;
    }

    private Race resolveRace(CommandContext<CommandSourceStack> ctx, CommandSender sender, boolean named) {
        if (named) return getRace(ctx, "race", raceManager);

        List<Race> races = raceManager.getRaces();
        if (races.size() == 1) return races.getFirst();

        // No race name given and it's ambiguous (0 or several races)
        sender.sendMessage(getMessage(races.isEmpty() ? "race.notFound" : "race.help"));
        return null;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void start(CommandSender sender, Race race) {
        sender.sendMessage(getMessage("race.start", Messages.formatArguments("race", race.getName())));
        Result result = race.start();
        if (result == Result.ERROR) sender.sendMessage(getMessage("error.unknown"));
    }

    private void end(CommandSender sender, Race race) {
        try {
            race.end(true);
        } catch (Exception e) {
            switch (String.valueOf(e.getMessage())) {
                case "NO_RANKING" -> sender.sendMessage(getMessage("race.onEnd.error.noRanking"));
                case "NO_RACERS" -> sender.sendMessage(getMessage("race.onEnd.error.noRacers"));
                default -> {
                    sender.sendMessage(getMessage("error.unknown"));
                    err("Could not send ranking on /race end", e);
                }
            }
        }
    }

    private void delete(CommandSender sender, Race race) {
        String raceName = race.getName();
        if (raceManager.deleteRace(race)) {
            sender.sendMessage(getMessage("race.deleted", Messages.formatArguments("race", raceName)));
        } else {
            sender.sendMessage(getMessage("error.unknown"));
        }
    }

    private void showLapCount(CommandSender sender, Race race) {
        sender.sendMessage(getMessage("race.currentLapCount", Messages.formatArguments(
                "race", race.getName(),
                "lapCount", race.getLapCount()
        )));
    }

    private void setLapCount(CommandSender sender, Race race, int lapCount) {
        race.setLapCount(lapCount);
        if (!save(sender, race)) return;
        sender.sendMessage(getMessage("race.updatedLapCount", Messages.formatArguments(
                "race", race.getName(),
                "lapCount", race.getLapCount()
        )));
    }

    private void showPitStops(CommandSender sender, Race race) {
        sender.sendMessage(getMessage("race.currentPitStops", Messages.formatArguments(
                "race", race.getName(),
                "pitStops", race.getRequiredPitStops()
        )));
    }

    private void setPitStops(CommandSender sender, Race race, int pitStops) {
        race.setRequiredPitStops(pitStops);
        if (!save(sender, race)) return;
        sender.sendMessage(getMessage("race.updatedPitStops", Messages.formatArguments(
                "race", race.getName(),
                "pitStops", race.getRequiredPitStops()
        )));
    }

    private void showStartRotation(CommandSender sender, Race race) {
        sender.sendMessage(getMessage("race.currentStartRotation", Messages.formatArguments(
                "race", race.getName(),
                "startRotation", race.getStartRotation()
        )));
    }

    private void setStartRotation(CommandSender sender, Race race, float startRotation) {
        race.setStartRotation(startRotation);
        if (!save(sender, race)) return;
        sender.sendMessage(getMessage("race.updatedStartRotation", Messages.formatArguments(
                "race", race.getName(),
                "startRotation", race.getStartRotation()
        )));
    }

    private void lights(CommandSender sender, Race race) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(getMessage("error.playerToExecuteCommand"));
            return;
        }

        Location newPos1 = pos1.get(p);
        Location newPos2 = pos2.get(p);

        // No selection: show the current lights
        if (newPos1 == null || newPos2 == null) {
            p.sendMessage(getMessage("pos.notSet"));
            Race.Lights lights = race.getLights();

            if (lights == null) {
                p.sendMessage(getMessage("race.lightsNotSet"));
                return;
            }
            if (!lights.enabled()) {
                p.sendMessage(getMessage("race.lightsDisabled"));
                return;
            }

            Location l1 = lights.from();
            Location l2 = lights.to();
            p.sendMessage(getMessage("race.currentLights", Messages.formatArguments(
                    "race", race.getName(),
                    "x1", l1.getBlockX(), "y1", l1.getBlockY(), "z1", l1.getBlockZ(),
                    "x2", l2.getBlockX(), "y2", l2.getBlockY(), "z2", l2.getBlockZ()
            )));
            return;
        }

        race.setLights(new Race.Lights(true, newPos1, newPos2));
        if (!save(sender, race)) return;
        sender.sendMessage(getMessage("race.updatedLights", Messages.formatArguments(
                "race", race.getName(),
                "x1", newPos1.getBlockX(), "y1", newPos1.getBlockY(), "z1", newPos1.getBlockZ(),
                "x2", newPos2.getBlockX(), "y2", newPos2.getBlockY(), "z2", newPos2.getBlockZ()
        )));
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean save(CommandSender sender, Race race) {
        try {
            race.saveConfig();
            return true;
        } catch (IOException e) {
            sender.sendMessage(getMessage("error.unknown"));
            return false;
        }
    }
}