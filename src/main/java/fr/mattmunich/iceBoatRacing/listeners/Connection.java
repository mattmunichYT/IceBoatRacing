package fr.mattmunich.iceBoatRacing.listeners;

import fr.mattmunich.iceBoatRacing.LiveScoreManager;
import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.Racer;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;

import static fr.mattmunich.iceBoatRacing.Main.*;
import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public class Connection implements Listener {

    private final Main main;
    private final RaceManager raceManager;
    private final CarManager carManager;
    private final LiveScoreManager liveScoreManager;

    public Connection(Main main, RaceManager raceManager, CarManager carManager, LiveScoreManager liveScoreManager) {
        this.main = main;
        this.raceManager = raceManager;
        this.carManager = carManager;
        this.liveScoreManager = liveScoreManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.joinMessage(getMessage("noPrefix.join",formatArguments("player", p.getName())));

        if (raceManager.races.isEmpty()) return;

        for(Race race : raceManager.races) {
            Racer racer = race.racers.get(p.getUniqueId());
            if (racer == null) continue;
            if (race.hasStarted() && (racer.leftRace || !main.getConfig().getBoolean("race.allowRejoin"))) {
                racer.player = p;
                p.setGameMode(GameMode.SPECTATOR);
                liveScoreManager.updateRacer(racer);
                continue;
            }
            if(race.isPreparing() && race.racers.containsKey(p.getUniqueId())) {
                //Logs in when race is preparing
                final Car[] car = new Car[1];
                race.getCars().forEach(c -> {
                    if(c.getOwner().equals(p.getUniqueId())) {
                        car[0] = c;
                    }
                });

                //TP to spawn of the world -- else player won't be in the car
                if (p.getVehicle() != null) p.getVehicle().remove();
                p.teleport(car[0].getStartingLocation());

                try {
                    raceManager.prepareRacer(race, car[0]);
                } catch (Exception ex) {
                    err("Couldn't prepare " + p.getName() + " for the race " + race.getName() +  " when they rejoined", ex);
                    p.sendMessage(getMessage("error.unknown"));
                }
            } else if (race.hasStarted() && race.racers.containsKey(p.getUniqueId())) {
                //Logs back in when race is still going
                p.setGameMode(GameMode.ADVENTURE);
                if (racer.car == null) continue;

                Location logOutLocation = racer.getLogoutLocation();
                if(logOutLocation == null) {
                    warn("Couldn't put " + p.getName() + " back in it's car after logging back in because the log out location was null.");
                    return;
                }

                //Always remove and place back car to prevent bugs
                Car car = racer.car;
                carManager.spawnCar(race, car, p, logOutLocation);

                //Add racer back to racing list
                racer.player = p;
                racer.playerName = p.getName();
                racer.disqualified = false;
                racer.racing = true;
                racer.from = p.getLocation().clone();
                race.racing.add(racer);
                liveScoreManager.updateRacer(racer);
            } else {
                //Means player logged out during race and logged back in when it was ended
                racer.car.destroy();
                p.teleport(race.getWorld().getSpawnLocation());
                liveScoreManager.removeRacer(racer);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        liveScoreManager.forgetSidebar(p);
        e.quitMessage(getMessage("noPrefix.quit",formatArguments("player", p.getName())));

        if (main.getStatsManager() != null) {
            main.getStatsManager().savePlayerAsync(p.getUniqueId());
        }

        for (Race race : new ArrayList<>(raceManager.activeRaces)) {
            Racer racer = race.racers.get(p.getUniqueId());
            if(racer == null) continue;
            racer.racing = false;
            Car car = racer.car;
            if (racer.leftRace) {
                racer.player = null;
                continue;
            }
            if (!racer.racing && race.rankings.containsValue(racer)) {
                racer.player = null;
                continue;
            }
            if (!race.hasStarted()) {
                if (car != null && main.getConfig().getBoolean("race.allowRejoin")) {
                    racer.setLogoutLocation(car.getLocation());
                    if (REMOVE_CAR_ON_LOGOUT) car.destroy();
                }
                continue;
            }
            race.racing.remove(racer);
            racer.disqualified = true;
            if (car != null && main.getConfig().getBoolean("race.allowRejoin")) {
                racer.setLogoutLocation(car.getLocation());
                if (REMOVE_CAR_ON_LOGOUT) car.destroy();
            } else if (car != null) {
                car.destroy();
            }
            liveScoreManager.updateRacer(racer);
            racer.player = null;

            //Auto end race if the last racer leaves
            if(race.racing.isEmpty()) {
                try {
                    race.end(true);
                } catch (Exception err) {
                    switch (err.getMessage()) {
                        case "NO_RANKING" -> Bukkit.getConsoleSender().sendMessage(getMessage("race.onEnd.error.noRanking"));
                        case "NO_RACERS" -> Bukkit.getConsoleSender().sendMessage(getMessage("race.onEnd.error.noRacers"));
                        default -> Bukkit.getConsoleSender().sendMessage(getMessage("error.unknown"));
                    }
                }
            }
        }


    }
}
