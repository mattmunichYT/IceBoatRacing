package fr.mattmunich.iceBoatRacing.cars;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.race.RaceData;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public class CarListener implements Listener {

    private final Main main;

    public CarListener(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onExitVehicle(VehicleExitEvent e) {
        if (!(e.getExited() instanceof Player p)) return;
        if(main.hasRaceStarted || (!main.startingRace && !main.preparingRace)) return;

        RaceData racer = main.racers.get(p.getUniqueId());
        if (racer == null || racer.car == null) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void onMoveCar(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if(p.getVehicle()!=null && !(p.getVehicle() instanceof Boat)) return;
        if (!main.racers.containsKey(p.getUniqueId())) return;
        if(!main.startingRace && !main.preparingRace) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void onMoveCar2(VehicleMoveEvent e) {
        Vehicle vehicle = e.getVehicle();
        if(!(vehicle instanceof Boat)) return;
        Entity passenger;
        try {
            passenger = vehicle.getPassengers().getFirst();
        } catch (NullPointerException ex) { return; }
        if(passenger == null) return;
        if(!(passenger instanceof Player p)) return;
        if(!main.racers.containsKey(p.getUniqueId())) return;
        if(!main.startingRace && !main.preparingRace) return;

        e.getVehicle().teleport(e.getFrom());
    }
}
