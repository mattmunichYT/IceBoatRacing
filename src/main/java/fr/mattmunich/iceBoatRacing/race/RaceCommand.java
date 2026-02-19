package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class RaceCommand implements BasicCommand {
    private final Main main;
    private final RaceManager raceManager;

    public RaceCommand(Main main, RaceManager raceManager) {
        this.main = main;
        this.raceManager = raceManager;
    }

    @Override
    public void execute(@NonNull CommandSourceStack source, String @NonNull [] args) {
        if(args.length == 1 && args[0].equalsIgnoreCase("start")) {
            source.getSender().sendMessage(Messages.getMessage("race.start"));
            raceManager.startRace();
        } else if(args.length == 1 && args[0].equalsIgnoreCase("end")) {
            raceManager.endRace();
        } else if (args.length==1 && args[0].equalsIgnoreCase("prepare")) {
            raceManager.togglePrepareRace(source.getSender());
        } else {
            source.getSender().sendMessage(Messages.getMessage("race.help"));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack commandSourceStack, String @NonNull [] args) {
        return List.of("start","end","prepare");
    }

    @Override
    public boolean canUse(@NonNull CommandSender sender) {
        return BasicCommand.super.canUse(sender);
    }

    @Override
    public @Nullable String permission() {
        return "iceboatracing.command.race";
    }
}
