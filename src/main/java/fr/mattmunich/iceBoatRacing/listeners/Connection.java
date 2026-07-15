package fr.mattmunich.iceBoatRacing.listeners;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceData;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public class Connection implements Listener {

    private final Main main;
    private final RaceManager raceManager;
    private final CarManager carManager;

    public Connection(Main main, RaceManager raceManager, CarManager carManager) {
        this.main = main;
        this.raceManager = raceManager;
        this.carManager = carManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        RaceData racer = main.racers.get(p.getUniqueId());

        if (!raceManager.races.isEmpty()) {
            for(Race race : raceManager.races) {
                if(race.isPreparing() && race.racers.containsKey(p.getUniqueId())) {
                    final Car[] car = new Car[1];
                    race.getCars().forEach(c -> {
                        if(c.getOwner().equals(p.getUniqueId())) {
                            car[0] = c;
                        }
                    });

                    //TP to spawn of the world -- else player won't be in the car
                    p.teleport(car[0].getStartingLocation().getWorld().getSpawnLocation());

                    raceManager.prepareRacer(race, car[0]);


                } else if (race.hasStarted() && race.racers.containsKey(p.getUniqueId()) && main.getConfig().getBoolean("allowRejoin")) {
                    if(racer == null) {
                        main.warn("Couldn't put " + p.getName() + " back in it's car after logging back in because the it's RaceData was null.");
                        return;
                    }
                    if(main.getConfig().getBoolean("removeCarWhenLoggingOut")) {
                        Location logOutLocation = racer.getLogoutLocation();
                        if(logOutLocation == null) {
                            main.warn("Couldn't put " + p.getName() + " back in it's car after logging back in because the log out location was null.");
                            return;
                        }
                        Car car = racer.car;
                        carManager.spawnCar(car,racer.player,logOutLocation);
                    } else {
                        boolean success = racer.car.getBoat().addPassenger(p);
                        if (!success) {
                            Location logOutLocation = racer.getLogoutLocation();
                            if(logOutLocation == null) {
                                main.warn("Couldn't put " + p.getName() + " back in it's car after logging back in because the log out location was null.");
                                return;
                            }
                            Car car = racer.car;
                            carManager.spawnCar(car,racer.player,logOutLocation);
                        }
                    }
                } else {
                    main.liveSidebar.getScore(p.getName()).resetScore();
                }
            }
        }

        e.joinMessage(getMessage("noPrefix.join",formatArguments("player", p.getName())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.quitMessage(getMessage("noPrefix.quit",formatArguments("player", p.getName())));


        RaceData racer = main.racers.get(p.getUniqueId());
        if (racer != null && racer.car != null) {
            if (main.getConfig().getBoolean("allowRejoin")) {
                if (main.getConfig().getBoolean("removeCarWhenLoggingOut")) {
                    racer.car.destroy();
                }
                racer.setLogoutLocation(racer.car.getBoat().getLocation());
            } else {
                racer.car.destroy();
                main.liveSidebar.getScore(p.getName()).resetScore();
                racer.race.racers.remove(p.getUniqueId());
            }
        } else {
            main.liveSidebar.getScore(p.getName()).resetScore();
        }

    }
}
