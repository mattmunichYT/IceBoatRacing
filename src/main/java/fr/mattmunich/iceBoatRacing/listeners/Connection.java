package fr.mattmunich.iceBoatRacing.listeners;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceData;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public class Connection implements Listener {

    private final Main main;
    private final RaceManager raceManager;

    public Connection(Main main, RaceManager raceManager) {
        this.main = main;
        this.raceManager = raceManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (!raceManager.races.isEmpty()) {
            for(Race race : raceManager.races) {
                if(race.isPreparing() && race.racers.containsKey(p.getUniqueId())) {
                    final Car[] car = new Car[1];
                    race.getCars().forEach(c -> {
                        if(c.getOwner().equals(p.getUniqueId())) {
                            car[0] = c;
                        }
                    });

                    if(race.isPreparing()) {
                        //TP to spawn of the world -- else player won't be in the car
                        p.teleport(car[0].getStartingLocation().getWorld().getSpawnLocation());

                        raceManager.prepareRacer(race, car[0]);
                    }

                }
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
