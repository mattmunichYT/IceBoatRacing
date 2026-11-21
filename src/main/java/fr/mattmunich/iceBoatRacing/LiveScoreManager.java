package fr.mattmunich.iceBoatRacing;

import fr.mattmunich.iceBoatRacing.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.Racer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.formatTime;
import static fr.mattmunich.iceBoatRacing.Messages.*;

public class LiveScoreManager {
    /** Shown for a value that doesn't exist (yet), e.g. the gap to the player ahead when you are leading. */
    private static final String NO_VALUE = "-";

    /** How often the sidebar is refreshed on its own, so that time-based values keep ticking between checkpoints. */
    private static final long REFRESH_INTERVAL_TICKS = 20L;

    private final Plugin plugin;
    private BukkitTask refreshTask;

    /** The racers currently shown on the sidebar. Their lines are all refreshed together, as one racer moving changes everyone's ranking/gaps. */
    private final Map<UUID, Racer> tracked = new LinkedHashMap<>();
    /** Scoreboards replaced while a player has hidden the shared race sidebar. */
    private final Map<UUID, Scoreboard> hiddenSidebarScoreboards = new HashMap<>();

    Scoreboard scoreboard;
    Objective liveScore;
    private String entryFormat(Racer racer) { return "liveScore-" + racer.getPlayerName(); }

    public LiveScoreManager(Plugin plugin) {
        this.plugin = plugin;
        register();
    }

    private void register() {
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective tempSidebar = scoreboard.getObjective("IBR.liveScore");
        if(tempSidebar != null) tempSidebar.unregister();
        liveScore = scoreboard.registerNewObjective("IBR.liveScore", Criteria.DUMMY, getMessage("liveScore.title"));
        liveScore.setDisplaySlot(DisplaySlot.SIDEBAR);
//        liveScore.numberFormat(NumberFormat.blank());
        startRefreshTask();
    }

    private void startRefreshTask() {
        stopRefreshTask();
        if (plugin == null) return;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    private void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public LiveScoreManager reload() {
        reset();
        register();
        return this;
    }

    public void reset() {
        stopRefreshTask();
        tracked.clear();
        if (liveScore == null) return;
        try { liveScore.unregister(); } catch (Exception ignored) {}
        liveScore = null;
        scoreboard = null;
    }

    public void empty() {
        tracked.clear();
        if (liveScore == null) return;
        try { liveScore.unregister(); } catch (Exception ignored) {}
        liveScore = null;
        register();
    }

    public void addRacer(Racer racer) throws NullPointerException, IllegalArgumentException {
        if (liveScore == null) {
            throw new NullPointerException("Couldn't add racer, because the liveScore wasn't found! (this.liveScore is null)");
        }
        Score s;
        try {
            s = liveScore.getScore(entryFormat(racer));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        s.customName(formatScore(racer, new Standings(racer.getRace())));
        s.setScore(0);
        tracked.put(racer.uniqueId, racer);
    }

    public void removeRacer(Racer racer) {
        if (liveScore == null) return;
        tracked.remove(racer.uniqueId);
        Score s = liveScore.getScore(entryFormat(racer));
        s.resetScore();
    }

    public void removeRacer(Player racer) {
        if (liveScore == null) return;
        tracked.remove(racer.getUniqueId());
        try {
            Score s = liveScore.getScore(racer);
            s.resetScore();
        } catch (Exception ignored) {}
    }

    /** Toggles the race sidebar for one player without changing the shared scoreboard. */
    public boolean toggleSidebar(Player player) {
        UUID playerId = player.getUniqueId();
        Scoreboard previous = hiddenSidebarScoreboards.remove(playerId);
        if (previous != null) {
            player.setScoreboard(previous);
            return false;
        }

        hiddenSidebarScoreboards.put(playerId, player.getScoreboard());
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        return true;
    }

    /** Drops per-session sidebar state when the player disconnects. */
    public void forgetSidebar(Player player) {
        hiddenSidebarScoreboards.remove(player.getUniqueId());
    }

    /**
     * Call when a racer's progress changed. Refreshes the whole race's lines, not just this racer's,
     * because the ranking and the gaps of the other racers may have changed too.
     */
    public void updateRacer(Racer racer) {
        if (liveScore == null) return;
        tracked.putIfAbsent(racer.uniqueId, racer);
        refreshRace(racer.getRace());
    }

    private void refreshAll() {
        if (liveScore == null || tracked.isEmpty()) return;
        Set<Race> races = new LinkedHashSet<>();
        for (Racer racer : tracked.values()) {
            if (racer.getRace() != null) races.add(racer.getRace());
        }
        for (Race race : races) refreshRace(race);
    }

    private void refreshRace(Race race) {
        if (liveScore == null || race == null) return;
        Standings standings = new Standings(race);
        for (Racer racer : tracked.values()) {
            if (racer.getRace() != race) continue;
            Score s = liveScore.getScore(entryFormat(racer));
            s.setScore(racer.getScore());
            s.customName(formatScore(racer, standings));
        }
    }

    /**
     * Placeholders available in {@code liveScore.format} (lang files):
     * <ul>
     *   <li>{@code player}, {@code completedLapCount}, {@code lapCount}, {@code completedPitStops}, {@code requiredPitStops}</li>
     *   <li>{@code ranking}: current position, 1 = leading (racers who already finished come first, in finishing order)</li>
     *   <li>{@code raceTime}: time since the racer first crossed the start line</li>
     *   <li>{@code lastLapTime} / {@code bestLapTime}: last / best lap of this racer</li>
     *   <li>{@code gapToLeader}: time behind the race leader</li>
     *   <li>{@code gapToNext}: time behind the racer in front (same meaning as in the finish messages)</li>
     *   <li>{@code gapToPrevious}: time ahead of the racer right behind</li>
     *   <li>{@code remainingPitStops}: required pit stops still to do (never negative)</li>
     *   <li>{@code currentLap}: the lap the racer is driving, 1-based (0 before crossing the start line)</li>
     *   <li>{@code lapProgress}: how far through the current lap the racer is, in %</li>
     *   <li>{@code positionChange}: places gained (+) or lost (-) compared to the starting grid</li>
     *   <li>{@code playersRemaining}: racers still on track (not finished, not gone)</li>
     *   <li>{@code isLeader}: {@code true} / {@code false}</li>
     * </ul>
     * Times and gaps have no value ({@value NO_VALUE}) until they exist, e.g. {@code lastLapTime} before the first lap is done.
     */
    private Component formatScore(Racer racer, Standings standings) {
        Race race = racer.getRace();
        long now = System.currentTimeMillis();

        int index = standings.indexOf(racer);
        boolean known = index >= 0;
        int ranking = index + 1;

        // Race time: runs from the racer's own first start-line crossing, like the final result does
        long raceTime = 0;
        if (racer.startTime > 0) raceTime = (racer.endTime > 0 ? racer.endTime : now) - racer.startTime;

        List<Long> lapTimes = racer.getLapTimes();
        String lastLapTime = lapTimes.isEmpty() ? NO_VALUE : formatTime(lapTimes.getLast());
        String bestLapTime = lapTimes.isEmpty() ? NO_VALUE : formatTime(racer.bestLapTime());

        String gapToLeader = known && index > 0 ? formatGap(racer, standings.get(0)) : NO_VALUE;
        String gapToNext = known && index > 0 ? formatGap(racer, standings.get(index - 1)) : NO_VALUE;
        String gapToPrevious = known && index < standings.size() - 1 ? formatGap(standings.get(index + 1), racer) : NO_VALUE;

        // gridPosition is 0 until the race has started
        int positionChange = known && racer.gridPosition > 0 ? racer.gridPosition - ranking : 0;

        return getMessage("liveScore.format", formatArguments(
                "ranking", known ? String.valueOf(ranking) : NO_VALUE,
                "player", racer.disqualified ? racer.getPlayerName() + " §cDSQ" : racer.getPlayerName(),
                "raceTime", formatTime(raceTime),
                "lastLapTime", lastLapTime,
                "bestLapTime", bestLapTime,
                "gapToLeader", gapToLeader,
                "gapToNext", gapToNext,
                "gapToPrevious", gapToPrevious,
                "completedLapCount", racer.getCompletedLapCount(),
                "lapCount", race.getLapCount(),
                "completedPitStops", racer.getCompletedPitStops(),
                "requiredPitStops", race.getRequiredPitStops(),
                "remainingPitStops", Math.max(0, race.getRequiredPitStops() - racer.getCompletedPitStops()),
                "currentLap", Math.max(0, racer.lapCount),
                "lapProgress", standings.lapProgress(racer) + "%",
                "positionChange", positionChange > 0 ? "+" + positionChange : String.valueOf(positionChange),
                "playersRemaining", standings.playersRemaining,
                "isLeader", known && index == 0
        ));
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
