package fr.mattmunich.iceBoatRacing.cars;

import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.Racer;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import java.util.List;

import static fr.mattmunich.iceBoatRacing.Main.log;

public class CarListener implements Listener {

    private final RaceManager raceManager;

    public CarListener(RaceManager raceManager) {
        this.raceManager = raceManager;
    }

    @EventHandler
    public void onExitVehicle(VehicleExitEvent e) {
        if (!(e.getExited() instanceof Player p)) return;
        Racer racer = null;
        for (Race race : raceManager.activeRaces) {
            Racer found = race.racers.get(p.getUniqueId());
            if (found != null) {
                racer = found;
                break;
            }
        }
        if (racer == null || racer.race == null || racer.car == null) return;
        if(!(racer.race.isStarting() || racer.race.isPreparing() || racer.race.hasStarted())) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void onMoveCar(VehicleMoveEvent e) {
        if (!(e.getVehicle() instanceof Boat boat)) return;

        List<Entity> passengers = boat.getPassengers();
        if (passengers.isEmpty()) return;
        if (!(passengers.getFirst() instanceof Player p)) return;

        Racer racer = null;
        for (Race race : raceManager.activeRaces) {
            Racer found = race.racers.get(p.getUniqueId());
            if (found != null) {
                racer = found;
                break;
            }
        }
        if (racer == null || racer.race == null || racer.car == null) return;
        if (!(racer.race.isPreparing() || racer.race.isStarting() || racer.pittingBox != null)) return;

        boat.teleport(e.getFrom());
        boat.setVelocity(new Vector(0,0,0));
    }
}
