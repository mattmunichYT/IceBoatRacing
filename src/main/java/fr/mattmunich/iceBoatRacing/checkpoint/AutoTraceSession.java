package fr.mattmunich.iceBoatRacing.checkpoint;

import fr.mattmunich.iceBoatRacing.race.Race;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class AutoTraceSession {

    public final Player player;
    public final Race race;

    /** Raw sampled points from the recording pass, in order. */
    public final List<Location> rawPoints = new ArrayList<>();

    /** Running total of distance traveled during recording, used for the loop auto-stop check. */
    public double traveledDistance = 0.0;

    /** Non-null while actively sampling; null once stopped. */
    public BukkitTask samplingTask;

    /** Generated but not-yet-saved checkpoints, shown via preview(). Mutable — sectors get inserted into this. */
    public List<Checkpoint> preview = new ArrayList<>();

    /** The recentered centerline behind the current preview, kept so sector markers can be placed
     *  in the correct sequence position and given a sensible tangent. */
    public List<Location> centerline = new ArrayList<>();
    public double[] centerlineArcLengths = new double[0];

    public double spacing = 10.0;
    public double halfWidth = 3.0;
    public double halfHeight = 2.5;

    /** Whether the track is a closed loop (tangent wraps at the ends). */
    public boolean loop = true;

    public AutoTraceSession(Player player, Race race) {
        this.player = player;
        this.race = race;
    }
}