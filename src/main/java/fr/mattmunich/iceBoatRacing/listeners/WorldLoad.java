package fr.mattmunich.iceBoatRacing.listeners;

import fr.mattmunich.iceBoatRacing.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldLoad implements Listener {

    private final Main main;

    public WorldLoad(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        Bukkit.getScheduler().runTask(main, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ibr reload"));
    }
}
