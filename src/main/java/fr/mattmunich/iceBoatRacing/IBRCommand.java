package fr.mattmunich.iceBoatRacing;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scoreboard.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;
import static fr.mattmunich.iceBoatRacing.Messages.getStringMessage;

public class IBRCommand implements BasicCommand {

    private final Main main;

    public IBRCommand(Main main) {
        this.main = main;
    }

    @Override
    public void execute(@NonNull CommandSourceStack source, String @NonNull [] args) {
        CommandSender s = source.getSender();
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!s.hasPermission("iceBoatRacing.reload")) return;
            s.sendMessage(getMessage("prefix").append(c(" §b§oReloading...")));

            main.loadConfigs();                // reloads config.yml from disk
            main.loadMessages();               // reloads lang files

            // Reload in-memory data from the freshly loaded config
            Bukkit.getScheduler().runTask(main, () -> {
                main.checkpointManager.loadCheckpoints();
                main.carManager.loadCars();
            });

            main.registerScoreboard();
            s.sendMessage(getMessage("prefix").append(c(" §bDone reloading!")));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
            s.sendMessage(
                    getStringMessage("prefix")
                    + "§3---§b Ice Boat Racing §3---\n"
                    + "§3Created by§b mattmunich\n"
                    + "§3Created for §bGP 2026§3 of§b Mini Jeux Entre Potes\n "
            );
        } else {
            s.sendMessage(
                    getStringMessage("prefix")
                    + "§3---§b /iceboatracing §3---\n"
                    + "§3- reload §7- §bReloads the plugin\n "
                    + "§3- info   §7- §bSome basic info\n"
            );
        }

    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack commandSourceStack, String @NonNull [] args) {
        return List.of("reload","info");
    }

    @Override
    public boolean canUse(@NonNull CommandSender sender) {
        return BasicCommand.super.canUse(sender);
    }

    @Override
    public @Nullable String permission() {
        return "iceboatracing.command.plugin";
    }
}
