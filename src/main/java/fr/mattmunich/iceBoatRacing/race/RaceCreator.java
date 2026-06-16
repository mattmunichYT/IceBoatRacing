package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.cars.CarCreator;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.Checkpoint;
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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;
import static fr.mattmunich.iceBoatRacing.Messages.getStringMessage;

@SuppressWarnings("unused")
public class RaceCreator implements Listener {

    private final Main main;
    private final RaceManager raceManager;
    private final CheckpointManager checkpointManager;
    private final CarCreator carCreator;

    public RaceCreator(Main main, RaceManager raceManager, CheckpointManager checkpointManager, CarManager carManager, CarCreator carCreator) {
        this.main = main;
        this.raceManager = raceManager;
        this.checkpointManager = checkpointManager;
        this.carCreator = carCreator;
    }

    /**
     * The map that contains players who are creating a race and the stage of the creation process
     */
    public static final Map<Player, Integer> creatingRace = new HashMap<>();
    //Saved data during the process
    /**
     * Saves the name of the race during the creation process
     */
    static final Map<Player,String> raceName =  new HashMap<>();
    /**
     * Save wether the player has already created a checkpoint
     * (-> should next checkpoint be start line?)
     */
    static final Map<Player,Boolean> firstCheckpointDefined =  new HashMap<>();


    /**
     * When player wants to cancel the creation process of a race, it will be put in this list.
     * A listener will then await a confirmation message.
     */
    static final List<Player> confirmRaceCancel = new ArrayList<>();

    /**
     * The list the player will be put into, to be able to identify if they are using this
     * class (RaceCreator) to create cars. This will automatically ask them if they want to
     * rerun the /car create command when they're done creating a car. This will allow
     * CarCommand to identify players who are defining cars while creating the race.
     * @apiNote Work in progress
     */
    public static Map<Player, String> definingCars = new HashMap<>();

    /**
     * 1st position of the checkpoint (like WorldEdit)
     */
    static final Map<Player, Location> pos1 = new HashMap<>();
    /**
     * 2nd position of the checkpoint (like WorldEdit)
     */
    static final Map<Player, Location> pos2 = new HashMap<>();

    /**
     * Saves the pos1 and pos2 when using a wooden shovel AND in creator mode
     */
    @EventHandler
    public void onSelect(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getItem().getType() != Material.WOODEN_SHOVEL) return;

        Player p = event.getPlayer();
        //Only run when player is actually creating a race
        if(!creatingRace.containsKey(p) || !creatingRace.get(p).equals(3)) return;

        if(!p.hasPermission("iceboatracing.race.create")) return;

        Block clicked = event.getClickedBlock();

        if(clicked == null) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(p, clicked.getLocation());
            p.sendMessage(getMessage("checkpoint.pos.1",formatArguments("x",""+clicked.getX(),"y",""+clicked.getY(),"z",""+clicked.getZ())));
            event.setCancelled(true);
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2.put(p, clicked.getLocation());
            p.sendMessage(getMessage("checkpoint.pos.2",formatArguments("x",""+clicked.getX(),"y",""+clicked.getY(),"z",""+clicked.getZ())));
            event.setCancelled(true);
        }
    }


    /* TODO Race creation:
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
    * The actual things to do:
    * - Add support to add cars
    * - Custom lap count for each race
    * -> Define to 1 if define finish =/= start
    * - After defining finish => end checkpoint creation process
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
                getMessage("race.create.1.subtitle")
        );
        p.showTitle(title);
        p.sendMessage(getMessage("race.create.1.message"));

    }

    @EventHandler
    public void nameRace(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingRace.get(p);
        if (step == null || step != 1) return;

        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();

        Bukkit.getScheduler().runTask(main, () -> {
            //Allow to cancel process
            if(message.equals(getStringMessage("race.create.cancel"))) {
                creatingRace.remove(p);
                p.sendMessage(getMessage("race.create.cancelled"));
                return;
            }

            //Check if a race with the same name already exists
            final boolean[] exists = {false};
            raceManager.getRaces().forEach(race -> {if(race.getName().equalsIgnoreCase(message)) exists[0]=true;});

            if(exists[0]) {
                p.sendMessage(getMessage("race.create.1.alreadyExists"));
                return;
            }

            // Save race name temporarily
            raceName.put(p, message);

            p.sendMessage(getMessage(
                    "race.create.1.completed",
                    formatArguments("name", message)
            ));

            // STEP 2
            Bukkit.getScheduler().runTaskLater(main, () -> {
                creatingRace.replace(p, 2);

                Title title = Title.title(
                        getMessage("race.create.2.title"),
                        getMessage("race.create.2.subtitle")
                );
                p.showTitle(title);
                String start = getStringMessage("race.create.2.start");
                String later = getStringMessage("race.create.2.later");
                p.sendMessage(getMessage("race.create.2.message", formatArguments("start", start,"later", later)));
            },40L);
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

                showStep3Info(p);
                p.give(new ItemStack(Material.WOODEN_SHOVEL));

                firstCheckpointDefined.put(p, false);
            } else if (message.equalsIgnoreCase(getStringMessage("race.create.2.later"))) {
                //SKIP STEP 3 -> STEP 4
                creatingRace.replace(p, 4);

                showStep4(p);
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
                p.sendMessage(getMessage("race.create.2.message", formatArguments("start", getStringMessage("race.create.2.start"), "later", getStringMessage("race.create.2.later"))));
            }
        });
    }

    private static void showStep4(Player p) {
        Title title = Title.title(
                getMessage("race.create.4.title"),
                getMessage("race.create.4.subtitle")
        );
        p.showTitle(title);
        p.sendMessage(getMessage("race.create.4.message", formatArguments(
                "start", getStringMessage("race.create.4.start"),
                "later", getStringMessage("race.create.4.later")
        )));
    }

    @EventHandler
    public void checkpointCreation(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingRace.get(p);
        if (step == null || step != 3) return;

        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();
        String path = "race.create.3.checkpointCreation.";

        Bukkit.getScheduler().runTask(main, () -> {
            if(message.equalsIgnoreCase(getStringMessage(path + "define"))) {
                defineCheckpoint(p);
            } else if (message.equalsIgnoreCase(getStringMessage(path + "defineSector"))) {
                defineSector(p);
            }  else if (message.equalsIgnoreCase(getStringMessage(path + "defineFinish"))) {
                defineFinish(p);
            }  else if (message.equalsIgnoreCase(getStringMessage(path + "help"))) {
                showStep3Info(p);
            }  else if (message.equalsIgnoreCase(getStringMessage(path + "done"))) {
                String basePath = "race.create.3.feedback.";
                Title title = Title.title(
                        getMessage(basePath + "done.title"),
                        getMessage(basePath + "done.subtitle")
                );
                p.showTitle(title);

                firstCheckpointDefined.remove(p);

                Bukkit.getScheduler().runTaskLater(main, () -> {
                    creatingRace.replace(p, 4);

                    showStep4(p);
                },100L);
            }
        });
    }

    @EventHandler
    public void checkIfCarCreation(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingRace.get(p);
        if (step == null || step != 4) return;
        if(confirmRaceCancel.contains(p)) return;

        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();
        Bukkit.getScheduler().runTask(main, () -> {

            String basePath = "race.create.4";
            if (message.equalsIgnoreCase(getStringMessage(basePath + ".start"))) {
                //STEP 5
                creatingRace.replace(p, 5);

                Title title = Title.title(
                        getMessage("race.create.5.title"),
                        getMessage("race.create.5.subtitle")
                );
                p.showTitle(title);
                p.sendMessage(getMessage("race.create.5.message", formatArguments(
                        "done", getStringMessage("race.create.5.carCreation.done")
                )));

                definingCars.put(p, raceName.get(p));
                Bukkit.getScheduler().runTaskLater(main, () -> carCreator.createCar(p) ,40L);
                Bukkit.getScheduler().runTaskTimer(main, task -> {
                    if(!definingCars.containsKey(p)) task.cancel();

                    p.sendActionBar(getMessage("race.create.5.feedback.endInstructions.actionBar", formatArguments(
                            "done", getStringMessage("race.create.5.carCreation.done")
                    )));
                } ,100L, 40L);
            } else if (message.equalsIgnoreCase(getStringMessage(basePath + ".later"))) {
                //SKIP STEP 5 -> END
                endRaceCreation(p);
            } else if (message.equalsIgnoreCase(getStringMessage("race.create.cancel"))) {
                confirmRaceCancel.add(p);
                p.sendMessage(getMessage("race.create.confirmCancel", formatArguments(
                        "confirm", getStringMessage("race.create.confirm"),
                        "cancel", getStringMessage("race.create.cancel")
                )));
            } else {
                Title title = Title.title(
                        getMessage(basePath + ".title"),
                        getMessage(basePath + ".subtitle")
                );
                p.showTitle(title);
                p.sendMessage(getMessage(basePath + ".message", formatArguments("start", getStringMessage(basePath + ".start"), "later", getStringMessage(basePath + ".later"))));
            }
        });
    }

    @EventHandler
    public void onFinishDefinigCars(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingRace.get(p);
        if (step == null || step != 5) return;

        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();
        if(!message.equalsIgnoreCase(getStringMessage("race.create.5.carCreation.done"))) return;

        Bukkit.getScheduler().runTask(main, () -> {
            Title title = Title.title(
                    getMessage("race.create.5.feedback.done.title"),
                    getMessage("race.create.5.feedback.done.subtitle")
            );
            p.showTitle(title);

            definingCars.remove(p);
            carCreator.cleanup(p);

            Bukkit.getScheduler().runTaskLater(main ,() -> endRaceCreation(p), 60L);
        });
    }

    private void endRaceCreation(Player p) {
        creatingRace.remove(p);
        raceName.remove(p);
        pos1.remove(p);
        pos2.remove(p);

        String donePath = "race.create.done.";
        Title title = Title.title(
                getMessage( donePath + "title"),
                getMessage(donePath + "subtitle")
        );
        p.showTitle(title);
        p.sendMessage(getMessage(donePath + "message"));
    }

    @EventHandler
    public void confirmRaceCancel(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if(!confirmRaceCancel.contains(p)) return;
        confirmRaceCancel.remove(p);

        String message = ((TextComponent) e.message()).content();
        if(message.equalsIgnoreCase(getStringMessage("race.create.confirm"))) {
            Race race = raceManager.getRace(raceName.get(p));

            if (race == null) {
                p.sendMessage(getMessage("error.unknown"));
                main.severe("Couldn't delete race " + raceName.get(p) + " because it didn't exist (race creating process cancel)");
                return;
            }

            raceManager.deleteRace(race);
            creatingRace.remove(p);
            raceName.remove(p);

            p.sendMessage(getMessage("race.create.cancelled"));
        } else {
            showStep4(p);
        }
    }

    private static void showStep3Info(Player p) {
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

        Race race = raceManager.getRace(raceName.get(p));

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

        //Be sure that the car is detected, even when you click the ground when defining checkpoints
        if(l1.getY() == l2.getY()) l2.add(0, 1, 0);

        String basePath = "race.create.3.feedback.";
        if(!firstCheckpointDefined.get(p)) {
            p.sendMessage(getMessage(basePath + "error.defineStartLineFirst"));
            return;
        }

        Race race = raceManager.getRace(raceName.get(p));

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

        //Be sure that the car is detected, even when you click the ground when defining checkpoints
        if(l1.getY() == l2.getY()) l2.add(0, 1, 0);

        Race race;

        //Register the race first
        if(!firstCheckpointDefined.get(p)) {
            race = new Race(raceName.get(p), l1.getWorld());
            race = raceManager.createRace(race);
        } else {
            race = raceManager.getRace(raceName.get(p));
        }

        if(race==null) {
            p.sendMessage(getMessage("error.unknown"));
            return;
        }

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

        boolean result = raceManager.saveRace(race);
        if(!result) {
            p.sendMessage(getMessage("error.unknown"));
            return;
        }
        p.showTitle(title);
    }
}
