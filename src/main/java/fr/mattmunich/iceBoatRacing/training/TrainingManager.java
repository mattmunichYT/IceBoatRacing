package fr.mattmunich.iceBoatRacing.training;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import fr.mattmunich.iceBoatRacing.stats.StatsManager;
import fr.mattmunich.iceBoatRacing.training.ghost.GhostManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static fr.mattmunich.iceBoatRacing.Main.*;
import static fr.mattmunich.iceBoatRacing.Messages.*;

/**
 * Dedicated training session management component.
 *
 * <p>Handles:
 * <ul>
 *   <li>Starting, restarting, leaving, and toggling training sessions</li>
 *   <li>Player and entity visibility isolation between training sessions, official races, and the rest of the server</li>
 *   <li>Collision suppression and interaction prevention for training and ghost entities</li>
 *   <li>Persistence and lifecycle management</li>
 * </ul>
 */
public class TrainingManager {

    private final Main main;
    private final RaceManager raceManager;
    private final StatsManager statsManager;
    private final GhostManager ghostManager;

    private final Map<UUID, TrainingSession> activeSessions = new ConcurrentHashMap<>();

    public TrainingManager(Main main, RaceManager raceManager, StatsManager statsManager) {
        this.main = main;
        this.raceManager = raceManager;
        this.statsManager = statsManager;
        this.ghostManager = new GhostManager(main);
    }

    public GhostManager getGhostManager() {
        return ghostManager;
    }

    public Map<UUID, TrainingSession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    @Nullable
    public TrainingSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    public boolean isTraining(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    /**
     * Checks if the entity is an active training boat.
     */
    public boolean isTrainingBoat(Entity entity) {
        if (!(entity instanceof Boat)) return false;
        for (TrainingSession s : activeSessions.values()) {
            if (s.getBoat() != null && s.getBoat().getUniqueId().equals(entity.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the entity is an active ghost boat.
     */
    public boolean isGhostEntity(Entity entity) {
        if (entity == null) return false;
        for (TrainingSession s : activeSessions.values()) {
            Boat ghostBoat = s.getGhostBoat();
            if (ghostBoat != null && ghostBoat.getUniqueId().equals(entity.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Session Controls
    // -------------------------------------------------------------------------

    /**
     * Starts an individual training session for the given player on the given race.
     *
     * @param player The player
     * @param race   The race to train on
     * @return True if training started successfully, false otherwise
     */
    public boolean startTraining(Player player, Race race) {
        if (isTraining(player.getUniqueId())) {
            player.sendMessage(getMessage("race.train.alreadyTraining"));
            return false;
        }

        if (raceManager.isRacing(player)) {
            player.sendMessage(getMessage("race.train.inOfficialRace"));
            return false;
        }

        if (race.getWorld() == null) {
            player.sendMessage(getMessage("race.notFound"));
            return false;
        }

        // Determine starting location and boat material from race cars
        List<Car> cars = race.getCars();
        if (cars.isEmpty()) {
            player.sendMessage(getMessage("race.train.noCars", formatArguments("race", race.getName())));
            return false;
        }

        Car assignedCar = null;
        for (Car c : cars) {
            if (c.getOwner().equals(player.getUniqueId())) {
                assignedCar = c;
                break;
            }
        }
        if (assignedCar == null) {
            assignedCar = cars.getFirst();
        }

        Location startLoc = assignedCar.getStartingLocation().clone();
        float startRotation = race.getStartRotation();
        Material boatMat = assignedCar.getBoatMaterial();

        // Prepare player environment
        player.setGameMode(RACING_GAMEMODE);
        player.setCollidable(false);

        // Spawn isolated training boat
        Boat boat = (Boat) startLoc.getWorld().spawnEntity(startLoc, boatEntityFromMaterial(boatMat));
        boat.setInvulnerable(true);
        boat.customName(MiniMessage.miniMessage().deserialize("<gradient:#8DABF3:#10CA9A><b>Training Boat</b></gradient>"));
        boat.setCustomNameVisible(false);

        Location boatLoc = boat.getLocation();
        boatLoc.setRotation(startRotation, 0F);
        boat.teleport(boatLoc);
        player.teleport(boatLoc);
        boat.addPassenger(player);

        // Scoreboard & team isolation
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Team team = scoreboard.registerNewTeam("ibr_training");
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(false);
        team.addPlayer(player);
        player.setScoreboard(scoreboard);

        TrainingSession session = new TrainingSession(
                main, player, race, boat, startLoc, startRotation,
                statsManager, ghostManager, scoreboard
        );
        activeSessions.put(player.getUniqueId(), session);

        // Apply entity and player visibility isolation
        applyIsolation(session);

        player.sendMessage(getMessage("race.train.start", formatArguments("race", race.getName())));
        session.startCountdown();
        return true;
    }

    /**
     * Ends the training session for the given player.
     *
     * @param player   The player
     * @param announce Whether to send the session leave message
     */
    public void leaveTraining(Player player, boolean announce) {
        TrainingSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            if (announce) player.sendMessage(getMessage("race.train.notTraining"));
            return;
        }

        Race race = session.getRace();
        session.cleanup();

        // Restore isolation
        removeIsolation(session);

        // Restore scoreboard and collision
        player.setCollidable(true);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        // Teleport to safety
        if (race.getWorld() != null) {
            player.teleport(race.getWorld().getSpawnLocation());
        }
        player.setGameMode(GameMode.SURVIVAL);

        // Persist dirty statistics
        statsManager.savePlayerAsync(player.getUniqueId());

        if (announce) {
            player.sendMessage(getMessage("race.train.leave", formatArguments("race", race.getName())));
        }
    }

    /**
     * Restarts the training session for the given player.
     */
    public void restartTraining(Player player) {
        TrainingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(getMessage("race.train.notTraining"));
            return;
        }
        session.restart();
    }

    /**
     * Toggles ghost replay for the given player's training session.
     */
    public void toggleGhost(Player player) {
        TrainingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(getMessage("race.train.notTraining"));
            return;
        }

        boolean enabled = session.toggleGhost();
        if (enabled) {
            player.sendMessage(getMessage("race.train.ghostEnabled"));
        } else {
            player.sendMessage(getMessage("race.train.ghostDisabled"));
        }
    }

    // -------------------------------------------------------------------------
    // Visibility & Isolation
    // -------------------------------------------------------------------------

    /**
     * Fully isolates a training session:
     * <ul>
     *   <li>Hides all other players and their vehicles/ghosts from this player</li>
     *   <li>Hides this player, their training boat, and their ghost from all other players</li>
     * </ul>
     */
    public void applyIsolation(TrainingSession session) {
        Player trainee = session.getPlayer();
        Boat traineeBoat = session.getBoat();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(trainee.getUniqueId())) continue;

            // Hide other player from trainee, and trainee from other player
            trainee.hidePlayer(main, other);
            other.hidePlayer(main, trainee);

            // Hide trainee's boat from other player
            if (traineeBoat != null && traineeBoat.isValid()) {
                other.hideEntity(main, traineeBoat);
            }

            // If the other player is also training, hide their boat and ghost from trainee
            TrainingSession otherSession = activeSessions.get(other.getUniqueId());
            if (otherSession != null) {
                if (otherSession.getBoat() != null && otherSession.getBoat().isValid()) {
                    trainee.hideEntity(main, otherSession.getBoat());
                }
                if (otherSession.getGhostBoat() != null && otherSession.getGhostBoat().isValid()) {
                    trainee.hideEntity(main, otherSession.getGhostBoat());
                }
            }
        }

        // Hide ghost entity from others if already spawned
        session.restartGhostReplay();
    }

    /**
     * Restores visibility when a player leaves training.
     */
    public void removeIsolation(TrainingSession session) {
        Player trainee = session.getPlayer();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(trainee.getUniqueId())) continue;

            // Show other player to trainee, and trainee to other player
            trainee.showPlayer(main, other);
            other.showPlayer(main, trainee);
        }
    }

    /**
     * Handles when a new player joins the server to maintain isolation for all active training sessions.
     */
    public void onPlayerJoin(Player joiningPlayer) {
        for (TrainingSession session : activeSessions.values()) {
            if (session.getPlayer().getUniqueId().equals(joiningPlayer.getUniqueId())) continue;

            // Hide training player and their entities from the joining player
            joiningPlayer.hidePlayer(main, session.getPlayer());
            session.getPlayer().hidePlayer(main, joiningPlayer);

            if (session.getBoat() != null && session.getBoat().isValid()) {
                joiningPlayer.hideEntity(main, session.getBoat());
            }
            if (session.getGhostBoat() != null && session.getGhostBoat().isValid()) {
                joiningPlayer.hideEntity(main, session.getGhostBoat());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Server Lifecycle Cleanup
    // -------------------------------------------------------------------------

    /**
     * Cleans up all active training sessions (e.g. on server disable / reload).
     */
    public void cleanupAll() {
        for (UUID uuid : new ArrayList<>(activeSessions.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                leaveTraining(p, false);
            } else {
                TrainingSession s = activeSessions.remove(uuid);
                if (s != null) s.cleanup();
            }
        }
        ghostManager.clearCache();
    }

    private EntityType boatEntityFromMaterial(Material material) {
        return switch (material) {
            case SPRUCE_BOAT -> EntityType.SPRUCE_BOAT;
            case BIRCH_BOAT -> EntityType.BIRCH_BOAT;
            case JUNGLE_BOAT -> EntityType.JUNGLE_BOAT;
            case ACACIA_BOAT -> EntityType.ACACIA_BOAT;
            case DARK_OAK_BOAT -> EntityType.DARK_OAK_BOAT;
            case MANGROVE_BOAT -> EntityType.MANGROVE_BOAT;
            case CHERRY_BOAT -> EntityType.CHERRY_BOAT;
            case BAMBOO_RAFT -> EntityType.BAMBOO_RAFT;
            case PALE_OAK_BOAT -> EntityType.PALE_OAK_BOAT;
            case BAMBOO_CHEST_RAFT -> EntityType.BAMBOO_CHEST_RAFT;
            case ACACIA_CHEST_BOAT -> EntityType.ACACIA_CHEST_BOAT;
            case BIRCH_CHEST_BOAT -> EntityType.BIRCH_CHEST_BOAT;
            case CHERRY_CHEST_BOAT -> EntityType.CHERRY_CHEST_BOAT;
            case DARK_OAK_CHEST_BOAT -> EntityType.DARK_OAK_CHEST_BOAT;
            case JUNGLE_CHEST_BOAT -> EntityType.JUNGLE_CHEST_BOAT;
            case MANGROVE_CHEST_BOAT -> EntityType.MANGROVE_CHEST_BOAT;
            case OAK_CHEST_BOAT -> EntityType.OAK_CHEST_BOAT;
            case PALE_OAK_CHEST_BOAT -> EntityType.PALE_OAK_CHEST_BOAT;
            case SPRUCE_CHEST_BOAT -> EntityType.SPRUCE_CHEST_BOAT;
            default -> EntityType.OAK_BOAT;
        };
    }
}
