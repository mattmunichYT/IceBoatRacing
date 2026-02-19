package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import fr.mattmunich.iceBoatRacing.cars.Car;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import static fr.mattmunich.iceBoatRacing.Main.c;

public class RaceManager {

    private final Main main;
    private final CarManager carManager;
    private final World raceWorld =  Bukkit.getWorld("GP-Canada");


    public RaceManager(Main main, CarManager carManager) {
        this.main = main;
        this.carManager = carManager;
    }

    public void startRace() {
        main.startingRace = true;
        if(!main.preparingRace) {
            for (Car car : carManager.cars) {
                Player owner = Bukkit.getPlayer(car.getOwner());
                if (owner == null) continue;

                carManager.spawnCar(car,owner);
                main.racers.put(owner.getUniqueId(),new RaceData(owner));
                main.racers.get(owner.getUniqueId()).car = car;
                main.racers.get(owner.getUniqueId()).checkpointIndex = -1;
                main.racers.get(owner.getUniqueId()).lapCount = 0;
                main.racers.get(owner.getUniqueId()).lapTime = 0;
                main.racers.get(owner.getUniqueId()).startTime = 0;
                main.racers.get(owner.getUniqueId()).player = owner;

                main.liveSidebar.getScore(owner.getName()).resetScore();
            }
        } else {
            main.preparingRace = false;
        }

        final int[] timesRun = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(main, () -> {
            String titleContent;
            switch (timesRun[0]) {
                case 0 -> {
                    titleContent = "§45";
                    fillRegion(raceWorld, 805, 79, 666, 805, 79, 670, Material.BROWN_STAINED_GLASS);
                }
                case 1 -> {
                    titleContent = "§c4";
                    fillRegion(raceWorld, 805, 79, 666, 805, 79, 670, Material.RED_STAINED_GLASS);
                }
                case 2 -> {
                    titleContent = "§e3";
                    fillRegion(raceWorld, 805, 79, 666, 805, 79, 670, Material.ORANGE_STAINED_GLASS);
                }
                case 3 -> {
                    titleContent = "§22";
                    fillRegion(raceWorld, 805, 79, 666, 805, 79, 670, Material.YELLOW_STAINED_GLASS);
                }
                case 4 -> {
                    titleContent = "§a1";
                    fillRegion(raceWorld, 805, 79, 666, 805, 79, 670, Material.LIME_STAINED_GLASS);
                }
                case 5 -> {
                    titleContent = "§3GO!";
                    fillRegion(raceWorld, 805, 79, 666, 805, 79, 670, Material.GREEN_STAINED_GLASS);
                }
                default -> {
                    titleContent = "§cError";
                    fillRegion(raceWorld, 805, 79, 666, 805, 79, 670, Material.BLACK_STAINED_GLASS);
                }
            }

            Title title = Title.title(c(titleContent),c(""));


            for(Player p : Bukkit.getOnlinePlayers()) {
                //Title with time before start
                p.showTitle(title);

                //Sound
                if(timesRun[0] != 5) p.playSound(Sound.sound(Key.key("minecraft:block.note_block.pling"), Sound.Source.NEUTRAL, 1F, 1F));
                else p.playSound(Sound.sound(Key.key("minecraft:block.note_block.pling"), Sound.Source.NEUTRAL, 1F, 2F));

            }
            if(timesRun[0] == 5) {
                main.startingRace=false;
                main.hasRaceStarted=true;
                task[0].cancel();
            }
            timesRun[0]++;
        },0L,20L);
    }

    public void togglePrepareRace(CommandSender sender) {
        if(main.preparingRace) cancelPrepareRace(sender);
        else prepareRace(sender);
    }

    public void prepareRace(CommandSender sender) {
        sender.sendMessage(Messages.getMessage("race.prepare"));
        main.preparingRace = true;
        for (Car car : carManager.cars) {
            Player owner = Bukkit.getPlayer(car.getOwner());
            if (owner == null) continue;

            carManager.spawnCar(car,owner);
            main.racers.put(owner.getUniqueId(),new RaceData(owner));
            main.racers.get(owner.getUniqueId()).car = car;
            main.racers.get(owner.getUniqueId()).checkpointIndex = -1;
            main.racers.get(owner.getUniqueId()).lapCount = 0;
            main.racers.get(owner.getUniqueId()).lapTime = 0;
            main.racers.get(owner.getUniqueId()).startTime = 0;
            main.racers.get(owner.getUniqueId()).player = owner;

            main.liveSidebar.getScore(owner.getName()).resetScore();
        }
    }

    public void cancelPrepareRace(CommandSender sender) {
        sender.sendMessage(Messages.getMessage("race.cancelPrepare"));
        main.preparingRace = false;
        for (Car car : carManager.cars) {
            Player owner = Bukkit.getPlayer(car.getOwner());
            if (owner == null) continue;
            try {
                car.getBoat().remove();
            } catch (Exception ignored) { continue; }
            owner.teleport(owner.getWorld().getSpawnLocation());
        }
    }

    public void endRace() {
        Bukkit.broadcast(Messages.getMessage("race.end"));
        for (Car car : carManager.cars) {
            Player owner = Bukkit.getPlayer(car.getOwner());
            if (owner == null) continue;

            car.destroy();
            main.racers.remove(owner.getUniqueId());

            main.liveSidebar.getScore(owner.getName()).resetScore();
        }

        main.hasRaceStarted=false;
    }

    //ONLY FOR GP 2026
    public void fillRegion(World world,
                           int x1, int y1, int z1,
                           int x2, int y2, int z2,
                           Material material) {

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

}
