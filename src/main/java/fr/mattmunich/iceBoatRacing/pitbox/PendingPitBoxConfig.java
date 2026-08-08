package fr.mattmunich.iceBoatRacing.pitbox;

import fr.mattmunich.iceBoatRacing.race.Race;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class PendingPitBoxConfig {

    public final Race race;
    public String name;
    public Location pos1;
    public Location pos2;
    public int duration = 5;
    public List<String> allowed = new ArrayList<>();

    public PendingPitBoxConfig(Race race) {
        this.race = race;
    }
}