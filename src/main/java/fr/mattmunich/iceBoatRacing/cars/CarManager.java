package fr.mattmunich.iceBoatRacing.cars;

import fr.mattmunich.iceBoatRacing.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Main.s;

public class CarManager {
    private final Main main;

    public final List<Car> cars = new ArrayList<>();

    public CarManager(Main main) {
        this.main = main;
    }

    public void add(Car car) {
        cars.removeIf(c -> c.getId() == car.getId());
        cars.add(car);
        Player owner = Bukkit.getPlayer(car.getOwner());
        if(owner != null && main.racers.containsKey(owner.getUniqueId())) {
            main.racers.get(owner.getUniqueId()).car = car;
        }
    }

    public @Nullable Car get(int id) {
        return cars.stream()
                .filter(c -> c.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public List<Car> getAll() {
        return cars;
    }

    public void changeOwner(Car car, UUID newOwner) {
        car.setOwner(newOwner);

        String path = "cars." + car.getId() + ".owner";
        main.getConfig().set(path, newOwner.toString());
        main.saveConfig();

        Player p = Bukkit.getPlayer(newOwner);
        if (p != null && main.racers.containsKey(p.getUniqueId())) {
            main.racers.get(p.getUniqueId()).car = car;
        }
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

    public void saveCar(UUID owner, Location startingLocation, ItemStack boatItem) {

        int id = count();
        String path = "cars." + id;
        String customName = s(boatItem.getItemMeta().customName()).isBlank() ? "Race car" : s(boatItem.getItemMeta().customName());

        main.getConfig().set(path + ".world", startingLocation.getWorld().getName());
        main.getConfig().set(path + ".startingLocation", serialize(startingLocation));
        main.getConfig().set(path + ".owner", owner.toString());
        main.getConfig().set(path + ".boatMaterial", boatItem.getType().name());
        main.getConfig().set(path + ".boatCustomName", customName);

        main.saveConfig();

        add(new Car(id, owner, startingLocation, boatItem.getType(), s(boatItem.getItemMeta().customName())));
    }

    public void loadCars() {
        cars.clear();

        if (!main.getConfig().isConfigurationSection("cars")) return;

        for (String key : Objects.requireNonNull(
                main.getConfig().getConfigurationSection("cars")
        ).getKeys(false)) {

            int id = Integer.parseInt(key);
            String path = "cars." + id;

            String worldName = main.getConfig().getString(path + ".world");
            Location loc = deserialize(
                    worldName,
                    main.getConfig().getString(path + ".startingLocation")
            );

            if (loc == null) continue;

            UUID owner = UUID.fromString(
                    Objects.requireNonNull(main.getConfig().getString(path + ".owner"))
            );

            Material material = Material.valueOf(
                    main.getConfig().getString(path + ".boatMaterial", "OAK_BOAT")
            );

            String customName = main.getConfig().getString(path + ".boatCustomName");

            cars.add(new Car(id, owner, loc, material,customName));
        }
    }

    public int count() {
        return getAll().size();
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

    public void remove(Car car) {
        if (car == null) return;

        cars.remove(car);
        main.getConfig().set("cars." + car.getId(), null);
        main.saveConfig();
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
