package fr.mattmunich.iceBoatRacing.cars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;

import java.util.UUID;

public class Car {

    private final int id;
    private UUID owner;
    private Boat boat;
    private final Location startingLocation;
    private final Material boatMaterial;
    private final String customName;

    public Car(int id, UUID owner, Location startingLocation, Material boatMaterial, String customName) {
        this.id = id;
        this.owner = owner;
        this.startingLocation = startingLocation;
        this.boatMaterial = boatMaterial;
        this.customName = customName;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public void setBoat(Boat boat) {
        this.boat = boat;
    }

    public Boat getBoat() {
        return boat;
    }

    public Material getBoatMaterial() {
        return boatMaterial;
    }

    public void destroy() {
        if (boat != null && !boat.isDead()) {
            boat.remove();
        }
        boat = null;
    }

    public int getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public Location getStartingLocation() {
        return startingLocation;
    }

    public String getCustomName() {
        return customName;
    }
}