package fr.mattmunich.iceBoatRacing;

import fr.mattmunich.iceBoatRacing.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.Racer;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Main.formatTime;

public class LiveScoreManager {
    /** Shown for a value that doesn't exist (yet), e.g. the gap to the player ahead when you are leading. */
    private static final String NO_VALUE = "-";

    /** How often the sidebar is refreshed on its own, so that time-based values keep ticking between checkpoints. */
    private static final long REFRESH_INTERVAL_TICKS = 20L;

    private final Main main;
    private BukkitTask refreshTask;

    /** Registered racers keep their personalized view, including finishers and disqualified racers. */
    private final Map<UUID, Racer> tracked = new LinkedHashMap<>();
    /** One independent sidebar scoreboard per viewer, with the previous scoreboard retained for handoff. */
    private final Map<UUID, SidebarState> sidebars = new HashMap<>();
    /** Scoreboards hidden by /sidebar; the value is the scoreboard to restore when shown again. */
    private final Map<UUID, Scoreboard> hiddenSidebarScoreboards = new HashMap<>();

    private static final String OBJECTIVE_NAME = "ibr_sidebar";
    private static final int MAX_LINES = 15;

    public LiveScoreManager(Main main) {
        this.main = main;
        Objective oldSharedSidebar = Bukkit.getScoreboardManager().getMainScoreboard().getObjective("IBR.liveScore");
        if (oldSharedSidebar != null) oldSharedSidebar.unregister();
        startRefreshTask();
    }

    private void startRefreshTask() {
        stopRefreshTask();
        refreshTask = Bukkit.getScheduler().runTaskTimer(main, this::refreshAll, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    private void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public LiveScoreManager reload() {
        reset();
        startRefreshTask();
        return this;
    }

    public void reset() {
        stopRefreshTask();
        tracked.clear();
        for (UUID playerId : new ArrayList<>(sidebars.keySet())) clearSidebar(playerId);
        hiddenSidebarScoreboards.clear();
    }

    public void empty() {
        tracked.clear();
        for (UUID playerId : new ArrayList<>(sidebars.keySet())) clearSidebar(playerId);
        hiddenSidebarScoreboards.clear();
    }

    public void addRacer(Racer racer) throws NullPointerException, IllegalArgumentException {
        tracked.put(racer.uniqueId, racer);
        refreshRace(racer.getRace(), new Standings(racer.getRace()));
    }

    public void removeRacer(Racer racer) {
        // Finishers remain racers for sidebar purposes; this call refreshes their final position.
        if (racer.getRace() != null) refreshRace(racer.getRace(), new Standings(racer.getRace()));
    }

    public void removeRacer(Player racer) {
        Racer removed = tracked.remove(racer.getUniqueId());
        if (removed != null) {
            clearSidebar(racer.getUniqueId());
            if (removed.getRace() != null) refreshRace(removed.getRace(), new Standings(removed.getRace()));
        }
    }

    /** Toggles the race sidebar for one player without changing the shared scoreboard. */
    public boolean toggleSidebar(Player player) {
        UUID playerId = player.getUniqueId();
        Scoreboard previous = hiddenSidebarScoreboards.remove(playerId);
        if (previous != null) {
            SidebarState state = sidebars.get(playerId);
            if (state != null && state.yieldedToTraining
                    && !hasTrainingScoreboard(player.getScoreboard()) && hasTrainingScoreboard(previous)) {
                previous = state.scoreboard;
            }
            player.setScoreboard(previous);
            refreshAll();
            return false;
        }

        hiddenSidebarScoreboards.put(playerId, player.getScoreboard());
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        return true;
    }

    /** Drops per-session sidebar state when the player disconnects. */
    public void forgetSidebar(Player player) {
        UUID playerId = player.getUniqueId();
        clearSidebar(playerId);
        hiddenSidebarScoreboards.remove(playerId);
    }

    /**
     * Call when a racer's progress changed. Refreshes the whole race's lines, not just this racer's,
     * because the ranking and the gaps of the other racers may have changed too.
     */
    public void updateRacer(Racer racer) {
        tracked.putIfAbsent(racer.uniqueId, racer);
        if (racer.getRace() != null) refreshRace(racer.getRace(), new Standings(racer.getRace()));
    }

    private void refreshAll() {
        if (tracked.isEmpty()) {
            for (UUID playerId : new ArrayList<>(sidebars.keySet())) clearSidebar(playerId);
            return;
        }
        Set<Race> races = new LinkedHashSet<>();
        for (Racer racer : tracked.values()) {
            if (racer.getRace() != null) races.add(racer.getRace());
        }
        Set<UUID> activeViewers = new HashSet<>();
        for (Race race : races) {
            refreshRace(race, new Standings(race));
            for (UUID id : race.racers.keySet()) activeViewers.add(id);
            for (Player player : race.getWorld().getPlayers()) {
                if (!race.racers.containsKey(player.getUniqueId())) activeViewers.add(player.getUniqueId());
            }
        }
        for (UUID playerId : new ArrayList<>(sidebars.keySet())) {
            if (!activeViewers.contains(playerId)) clearSidebar(playerId);
        }
    }

    private void refreshRace(Race race, Standings standings) {
        if (race == null || race.getWorld() == null) return;
        Set<UUID> racerIds = race.racers.keySet();
        for (Racer racer : race.racers.values()) {
            Player player = Bukkit.getPlayer(racer.uniqueId);
            if (player != null && player.isOnline()) {
                refreshViewer(player, race, standings, racer, false);
            }
        }
        for (Player player : race.getWorld().getPlayers()) {
            if (!racerIds.contains(player.getUniqueId())) refreshViewer(player, race, standings, null, true);
        }
    }

    private void refreshViewer(Player player, Race race, Standings standings, Racer racer, boolean spectator) {
        UUID id = player.getUniqueId();
        SidebarState state = sidebars.get(id);
        if (hasTrainingScoreboard(player.getScoreboard())) {
            if (state != null) state.yieldedToTraining = true;
            return;
        }
        if (hiddenSidebarScoreboards.containsKey(id)) {
            if (state != null && state.yieldedToTraining
                    && player.getScoreboard() == state.previousScoreboard) {
                player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
            return;
        }
        if (state != null && state.race != race) {
            clearSidebar(id);
            state = null;
        }
        if (state == null) {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
            state = new SidebarState(race, board, objective, player.getScoreboard());
            sidebars.put(id, state);
            player.setScoreboard(board);
        } else if (player.getScoreboard() == state.previousScoreboard
                || (state.yieldedToTraining && player.getScoreboard() == Bukkit.getScoreboardManager().getMainScoreboard())) {
            // TrainingManager restores the main scoreboard when training ends.
            player.setScoreboard(state.scoreboard);
            state.yieldedToTraining = false;
        } else if (player.getScoreboard() != state.scoreboard) {
            // Another plugin currently owns the player's scoreboard.
            return;
        }

        SidebarLayout layout = readLayout(spectator ? "spectator" : "racer");
        Map<String, String> values = spectator
                ? spectatorValues(race, standings, layout)
                : racerValues(racer, standings, layout);
        state.objective.displayName(c(formatTemplate(layout.title, values)));
        List<String> lines = layout.lines.stream()
                .map(line -> formatTemplate(line, values))
                .limit(MAX_LINES)
                .toList();
        for (int i = 0; i < lines.size(); i++) {
            String entry = "ibr-line-" + i;
            Score score = state.objective.getScore(entry);
            score.setScore(MAX_LINES - i);
            score.customName(c(lines.get(i)));
            state.lineEntries.add(entry);
        }
        Set<String> currentEntries = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) currentEntries.add("ibr-line-" + i);
        for (String stale : new HashSet<>(state.lineEntries)) {
            if (!currentEntries.contains(stale)) {
                state.objective.getScore(stale).resetScore();
                state.lineEntries.remove(stale);
            }
        }
    }

    private static boolean hasTrainingScoreboard(Scoreboard scoreboard) {
        return scoreboard.getTeam("ibr_training") != null;
    }

    private Map<String, String> racerValues(Racer racer, Standings standings, SidebarLayout layout) {
        Map<String, String> values = new HashMap<>();
        if (racer == null) return values;
        int index = standings.indexOf(racer);
        int position = index + 1;
        long now = System.currentTimeMillis();
        List<Long> lapTimes = racer.getLapTimes();
        String raceTime = racer.startTime <= 0 ? NO_VALUE : formatTime((racer.endTime > 0 ? racer.endTime : now) - racer.startTime);
        String gapToLeader = index > 0 ? formatGap(racer, standings.get(0)) : NO_VALUE;
        values.put("race", racer.getRace().getName());
        values.put("player", racer.getPlayerName());
        values.put("position", index < 0 ? NO_VALUE : String.valueOf(position));
        values.put("lap", String.valueOf(Math.max(0, racer.lapCount)));
        values.put("lapCount", String.valueOf(racer.getRace().getLapCount()));
        values.put("completedLapCount", String.valueOf(racer.getCompletedLapCount()));
        values.put("lapProgress", standings.lapProgress(racer) + "%");
        values.put("lastLapTime", lapTimes.isEmpty() ? NO_VALUE : formatTime(lapTimes.getLast()));
        values.put("bestLapTime", lapTimes.isEmpty() ? NO_VALUE : formatTime(racer.bestLapTime()));
        values.put("raceTime", raceTime);
        values.put("gapToLeader", gapToLeader);
        values.put("gapToNext", index > 0 ? formatGap(racer, standings.get(index - 1)) : NO_VALUE);
        values.put("gapToPrevious", index >= 0 && index < standings.size() - 1 ? formatGap(standings.get(index + 1), racer) : NO_VALUE);
        values.put("completedPitStops", String.valueOf(racer.getCompletedPitStops()));
        values.put("requiredPitStops", String.valueOf(racer.getRace().getRequiredPitStops()));
        values.put("remainingPitStops", String.valueOf(Math.max(0, racer.getRace().getRequiredPitStops() - racer.getCompletedPitStops())));
        int positionChange = racer.gridPosition > 0 ? racer.gridPosition - position : 0;
        values.put("positionChange", positionChange > 0 ? "+" + positionChange : String.valueOf(positionChange));
        values.put("currentLap", String.valueOf(Math.max(0, racer.lapCount)));
        values.put("playersRemaining", String.valueOf(standings.playersRemaining));
        values.put("isLeader", String.valueOf(index == 0));

        Racer ahead = index > 0 ? standings.get(index - 1) : null;
        Racer behind = index >= 0 && index < standings.size() - 1 ? standings.get(index + 1) : null;
        values.put("aheadLine", ahead == null ? layout.emptyValue : racerRow(layout.aheadFormat, ahead, index - 1, standings));
        values.put("selfLine", racerRow(layout.selfFormat, racer, index, standings));
        values.put("behindLine", behind == null ? layout.emptyValue : racerRow(layout.behindFormat, behind, index + 1, standings));
        return values;
    }

    private Map<String, String> spectatorValues(Race race, Standings standings, SidebarLayout layout) {
        Map<String, String> values = new HashMap<>();
        values.put("race", race.getName());
        values.put("lapCount", String.valueOf(race.getLapCount()));
        values.put("requiredPitStops", String.valueOf(race.getRequiredPitStops()));
        values.put("playersRemaining", String.valueOf(standings.playersRemaining));
        values.put("racerCount", String.valueOf(race.racers.size()));
        for (int i = 0; i < 3; i++) {
            String key = "top" + (i + 1);
            if (i < standings.size()) values.put(key, racerRow(layout.topFormat, standings.get(i), i, standings));
            else values.put(key, layout.emptyValue);
        }
        Racer leader = standings.size() > 0 ? standings.get(0) : null;
        values.put("leader", leader == null ? layout.emptyValue : leader.getPlayerName());
        values.put("leaderLap", leader == null ? NO_VALUE : String.valueOf(Math.max(0, leader.lapCount)));
        values.put("leaderLapProgress", leader == null ? NO_VALUE : standings.lapProgress(leader) + "%");
        values.put("leaderBestLap", leader == null || leader.getLapTimes().isEmpty() ? NO_VALUE : formatTime(leader.bestLapTime()));
        return values;
    }

    private String racerRow(String template, Racer racer, int index, Standings standings) {
        String gap = index > 0 ? formatGap(racer, standings.get(index - 1)) : NO_VALUE;
        return formatTemplate(template, Map.of(
                "position", String.valueOf(index + 1),
                "player", racer.getPlayerName(),
                "gap", gap,
                "lap", String.valueOf(Math.max(0, racer.lapCount))
        ));
    }

    private SidebarLayout readLayout(String kind) {
        FileConfiguration config = main.getConfig();
        String path = "raceSidebar." + kind;
        List<String> defaults = kind.equals("racer")
                ? List.of("§7%race%", "", "§bPOS §f%position%", "§bLAP §f%completedLapCount%/%lapCount%", "§bLAST §f%lastLapTime%", "§bBEST §f%bestLapTime%", "§bGAP §f%gapToLeader%", "", "%aheadLine%", "%selfLine%", "%behindLine%", "", "§bPITS §f%completedPitStops%/%requiredPitStops%")
                : List.of("§7%race%", "§7LIVE STANDINGS", "%top1%", "%top2%", "%top3%", "", "§bON TRACK §f%playersRemaining%");
        List<String> lines = config.getStringList(path + ".lines");
        if (lines.isEmpty()) lines = defaults;
        return new SidebarLayout(
                config.getString(path + ".title", "§3-- §bIce Boat Racing §3--"),
                lines,
                config.getString(path + ".emptyValue", "---"),
                config.getString(path + ".aheadFormat", "§e%position%. §f%player% §7%gap%"),
                config.getString(path + ".selfFormat", "§b> %position%. §f%player% §7%gap%"),
                config.getString(path + ".behindFormat", "§7%position%. §f%player% §8%gap%"),
                config.getString(path + ".topFormat", "§b%position%. §f%player% §7%gap%")
        );
    }

    private static String formatTemplate(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result.replaceAll("%[A-Za-z][A-Za-z0-9]*%", "");
    }

    private void clearSidebar(UUID playerId) {
        SidebarState state = sidebars.remove(playerId);
        if (state == null) return;
        Player player = Bukkit.getPlayer(playerId);
        Scoreboard restore = state.previousScoreboard;
        if (player != null && player.isOnline() && player.getScoreboard() == state.scoreboard) player.setScoreboard(restore);
        if (hiddenSidebarScoreboards.get(playerId) == state.scoreboard) hiddenSidebarScoreboards.put(playerId, restore);
        unregister(state);
    }

    private static void unregister(SidebarState state) {
        try { state.objective.unregister(); } catch (Exception ignored) {}
    }

    /** Clears one race's viewers without disturbing scoreboards for races in other worlds. */
    public void removeRace(Race race) {
        tracked.values().removeIf(racer -> racer.getRace() == race);
        for (UUID playerId : new ArrayList<>(sidebars.keySet())) {
            SidebarState state = sidebars.get(playerId);
            if (state != null && state.race == race) clearSidebar(playerId);
        }
        refreshAll();
    }

    private record SidebarLayout(String title, List<String> lines, String emptyValue,
                                 String aheadFormat, String selfFormat, String behindFormat, String topFormat) {}

    private static final class SidebarState {
        final Race race;
        final Scoreboard scoreboard;
        final Objective objective;
        final Scoreboard previousScoreboard;
        final Set<String> lineEntries = new HashSet<>();
        boolean yieldedToTraining;

        SidebarState(Race race, Scoreboard scoreboard, Objective objective, Scoreboard previousScoreboard) {
            this.race = race;
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.previousScoreboard = previousScoreboard;
        }
    }

    private static String formatGap(Racer behind, Racer ahead) {
        long gap = gap(behind, ahead);
        return gap < 0 ? NO_VALUE : formatTime(gap);
    }

    /**
     * Time between {@code ahead} and {@code behind} passing the same checkpoint: the latest one
     * that {@code behind} has passed and that both have a recorded time for.
     * @return the gap in ms, or -1 if there is no checkpoint both have passed yet
     */
    private static long gap(Racer behind, Racer ahead) {
        for (int progress = behind.getProgress(); progress > 0; progress--) {
            Long timeBehind = behind.getProgressTime(progress);
            Long timeAhead = ahead.getProgressTime(progress);
            if (timeBehind != null && timeAhead != null) return Math.max(0, timeBehind - timeAhead);
        }
        return -1;
    }

    private static double distance(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return 0;
        return a.distance(b);
    }

    /**
     * A snapshot of the race order, built once per refresh and shared by every racer's line.
     * <p>
     * Order: racers who finished (in finishing order), then those still racing, by how many checkpoints
     * they have passed; ties go to whoever passed their latest checkpoint first (like timing loops in F1).
     * Before the start, the starting grid order is used.
     */
    private static final class Standings {
        private final List<Racer> order = new ArrayList<>();
        final int playersRemaining;

        /** The checkpoints of one lap in driving order (start/finish first), and the length of the stretch after each one. */
        private final List<Checkpoint> lap;
        private final double[] segmentLength;
        private final double lapLength;

        Standings(Race race) {
            List<Racer> finished = new ArrayList<>();
            race.rankings.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> finished.add(e.getValue()));

            List<Racer> racing = new ArrayList<>();
            for (Racer racer : race.racing) {
                if (!finished.contains(racer) && !racer.disqualified) racing.add(racer);
            }

            List<Racer> disqualified = race.racers.values().stream()
                    .filter(racer -> racer.disqualified && !finished.contains(racer))
                    .toList();

            if (race.hasStarted()) assignGridPositions(race);

            racing.sort((a, b) -> {
                int byProgress = Integer.compare(b.getProgress(), a.getProgress());
                if (byProgress != 0) return byProgress;
                Long timeA = a.getProgressTime(a.getProgress());
                Long timeB = b.getProgressTime(b.getProgress());
                if (timeA != null && timeB != null && !timeA.equals(timeB)) return Long.compare(timeA, timeB);
                return Integer.compare(a.gridPosition, b.gridPosition);
            });

            order.addAll(finished);
            order.addAll(racing);
            order.addAll(disqualified);
            playersRemaining = racing.size();

            lap = new ArrayList<>(race.getCheckpoints());
            lap.sort(Comparator.comparingInt(Checkpoint::getId));
            segmentLength = new double[lap.size()];
            double total = 0;
            for (int i = 0; i < lap.size(); i++) {
                segmentLength[i] = distance(lap.get(i).getCenter(), lap.get((i + 1) % lap.size()).getCenter());
                total += segmentLength[i];
            }
            lapLength = total;
        }

        /**
         * Gives every racer who doesn't have one yet a grid position: the closer their starting spot
         * is to the start/finish line, the better. Done once, when the race starts, so that it doesn't
         * depend on where the cars are by the time this runs.
         */
        private static void assignGridPositions(Race race) {
            List<Racer> unassigned = new ArrayList<>();
            int highest = 0;
            for (Racer racer : race.racers.values()) {
                if (racer.gridPosition == 0) unassigned.add(racer);
                else highest = Math.max(highest, racer.gridPosition);
            }
            if (unassigned.isEmpty()) return;

            Location line = race.getCheckpoints().stream()
                    .filter(cp -> cp.getType() == Checkpoint.Type.START_FINISH)
                    .map(Checkpoint::getCenter)
                    .findFirst().orElse(null);
            if (line != null) {
                unassigned.sort(Comparator.comparingDouble(racer -> {
                    Location spawn = racer.car == null ? null : racer.car.getStartingLocation();
                    if (spawn == null || spawn.getWorld() == null || !spawn.getWorld().equals(line.getWorld())) return Double.MAX_VALUE;
                    return spawn.distanceSquared(line);
                }));
            }
            for (Racer racer : unassigned) racer.gridPosition = ++highest;
        }

        int size() { return order.size(); }

        Racer get(int index) { return order.get(index); }

        /** @return the racer's 0-based place, or -1 if they aren't part of the order (yet) */
        int indexOf(Racer racer) { return order.indexOf(racer); }

        /**
         * @return how far through the current lap the racer is, 0-100. Based on the distance covered
         * between the checkpoints (not just the number of checkpoints passed), so it moves smoothly.
         */
        int lapProgress(Racer racer) {
            int passed = racer.getCheckpointOrdinal(); // the last checkpoint passed is lap.get(passed - 1)
            if (racer.lapCount < 1 || passed < 1 || passed > lap.size() || lapLength <= 0) return 0;

            double covered = 0;
            for (int i = 0; i < passed - 1; i++) covered += segmentLength[i];

            // How far along the stretch to the next checkpoint (the last one leads back to the start/finish line)
            Location from = lap.get(passed - 1).getCenter();
            Location to = lap.get(passed % lap.size()).getCenter();
            Location position = racer.getPlayer() == null || !racer.getPlayer().isOnline() ? null : racer.getPlayer().getLocation();
            if (position != null && position.getWorld() != null && position.getWorld().equals(from.getWorld()) && position.getWorld().equals(to.getWorld())) {
                Vector stretch = to.toVector().subtract(from.toVector());
                double lengthSquared = stretch.lengthSquared();
                if (lengthSquared > 0) {
                    double fraction = position.toVector().subtract(from.toVector()).dot(stretch) / lengthSquared;
                    covered += Math.max(0, Math.min(1, fraction)) * segmentLength[passed - 1];
                }
            }

            return (int) Math.max(0, Math.min(100, Math.floor(covered / lapLength * 100)));
        }
    }
}
