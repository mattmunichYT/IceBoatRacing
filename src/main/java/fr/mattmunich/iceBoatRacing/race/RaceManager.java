package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.CheckpointManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static fr.mattmunich.iceBoatRacing.Main.c;

public class RaceManager {

    private final Main main;
    private final CarManager carManager;
    private final CheckpointManager checkpointManager;
    private World raceWorld = Bukkit.getWorld("world");

    public List<Race> races;

    public RaceManager(Main main, CarManager carManager, CheckpointManager checkpointManager) {
        this.main = main;
        this.carManager = carManager;
        this.checkpointManager = checkpointManager;
        loadAllRaces();

        Bukkit.getScheduler().runTask(main, () -> {
            Car firstCar = carManager.get(0);
            if (firstCar != null && firstCar.getStartingLocation() != null) {
                raceWorld = firstCar.getStartingLocation().getWorld();
                main.log("[RaceManager] Detected race world: " + raceWorld.getName());
            } else {
                main.log("[RaceManager] No cars loaded yet, using default world for raceWorld.");
                raceWorld = Bukkit.getWorlds().getFirst();
            }
        });
    }

    public Race getRace(String raceName) {
        for (Race race : races) {
            if (race.getName().equalsIgnoreCase(raceName)) {
                return race;
            }
        }
        return null;
    }

    public void loadAllRaces() {
        File racesFolder = new File(main.getDataFolder(), "races");
        if (!racesFolder.exists()) return;

        for (File file : Objects.requireNonNull(racesFolder.listFiles())) {
            if (!file.getName().endsWith(".yml")) continue;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String name = config.getString("name");
            World world = Bukkit.getWorld(Objects.requireNonNull(config.getString("world")));
            if(world == null) {
                main.log("Couldn't load race " + name + " because it's world wasn't found.");
                continue;
            }
            Race race = new Race(name, world);
            checkpointManager.loadRaceCheckpoints(race);
            carManager.loadCars(race);

            races.add(race);
        }
    }

    public void saveRace(Race race) {
        File raceFile = new File(main.getDataFolder(), "races/" + race.getName() + ".yml");
        raceFile.getParentFile().mkdirs(); // creates the races/ folder if it doesn't exist

        YamlConfiguration config = new YamlConfiguration();
        config.set("name", race.getName());
        config.set("world", race.getWorld().getName());

        // set whatever else you need

        try {
            config.save(raceFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public YamlConfiguration getRaceConfig(Race race) {
        File racesFolder = new File(main.getDataFolder(), "races");

        if (!racesFolder.exists()) {
            main.log("§cCouldn't get config for race " + race.getName() + " because the race folder didn't exist.");
            return null;
        }

        File file = new File(racesFolder, race.getName() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!file.exists()) {
            main.log("§cCouldn't get config for race " + race.getName() + " because the race config file didn't exist.");
            return null;
        }

        return config;
    }

    public void saveRaceConfig(Race race, YamlConfiguration config) throws IOException {
        File racesFolder = new File(main.getDataFolder(), "races");

        if (!racesFolder.exists()) {
            main.log("§cCouldn't get config for race " + race.getName() + " because the race folder didn't exist.");
            return;
        }

        File file = new File(racesFolder, race.getName() + ".yml");
        config.save(file);
    }

    public void startRace(Race race) {
        YamlConfiguration config = getRaceConfig(race);
        if(config == null) {
            main.log("§cCouldn't start race " + race.getName() + " because the race config file didn't exist.");
            return;
        }

        race.startingRace = true;
        if(!race.preparingRace) {
            for (Car car : race.getCars()) {
                Player owner = Bukkit.getPlayer(car.getOwner());
                if (owner == null) continue;

                carManager.spawnCar(car,owner);
                race.racers.put(owner.getUniqueId(),new RaceData(owner));
                race.racers.get(owner.getUniqueId()).car = car;
                race.racers.get(owner.getUniqueId()).checkpointIndex = -1;
                race.racers.get(owner.getUniqueId()).lapCount = 0;
                race.racers.get(owner.getUniqueId()).lapTime = 0;
                race.racers.get(owner.getUniqueId()).startTime = 0;
                race.racers.get(owner.getUniqueId()).player = owner;

                main.liveSidebar.getScore(owner.getName()).resetScore();
            }
        } else {
            race.preparingRace = false;
        }

        final int[] timesRun = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(main, () -> {
            int fromX = config.getInt("lights.from.x",123456789);
            int fromY = config.getInt("lights.from.y",123456789);
            int fromZ = config.getInt("lights.from.z",123456789);
            int toX = config.getInt("lights.to.x",123456789);
            int toY = config.getInt("lights.to.y",123456789);
            int toZ = config.getInt("lights.to.z",123456789);
            boolean raceLightsEnabled = config.getBoolean("lights.enabled");
            if(fromX == 123456789 || fromY == 123456789 || fromZ == 123456789 || toX == 123456789 || toY == 123456789 || toZ == 123456789) raceLightsEnabled = false;
            String titleContent;
            switch (timesRun[0]) {
                case 0 -> {
                    titleContent = "§45";
                    if (raceLightsEnabled) fillRegion(race.world, fromX, fromY, fromZ, toX, toY, toZ, Material.BROWN_STAINED_GLASS);
                }
                case 1 -> {
                    titleContent = "§c4";
                    if (raceLightsEnabled) fillRegion(race.world, fromX, fromY, fromZ, toX, toY, toZ, Material.RED_STAINED_GLASS);
                }
                case 2 -> {
                    titleContent = "§e3";
                    if (raceLightsEnabled) fillRegion(race.world, fromX, fromY, fromZ, toX, toY, toZ, Material.ORANGE_STAINED_GLASS);
                }
                case 3 -> {
                    titleContent = "§22";
                    if (raceLightsEnabled) fillRegion(race.world, fromX, fromY, fromZ, toX, toY, toZ, Material.YELLOW_STAINED_GLASS);
                }
                case 4 -> {
                    titleContent = "§a1";
                    if (raceLightsEnabled) fillRegion(race.world, fromX, fromY, fromZ, toX, toY, toZ, Material.LIME_STAINED_GLASS);
                }
                case 5 -> {
                    titleContent = "§3GO!";
                    if (raceLightsEnabled) fillRegion(race.world, fromX, fromY, fromZ, toX, toY, toZ, Material.GREEN_STAINED_GLASS);
                }
                default -> {
                    titleContent = "§cError";
                    if (raceLightsEnabled) fillRegion(race.world, fromX, fromY, fromZ, toX, toY, toZ, Material.BLACK_STAINED_GLASS);
                }
            }

            Title title = Title.title(c(titleContent),c(""));


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
                race.startingRace=false;
                race.hasRaceStarted=true;
                task[0].cancel();
            }
            timesRun[0]++;
        },0L,20L);
    }

    public void togglePrepareRace(CommandSender sender, Race race) {
        if(race.preparingRace) cancelPrepareRace(sender, race);
        else prepareRace(sender, race);
    }

    public void prepareRace(CommandSender sender, Race race) {
        sender.sendMessage(Messages.getMessage("race.prepare", Messages.formatArguments("name", race.getName())));
        race.preparingRace = true;
        for (Car car : race.getCars()) {
            Player owner = Bukkit.getPlayer(car.getOwner());
            if (owner == null) continue;

            carManager.spawnCar(car,owner);
            race.racers.put(owner.getUniqueId(),new RaceData(owner));
            race.racers.get(owner.getUniqueId()).car = car;
            race.racers.get(owner.getUniqueId()).checkpointIndex = -1;
            race.racers.get(owner.getUniqueId()).lapCount = 0;
            race.racers.get(owner.getUniqueId()).lapTime = 0;
            race.racers.get(owner.getUniqueId()).startTime = 0;
            race.racers.get(owner.getUniqueId()).player = owner;

            main.liveSidebar.getScore(owner.getName()).resetScore();
        }
    }

    public void cancelPrepareRace(CommandSender sender, Race race) {
        sender.sendMessage(Messages.getMessage("race.cancelPrepare", Messages.formatArguments("name", race.getName())));
        race.preparingRace = false;
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

    public void endRace(Race race) {
        Bukkit.broadcast(Messages.getMessage("race.end", Messages.formatArguments("name", race.getName())));
        for (Car car : race.getCars()) {
            Player owner = Bukkit.getPlayer(car.getOwner());
            if (owner == null) continue;

            car.destroy();
            race.racers.remove(owner.getUniqueId());

            main.liveSidebar.getScore(owner.getName()).resetScore();
        }

        race.hasRaceStarted=false;
    }

    public void fillRegion(World world,
                           int x1, int y1, int z1,
                           int x2, int y2, int z2,
                           Material material) {

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

}
