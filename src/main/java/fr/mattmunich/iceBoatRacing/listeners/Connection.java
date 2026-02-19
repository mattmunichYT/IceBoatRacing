package fr.mattmunich.iceBoatRacing.listeners;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.race.RaceData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public class Connection implements Listener {

    private final Main main;
    private final CarManager carManager;

    public Connection(Main main, CarManager carManager) {
        this.main = main;
        this.carManager = carManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if(main.racers.containsKey(p.getUniqueId())) {
            final Car[] car = new Car[1];
            carManager.cars.forEach(c -> {
                if(c.getOwner().equals(p.getUniqueId())) {
                    car[0] = c;
                }
            });

            main.racers.get(p.getUniqueId()).car = car[0];
            if(main.preparingRace) {
                //TP to spawn of the world -- else player won't be in the car
                p.teleport(car[0].getStartingLocation().getWorld().getSpawnLocation());

                carManager.spawnCar(car[0],p);
                main.racers.put(p.getUniqueId(),new RaceData(p));
                main.racers.get(p.getUniqueId()).checkpointIndex = -1;
                main.racers.get(p.getUniqueId()).lapCount = 0;
                main.racers.get(p.getUniqueId()).lapTime = 0;
                main.racers.get(p.getUniqueId()).startTime = 0;
                main.racers.get(p.getUniqueId()).player = p;

                main.liveSidebar.getScore(p.getName()).resetScore();
            }
        }

        e.joinMessage(getMessage("noPrefix.join",formatArguments("player", p.getName())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.quitMessage(getMessage("noPrefix.quit",formatArguments("player", p.getName())));
        main.liveSidebar.getScore(p.getName()).resetScore();

        RaceData racer = main.racers.get(p.getUniqueId());
        if (racer != null && racer.car != null) {
            racer.car.destroy();
        }
    }
}
