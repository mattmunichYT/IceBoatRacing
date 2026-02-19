package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.cars.Car;
import org.bukkit.entity.Player;

public class RaceData {
    public Player player;
    public int checkpointIndex = -1;
    public int lapCount = -1;
    public Car car;
    public long startTime;
    public long lapTime;

    public RaceData(Player player) {
        this.player = player;
    }
}