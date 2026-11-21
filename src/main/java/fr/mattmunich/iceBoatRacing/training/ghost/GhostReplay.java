package fr.mattmunich.iceBoatRacing.training.ghost;

import fr.mattmunich.iceBoatRacing.Main;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Handles the in-world visual representation and playback of a ghost boat for a single training session.
 */
public class GhostReplay {

    private final Main main;
    private final Player player;
    private final World world;
    private GhostTrajectory trajectory;
    private Boat ghostBoat;
    private boolean finished = false;

    public GhostReplay(Main main, Player player, World world, GhostTrajectory trajectory) {
        this.main = main;
        this.player = player;
        this.world = world;
        this.trajectory = trajectory;
    }

    public void setTrajectory(GhostTrajectory trajectory) {
        this.trajectory = trajectory;
        this.finished = false;
        cleanup();
    }

    public GhostTrajectory getTrajectory() {
        return trajectory;
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Updates the ghost's position along its trajectory according to the given elapsed time in the lap.
     *
     * @param elapsedMs Elapsed milliseconds from the start of the current lap.
     */
    public void update(long elapsedMs) {
        if (finished || trajectory == null || trajectory.isEmpty()) {
            return;
        }

        // If the ghost has finished its lap, clean it up until next lap
        if (elapsedMs > trajectory.getLapTimeMs()) {
            finish();
            return;
        }

        GhostPoint target = trajectory.interpolate(elapsedMs);
        if (target == null) return;

        Location loc = new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch());

        if (ghostBoat == null || !ghostBoat.isValid()) {
            spawn(loc);
        } else {
            ghostBoat.teleport(loc);
            // Spawn subtle ghost trail particles around the ghost boat
            if (elapsedMs % 100 < 75) {
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.3, 0), 1, 0.1, 0.05, 0.1, 0.01);
            }
        }
    }

    private void spawn(Location loc) {
        ghostBoat = (Boat) world.spawnEntity(loc, EntityType.OAK_BOAT);
        ghostBoat.setGravity(false);
        ghostBoat.setInvulnerable(true);
        ghostBoat.setGlowing(true);

        ghostBoat.customName(MiniMessage.miniMessage().deserialize("<gradient:#8DABF3:#10CA9A><b>GHOST</b></gradient> <gray>(PB)</gray>"));
        ghostBoat.setCustomNameVisible(true);

        // Hide ghost from all other players so it's strictly visible only to the session owner
        hideFromOthers();
    }

    /**
     * Ensures all online players except the owner cannot see the ghost entity.
     */
    public void hideFromOthers() {
        if (ghostBoat == null || !ghostBoat.isValid()) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                online.hideEntity(main, ghostBoat);
            }
        }
    }

    /**
     * Hides the ghost entity from a specific player (e.g. upon player join).
     */
    public void hideFrom(Player other) {
        if (ghostBoat != null && ghostBoat.isValid() && !other.getUniqueId().equals(player.getUniqueId())) {
            other.hideEntity(main, ghostBoat);
        }
    }

    /**
     * Marks the ghost replay as finished and removes the ghost entity from the world.
     */
    public void finish() {
        finished = true;
        cleanup();
    }

    /**
     * Resets the ghost replay state ready for a new lap.
     */
    public void reset() {
        finished = false;
        cleanup();
    }

    /**
     * Completely removes the ghost entity from the world.
     */
    public void cleanup() {
        if (ghostBoat != null) {
            try {
                ghostBoat.remove();
            } catch (Exception ignored) {}
            ghostBoat = null;
        }
    }

    public Boat getGhostBoat() {
        return ghostBoat;
    }
}
