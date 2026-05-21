package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Messages.formatArguments;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;
import static fr.mattmunich.iceBoatRacing.Messages.getStringMessage;

public class RaceCreator implements Listener {

    private final Main main;

    public RaceCreator(Main main) {
        this.main = main;
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
    /* TODO
    * Plan:
    * - Ask for name
    * - [OPTIONAL] Define checkpoints AND sectors
    * - [OPTIONAL] Define cars
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
            creatingRace.replace(p, 2);
            raceName.put(p, message);

            // STEP 2
            Title title = Title.title(
                    getMessage("race.create.2.title"),
                    getMessage("race.create.2.subtitle")
            );
            p.showTitle(title);
            p.sendMessage(getMessage("race.create.2.message", formatArguments("check",getStringMessage("car.create.2.check"))));
        });
    }
}
