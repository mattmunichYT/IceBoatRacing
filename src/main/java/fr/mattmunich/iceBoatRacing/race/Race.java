package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.Checkpoint;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

public class Race {

    String name;
    World world;
    List<Checkpoint> checkpoints = new ArrayList<>();
    List<Car> cars = new ArrayList<>();
    public Map<UUID, RaceData> racers = new HashMap<>();

    boolean startingRace = false;
    boolean preparingRace = false;
    boolean hasRaceStarted = false;


    public Race(
            String name,
            World world
            ) {
        this.name = name;
        this.world = world;
    }

    public String getName() {
        return name;
    }
    public World getWorld() {
        return world;
    }

    //Checkpoints
    public List<Checkpoint> getCheckpoints() {
        return checkpoints;
    }

    public void addCheckpoint(Checkpoint checkpoint) {
        checkpoints.add(checkpoint);
    }

    public void clearCheckpoints() {
        checkpoints.clear();
    }

    public boolean removeCheckpoint(Checkpoint checkpoint) {
        return checkpoints.remove(checkpoint);
    }

    //Cars
    public List<Car> getCars() {
        return cars;
    }

    public void addCar(Car car) {
        cars.add(car);
    }

    public void clearCars() {
        cars.clear();
    }

    public boolean removeCar(Car car) {
        return cars.remove(car);
    }
}
