package fr.mattmunich.iceBoatRacing.training;

import fr.mattmunich.iceBoatRacing.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceListener;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import static fr.mattmunich.iceBoatRacing.Main.warn;

/**
 * Listens to player and vehicle events to handle training checkpoint detection,
 * collision isolation, interaction prevention, and player connection lifecycle.
 */
public class TrainingListener implements Listener {

    private final TrainingManager trainingManager;

    public TrainingListener(TrainingManager trainingManager) {
        this.trainingManager = trainingManager;
    }

    // -------------------------------------------------------------------------
    // Checkpoint & Movement Detection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        TrainingSession session = trainingManager.getSession(player.getUniqueId());
        if (session == null || !session.isStarted()) return;

        if (!player.isInsideVehicle()) return;
        if (player.getVehicle() != session.getBoat()) return;

        Location from = session.getFrom() == null ? event.getFrom() : session.getFrom();
        Location to = event.getTo();
        session.setFrom(to.clone());

        Race race = session.getRace();
        if (race == null || race.getCheckpoints().isEmpty()) return;

        while (true) {
            Checkpoint nextCheckpoint = RaceListener.checkpointAfter(race, session.getCheckpointIndex());
            if (nextCheckpoint == null) break;

            if (!nextCheckpoint.crosses(from, to)) {
                // Allow skipping 1 checkpoint if placement is tight
                Checkpoint afterNext = RaceListener.checkpointAfter(race, nextCheckpoint.getId());
                if (afterNext == null || afterNext == nextCheckpoint || !afterNext.crosses(from, to)) {
                    break;
                }

                warn("Allowed " + player.getName() + " to skip checkpoint " + nextCheckpoint.getId() + " in training on " + race.getName());
                long now = System.currentTimeMillis();

                if (handleCrossing(session, nextCheckpoint, now, race)) break;
                if (handleCrossing(session, afterNext, now, race)) break;

                session.setCheckpointIndex(afterNext.getId());
                break;
            }

            long now = System.currentTimeMillis();
            if (handleCrossing(session, nextCheckpoint, now, race)) break;

            session.setCheckpointIndex(nextCheckpoint.getId());
        }
    }

    private boolean handleCrossing(TrainingSession session, Checkpoint checkpoint, long now, Race race) {
        switch (checkpoint.getType()) {
            case START_FINISH -> {
                session.crossStartFinish(now);
                return true;
            }
            case SECTOR -> session.crossSector(checkpoint, now);
            default -> {}
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Vehicle Controls & Countdown Freeze
    // -------------------------------------------------------------------------

    @EventHandler
    public void onMoveCar(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        if (!trainingManager.isTrainingBoat(boat)) return;

        if (boat.getPassengers().isEmpty() || !(boat.getPassengers().getFirst() instanceof Player p)) {
            return;
        }

        TrainingSession session = trainingManager.getSession(p.getUniqueId());
        if (session == null || !session.isPreparing()) return;

        boat.teleport(session.getStartLocation());
        boat.setVelocity(new Vector(0, 0, 0));
    }

    @EventHandler
    public void onExitVehicle(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player p)) return;
        TrainingSession session = trainingManager.getSession(p.getUniqueId());
        if (session == null) return;

        // Prevent accidental vehicle exit during active training
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Collision, Damage & Interaction Isolation
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings({"removal"})
    public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        Entity vehicle = event.getVehicle();
        Entity entity = event.getEntity();

        boolean isVehicleTraining = trainingManager.isTrainingBoat(vehicle) || trainingManager.isGhostEntity(vehicle);
        boolean isEntityTraining = trainingManager.isTrainingBoat(entity) || trainingManager.isGhostEntity(entity)
                || (entity instanceof Player p && trainingManager.isTraining(p.getUniqueId()));

        if (isVehicleTraining || isEntityTraining) {
            event.setCancelled(true);
            event.setCollisionCancelled(true);
            event.setPickupCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (trainingManager.isGhostEntity(entity)) {
            event.setCancelled(true);
            return;
        }
        if (trainingManager.isTrainingBoat(entity)) {
            // Only allow the owner of the session to interact with their own boat
            TrainingSession session = trainingManager.getSession(event.getPlayer().getUniqueId());
            if (session == null || session.getBoat() != entity) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractAt(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (trainingManager.isGhostEntity(entity)) {
            event.setCancelled(true);
            return;
        }
        if (trainingManager.isTrainingBoat(entity)) {
            TrainingSession session = trainingManager.getSession(event.getPlayer().getUniqueId());
            if (session == null || session.getBoat() != entity) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (trainingManager.isGhostEntity(entity) || trainingManager.isTrainingBoat(entity)) {
            event.setCancelled(true);
            return;
        }
        if (entity instanceof Player p && trainingManager.isTraining(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (trainingManager.isGhostEntity(event.getVehicle()) || trainingManager.isTrainingBoat(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (trainingManager.isGhostEntity(event.getVehicle()) || trainingManager.isTrainingBoat(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Player Join / Quit Lifecycle
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        trainingManager.leaveTraining(event.getPlayer(), false);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        trainingManager.onPlayerJoin(event.getPlayer());
    }
}
