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
        if(args.length == 2 && args[0].equalsIgnoreCase("start")) {
            String raceName = args[1];
            Race race = raceManager.getRace(raceName);
            if(race == null) {
                source.getSender().sendMessage(Messages.getMessage("race.notFound"));
                return;
            }
            source.getSender().sendMessage(Messages.getMessage("race.start", Messages.formatArguments("name", race.getName())));
            raceManager.startRace(race);
        } else if(args.length == 2 && args[0].equalsIgnoreCase("end")) {
            String raceName = args[1];
            Race race = raceManager.getRace(raceName);
            if(race == null) {
                source.getSender().sendMessage(Messages.getMessage("race.notFound"));
                return;
            }
            source.getSender().sendMessage(Messages.getMessage("race.end", Messages.formatArguments("name", race.getName())));
            raceManager.endRace(race);
        } else if (args.length==1 && args[0].equalsIgnoreCase("prepare")) {
            String raceName = args[1];
            Race race = raceManager.getRace(raceName);
            if(race == null) {
                source.getSender().sendMessage(Messages.getMessage("race.notFound"));
                return;
            }
            raceManager.togglePrepareRace(source.getSender(),race);
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
