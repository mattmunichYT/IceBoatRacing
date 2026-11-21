package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.LiveScoreManager;
import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.Result;
import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.checkpoint.CheckpointManager;
import fr.mattmunich.iceBoatRacing.pitbox.PitBoxManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.*;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class RaceManager {

    private final Main main;
    private final CarManager carManager;
    private final CheckpointManager checkpointManager;
    public final PitBoxManager pitBoxManager;
    public final LiveScoreManager liveScoreManager;
    public final List<Race> races = new ArrayList<>();
    public final List<Race> activeRaces = new ArrayList<>();

    /** Running countdown task per race, so ending a race before "GO" can cancel it. */
    private final Map<Race, BukkitTask> countdownTasks = new HashMap<>();

    /**
     * A map that contains the races that failed to load on startup. <br/>
     * Format: {@code Map<worldName, raceName>}
     */
    public final Map<String, String> unloadedRaces = new HashMap<>();

    public RaceManager(Main main, CarManager carManager, CheckpointManager checkpointManager, PitBoxManager pitBoxManager, LiveScoreManager liveScoreManager) {
        this.main = main;
        this.carManager = carManager;
        this.checkpointManager = checkpointManager;
        this.pitBoxManager = pitBoxManager;
        this.liveScoreManager = liveScoreManager;
    }

    /**
     * Single source of truth for a race's config file, so every read/write/delete hits the same file
     * (spaces in the race name become underscores).
     */
    public File getRaceFile(String raceName) {
        return new File(new File(main.getDataFolder(), "races"), raceName.replace(" ", "_") + ".yml");
    }

    /**
     * Applies lap count, pit stops, start rotation and lights from {@code config} onto {@code race}.
     */
    private void applyRaceSettings(Race race, YamlConfiguration config) {
        int lapCount = config.getInt("lapCount");
        if (lapCount <= 0) {
            lapCount = 10;
            warn(race.getName() + "'s lap count wasn't found, therefore was set to the default value : 10");
        }
        race.setLapCount(lapCount);
        race.setRequiredPitStops(config.getInt("requiredPitStops", 0));
        race.setStartRotation((float) config.getDouble("startRotation", 0));

        if (!config.getBoolean("lights.enabled", false)
                || !config.isConfigurationSection("lights.from")
                || !config.isConfigurationSection("lights.to")) {
            race.setLights(new Race.Lights(false, null, null));
            return;
        }
        Location from = new Location(race.getWorld(),
                config.getInt("lights.from.x"), config.getInt("lights.from.y"), config.getInt("lights.from.z"));
        Location to = new Location(race.getWorld(),
                config.getInt("lights.to.x"), config.getInt("lights.to.y"), config.getInt("lights.to.z"));
        race.setLights(new Race.Lights(true, from, to));
    }

    /**
     * @param raceName The name of the race
     * @return The {@link Race} with the matching name
     */
    public Race getRace(String raceName) {
        return races.stream().filter(
                r -> r.getName().equalsIgnoreCase(raceName)
        ).findFirst().orElse(null);
    }

    /**
     * Saves all the races
     */
    public void saveAllRaces() {
        if (races.isEmpty()) {
            warn("No races found, therefore none were saved.");
        } else {
            for (Race race : new ArrayList<>(races)) {
                try {
                    race.saveConfig();
                } catch (IOException e) {
                    err("Could not save race " + race.getName(), e);
                }
            }
        }
    }

    /**
     * @return A list of all the loaded races
     */
    public List<Race> getRaces() {
        return races;
    }

    /**
     * Loads and initializes all the races from file
     */
    public void loadAllRaces() {
        log("Loading all races...");
        int loadedRaces = 0;

        if(!races.isEmpty()) {
            races.forEach(race -> {try { race.end(false); } catch (Exception ignored) {}});
            races.clear();
        }
        File racesFolder = new File(main.getDataFolder(), "races");
        if (!racesFolder.exists()) return;

        for (File file : Objects.requireNonNull(racesFolder.listFiles())) {
            if (!file.getName().endsWith(".yml")) {
                warn("File " + file.getName() + " wasn't loaded as a race, as it is not a .yml");
                continue;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String name = config.getString("name");

            if(name == null) {
                warn("Ignored " + file.getName() + "because it didn't contain any name for the race.");
                continue;
            }

            log("Loading race " + name);
            String worldName = Objects.requireNonNull(config.getString("world"));
            World world = Bukkit.getWorld(worldName);
            if(world == null) {
                severe("Couldn't load race " + name + " because it's world wasn't found.");
                unloadedRaces.put(worldName, name);
                continue;
            }

            Race race = new Race(name, world);
            race.setRaceManager(this);
            race.setConfig(config);

            checkpointManager.loadRaceCheckpoints(race);
            pitBoxManager.loadRacePitBoxes(race);
            carManager.loadCars(race);

            applyRaceSettings(race, config);

            races.add(race);
            log("Race " + name + " has been loaded");
            loadedRaces++;
        }
        log("Loaded " + loadedRaces + " race(s)!");
    }

    /**
     * Loads and initializes all the races from file
     */
    public boolean loadRace(String raceName) {
        log("Loading race " + raceName + "...");

        File racesFolder = new File(main.getDataFolder(), "races");
        if (!racesFolder.exists()) {
            severe("The race folder wasn't found, therefore " + raceName + " could not be loaded.");
            return false;
        }

        File file = getRaceFile(raceName);
        if (!file.exists()) {
            severe("The race " + raceName + "'s file wasn't found and therefore could not be loaded.");
            return false;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String name = config.getString("name");

        if(name == null) {
            severe("Race " + file.getName() + " wasn't loaded because it didn't contain any name.");
            return false;
        }

        String worldName = Objects.requireNonNull(config.getString("world"));
        World world = Bukkit.getWorld(worldName);
        if(world == null) {
            severe("Couldn't load race " + name + " because it's world wasn't found.");
            unloadedRaces.put(worldName, name);
            return false;
        }
        Race race = new Race(name, world);
        race.setRaceManager(this);
        race.setConfig(config);

        checkpointManager.loadRaceCheckpoints(race);
        pitBoxManager.loadRacePitBoxes(race);
        carManager.loadCars(race);

        applyRaceSettings(race, config);

        races.add(race);

        log("Loaded race " + raceName + " successfully!");
        return true;
    }

    /**
     * Updates all the races from file. (Called on reload)
     */
    public void updateAllRaces() {
        log("Updating all races...");
        int loadedRaces = 0;

        if(!races.isEmpty()) {
            for (Race race : new ArrayList<>(races)) {
                boolean success = race.update();
                if(success) log("Race " + race.getName() + " has been updated");
                else warn("Could not update race " + race.getName() + ", see error above.");
                loadedRaces++;
                try { race.end(false); } catch (Exception ignored) {}
            }
            log("Updated " + loadedRaces + " race(s)!");
        } else {
            warn("No races updated (none were found).");
        }
    }

    /**
     * Creates and loads the inputted {@code race}: <br/>
     * - Creates the config file <br/>
     * - Saves it to {@link RaceManager#races} <br/>
     * - Initializes {@link RaceManager} and the config for the {@link Race}
     * @param race The race to create
     * @return The created race
     */
    @SuppressWarnings("UnusedReturnValue")
    public Race createRace(Race race) {
        log("Creating race " + race.getName());
        File raceFile = getRaceFile(race.getName());
        raceFile.getParentFile().mkdirs();

        YamlConfiguration config = new YamlConfiguration();
        config.set("name", race.getName());
        config.set("world", race.getWorld().getName());
        config.set("lapCount", race.getLapCount());
        config.set("startRotation", race.getStartRotation());
        config.set("requiredPitStops", race.getRequiredPitStops());
        config.set("lights.enabled", false);

        try {
            config.save(raceFile);
            log("Race " + race.getName() + " has been saved");
        } catch (IOException e) {
            err("Couldn't create race " + race.getName(),e);
            return null;
        }

        //Update race in race list
        races.add(race);
        race.setRaceManager(this);
        race.setConfig(config);
        return race;
    }

    /**
     * Updates the race from its config file
     * @param race The race to update
     * @return Whether the operation succeeded
     */
    public boolean updateRace(Race race) {
        try {
            File raceFile = getRaceFile(race.getName());
            if (!raceFile.exists()) {
                warn("Race " + race.getName() + " wasn't updated because it's config file does not exist.");
                return false;
            }

            YamlConfiguration config;
            try {
                config = YamlConfiguration.loadConfiguration(raceFile);
            } catch (Exception e) {
                err("Couldn't load config file for race " + race.getName(),e);
                return false;
            }

            race.setConfig(config);
            applyRaceSettings(race, config);

            //More like reload from file if file/Race unsynced (not really load)
            carManager.loadCars(race);
            checkpointManager.loadRaceCheckpoints(race);
            pitBoxManager.loadRacePitBoxes(race);

            //Update race in race list
            races.remove(race);
            races.add(race);

            if(activeRaces.contains(race)) {
                activeRaces.remove(race);
                activeRaces.add(race);
            }
            return true;
        } catch (Exception e) {
            err("An error occurred when updating the race " + race.getName(), e);
            return false;
        }
    }

    /**
     * Deletes the inputted {@code race}
     * @param race The race to delete
     * @return Whether the operation succeeded
     */
    public boolean deleteRace(Race race) {
        log("Deleting race " + race.getName());
        try { race.end(false); } catch (Exception ignored) {}

        File raceFile = getRaceFile(race.getName());
        boolean deleted = raceFile.delete();

        races.remove(race);

        if(deleted) log("Race " + race.getName() + " has been deleted");
        return deleted;
    }

    /**
     * Gets the {@code race}'s config from file
     * @param race The race to get the config for
     * @return The {@code race}'s config
     */
    public YamlConfiguration getRaceConfig(Race race) {
        File racesFolder = new File(main.getDataFolder(), "races");

        if (!racesFolder.exists()) {
            log("§cCouldn't get config for race " + race.getName() + " because the race folder didn't exist.");
            return null;
        }

        File file = getRaceFile(race.getName());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!file.exists()) {
            log("§cCouldn't get config for race " + race.getName() + " because the race config file didn't exist.");
            return null;
        }

        return config;
    }

    /**
     * Saves the {@code config} for the {@code race}
     * @param race The race that the config belongs to
     * @param config The {@link YamlConfiguration} to save
     * @throws IOException When the saving fails, caused by {@code config.save(file)}
     */
    public void saveRaceConfig(Race race, YamlConfiguration config) throws IOException {
        File racesFolder = new File(main.getDataFolder(), "races");

        if (!racesFolder.exists()) {
            log("§cCouldn't get config for race " + race.getName() + " because the race folder didn't exist.");
            return;
        }

        config.save(getRaceFile(race.getName()));
    }

    //RaceData management

    /**
     * @return All the racers from every race
     */
    public Map<UUID, Racer> getRacers() {
        Map<UUID, Racer> allRacers = new HashMap<>();
        getRaces().forEach(race -> allRacers.putAll(race.racers));
        return allRacers;
    }

    public Racer getRacer(@NotNull UUID uniqueId) {
        return getRacers().get(uniqueId);
    }

    /**
     * @param player The player to check
     * @return Whether the {@code player} is racing
     */
    @SuppressWarnings("unused")
    public boolean isRacing(Player player) {
        return getRacers().containsKey(player.getUniqueId()) && getRacers().get(player.getUniqueId()).racing;
    }

    /** Marks a participant as disqualified and removes them from the active race. */
    public boolean leaveRace(Player player) {
        Racer racer = getRacer(player.getUniqueId());
        if (racer == null || racer.race == null || !racer.race.hasStarted() || !racer.racing) return false;

        Race race = racer.race;
        racer.disqualified = true;
        racer.leftRace = true;
        racer.racing = false;
        race.racing.remove(racer);
        if (racer.pittingBox != null) pitBoxManager.cancelSession(player, racer.pittingBox);
        if (racer.car != null) racer.car.destroy();
        player.leaveVehicle();
        player.setGameMode(GameMode.SPECTATOR);
        liveScoreManager.updateRacer(racer);

        if (race.racing.isEmpty()) {
            try {
                race.end(true);
            } catch (Exception e) {
                player.sendMessage(getMessage("error.unknown"));
                err("Could not send ranking after /race leave", e);
            }
        }
        return true;
    }

    //Race start/end/...

    /**
     * Starts the inputted {@code race}.
     * @param race The race to start
     */
    public Result startRace(Race race) {
        YamlConfiguration config = getRaceConfig(race);
        if(config == null) {
            log("§cCouldn't start race " + race.getName() + " because the race config file didn't exist.");
            return Result.ERROR;
        }

        // Make sure the race isn't already running / counting down (would start it twice).
        // A race that is only in preparation mode is kept: its racers are already placed.
        if (race.startingRace || race.hasRaceStarted) {
            endRace(race, false);
        }

        race.startingRace = true;
        if (!activeRaces.contains(race)) activeRaces.add(race);
        if(!race.preparingRace) {
            for (Car car : race.getCars()) {
                try {
                    Result r = prepareRacer(race, car);
                    if (r == Result.ERROR) return Result.ERROR;
                } catch (Exception e) {
                    err("Couldn't prepare racer", e);
                    return Result.ERROR;
                }
            }
        } else {
            race.preparingRace = false;
        }

        final int[] timesRun = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(main, () -> {
            String titleContent;
            Material mat;
            switch (timesRun[0]) {
                case 0 -> {
                    titleContent = "§45";
                    mat = Material.BROWN_STAINED_GLASS;
                }
                case 1 -> {
                    titleContent = "§c4";
                    mat = Material.RED_STAINED_GLASS;
                }
                case 2 -> {
                    titleContent = "§e3";
                    mat = Material.ORANGE_STAINED_GLASS;
                }
                case 3 -> {
                    titleContent = "§22";
                    mat = Material.YELLOW_STAINED_GLASS;
                }
                case 4 -> {
                    titleContent = "§a1";
                    mat = Material.LIME_STAINED_GLASS;
                }
                case 5 -> {
                    titleContent = "§3GO!";
                    mat = Material.GREEN_STAINED_GLASS;
                }
                default -> {
                    titleContent = "§cError";
                    mat = Material.BLACK_STAINED_GLASS;
                }
            }

            Title title = Title.title(Objects.requireNonNull(c(titleContent)), Objects.requireNonNull(c("")));
            if (race.getLights() != null && race.getLights().enabled()) fillRegion(race.world, race.getLights().from(), race.getLights().to(), mat);


            for(UUID racer : race.racers.keySet()) {
                Player p = Bukkit.getPlayer(racer);
                //Title with time before start
                if(p == null) continue;
                p.showTitle(title);

                //Sound
                if(timesRun[0] != 5) p.playSound(Sound.sound(Key.key("minecraft:block.note_block.pling"), Sound.Source.NEUTRAL, 1F, 1F));
                else p.playSound(Sound.sound(Key.key("minecraft:block.note_block.pling"), Sound.Source.NEUTRAL, 1F, 2F));

            }
            if(timesRun[0] == 5) {
                race.startingRace = false;
                race.hasRaceStarted = true;
                countdownTasks.remove(race);
                task[0].cancel();
            }
            timesRun[0]++;
        },0L,20L);
        countdownTasks.put(race, task[0]);
        return Result.SUCCESS;
    }

    /**
     * Prepares a racer for the inputted {@code race}
     * @param race The race to prepare the racer for
     * @param car The racer's car
     */
    public Result prepareRacer(Race race, Car car) throws NullPointerException, IllegalArgumentException {
        Map<UUID, Racer> racers = race.racers;
        Player owner = Bukkit.getPlayer(car.getOwner());
        if (owner == null) return Result.NULL_POINTER;
        Racer racer = new Racer(owner);

        owner.setGameMode(GameMode.ADVENTURE);
        carManager.spawnCar(race, car, owner);

        racer.racing = true;
        racer.car = car;
        racer.checkpointIndex = -1;
        racer.lapCount = 0;
        racer.lapTime = 0;
        racer.startTime = 0;
        racer.race = race;
        racer.player = owner;

        race.racing.add(racer);
        racers.put(owner.getUniqueId(), racer);
        liveScoreManager.removeRacer(owner);
        liveScoreManager.addRacer(racer);
        return Result.SUCCESS;
    }

    /**
     * Toggles the preparation mode for the {@code race}
     * @param sender Who the feedback will be sent to
     * @param race The race to toggle the preparation for
     */
    public void togglePrepareRace(CommandSender sender, Race race) {
        if(race.preparingRace) cancelPrepareRace(sender, race);
        else prepareRace(sender, race);
    }

    public void prepareRace(CommandSender sender, Race race) {
        sender.sendMessage(getMessage("race.prepare", Messages.formatArguments("name", race.getName())));
        race.preparingRace = true;
        if (!activeRaces.contains(race)) activeRaces.add(race);
        for (Car car : race.getCars()) {
            Player owner = Bukkit.getPlayer(car.getOwner());
            if (owner == null) continue;

            try {
                Result r = prepareRacer(race, car);
                if(r == Result.ERROR) sender.sendMessage(getMessage("error.unknown"));
            } catch (Exception e) {
                err("Couldn't prepare racer", e);
                sender.sendMessage(getMessage("error.unknown"));
                return;
            }
        }
    }

    public void cancelPrepareRace(CommandSender sender, Race race) {
        sender.sendMessage(getMessage("race.cancelPrepare", Messages.formatArguments("name", race.getName())));
        race.preparingRace = false;
        activeRaces.remove(race);
        for (Car car : race.getCars()) {
            Player owner = Bukkit.getPlayer(car.getOwner());
            if (owner == null) continue;
            try {
                car.getBoat().remove();
            } catch (Exception ignored) { continue; }
            owner.teleport(race.getWorld().getSpawnLocation());
            race.racers.remove(owner.getUniqueId());
        }
    }

    /**
     * Ends the inputted {@code race}
     * @param race The race to end
     */
    public void endRace(Race race) {
        endRace(race, true);
    }

    /**
     * Ends the inputted {@code race}. Works at any stage: preparation, countdown or running.
     * Does nothing if the race isn't active at all.
     * @param race The race to end
     * @param announce Whether to broadcast the "race ended" message
     */
    public void endRace(Race race, boolean announce) {
        boolean wasStarted = race.hasStarted();
        if (!wasStarted && !race.isStarting() && !race.isPreparing() && !activeRaces.contains(race)) return;

        // Stop a countdown that is still running (ending the race before "GO")
        BukkitTask countdown = countdownTasks.remove(race);
        if (countdown != null) countdown.cancel();

        activeRaces.removeIf(r -> r == race); // removes every entry, not just the first
        race.rankings = new HashMap<>();

        race.currentBestLapTime = Long.MAX_VALUE;

        race.racing = new ArrayList<>();

        race.startingRace = false;
        race.preparingRace = false;
        race.hasRaceStarted = false;

        if (announce) Bukkit.broadcast(getMessage("race.end", Messages.formatArguments("name", race.getName())));
        for (Car car : race.getCars()) {
            Player owner = Bukkit.getPlayer(car.getOwner());
            Racer data = race.racers.get(car.getOwner());
            if (owner != null) {
                if (SPECTATE_ON_FINISH && wasStarted) owner.setGameMode(GameMode.SPECTATOR);
                if (data != null && data.pittingBox != null) pitBoxManager.cancelSession(owner, data.pittingBox);
            }

            car.destroy();
        }

        liveScoreManager.removeRace(race);
        race.racers.clear();

        if (wasStarted && main.getStatsManager() != null) {
            main.getStatsManager().saveDirtyAsync();
        }

        race.hasRaceStarted=false;
    }

    /**
     * Fills a cuboid region between two locations with the given material.
     *
     * @param world    the world in which to fill the region
     * @param from     the first corner of the region
     * @param to     the second corner of the region
     * @param material the material to fill the region with
     */
    public void fillRegion(World world, Location from, Location to, Material material) {
        int minX = Math.min(from.getBlockX(), to.getBlockX());
        int minY = Math.min(from.getBlockY(), to.getBlockY());
        int minZ = Math.min(from.getBlockZ(), to.getBlockZ());

        int maxX = Math.max(from.getBlockX(), to.getBlockX());
        int maxY = Math.max(from.getBlockY(), to.getBlockY());
        int maxZ = Math.max(from.getBlockZ(), to.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(material);
                }
            }
        }
    }
}
