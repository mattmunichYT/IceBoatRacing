package fr.mattmunich.iceBoatRacing.cars;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.CommandUtil.*;
import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Messages.*;

/**
 * /car command (Brigadier).
 * <pre>
 * /car create
 * /car list [page]
 * /car changeOwner <id> <race> <player>
 * /car remove <id> <race>
 * /car selectRace <race>      (used by the car creation wizard)
 * </pre>
 */
public class CarCommand {
    public static final String PERM_BASE = "iceboatracing.command.car";
    public static final String PERM_CREATE = PERM_BASE + ".create";           // create + selectRace
    public static final String PERM_LIST = PERM_BASE + ".list";
    public static final String PERM_REMOVE = PERM_BASE + ".remove";
    public static final String PERM_CHANGE_OWNER = PERM_BASE + ".changeowner";

    private static final int PAGE_SIZE = 10;

    private final CarManager carManager;
    private final RaceManager raceManager;
    private final CarCreator carCreator;

    public CarCommand(CarManager carManager, RaceManager raceManager, CarCreator carCreator) {
        this.carManager = carManager;
        this.raceManager = raceManager;
        this.carCreator = carCreator;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("car")
                .requires(playerPerm(PERM_BASE, PERM_CREATE, PERM_LIST, PERM_REMOVE, PERM_CHANGE_OWNER))
                .executes(ctx -> help(ctx, "car.help"))
                .then(Commands.literal("create")
                        .requires(perm(PERM_CREATE))
                        .executes(ctx -> {
                            carCreator.createCar(player(ctx));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("list")
                        .requires(perm(PERM_LIST))
                        .executes(ctx -> list(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("changeOwner")
                        .requires(perm(PERM_CHANGE_OWNER))
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .then(raceArgument("race", raceManager)
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests((ctx, builder) -> CommandUtil.suggest(
                                                        Arrays.stream(Bukkit.getOfflinePlayers())
                                                                .map(OfflinePlayer::getName)
                                                                .filter(Objects::nonNull)
                                                                .toList(), builder))
                                                .executes(this::changeOwner)))))
                .then(Commands.literal("remove")
                        .requires(perm(PERM_REMOVE))
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .then(raceArgument("race", raceManager).executes(this::remove))))
                .then(Commands.literal("selectRace")
                        .requires(perm(PERM_CREATE))
                        .then(raceArgument("race", raceManager).executes(ctx -> {
                            // Race validation is done by the creator, as before
                            carCreator.selectRace(
                                    new String[]{"selectRace", StringArgumentType.getString(ctx, "race")},
                                    player(ctx));
                            return Command.SINGLE_SUCCESS;
                        })))
                .build();
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    private int remove(CommandContext<CommandSourceStack> ctx) {
        Player p = player(ctx);
        int id = IntegerArgumentType.getInteger(ctx, "id");

        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        Car car = carManager.get(race, id);
        if (car == null) {
            p.sendMessage(getMessage("car.invalidID", formatArguments("id", String.valueOf(id))));
            return 0;
        }

        if (!carManager.remove(race, car)) {
            p.sendMessage(getMessage("error.unknown"));
            return 0;
        }
        p.sendMessage(getMessage("car.removed", formatArguments("id", String.valueOf(id))));
        return Command.SINGLE_SUCCESS;
    }

    private int changeOwner(CommandContext<CommandSourceStack> ctx) {
        Player p = player(ctx);
        int id = IntegerArgumentType.getInteger(ctx, "id");

        Race race = getRace(ctx, "race", raceManager);
        if (race == null) return 0;

        Car car = carManager.get(race, id);
        if (car == null) {
            p.sendMessage(getMessage("car.invalidID", formatArguments("id", String.valueOf(id))));
            return 0;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(StringArgumentType.getString(ctx, "player"));
        if (!target.hasPlayedBefore()) {
            p.sendMessage(getMessage("error.playerNotFound"));
            return 0;
        }

        if (!carManager.changeOwner(race, car, target.getUniqueId())) {
            p.sendMessage(getMessage("error.unknown"));
            return 0;
        }
        p.sendMessage(getMessage("car.changedOwner", formatArguments("owner", target.getName())));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx, int page) {
        Player p = player(ctx);
        Map<Race, List<Car>> all = carManager.getAll();

        if (all.values().stream().allMatch(List::isEmpty)) {
            p.sendMessage(getMessage("car.noCars"));
            return 0;
        }

        // Flatten into ordered (Race, Car) pairs for pagination
        List<Map.Entry<Race, Car>> entries = new ArrayList<>();
        for (Map.Entry<Race, List<Car>> entry : all.entrySet()) {
            for (Car car : entry.getValue()) {
                entries.add(Map.entry(entry.getKey(), car));
            }
        }

        int totalPages = (int) Math.ceil((double) entries.size() / PAGE_SIZE);
        if (page > totalPages) {
            p.sendMessage(c("§cInvalid page (1-" + totalPages + ")"));
            return 0;
        }

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());

        p.sendMessage(c("§bCars §7(page §f" + page + "§7/§f" + totalPages + "§7):"));

        String lastRaceName = null;
        for (int i = start; i < end; i++) {
            Race race = entries.get(i).getKey();
            Car car = entries.get(i).getValue();

            // Print race header when the race changes
            if (!race.getName().equals(lastRaceName)) {
                lastRaceName = race.getName();
                p.sendMessage(c("§3▌ §b" + race.getName()));
            }

            Location loc = car.getStartingLocation();

            String ownerName = "Unknown";
            OfflinePlayer owner = Bukkit.getOfflinePlayer(car.getOwner());
            if (owner.getName() != null) ownerName = owner.getName();

            Component removeText = c("§c[x]")
                    .hoverEvent(HoverEvent.showText(c("Click to delete")))
                    .clickEvent(ClickEvent.runCommand("/car remove " + car.getId() + " " + race.getName()));

            Component tpText = c("§a[→]")
                    .hoverEvent(HoverEvent.showText(c("Click to teleport")))
                    .clickEvent(ClickEvent.runCommand(
                            "/tp " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                    ));

            Component changeOwnerText = c("§e[✎]")
                    .hoverEvent(HoverEvent.showText(c("Click to change the car's owner")))
                    .clickEvent(ClickEvent.suggestCommand(
                            "/car changeOwner " + car.getId() + " " + race.getName() + " "
                    ));

            Component line = c(
                    "§3  #" + car.getId()
                            + " §7Owner: §f" + ownerName
                            + " §7Type: §f" + car.getBoatMaterial().name().replace("_BOAT", "").replace("_RAFT", "").toLowerCase()
                            + " §7[" + (loc.getWorld() == null ? "§8§oUnknown" : loc.getWorld().getName()) + "]"
                            + " §fX:" + loc.getBlockX()
                            + " Y:" + loc.getBlockY()
                            + " Z:" + loc.getBlockZ()
                            + " §7| §f"
            )
                    .append(removeText)
                    .append(c(" "))
                    .append(tpText)
                    .append(c(" "))
                    .append(changeOwnerText);

            p.sendMessage(line);
        }

        // Page navigation row
        if (totalPages > 1) {
            Component prev = page > 1
                    ? c("§a[←]").clickEvent(ClickEvent.runCommand("/car list " + (page - 1)))
                    : c("§8[←]");
            Component next = page < totalPages
                    ? c("§a[→]").clickEvent(ClickEvent.runCommand("/car list " + (page + 1)))
                    : c("§8[→]");

            p.sendMessage(prev.append(c("  §7Page §f" + page + " §7of §f" + totalPages + "  ")).append(next));
        }
        return Command.SINGLE_SUCCESS;
    }
}