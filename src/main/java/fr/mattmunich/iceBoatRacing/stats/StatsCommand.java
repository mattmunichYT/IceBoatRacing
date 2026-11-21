package fr.mattmunich.iceBoatRacing.stats;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.CommandUtil.*;
import static fr.mattmunich.iceBoatRacing.Main.formatTime;
import static fr.mattmunich.iceBoatRacing.Messages.*;

/**
 * Implements the {@code /race stats [player] [race]} sub-tree.
 *
 * <p>This class intentionally contains only the presentation layer. All data retrieval
 * is delegated to {@link StatsManager}.
 *
 * <h3>Command forms</h3>
 * <pre>
 *   /race stats                      – own stats, all races (or sole race if only one)
 *   /race stats &lt;race&gt;               – own stats for a specific race
 *   /race stats &lt;player&gt;            – target's stats, all races (admin)
 *   /race stats &lt;player&gt; &lt;race&gt;    – target's stats for a specific race (admin)
 * </pre>
 */
public class StatsCommand {

    private static final String PERM_STATS       = "iceboatracing.command.race.stats";
    private static final String PERM_STATS_OTHER = "iceboatracing.command.race.stats.other";

    private final StatsManager statsManager;
    private final RaceManager  raceManager;

    public StatsCommand(StatsManager statsManager, RaceManager raceManager) {
        this.statsManager = statsManager;
        this.raceManager  = raceManager;
    }

    /**
     * Builds and returns the {@code stats} literal node, ready to be attached as
     * a child of the {@code /race} command.
     */
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("stats")
                .requires(perm(PERM_STATS, PERM_STATS_OTHER))
                // /race stats
                .executes(ctx -> showOwnAllRaces(ctx.getSource().getSender()))
                // /race stats <race|player>
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            List<String> options = new ArrayList<>(raceManager.getRaces().stream().map(Race::getName).toList());
                            if (ctx.getSource().getSender().hasPermission(PERM_STATS_OTHER)) {
                                options.addAll(Arrays.stream(Bukkit.getOfflinePlayers()).toList().stream().map(OfflinePlayer::getName).toList());
                            }
                            return suggest(options, builder);
                        })
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String targetName = StringArgumentType.getString(ctx, "player");

                            // Check if the argument is a known race name first (/race stats <raceName>)
                            Race matchedRace = raceManager.getRace(targetName);
                            if (matchedRace != null) {
                                return showOwnRace(sender, matchedRace);
                            }

                            // Otherwise treat as a player name (/race stats <player>)
                            return showOtherAllRaces(sender, targetName);
                        })
                        // /race stats <player> <race>
                        .then(raceArgument("race", raceManager)
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    Race race = getRace(ctx, "race", raceManager);
                                    if (race == null) return 0;
                                    return showOtherRace(sender, targetName, race);
                                })
                        )
                )
                .build();
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private int showOwnAllRaces(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(getMessage("error.playerToExecuteCommand"));
            return 0;
        }
        List<Race> races = raceManager.getRaces();
        if (races.isEmpty()) {
            sender.sendMessage(getMessage("race.notFound"));
            return Command.SINGLE_SUCCESS;
        }
        if (races.size() == 1) {
            return showStats(sender, p.getUniqueId(), p.getName(), races.getFirst());
        }
        return showSummary(sender, p.getUniqueId(), p.getName());
    }

    private int showOwnRace(CommandSender sender, Race race) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(getMessage("error.playerToExecuteCommand"));
            return 0;
        }
        return showStats(sender, p.getUniqueId(), p.getName(), race);
    }

    private int showOtherAllRaces(CommandSender sender, String targetName) {
        if (!sender.hasPermission(PERM_STATS_OTHER)) {
            sender.sendMessage(getMessage("error.noPermission"));
            return 0;
        }
        UUID targetUuid = resolveUUID(sender, targetName);
        if (targetUuid == null) return 0;

        List<Race> races = raceManager.getRaces();
        if (races.isEmpty()) {
            sender.sendMessage(getMessage("race.notFound"));
            return Command.SINGLE_SUCCESS;
        }
        if (races.size() == 1) {
            return showStats(sender, targetUuid, targetName, races.getFirst());
        }
        return showSummary(sender, targetUuid, targetName);
    }

    private int showOtherRace(CommandSender sender, String targetName, Race race) {
        if (!sender.hasPermission(PERM_STATS_OTHER)) {
            sender.sendMessage(getMessage("error.noPermission"));
            return 0;
        }
        UUID targetUuid = resolveUUID(sender, targetName);
        if (targetUuid == null) return 0;
        return showStats(sender, targetUuid, targetName, race);
    }

    // -------------------------------------------------------------------------
    // Rendering helpers
    // -------------------------------------------------------------------------

    /** Shows full stats for one player on one race. */
    private int showStats(CommandSender sender, UUID uuid, String playerName, Race race) {
        PlayerRaceStats stats = statsManager.getOfficialStats(uuid, race.getName());
        PlayerRaceStats trainingStats = statsManager.getTrainingStats(uuid, race.getName());

        int officialLaps = stats != null ? stats.getCompletedLaps() : 0;
        int trainingLaps = trainingStats != null ? trainingStats.getCompletedLaps() : 0;

        if (officialLaps == 0 && trainingLaps == 0) {
            sender.sendMessage(getMessage("race.stats.noData",
                    formatArguments("player", playerName, "race", race.getName())));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(getMessage("race.stats.header",
                formatArguments("player", playerName, "race", race.getName())));

        if (stats != null && officialLaps > 0) {
            OptionalLong pb      = stats.getPersonalBestLapMs();
            OptionalLong avg     = stats.getAverageLapMs();
            OptionalLong fastest = stats.getFastestRaceMs();

            sender.sendMessage(getMessage("race.stats.races",
                    formatArguments("count", stats.getCompletedRaces())));
            sender.sendMessage(getMessage("race.stats.laps",
                    formatArguments("count", stats.getCompletedLaps())));
            sender.sendMessage(getMessage("race.stats.personalBest",
                    formatArguments("time", pb.isPresent() ? formatTime(pb.getAsLong()) : "N/A")));
            sender.sendMessage(getMessage("race.stats.averageLap",
                    formatArguments("time", avg.isPresent() ? formatTime(avg.getAsLong()) : "N/A")));
            sender.sendMessage(getMessage("race.stats.fastestRace",
                    formatArguments("time", fastest.isPresent() ? formatTime(fastest.getAsLong()) : "N/A")));
        }

        if (trainingStats != null && trainingLaps > 0) {
            OptionalLong tPb = trainingStats.getPersonalBestLapMs();
            OptionalLong tAvg = trainingStats.getAverageLapMs();

            sender.sendMessage(getMessage("race.stats.trainingLaps",
                    formatArguments("count", trainingLaps)));
            sender.sendMessage(getMessage("race.stats.trainingPB",
                    formatArguments("time", tPb.isPresent() ? formatTime(tPb.getAsLong()) : "N/A")));
            sender.sendMessage(getMessage("race.stats.trainingAverage",
                    formatArguments("time", tAvg.isPresent() ? formatTime(tAvg.getAsLong()) : "N/A")));
        }

        sender.sendMessage(getMessage("race.stats.footer"));

        return Command.SINGLE_SUCCESS;
    }

    /** Shows a summary across all races (personal bests only). */
    private int showSummary(CommandSender sender, UUID uuid, String playerName) {
        var allStats = statsManager.getAllOfficialStats(uuid);
        var allTrainingStats = statsManager.getAllTrainingStats(uuid);

        if (allStats.isEmpty() && allTrainingStats.isEmpty()) {
            sender.sendMessage(getMessage("race.stats.noDataAny",
                    formatArguments("player", playerName)));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(getMessage("race.stats.summaryHeader",
                formatArguments("player", playerName)));

        for (Race race : raceManager.getRaces()) {
            PlayerRaceStats s = allStats.get(race.getName());
            PlayerRaceStats ts = allTrainingStats.get(race.getName());
            int officialLaps = s != null ? s.getCompletedLaps() : 0;
            int trainingLaps = ts != null ? ts.getCompletedLaps() : 0;
            if (officialLaps == 0 && trainingLaps == 0) continue;

            OptionalLong pb = s != null ? s.getPersonalBestLapMs() : OptionalLong.empty();
            String pbStr = pb.isPresent() ? formatTime(pb.getAsLong()) : "N/A";
            if (ts != null && ts.getPersonalBestLapMs().isPresent()) {
                pbStr += " §8(§7Train: §f" + formatTime(ts.getPersonalBestLapMs().getAsLong()) + "§8)";
            }

            sender.sendMessage(getMessage("race.stats.summaryRow",
                    formatArguments(
                            "race",  race.getName(),
                            "races", s != null ? s.getCompletedRaces() : 0,
                            "laps",  officialLaps + trainingLaps,
                            "pb",    pbStr
                    )));
        }

        sender.sendMessage(getMessage("race.stats.footer"));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private UUID resolveUUID(CommandSender sender, String name) {
        // Check online players first
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();

        // Fall back to offline cached players
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null && offline.hasPlayedBefore()) return offline.getUniqueId();

        sender.sendMessage(getMessage("error.playerNeverJoined",
                formatArguments("name", name)));
        return null;
    }
}
