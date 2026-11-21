package fr.mattmunich.iceBoatRacing.checkpoint;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.mattmunich.iceBoatRacing.CommandUtil;
import fr.mattmunich.iceBoatRacing.LiveScoreManager;
import fr.mattmunich.iceBoatRacing.PosSelection;
import fr.mattmunich.iceBoatRacing.checkpoint.autotrace.AutoTraceManager;
import fr.mattmunich.iceBoatRacing.checkpoint.autotrace.AutoTraceSession;
import fr.mattmunich.iceBoatRacing.checkpoint.autotrace.PendingAutoTraceConfig;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceCreator;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.CommandUtil.*;
import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Messages.*;

/**
 * /checkpoint command (Brigadier).
 * <pre>
 * /checkpoint create [sector|finish] <race>
 * /checkpoint remove|delete                 (nearest checkpoint)
 * /checkpoint remove|delete <id> <race>
 * /checkpoint view <race>
 * /checkpoint list [page]
 * /checkpoint count
 * /checkpoint getID <race>
 * /checkpoint resetData <player> <race>
 * /checkpoint addAlternate <race> <checkpointID>
 * /checkpoint clearAll <race>               (run twice within 10s to confirm)
 * /checkpoint autotrace start|confirm|configure|stop|preview|sector|add|resize|delete|accept|cancel
 * </pre>
 */
public class CheckpointCommand {
    public static final String PERM_BASE = "iceboatracing.command.checkpoint";
    public static final String PERM_CREATE = PERM_BASE + ".create";
    public static final String PERM_REMOVE = PERM_BASE + ".remove";
    public static final String PERM_VIEW = PERM_BASE + ".view";
    public static final String PERM_LIST = PERM_BASE + ".list";             // list, count, getID
    public static final String PERM_RESET_DATA = PERM_BASE + ".resetdata";
    public static final String PERM_AUTOTRACE = PERM_BASE + ".autotrace";
    public static final String PERM_ALTERNATE = PERM_BASE + ".alternate";
    public static final String PERM_CLEAR_ALL = PERM_BASE + ".clearall";

    private static final int PAGE_SIZE = 10;

    private final CheckpointManager checkpointManager;
    private final RaceManager raceManager;
    private final AutoTraceManager autoTraceManager;
    private final RaceCreator raceCreator;
    private final LiveScoreManager liveScoreManager;

    private final Map<UUID, PendingClear> pendingClears = new HashMap<>();

    private record PendingClear(String raceName, long timestamp) {}

    public CheckpointCommand(CheckpointManager checkpointManager, RaceManager raceManager, AutoTraceManager autoTraceManager,
                             RaceCreator raceCreator, LiveScoreManager liveScoreManager) {
        this.checkpointManager = checkpointManager;
        this.raceManager = raceManager;
        this.autoTraceManager = autoTraceManager;
        this.raceCreator = raceCreator;
        this.liveScoreManager = liveScoreManager;
    }

    // ------------------------------------------------------------------
    // Command tree
    // ------------------------------------------------------------------

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("checkpoint")
                .requires(playerPerm(PERM_BASE, PERM_CREATE, PERM_REMOVE, PERM_VIEW, PERM_LIST,
                        PERM_RESET_DATA, PERM_AUTOTRACE, PERM_ALTERNATE, PERM_CLEAR_ALL))
                .executes(ctx -> help(ctx, "checkpoint.help"))
                .then(create())
                .then(removeNode("remove"))
                .then(removeNode("delete"))
                .then(Commands.literal("view")
                        .requires(perm(PERM_VIEW))
                        .executes(ctx -> view(ctx, false))
                        .then(raceArgument("race", raceManager).executes(ctx -> view(ctx, true))))
                .then(Commands.literal("list")
                        .requires(perm(PERM_LIST))
                        .executes(ctx -> list(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("count")
                        .requires(perm(PERM_LIST))
                        .executes(this::count))
                .then(Commands.literal("getID")
                        .requires(perm(PERM_LIST))
                        .then(raceArgument("race", raceManager).executes(this::getId)))
                .then(Commands.literal("resetData")
                        .requires(perm(PERM_RESET_DATA))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(raceArgument("race", raceManager).executes(this::resetData))))
                .then(Commands.literal("addAlternate")
                        .requires(perm(PERM_ALTERNATE))
                        .then(raceArgument("race", raceManager)
                                .then(Commands.argument("checkpoint", IntegerArgumentType.integer(0))
                                        .suggests((ctx, builder) -> {
                                            List<String> ids = new ArrayList<>();
                                            Race race = raceManager.getRace(StringArgumentType.getString(ctx, "race"));
                                            if (race != null) race.getCheckpoints().forEach(cp -> ids.add(String.valueOf(cp.getId())));
                                            return CommandUtil.suggest(ids, builder);
                                        })
                                        .executes(this::addAlternate))))
                .then(Commands.literal("clearAll")
                        .requires(perm(PERM_CLEAR_ALL))
                        .then(raceArgument("race", raceManager).executes(this::clearAll)))
                .then(autotrace())
                .build();
    }

    /** /checkpoint create [sector|finish] <race>   (+ legacy "/checkpoint create <race> SECTOR") */
    private LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("create")
                .requires(perm(PERM_CREATE))
                .executes(ctx -> help(ctx, "checkpoint.create.usage"))
                .then(Commands.literal("sector")
                        .then(raceArgument("race", raceManager)
                                .executes(ctx -> create(ctx, Checkpoint.Type.SECTOR))))
                .then(Commands.literal("finish")
                        .then(raceArgument("race", raceManager)
                                .executes(ctx -> create(ctx, Checkpoint.Type.START_FINISH))))
                .then(raceArgument("race", raceManager)
                        .executes(ctx -> create(ctx, null))
                        .then(Commands.literal("SECTOR")
                                .executes(ctx -> create(ctx, Checkpoint.Type.SECTOR))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> removeNode(String literal) {
        return Commands.literal(literal)
                .requires(perm(PERM_REMOVE))
                .executes(this::removeNearest)
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .suggests((ctx, builder) -> {
                            List<String> ids = new ArrayList<>();
                            for (int i = 0; i < checkpointManager.count(); i++) ids.add(String.valueOf(i));
                            return CommandUtil.suggest(ids, builder);
                        })
                        .then(raceArgument("race", raceManager).executes(this::removeById)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> autotrace() {
        return Commands.literal("autotrace")
                .requires(perm(PERM_AUTOTRACE))
                .executes(ctx -> help(ctx, "checkpoint.autotrace.help"))
                .then(Commands.literal("start")
                        .executes(ctx -> help(ctx, "checkpoint.autotrace.usageStart"))
                        .then(raceArgument("race", raceManager)
                                .executes(ctx -> autoStart(ctx, false))
                                .then(Commands.literal("--create").executes(ctx -> autoStart(ctx, true)))))
                .then(Commands.literal("confirm").executes(this::autoConfirm))
                .then(autoConfigure())
                .then(Commands.literal("stop").executes(this::autoStop))
                .then(Commands.literal("preview").executes(this::autoPreview))
                .then(Commands.literal("sector").executes(this::autoSector))
                .then(Commands.literal("add").executes(this::autoAdd))
                .then(Commands.literal("resize").executes(this::autoResize))
                .then(Commands.literal("delete").executes(this::autoDelete))
                .then(Commands.literal("accept").executes(this::autoAccept))
                .then(Commands.literal("cancel")
                        .executes(ctx -> autoCancel(ctx, false))
                        .then(Commands.literal("--noRestart").executes(ctx -> autoCancel(ctx, true))));
    }

    /** /checkpoint autotrace configure <width|height|spacing> <value> | loop [toggle] */
    private LiteralArgumentBuilder<CommandSourceStack> autoConfigure() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("configure")
                .executes(ctx -> help(ctx, "checkpoint.autotrace.usageConfigure"));

        for (String param : List.of("width", "height", "spacing")) {
            node.then(Commands.literal(param)
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1))
                            .executes(ctx -> autoConfigureNumber(ctx, param))));
        }

        // The clickable panel runs "configure loop toggle"; the trailing word is ignored.
        node.then(Commands.literal("loop")
                .executes(this::autoToggleLoop)
                .then(Commands.argument("value", StringArgumentType.word()).executes(this::autoToggleLoop)));
        return node;
    }

    // ------------------------------------------------------------------
    // create / remove / view / list / misc
    // ------------------------------------------------------------------

    /**
     * explicitType == null  -> NORMAL (auto-promotes to START_FINISH if the race has no checkpoint yet)
     */
    private int create(CommandContext<CommandSourceStack> ctx, Checkpoint.Type explicitType) {
        Player p = player(ctx);

        Location l1 = PosSelection.pos1.get(p);
        Location l2 = PosSelection.pos2.get(p);
        if (l1 == null || l2 == null) {
            p.sendMessage(getMessage("pos.notSet"));
            return 0;
        }

        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        PosSelection.pos1.remove(p);
        PosSelection.pos2.remove(p);

        if (explicitType == Checkpoint.Type.SECTOR) {
            Checkpoint checkpoint = checkpointManager.savePlaneCheckpoint(race, l1, l2, Checkpoint.Type.SECTOR);
            if (checkpoint == null) { p.sendMessage(getMessage("error.unknown")); return 0; }
            p.sendMessage(getMessage("checkpoint.sectorSaved", formatArguments("sectorID", "" + checkpoint.getSectorID())));
            return Command.SINGLE_SUCCESS;
        }

        if (explicitType == Checkpoint.Type.START_FINISH) {
            Checkpoint checkpoint = checkpointManager.savePlaneCheckpoint(race, l1, l2, Checkpoint.Type.START_FINISH);
            if (checkpoint == null) { p.sendMessage(getMessage("error.unknown")); return 0; }
            p.sendMessage(getMessage("checkpoint.finishLineSaved"));
            return Command.SINGLE_SUCCESS;
        }

        if (race.getCheckpoints().isEmpty()) {
            checkpointManager.savePlaneCheckpoint(race, l1, l2, Checkpoint.Type.START_FINISH);
            p.sendMessage(getMessage("checkpoint.startLineSaved"));
            return Command.SINGLE_SUCCESS;
        }

        Checkpoint checkpoint = checkpointManager.savePlaneCheckpoint(race, l1, l2, Checkpoint.Type.NORMAL);
        if (checkpoint == null) { p.sendMessage(getMessage("error.unknown")); return 0; }
        p.sendMessage(getMessage("checkpoint.saved", formatArguments("id", String.valueOf(checkpoint.getId()))));
        return Command.SINGLE_SUCCESS;
    }

    private int removeNearest(CommandContext<CommandSourceStack> ctx) {
        Player p = player(ctx);

        CheckpointManager.NearestCheckpointOutput output = checkpointManager.getNearest(p.getLocation());
        Race race = output.bestRace();
        Checkpoint checkpoint = output.checkpoint();
        Checkpoint.AlternateRoute alt = output.alt();
        double dist = output.distance();

        if ((checkpoint == null && alt == null) || dist > 50) {
            p.sendMessage(getMessage("checkpoint.noCheckpointFound"));
            return 0;
        }

        if (checkpoint != null) checkpointManager.remove(race, checkpoint);
        else alt.remove();
        p.sendMessage(getMessage("checkpoint.removed", formatArguments("id",
                checkpoint == null ? alt.getParent().getId() + " - ALT" : "" + checkpoint.getId())));
        return Command.SINGLE_SUCCESS;
    }

    private int removeById(CommandContext<CommandSourceStack> ctx) {
        Player p = player(ctx);
        int checkpointNum = IntegerArgumentType.getInteger(ctx, "id");

        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        Checkpoint checkpoint = race.getCheckpoint(checkpointNum);
        if (checkpoint == null) {
            p.sendMessage(getMessage("checkpoint.invalid"));
            return 0;
        }

        if (!checkpointManager.remove(race, checkpoint)) {
            p.sendMessage(getMessage("error.unknown"));
            return 0;
        }
        p.sendMessage(getMessage("checkpoint.removed", formatArguments("id", "" + checkpointNum)));
        return Command.SINGLE_SUCCESS;
    }

    private int view(CommandContext<CommandSourceStack> ctx, boolean named) {
        Player p = player(ctx);
        List<Race> races = raceManager.getRaces();
        Race race;
        if (named) {
            race = getRace(ctx, "race", raceManager);
        } else if (races.size() == 1) {
            race = races.getFirst();
        } else {
            p.sendMessage(getMessage(races.isEmpty() ? "race.notFound" : "checkpoint.help"));
            return 0;
        }
        if (race == null) return 0;

        boolean nowOn = checkpointManager.toggleViewCheckpoints(p, race);
        p.sendMessage(getMessage(
                nowOn ? "checkpoint.view.on" : "checkpoint.view.off",
                formatArguments("race", race.getName())
        ));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx, int page) {
        Player p = player(ctx);
        Map<Race, List<Checkpoint>> all = checkpointManager.getAll();

        if (all.values().stream().allMatch(List::isEmpty)) {
            p.sendMessage(getMessage("checkpoint.noCheckpoints"));
            return 0;
        }

        // Flatten into ordered (Race, Checkpoint) pairs for pagination
        List<Map.Entry<Race, Checkpoint>> entries = new ArrayList<>();
        for (Map.Entry<Race, List<Checkpoint>> entry : all.entrySet()) {
            for (Checkpoint cp : entry.getValue()) {
                entries.add(Map.entry(entry.getKey(), cp));
            }
        }

        int totalPages = (int) Math.ceil((double) entries.size() / PAGE_SIZE);
        if (page > totalPages) {
            p.sendMessage(c("§cInvalid page (1-" + totalPages + ")"));
            return 0;
        }

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());

        p.sendMessage(c("§bCheckpoints §7(page §f" + page + "§7/§f" + totalPages + "§7):"));

        String lastRaceName = null;
        for (int i = start; i < end; i++) {
            Race race = entries.get(i).getKey();
            Checkpoint checkpoint = entries.get(i).getValue();

            // Print race header when the race changes
            if (!race.getName().equals(lastRaceName)) {
                lastRaceName = race.getName();
                p.sendMessage(c("§3▌ §b" + race.getName()));
            }

            Location min = checkpoint.getMin();
            Location max = checkpoint.getMax();

            Component minPos = Component.text(" X:" + min.getBlockX() + " Y:" + min.getBlockY() + " Z:" + min.getBlockZ())
                    .hoverEvent(HoverEvent.showText(c("Click to copy to clipboard")))
                    .clickEvent(ClickEvent.copyToClipboard(min.getBlockX() + " " + min.getBlockY() + " " + min.getBlockZ()));

            Component maxPos = Component.text(" X:" + max.getBlockX() + " Y:" + max.getBlockY() + " Z:" + max.getBlockZ())
                    .hoverEvent(HoverEvent.showText(c("Click to copy to clipboard")))
                    .clickEvent(ClickEvent.copyToClipboard(max.getBlockX() + " " + max.getBlockY() + " " + max.getBlockZ()));

            Component removeText = Component.text("[x]")
                    .hoverEvent(HoverEvent.showText(c("Click to delete checkpoint")))
                    .clickEvent(ClickEvent.runCommand("/checkpoint remove " + checkpoint.getId() + " " + race.getName()));

            Component tpText = Component.text("[→]")
                    .hoverEvent(HoverEvent.showText(c("Click to teleport")))
                    .clickEvent(ClickEvent.runCommand("/tp " + min.getBlockX() + " " + min.getBlockY() + " " + min.getBlockZ()));

            String shapeTag = checkpoint.getShape() == Checkpoint.Shape.PLANE ? getStringMessage("checkpoint.list.planeTag") : "";
            String altTag = checkpoint.getAlternates().isEmpty() ? "" :
                    getStringMessage("checkpoint.list.altTag").replace("%count%", "" + checkpoint.getAlternates().size());

            p.sendMessage(c(
                    "§e  #" + checkpoint.getId()
                            + " §7[" + race.getName() + "]"
                            + shapeTag
                            + altTag
            ).append(minPos).append(c(" §7→")).append(maxPos).append(c("§7 | §f")).append(removeText).append(c(" ")).append(tpText));
        }

        // Page navigation row
        if (totalPages > 1) {
            Component prev = page > 1
                    ? c("§a[←]").clickEvent(ClickEvent.runCommand("/checkpoint list " + (page - 1)))
                    : c("§8[←]");
            Component next = page < totalPages
                    ? c("§a[→]").clickEvent(ClickEvent.runCommand("/checkpoint list " + (page + 1)))
                    : c("§8[→]");

            p.sendMessage(prev.append(c("  §7Page §f" + page + " §7of §f" + totalPages + "  ")).append(next));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int count(CommandContext<CommandSourceStack> ctx) {
        int count = checkpointManager.getAllNoRaceInfo().size();
        player(ctx).sendMessage(getMessage("checkpoint.count", formatArguments("count", "" + count)));
        return Command.SINGLE_SUCCESS;
    }

    private int getId(CommandContext<CommandSourceStack> ctx) {
        Player p = player(ctx);
        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        int id = checkpointManager.getNearestCheckpoint(race, p.getLocation());
        p.sendMessage(getMessage("checkpoint.nearestCheckpointID", formatArguments(
                "race", race.getName(),
                "id", "" + id
        )));
        return Command.SINGLE_SUCCESS;
    }

    private int resetData(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player p = player(ctx);

        List<Player> targets = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (targets.isEmpty()) {
            p.sendMessage(getMessage("error.playerNotFound"));
            return 0;
        }
        Player target = targets.getFirst();

        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        // Remove data
        race.racers.remove(target.getUniqueId());

        // Reset player's data on live scoreboard
        liveScoreManager.removeRacer(target);
        p.sendMessage(getMessage("checkpoint.resetPlayerScore", formatArguments("player", target.getName())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Uses the wand (pos1/pos2) to define an alternate gate for an existing checkpoint — crossing
     * this gate will count as crossing the target checkpoint. Used for bypass routes (e.g. a
     * stands/pit lane) that rejoin the track without physically crossing the original checkpoint.
     */
    private int addAlternate(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);

        Location l1 = PosSelection.pos1.get(player);
        Location l2 = PosSelection.pos2.get(player);
        if (l1 == null || l2 == null) {
            player.sendMessage(getMessage("pos.notSet"));
            return 0;
        }

        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        Checkpoint target = race.getCheckpoint(IntegerArgumentType.getInteger(ctx, "checkpoint"));
        if (target == null) {
            player.sendMessage(getMessage("checkpoint.invalid"));
            return 0;
        }

        PosSelection.pos1.remove(player);
        PosSelection.pos2.remove(player);

        Vector span = l2.toVector().subtract(l1.toVector());
        Vector rawNormal = new Vector(-span.getZ(), 0, span.getX());
        if (rawNormal.lengthSquared() < 1e-6) rawNormal = new Vector(1, 0, 0);

        Location center = CheckpointGeometry.midpoint(l1, l2);
        double halfWidth = Math.max(l1.distance(l2) / 2.0 + CheckpointGeometry.WIDTH_PADDING, 0.5);
        double heightDiff = Math.abs(l1.getY() - l2.getY());
        double halfHeight = heightDiff > 0.5 ? heightDiff / 2.0 : 2.5;

        if (!checkpointManager.saveAlternateRoute(race, target, center, rawNormal, halfWidth, halfHeight)) {
            player.sendMessage(getMessage("error.unknown"));
            return 0;
        }

        player.sendMessage(getMessage("checkpoint.addAlternate.added", formatArguments("id", "" + target.getId())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Destructive — requires the command to be run twice within 10 seconds to actually clear.
     * The first run just warns and starts the confirmation window.
     */
    private int clearAll(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        String raceName = StringArgumentType.getString(ctx, "race");
        long now = System.currentTimeMillis();
        PendingClear pending = pendingClears.get(player.getUniqueId());

        if (pending != null && pending.raceName().equalsIgnoreCase(raceName) && now - pending.timestamp() <= 10_000) {
            pendingClears.remove(player.getUniqueId());

            if (!checkpointManager.clearAllCheckpoints(race)) {
                player.sendMessage(getMessage("error.unknown"));
                return 0;
            }

            player.sendMessage(getMessage("checkpoint.clearAll.cleared", formatArguments("race", race.getName())));
            return Command.SINGLE_SUCCESS;
        }

        pendingClears.put(player.getUniqueId(), new PendingClear(raceName, now));
        int count = race.getCheckpoints().size();
        player.sendMessage(getMessage("checkpoint.clearAll.warning", formatArguments("count", "" + count, "race", race.getName())));
        player.sendMessage(getMessage("checkpoint.clearAll.confirmHint", formatArguments("race", raceName)));
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------
    // autotrace
    // ------------------------------------------------------------------

    private int autoStart(CommandContext<CommandSourceStack> ctx, boolean isCreating) {
        Player player = player(ctx);
        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        PendingAutoTraceConfig pendingAutoTraceConfig = new PendingAutoTraceConfig(race);
        pendingAutoTraceConfig.isCreatingRace = isCreating;
        autoTraceManager.setPendingConfig(player, pendingAutoTraceConfig);
        player.sendMessage(buildAutoTracePanel(pendingAutoTraceConfig));
        return Command.SINGLE_SUCCESS;
    }

    private int autoConfirm(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PendingAutoTraceConfig pendingConfig = autoTraceManager.getPendingConfig(player);
        if (pendingConfig == null) {
            player.sendMessage(getMessage("checkpoint.autotrace.config.error.noneDefined")); // use /autotrace start first
            return 0;
        }

        Race race = pendingConfig.race;
        autoTraceManager.start(player, race, pendingConfig.spacing, pendingConfig.width / 2, pendingConfig.height / 2,
                pendingConfig.loop, pendingConfig.isCreatingRace);
        player.sendMessage(getMessage("checkpoint.autotrace.started", formatArguments("race", race.getName())));
        autoTraceManager.clearPendingConfig(player);
        return Command.SINGLE_SUCCESS;
    }

    private int autoConfigureNumber(CommandContext<CommandSourceStack> ctx, String param) {
        Player player = player(ctx);
        PendingAutoTraceConfig pendingConfig = autoTraceManager.getPendingConfig(player);
        if (pendingConfig == null) {
            player.sendMessage(getMessage("checkpoint.autotrace.config.error.noneDefined"));
            return 0;
        }

        double value = DoubleArgumentType.getDouble(ctx, "value");
        switch (param) {
            case "width" -> pendingConfig.width = value;
            case "height" -> pendingConfig.height = value;
            default -> pendingConfig.spacing = value;
        }

        player.sendMessage(getMessage("checkpoint.autotrace.config.defined", formatArguments(
                "param", param,
                "value", String.valueOf(value)
        )));
        player.sendMessage(buildAutoTracePanel(pendingConfig));
        return Command.SINGLE_SUCCESS;
    }

    private int autoToggleLoop(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PendingAutoTraceConfig pendingConfig = autoTraceManager.getPendingConfig(player);
        if (pendingConfig == null) {
            player.sendMessage(getMessage("checkpoint.autotrace.config.error.noneDefined"));
            return 0;
        }

        pendingConfig.loop = !pendingConfig.loop;
        player.sendMessage(getMessage("checkpoint.autotrace.config.defined", formatArguments(
                "param", "loop",
                "value", String.valueOf(pendingConfig.loop)
        )));
        player.sendMessage(buildAutoTracePanel(pendingConfig));
        return Command.SINGLE_SUCCESS;
    }

    private int autoStop(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (!autoTraceManager.hasSession(player)) {
            player.sendMessage(getMessage("checkpoint.autotrace.noSession"));
            return 0;
        }

        autoTraceManager.stop(player);
        List<Checkpoint> generated = autoTraceManager.generatePreview(player);

        if (generated.isEmpty()) {
            player.sendMessage(getMessage("checkpoint.autotrace.notEnoughPoints"));
            return 0;
        }

        player.sendMessage(getMessage("checkpoint.autotrace.stopped", formatArguments("count", "" + generated.size())));
        return Command.SINGLE_SUCCESS;
    }

    private int autoPreview(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (!hasPreview(player)) {
            player.sendMessage(getMessage("checkpoint.autotrace.noPreview"));
            return 0;
        }

        boolean nowOn = autoTraceManager.togglePreview(player);
        player.sendMessage(getMessage(nowOn ? "checkpoint.autotrace.previewOn" : "checkpoint.autotrace.previewOff"));
        return Command.SINGLE_SUCCESS;
    }

    private int autoSector(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        Location l1 = PosSelection.pos1.get(player);
        Location l2 = PosSelection.pos2.get(player);

        if (l1 == null || l2 == null) {
            player.sendMessage(getMessage("pos.notSet"));
            return 0;
        }
        if (!hasPreview(player)) {
            player.sendMessage(getMessage("checkpoint.autotrace.noPreviewForSector"));
            return 0;
        }

        PosSelection.pos1.remove(player);
        PosSelection.pos2.remove(player);

        if (!autoTraceManager.addSectorMarker(player, l1, l2)) {
            player.sendMessage(getMessage("checkpoint.autotrace.sectorFailed"));
            return 0;
        }

        player.sendMessage(getMessage("checkpoint.autotrace.sectorMarked"));
        return Command.SINGLE_SUCCESS;
    }

    private int autoAdd(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        Location l1 = PosSelection.pos1.get(player);
        Location l2 = PosSelection.pos2.get(player);

        if (l1 == null || l2 == null) {
            player.sendMessage(getMessage("pos.notSet"));
            return 0;
        }
        if (!hasPreview(player)) {
            player.sendMessage(getMessage("checkpoint.autotrace.noPreviewForSector"));
            return 0;
        }

        PosSelection.pos1.remove(player);
        PosSelection.pos2.remove(player);

        if (!autoTraceManager.addManualMarker(player, l1, l2)) {
            player.sendMessage(getMessage("checkpoint.autotrace.addFailed"));
            return 0;
        }

        player.sendMessage(getMessage("checkpoint.autotrace.added"));
        return Command.SINGLE_SUCCESS;
    }

    private int autoResize(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        Location l1 = PosSelection.pos1.get(player);
        Location l2 = PosSelection.pos2.get(player);

        if (l1 == null || l2 == null) {
            player.sendMessage(getMessage("pos.notSet"));
            return 0;
        }

        PosSelection.pos1.remove(player);
        PosSelection.pos2.remove(player);

        AutoTraceManager.EditInfo info = autoTraceManager.resizeNearest(player, l1, l2);
        if (info == null) {
            player.sendMessage(getMessage("checkpoint.autotrace.resizeFailed"));
            return 0;
        }

        player.sendMessage(getMessage("checkpoint.autotrace.resized", formatArguments(
                "position", "" + info.previewPosition(),
                "type", info.type().name(),
                "distance", String.format("%.1f", info.distance())
        )));
        return Command.SINGLE_SUCCESS;
    }

    private int autoDelete(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        AutoTraceManager.EditInfo info = autoTraceManager.deleteNearest(player, player.getLocation());
        if (info == null) {
            player.sendMessage(getMessage("checkpoint.autotrace.deleteFailed"));
            return 0;
        }

        player.sendMessage(getMessage("checkpoint.autotrace.deleted", formatArguments(
                "position", "" + info.previewPosition(),
                "type", info.type().name(),
                "distance", String.format("%.1f", info.distance())
        )));
        return Command.SINGLE_SUCCESS;
    }

    private int autoAccept(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        AutoTraceSession session = autoTraceManager.getSession(player);
        boolean isCreating = session != null && session.isCreating;

        if (!autoTraceManager.accept(player)) {
            player.sendMessage(getMessage("checkpoint.autotrace.acceptFailed"));
            if (isCreating) raceCreator.step4(player);
            return 0;
        }
        player.sendMessage(getMessage("checkpoint.autotrace.accepted"));
        if (isCreating) raceCreator.goToStep5RaceCreation(player);
        return Command.SINGLE_SUCCESS;
    }

    private int autoCancel(CommandContext<CommandSourceStack> ctx, boolean noRestart) {
        Player player = player(ctx);
        AutoTraceSession session = autoTraceManager.getSession(player);
        boolean isCreating = session != null && session.isCreating && !noRestart;

        autoTraceManager.cancel(player);
        player.sendMessage(getMessage("checkpoint.autotrace.cancelled"));
        if (isCreating) raceCreator.step4(player);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasPreview(Player player) {
        return autoTraceManager.hasSession(player) && !autoTraceManager.getSession(player).preview.isEmpty();
    }

    /**
     * Builds the clickable auto-trace settings panel: current values plus buttons to edit
     * each one (via SUGGEST_COMMAND, so the player just types the number and hits enter) and
     * a button to confirm and start recording.
     */
    private Component buildAutoTracePanel(PendingAutoTraceConfig config) {
        Component header = c(getStringMessage("checkpoint.autotrace.config.panel.header")
                .replace("%race%", config.race.getName()));

        Component spacingLine = c(getStringMessage("checkpoint.autotrace.config.panel.spacing")
                .replace("%value%", formatNum(config.spacing)))
                .append(c(" ")).append(editButton("spacing"));

        Component widthLine = c(getStringMessage("checkpoint.autotrace.config.panel.width")
                .replace("%value%", formatNum(config.width)))
                .append(c(" ")).append(editButton("width"));

        Component heightLine = c(getStringMessage("checkpoint.autotrace.config.panel.height")
                .replace("%value%", formatNum(config.height)))
                .append(c(" ")).append(editButton("height"));

        String loopKey = config.loop
                ? "checkpoint.autotrace.config.panel.loopOn"
                : "checkpoint.autotrace.config.panel.loopOff";
        Component loopLine = c(getStringMessage(loopKey))
                .append(c(" "))
                .append(c(getStringMessage("checkpoint.autotrace.config.panel.toggle"))
                        .clickEvent(ClickEvent.runCommand("/checkpoint autotrace configure loop toggle")));

        Component startButton = c(getStringMessage("checkpoint.autotrace.config.panel.start"))
                .clickEvent(ClickEvent.runCommand("/checkpoint autotrace confirm"));

        return header
                .append(c("\n")).append(spacingLine)
                .append(c("\n")).append(widthLine)
                .append(c("\n")).append(heightLine)
                .append(c("\n")).append(loopLine)
                .append(c("\n")).append(startButton);
    }

    private Component editButton(String param) {
        return c(getStringMessage("checkpoint.autotrace.config.panel.edit"))
                .clickEvent(ClickEvent.suggestCommand("/checkpoint autotrace configure " + param + " "));
    }

    /** Whole numbers print without a trailing ".0"; anything else keeps one decimal place. */
    private String formatNum(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.format("%.1f", value);
    }
}
