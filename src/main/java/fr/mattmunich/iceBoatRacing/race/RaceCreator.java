package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.Checkpoint;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.CheckpointCommand;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.CheckpointManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;

import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;
import static fr.mattmunich.iceBoatRacing.Messages.getStringMessage;

public class RaceCreator implements Listener {

    private final Main main;
    private final RaceManager raceManager;
    private final CheckpointManager checkpointManager;
    private final CarManager carManager;

    public RaceCreator(Main main, RaceManager raceManager, CheckpointManager checkpointManager, CarManager carManager) {
        this.main = main;
        this.raceManager = raceManager;
        this.checkpointManager = checkpointManager;
        this.carManager = carManager;
    }

    /**
     * The map that contains players who are creating a race and the stage of the creation process
     */
    Map<Player, Integer> creatingRace = new HashMap<>();
    //Saved data during the process
    /**
     * Saves the name of the race during the creation process
     */
    Map<Player,String> raceName =  new HashMap<>();
    Map<Player,Boolean> firstCheckpointDefined =  new HashMap<>();

    /**
     * 1st position of the checkpoint (like WorldEdit)
     */
    public static final Map<Player, Location> pos1 = new HashMap<>();
    /**
     * 2nd position of the checkpoint (like WorldEdit)
     */
    public static final Map<Player, Location> pos2 = new HashMap<>();

    /**
     * Saves the pos1 and pos2 when using a wooden shovel AND in creator mode
     */
    @EventHandler
    public void onSelect(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getItem().getType() != Material.WOODEN_SHOVEL) return;

        Player p = event.getPlayer();
        //Only run when player is actually creating a race
        if(!creatingRace.containsKey(p) || !creatingRace.get(p).equals(4)) return;

        if(!p.hasPermission("iceboatracing.race.create")) return;

        if(event.getClickedBlock() == null) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            assert clicked != null;
            pos1.put(p, clicked.getLocation());
            p.sendMessage(getMessage("checkpoint.pos.1",formatArguments("x",""+clicked.getX(),"y",""+clicked.getY(),"z",""+clicked.getZ())));
            event.setCancelled(true);
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            assert event.getClickedBlock() != null;
            pos2.put(p, event.getClickedBlock().getLocation());
            p.sendMessage(getMessage("checkpoint.pos.2"));
            event.setCancelled(true);
        }
    }


    /* TODO
    * Plan:
    * - Ask for name
    * - [OPTIONAL] Define checkpoints AND sectors
    * - [OPTIONAL] Define cars
    * Stages/Steps:
    * 1. Name the race
    * 2. Ask if define checkpoints now (yes=>3. ; no=>4.)
    * 3. Define checkpoints (Register race on 1st checkpoint then auto-save on each checkpoint)
    * 4. Ask if define cars now (yes=>5. ; no=>end?)
    * 5. Define cars
    * End (Save)
    *
    * - Continue process
    * - Actually do messages in config
    * */

    /**
     * Start the race creation process
     * @param p the player who will be guided through the process
     */
    public void createRace(Player p) {
        //Stage 1
        creatingRace.put(p, 1);

        Title title = Title.title(
                getMessage("race.create.1.title"),
                getMessage("race.create.1.subtitle",formatArguments(
                        "check",
                        getStringMessage("race.create.1.check")
                ))
        );
        p.showTitle(title);
        p.sendMessage(getMessage("race.create.1.message",formatArguments("check", getStringMessage("car.create.1.check"))));

    }

    @EventHandler
    public void nameRace(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingRace.get(p);
        if (step == null || step != 1) return;

        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();

        Bukkit.getScheduler().runTask(main, () -> {
            if(message.equals(getStringMessage("race.create.1.cancel"))) {
                creatingRace.remove(p);
                p.sendMessage(getMessage("race.create.cancelled"));
                return;
            }

            p.sendMessage(getMessage(
                    "race.create.1.completed",
                    formatArguments("name", message)
            ));

            // Save owner temporarily
            raceName.put(p, message);

            // STEP 2
            creatingRace.replace(p, 2);

            Title title = Title.title(
                    getMessage("race.create.2.title"),
                    getMessage("race.create.2.subtitle")
            );
            p.showTitle(title);
            p.sendMessage(getMessage("race.create.2.message", formatArguments("start",getStringMessage("car.create.2.start"),"later",getStringMessage("car.create.2.later"))));
        });
    }

    @EventHandler
    public void checkIfCheckpointCreation(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingRace.get(p);
        if (step == null || step != 2) return;

        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();
        Bukkit.getScheduler().runTask(main, () -> {
            if (message.equalsIgnoreCase(getStringMessage("race.create.2.start"))) {
                //STEP 3
                creatingRace.replace(p, 3);

                showStage3Help(p);

                firstCheckpointDefined.put(p, false);
            } else if (message.equalsIgnoreCase(getStringMessage("race.create.2.later"))) {
                //SKIP STEP 3 -> STEP 4
                creatingRace.replace(p, 4);

                Title title = Title.title(
                        getMessage("race.create.4.title"),
                        getMessage("race.create.4.subtitle")
                );
                p.showTitle(title);
                p.sendMessage(getMessage("race.create.4.message", formatArguments(
                        "start", getStringMessage("car.create.4.start"),
                        "later", getStringMessage("car.create.4.later")
                )));
            } else if (message.equalsIgnoreCase(getStringMessage("race.create.cancel"))) {
                creatingRace.remove(p);
                raceName.remove(p);

                p.sendMessage(getMessage("race.create.cancelled"));
            } else {
                Title title = Title.title(
                        getMessage("race.create.2.title"),
                        getMessage("race.create.2.subtitle")
                );
                p.showTitle(title);
                p.sendMessage(getMessage("race.create.2.message", formatArguments("start", getStringMessage("car.create.2.start"), "later", getStringMessage("car.create.2.later"))));
            }
        });
    }

    @EventHandler
    public void checkpointCreation(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingRace.get(p);
        if (step == null || step != 4) return;

        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();
        String path = "car.create.3.checkpointCreation.";

        Bukkit.getScheduler().runTask(main, () -> {
            if(message.equalsIgnoreCase(getStringMessage(path + "define"))) {
                defineCheckpoint(p);
            } else if (message.equalsIgnoreCase(getStringMessage(path + "defineSector"))) {
                defineSector(p);
            }  else if (message.equalsIgnoreCase(getStringMessage(path + "defineFinish"))) {
                defineFinish(p);
            }  else if (message.equalsIgnoreCase(getStringMessage(path + "help"))) {
                showStage3Help(p);
            }  else if (message.equalsIgnoreCase(getStringMessage(path + "done"))) {
                String basePath = "race.create.3.feedback.";
                Title title = Title.title(
                        getMessage(basePath + "done.title"),
                        getMessage(basePath + "done.subtitle")
                );
                p.showTitle(title);
                Bukkit.getScheduler().runTaskLater(main, () -> {
                    creatingRace.replace(p, 4);

                    Title titleStage4 = Title.title(
                            getMessage("race.create.4.title"),
                            getMessage("race.create.4.subtitle")
                    );
                    p.showTitle(titleStage4);
                    p.sendMessage(getMessage("race.create.4.message", formatArguments(
                            "start", getStringMessage("car.create.4.start"),
                            "later", getStringMessage("car.create.4.later")
                    )));
                },100L);
            }
        });
    }

    private static void showStage3Help(Player p) {
        String basePath = "race.create.3.";
        Title title = Title.title(
                getMessage(basePath + "title"),
                getMessage(basePath + "subtitle")
        );
        p.showTitle(title);

        String commandsPath = basePath + "checkpointCreation.";
        p.sendMessage(getMessage(basePath + "message", formatArguments(
                "define", getStringMessage(commandsPath + "define"),
                "defineSector", getStringMessage(commandsPath + "defineSector"),
                "defineFinish", getStringMessage(commandsPath + "defineFinish"),
                "help", getStringMessage(commandsPath + "help"),
                "done", getStringMessage(commandsPath + "done")
        )));
    }

    private void defineFinish(Player p) {
        Location l1 = pos1.get(p);
        Location l2 = pos2.get(p);

        if (l1 == null || l2 == null) {
            p.sendMessage(getMessage("checkpoint.pos.notSet"));
            return;
        }

        String basePath = "race.create.3.feedback.";
        if(!firstCheckpointDefined.get(p)) {
            p.sendMessage(getMessage(basePath + "error.defineStartLineFirst"));
            return;
        }

        Race race = new Race(raceName.get(p), l1.getWorld());

        checkpointManager.saveCheckpoint(race, l1, l2, Checkpoint.Type.START_FINISH);
        Title title = Title.title(
                getMessage(basePath + "finishDefined.title"),
                getMessage(basePath + "finishDefined.subtitle")
        );
        p.showTitle(title);
    }

    private void defineSector(Player p) {
        Location l1 = pos1.get(p);
        Location l2 = pos2.get(p);

        if (l1 == null || l2 == null) {
            p.sendMessage(getMessage("checkpoint.pos.notSet"));
            return;
        }

        String basePath = "race.create.3.feedback.";
        if(!firstCheckpointDefined.get(p)) {
            p.sendMessage(getMessage(basePath + "error.defineStartLineFirst"));
            return;
        }

        Race race = new Race(raceName.get(p), l1.getWorld());

        Checkpoint checkpoint = checkpointManager.saveSectorCheckpoint(race, l1, l2);
        Title title = Title.title(
                getMessage(basePath + "sectorDefined.title"),
                getMessage(basePath + "sectorDefined.subtitle",
                        formatArguments("sectorID", "" + checkpoint.getSectorID())
                )
        );
        p.showTitle(title);
    }

    private void defineCheckpoint(Player p) {
        Location l1 = pos1.get(p);
        Location l2 = pos2.get(p);

        if (l1 == null || l2 == null) {
            p.sendMessage(getMessage("checkpoint.pos.notSet"));
            return;
        }

        Race race = new Race(raceName.get(p), l1.getWorld());

        //Register the race first
        if(!firstCheckpointDefined.get(p)) raceManager.saveRace(race);

        Checkpoint checkpoint = checkpointManager.saveCheckpoint(race, l1, l2);
        String basePath = "race.create.3.feedback.";
        Title title;

        if(checkpoint.getType().equals(Checkpoint.Type.START_FINISH)) {
            title = Title.title(
                    getMessage(basePath + "startDefined.title"),
                    getMessage(basePath + "startDefined.subtitle")
            );
            firstCheckpointDefined.put(p, true);
        } else {
            title = Title.title(
                    getMessage(basePath + "checkpointDefined.title"),
                    getMessage(basePath + "checkpointDefined.subtitle",
                            formatArguments("checkpointID", "" + checkpoint.getId())
                    )
            );
        }

        p.showTitle(title);
    }
}
