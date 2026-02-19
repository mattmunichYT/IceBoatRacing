package fr.mattmunich.iceBoatRacing;

import fr.mattmunich.iceBoatRacing.cars.CarCommand;
import fr.mattmunich.iceBoatRacing.cars.CarManager;
import fr.mattmunich.iceBoatRacing.race.RaceData;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.CheckpointCommand;
import fr.mattmunich.iceBoatRacing.livescoreboard.checkpoint.CheckpointManager;
import fr.mattmunich.iceBoatRacing.race.RaceCommand;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

public final class Main extends JavaPlugin {

    CheckpointManager checkpointManager;
    CarManager carManager;
    Messages messages;
    RaceManager raceManager;
    public LuckPerms luckPerms;

    public Map<UUID, RaceData> racers = new HashMap<>();
    public Objective liveSidebar;
    public boolean startingRace = false;
    public boolean hasRaceStarted = false;
    public boolean preparingRace = false;

    @Override
    public void onEnable() {
        log("Enabling plugin...");

        loadConfigs();

        loadMessages();

        loadCheckpoints();

        loadCars();

        loadRaceManager();

        if (registerScoreboard()) return;

        registerCommands();

        log("Done enabling plugin!");

        loadLuckPerms();
    }


    public void loadConfigs() {
        log("Configuring config files");
        saveDefaultConfig();
        saveResource("lang/en_US.yml", false);
        saveResource("lang/fr_FR.yml", false);
        log("Done configuring config files!");
    }

    private void loadMessages() {
        log("Loading messages...");
        messages = new Messages(this);
        log("Done loading messages!");
    }

    private void loadCheckpoints() {
        log("Loading checkpoints...");
        saveDefaultConfig();
        checkpointManager = new CheckpointManager(this);
        checkpointManager.loadCheckpoints();
        log("Done loading checkpoints!");
    }

    private void loadCars() {
        log("Loading cars...");
        carManager = new CarManager(this);
        carManager.loadCars();
        log("Done loading cars!");
    }

    private void loadRaceManager() {
        log("Loading race manager...");
        raceManager = new RaceManager(this,carManager);
        log("Done loading race manager!");
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
        registerCommand("iceboatracing", "Command to manage the plugin", List.of("ibr"), new IBRCommand(this,messages));
        registerCommand("checkpoint", "Command to manage checkpoints", new CheckpointCommand(checkpointManager,this));
        registerCommand("car", "Command to manage cars", new CarCommand(this,carManager));
        registerCommand("race", "Command to manage the race", new RaceCommand(this,raceManager));
        log("Done registering commands!");
    }

    private void loadLuckPerms() {
        Bukkit.getScheduler().runTask(this, () -> {
            log("Loading LuckPerms dependency");
            RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                luckPerms = provider.getProvider();
                log("Done registering LuckPerms dependency!");
            }else {
                getLogger().warning("LuckPerms provider was null!");
            }
        });
    }



    @Override
    public void onDisable() {
        super.onDisable();
    }

    public void log(String message) {
        Bukkit.getConsoleSender().sendMessage("[IceBoatRacing] " + message);
    }

    public static Component c(String message) {
        return LegacyComponentSerializer.legacySection().deserialize(message);
    }

    public static String s(Component component) { return ((TextComponent) component).content(); }

    public static String formatTime(long durationMs) {
        long minutes = durationMs / 60000;
        long seconds = (durationMs / 1000) % 60;
        long milliseconds = durationMs % 1000;
        return minutes + "m" + seconds + "," + milliseconds;
    }
}
