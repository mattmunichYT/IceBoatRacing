package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.checkpoint.Checkpoint;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.formatTime;
import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public class Race {

    private RaceManager raceManager;

    public void setRaceManager(RaceManager raceManager) {
        this.raceManager = raceManager;
    }
    YamlConfiguration config = null;
    final String name;
    final World world;
    int lapCount = 10;
    final List<Checkpoint> checkpoints = new ArrayList<>();
    final List<Car> cars = new ArrayList<>();
    public final Map<UUID, RaceData> racers = new HashMap<>();

    /**
     * List of the racers who hae finished the race.
     * rankings.get(0) is first, rankings.get(1) is second and so on
     */
    public Map<Integer, RaceData> rankings = new HashMap<>();

    public long currentBestLapTime = Long.MAX_VALUE;

    /**
     * List of the racers that are actively racing
     * (-> those who have finished the race are not included)
     */
    public List<RaceData> racing = new ArrayList<>();

    boolean startingRace = false;
    boolean preparingRace = false;
    boolean hasRaceStarted = false;

    public Race(
            String name,
            World world
            ) {
        this.name = name;
        this.world = world;
    }

    public void setConfig(YamlConfiguration config) {
        this.config = config;
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public void saveConfig() throws IOException {
        raceManager.saveRaceConfig(this, config);
    }

    public void start() {
        raceManager.startRace(this);
    }

    /**
     * Ends the race
     * @param sendRanking Whether the ranking message should be sent
     * @throws Exception Only when {@code sendRanking} is true: rethrows {@link Race#sendRanking()}
     */
    public void end(boolean sendRanking) throws Exception {
        if (sendRanking) sendRanking();
        raceManager.endRace(this);
    }

    public void togglePrepare(CommandSender sender) {
        raceManager.togglePrepareRace(sender, this);
    }

    /**
     * @param time The time of the player who finished a lap (to check if it is the best lap time yet)
     * @return -1 if the inputted time is longer than the current best lap time. Otherwise, it returns the lastBestLapTime.
     */
    public long isBestLapTimeYet(long time) {
        if (currentBestLapTime > time) {
            long lastBestLapTime = currentBestLapTime;
            currentBestLapTime = time;
            return lastBestLapTime;
        }
        else return -1;
    }

    /**
     * Sends the ranking message for the race
     * @throws Exception Throws {@code NO_RACERS} when the race had no racers and {@code NO_RAKING} when there was no ranking for the race
     */
    public void sendRanking() throws Exception {
        if (racers.isEmpty()) throw new Exception("NO_RACERS");
        if (rankings.isEmpty()) throw new Exception("NO_RANKING");

        Bukkit.broadcast(getMessage("race.onEnd.top"));

        long bestLapTime = Long.MAX_VALUE;
        long worstLapTime = Long.MIN_VALUE;

        Player bestLapPlayer = null;
        Player worstLapPlayer = null;

        Map<Integer, Long> bestSectorTimes = new HashMap<>();
        Map<Integer, Player> bestSectorPlayers = new HashMap<>();

        for (RaceData data : racers.values()) {
            if(rankings.get(0).equals(data)) {
                Bukkit.broadcast(getMessage("race.onEnd.winnerFormat", formatArguments(
                        "ranking", data.ranking + "",
                        "player", data.player == null ? "OFFLINE" : data.player.getName(),
                        "raceTime", formatTime(data.getRaceTime())
                )));
            } else {
                Bukkit.broadcast(getMessage("race.onEnd.playerFormat", formatArguments(
                        "ranking", data.ranking + "",
                        "player", data.player == null ? "OFFLINE" : data.player.getName(),
                        "raceTime", formatTime(data.getRaceTime()),
                        "gapToNext", formatTime(data.gapToNextTime)
                )));
            }


            if (!data.getLapTimes().isEmpty()) {
                for (long lapTime : data.getLapTimes()) {
                    if (lapTime < bestLapTime) {
                        bestLapTime = lapTime;
                        bestLapPlayer = data.player;
                    }
                    if (lapTime > worstLapTime) {
                        worstLapTime = lapTime;
                        worstLapPlayer = data.player;
                    }
                }
            }


            Map<Integer, List<Long>> sectorTimes = data.getSectorsTimes();
            if (sectorTimes != null) {
                for (int sectorID : sectorTimes.keySet()) {
                    bestSectorTimes.putIfAbsent(sectorID, Long.MAX_VALUE);

                    Long playerSectorTime = Collections.min(sectorTimes.get(sectorID));

                    if (playerSectorTime < bestSectorTimes.get(sectorID)) {
                        bestSectorTimes.put(sectorID, playerSectorTime);
                        bestSectorPlayers.put(sectorID, data.player);
                    }
                }
            }
        }

        long winnerTime = rankings.get(0).getRaceTime();
        long lastTime = rankings.get(rankings.size()).getRaceTime();
        long winnerGap = lastTime - winnerTime;

        Bukkit.broadcast(getMessage("race.onEnd.highlights", formatArguments(
                "bestLapPlayer", bestLapPlayer == null ? "§c§oOffline" : bestLapPlayer.getName(),
                "bestLapTime", bestLapTime == Long.MAX_VALUE ? "N/A" : formatTime(bestLapTime),
                "worstLapPlayer", worstLapPlayer == null ? "§c§oOffline" : worstLapPlayer.getName(),
                "worstLapTime", worstLapTime == Long.MIN_VALUE ? "N/A" : formatTime(worstLapTime), // Correction ici (MIN_VALUE)
                "winnerGap", formatTime(winnerGap)
        )));

        // 4. Affichage des Secteurs
        if (!bestSectorTimes.isEmpty()) {
            Bukkit.broadcast(getMessage("race.onEnd.bestSectorTimesTop"));
            for (int sectorID : bestSectorTimes.keySet()) {
                Player sectorRecordHolder = bestSectorPlayers.get(sectorID);
                String playerName = (sectorRecordHolder == null) ? "§c§oUnknown" : sectorRecordHolder.getName();
                long recordTime = bestSectorTimes.get(sectorID);

                Bukkit.broadcast(getMessage("race.onEnd.sectorFormat", formatArguments(
                        "sectorID", String.valueOf(sectorID),
                        "player", playerName,
                        "time", recordTime == Long.MAX_VALUE ? "N/A" : formatTime(recordTime)
                )));
            }
        }

        Bukkit.broadcast(getMessage("race.onEnd.bottom"));
    }


    public String getName() {
        return name;
    }
    public World getWorld() {
        return world;
    }
    public int getLapCount() {
        return lapCount;
    }
    public void setLapCount(int lapCount) {
        this.lapCount = Math.max(lapCount, 1);
    }

    //Checkpoints
    public List<Checkpoint> getCheckpoints() {
        return checkpoints;
    }

    public @Nullable Checkpoint getCheckpoint(int ID) {
        final Checkpoint[] result = new Checkpoint[1];
        result[0] = null;
        getCheckpoints().forEach(checkpoint -> {if(checkpoint.getId()==ID) result[0]=checkpoint;});
        return result[0];
    }

    public void addCheckpoint(Checkpoint checkpoint) {
        checkpoints.add(checkpoint);
    }

    public void clearCheckpoints() {
        checkpoints.clear();
    }

    public boolean removeCheckpoint(Checkpoint checkpoint) {
        return checkpoints.remove(checkpoint);
    }

    //Cars
    public List<Car> getCars() {
        return cars;
    }

    public void addCar(Car car) {
        cars.add(car);
    }

    public void clearCars() {
        cars.clear();
    }

    public boolean removeCar(Car car) {
        return cars.remove(car);
    }

    public boolean isNotStarting() {
        return !startingRace;
    }

    public boolean isPreparing() {
        return preparingRace;
    }

    public boolean hasStarted() {
        return hasRaceStarted;
    }
}
