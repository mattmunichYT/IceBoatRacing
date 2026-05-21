package fr.mattmunich.iceBoatRacing.cars;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Main.s;

public class CarManager {
    private final Main main;
    private final RaceManager raceManager;

    public CarManager(Main main, RaceManager raceManager) {
        this.main = main;
        this.raceManager = raceManager;
    }

    public @Nullable Car get(Race race, int id) {
        return race.getCars().stream()
                .filter(c -> c.getId() == id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all the checkpoints
     * @return a map of all the races as keys and their checkpoint list as values
     */
    public Map<Race, List<Car>> getAll() {
        Map<Race, List<Car>> cars = new HashMap<>();
        for (Race race : raceManager.races) {
            cars.put(race, race.getCars());
        }
        return cars;
    }

    /**
     * Get all the checkpoints in the form of a list
     *@return a list of all the checkpoints
     */
    public List<Car> getAllNoRaceInfo() {
        List<Car> cars = new ArrayList<>();
        for (Race race : raceManager.races) {
            cars.addAll(race.getCars());
        }
        return cars;
    }

    public boolean changeOwner(Race race, Car car, UUID newOwner) {
        car.setOwner(newOwner);

        YamlConfiguration config = raceManager.getRaceConfig(race);
        if(config == null) return false;

        String path = "cars." + car.getId() + ".owner";
        config.set(path, newOwner.toString());

        try {
            raceManager.saveRaceConfig(race, config);
        } catch (IOException e) {
            main.err("Couldn't rename car " + car.getId() + " for race " + race.getName(),e);
            return false;
        }

        Player p = Bukkit.getPlayer(newOwner);
        if (p != null && race.racers.containsKey(p.getUniqueId())) {
            race.racers.get(p.getUniqueId()).car = car;
        }
        return true;
    }

    public void spawnCar(Car car, Player player) {
        Location loc = car.getStartingLocation().clone();
        Material boatMat = car.getBoatMaterial();

        // Spawn boat
        Boat boat = (Boat) loc.getWorld().spawnEntity(loc, boatEntityFromMaterial(boatMat));
        boat.customName(c(car.getCustomName()));
        Location boatLocation = boat.getLocation();
        float startRotation = (float) main.getConfig().getInt("race.startRotation");
        boatLocation.setRotation(startRotation,0F);
        boat.teleport(boatLocation);
        boat.setInvulnerable(true);

        boat.addPassenger(player);
        car.setBoat(boat);
    }

    public void saveCar(Race race, UUID owner, Location startingLocation, ItemStack boatItem) {

        int id = count(race);
        String path = "cars." + id;
        String customName = s(boatItem.getItemMeta().customName()).isBlank() ? "Race car" : s(boatItem.getItemMeta().customName());

        YamlConfiguration config = raceManager.getRaceConfig(race);

        config.set(path + ".world", startingLocation.getWorld().getName());
        config.set(path + ".startingLocation", serialize(startingLocation));
        config.set(path + ".owner", owner.toString());
        config.set(path + ".boatMaterial", boatItem.getType().name());
        config.set(path + ".boatCustomName", customName);

        try {
            raceManager.saveRaceConfig(race,config);
        } catch (IOException e) {
            main.err("Couldn't save car " + id + " for race " + race.getName() + " because the it's config threw an error on saving. ",e);
        }

        race.addCar(new Car(id, owner, startingLocation, boatItem.getType(), s(boatItem.getItemMeta().customName())));
    }

    public void loadCars(Race race) {
        race.clearCars();
        
        YamlConfiguration config = raceManager.getRaceConfig(race);

        if (!config.isConfigurationSection("cars")) return;

        for (String key : Objects.requireNonNull(
                config.getConfigurationSection("cars")
        ).getKeys(false)) {

            int id = Integer.parseInt(key);
            String path = "cars." + id;

            String worldName = config.getString(path + ".world");
            Location loc = deserialize(
                    worldName,
                    config.getString(path + ".startingLocation")
            );

            if (loc == null) continue;

            UUID owner = UUID.fromString(
                    Objects.requireNonNull(config.getString(path + ".owner"))
            );

            Material material = Material.valueOf(
                    config.getString(path + ".boatMaterial", "OAK_BOAT")
            );

            String customName = config.getString(path + ".boatCustomName");

            race.addCar(new Car(id, owner, loc, material, customName));
        }
    }

    public int count() {
        return getAll().size();
    }

    public int count(Race race) {
        return race.getCars().size();
    }

    private String serialize(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location deserialize(String worldName, String value) {
        if (value == null) return null;

        World world = Bukkit.getWorld(worldName);
//        if (world == null) return null;

        String[] parts = value.split(",");
        return new Location(
                world,
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2])
        );
    }

    public boolean remove(Race race, Car car) {
        if (car == null) return false;
        if (race == null) return false;

        YamlConfiguration config = raceManager.getRaceConfig(race);
        if(config == null) return false;

        config.set("car." + car.getId(), null);
        try {
            raceManager.saveRaceConfig(race, config);
        } catch (IOException e) {
            main.err("Couldn't remove car " + car.getId() + " for race " + race.getName(),e);
            return false;
        }
        race.removeCar(car);
        return true;
    }


    private EntityType boatEntityFromMaterial(Material material) {
        return switch (material) {
            //case OAK_BOAT -> EntityType.OAK_BOAT;
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
            default -> EntityType.OAK_BOAT;
        };
    }
}
