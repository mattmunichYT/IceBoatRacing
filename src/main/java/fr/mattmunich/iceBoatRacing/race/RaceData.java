package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.cars.Car;
import org.bukkit.entity.Player;

import java.util.*;

public class RaceData {
    public Player player;
    public int ranking = -1;
    public int checkpointIndex = 0;
    public int lapCount = -1;
    public Car car;
    public long startTime = 0;
    public long lapTime = 0;
    public long endTime = 0;
    public final List<Long> lapTimes = new ArrayList<>();
    public final Map<Integer,Long> sectorTimes = new HashMap<>();
    public Race race;

    public RaceData(Player player) {
        this.player = player;
    }

    public long bestLapTime() {
        return Collections.min(lapTimes);
    }

    public long worstLapTime() {
        return Collections.max(lapTimes);
    }

    public long meanLapTime() {
        return (long) lapTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
    }

    public long getRaceTime() {
        return endTime - startTime;
    }

    public List<Long> getLapTimes() {
        return lapTimes;
    }

    public Map<Integer,Long> getSectorTimes() {
        return sectorTimes;
    }
}