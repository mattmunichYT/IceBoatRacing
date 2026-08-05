package fr.mattmunich.iceBoatRacing.checkpoint.autotrace;

import fr.mattmunich.iceBoatRacing.race.Race;

public class PendingAutoTraceConfig {
    public final Race race;
    public double spacing = 10.0;
    public double width = 10.0;
    public double height = 5.0;
    public boolean loop = true;
    public boolean isCreatingRace = false;

    public PendingAutoTraceConfig(Race race) {
        this.race = race;
    }
}
