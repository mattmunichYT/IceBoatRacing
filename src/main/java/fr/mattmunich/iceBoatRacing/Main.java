package fr.mattmunich.iceBoatRacing;

import fr.mattmunich.iceBoatRacing.cars.CarCommand;
import fr.mattmunich.iceBoatRacing.cars.CarCreator;
import fr.mattmunich.iceBoatRacing.cars.CarListener;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.listeners.Connection;
import fr.mattmunich.iceBoatRacing.race.*;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.CheckpointCommand;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.CheckpointManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public final class Main extends JavaPlugin {

    CheckpointManager checkpointManager;
    CarManager carManager;
    CarCreator carCreator;
    Messages messages;
    RaceManager raceManager;
    RaceCreator raceCreator;

    public final Map<UUID, RaceData> racers = new HashMap<>();
    public Objective liveSidebar;
    public int raceLapCount = 0;

    @Override
    public void onEnable() {
        log("Enabling plugin...");

        loadConfigs();

        loadMessages();

        loadManagers();

        loadCreators();

        if (registerScoreboard()) return;

        registerCommands();

        registerListeners();

        log("Done enabling plugin!");

    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new CarListener(raceManager),this);
        pm.registerEvents(new CarCreator(this, raceManager, carManager), this);
        pm.registerEvents(new Connection(this,raceManager),this);
        pm.registerEvents(new CheckpointCommand(checkpointManager,raceManager, this),this);
        pm.registerEvents(new RaceListener(this,raceManager),this);
        pm.registerEvents(new RaceCreator(this,raceManager,checkpointManager,carManager,carCreator), this);
    }


    public void loadConfigs() {
        log("Configuring config files");
        saveDefaultConfig();
        reloadConfig();
        saveResource("lang/en_US.yml", true);
        saveResource("lang/fr_FR.yml", true);
        raceLapCount = getConfig().getInt("race.lapCount");
        log("Done configuring config files!");
    }

    void loadMessages() {
        log("Loading messages...");
        messages = new Messages(this);
        log("Done loading messages!");
    }

    private void loadManagers() {
        log("Loading managers...");
        //Preload car and checkpoint manager for racemanager
        carManager = new CarManager(this);
        checkpointManager = new CheckpointManager(this);

        //Load race manager
        raceManager = new RaceManager(this,carManager, checkpointManager);
        log("Done loading managers!");

        //Initalise race manager in car and checkpoint manager
        carManager.setRaceManager(raceManager);
        checkpointManager.setRaceManager(raceManager);
        log("Initiazed RaceManager for Car and Checkpoint managers");

        //Load races after CarManager and CheckpointManager have RaceManager set.
        raceManager.loadAllRaces();
    }

    private void loadCreators() {
        log("Loading car and race creator...");
        carCreator = new CarCreator(this, raceManager, carManager);
        raceCreator = new RaceCreator(this,raceManager,checkpointManager,carManager,carCreator);
        log("Done loading car and race creator!");
    }

    public boolean registerScoreboard() {
        log("Registering scoreboard...");
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = scoreboardManager.getMainScoreboard();
        Objective tempSidebar = scoreboard.getObjective("live_ice_boat_racing");
        if(tempSidebar != null) tempSidebar.unregister();
        try {
            liveSidebar = scoreboard.registerNewObjective("live_ice_boat_racing", Criteria.DUMMY,getMessage("noPrefix.liveSidebarTitle"));
        } catch (IllegalArgumentException ignored) {
            log("Live Sidebar was found, although it should have been unregistered.");
            liveSidebar = scoreboard.getObjective("live_ice_boat_racing");
            if(liveSidebar == null) {
                log("Could not find live sidebar!");
                Bukkit.getPluginManager().disablePlugin(this);
                return true;
            }
            liveSidebar.displayName(getMessage("noPrefix.liveSidebarTitle"));
        }
        if(liveSidebar == null) {
            log("Could not find live sidebar!");
            Bukkit.getPluginManager().disablePlugin(this);
            return true;
        }
        liveSidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        log("Done registering scoreboard!");
        return false;
    }

    private void registerCommands() {
        log("Registering commands...");
        registerCommand("iceboatracing", "Command to manage the plugin", List.of("ibr"), new IBRCommand(this));
        registerCommand("checkpoint", "Command to manage checkpoints", new CheckpointCommand(checkpointManager,raceManager, this));
        registerCommand("car", "Command to manage cars", new CarCommand(carManager,raceManager,carCreator));
        registerCommand("race", "Command to manage the race", new RaceCommand(this, raceManager,raceCreator));
        log("Done registering commands!");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        raceManager.saveAllRaces();
    }

    public void log(String message) {
        Bukkit.getConsoleSender().sendMessage("[IceBoatRacing] " + message);
    }

    public void err(String message, Exception e) { getLogger().severe("[IceBoatRacing] " + message + "\nStacktrace: "  + e.getMessage() + "\n" + Arrays.toString(e.getStackTrace())); }

    public void warn(String message) { getLogger().warning("[IceBoatRacing] " + message); }

    public void severe(String message) { getLogger().severe("[IceBoatRacing] " + message); }

    public static Component c(String message) {
        try {
            return LegacyComponentSerializer.legacySection().deserialize(message);
        } catch (Exception e) {
            return LegacyComponentSerializer.legacySection().deserialize("");
        }
    }

    public static String s(Component component) {
        try {
            return ((TextComponent) component).content();
        } catch (Exception e) {
            return null;
        }

    }

    public static String formatTime(long durationMs) {
        long minutes = durationMs / 60000;
        long seconds = (durationMs / 1000) % 60;
        long milliseconds = durationMs % 1000;
        return minutes + "m" + seconds + "," + milliseconds;
    }
}
