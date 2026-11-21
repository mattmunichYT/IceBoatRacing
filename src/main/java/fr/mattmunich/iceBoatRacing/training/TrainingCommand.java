package fr.mattmunich.iceBoatRacing.training;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static fr.mattmunich.iceBoatRacing.CommandUtil.*;
import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

/**
 * Handles the {@code /race train [race|leave|restart|ghost]} command tree.
 */
public class TrainingCommand {

    public static final String PERMISSION_TRAIN = "iceboatracing.train";

    private final TrainingManager trainingManager;
    private final RaceManager raceManager;

    public TrainingCommand(TrainingManager trainingManager, RaceManager raceManager) {
        this.trainingManager = trainingManager;
        this.raceManager = raceManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("train")
                .requires(perm(PERMISSION_TRAIN))
                // /race train
                .executes(this::handleDefault)
                // /race train leave
                .then(Commands.literal("leave")
                        .executes(this::handleLeave))
                // /race train restart
                .then(Commands.literal("restart")
                        .executes(this::handleRestart))
                // /race train ghost
                .then(Commands.literal("ghost")
                        .executes(this::handleToggleGhost))
                // /race train <race>
                .then(Commands.argument("race", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            List<String> options = new ArrayList<>(List.of("leave", "restart", "ghost"));
                            options.addAll(raceManager.getRaces().stream().map(Race::getName).toList());
                            return suggest(options, builder);
                        })
                        .executes(this::handleNamed)
                );
    }

    private int handleDefault(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("error.playerToExecuteCommand"));
            return 0;
        }

        List<Race> races = raceManager.getRaces();
        if (races.isEmpty()) {
            player.sendMessage(getMessage("race.notFound"));
            return Command.SINGLE_SUCCESS;
        }

        if (races.size() == 1) {
            trainingManager.startTraining(player, races.getFirst());
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(getMessage("race.help"));
        return Command.SINGLE_SUCCESS;
    }

    private int handleNamed(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("error.playerToExecuteCommand"));
            return 0;
        }

        String raceArg = StringArgumentType.getString(ctx, "race");
        Race race = raceManager.getRace(raceArg);
        if (race == null) {
            player.sendMessage(getMessage("race.notFound"));
            return 0;
        }

        trainingManager.startTraining(player, race);
        return Command.SINGLE_SUCCESS;
    }

    private int handleLeave(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("error.playerToExecuteCommand"));
            return 0;
        }

        trainingManager.leaveTraining(player, true);
        return Command.SINGLE_SUCCESS;
    }

    private int handleRestart(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("error.playerToExecuteCommand"));
            return 0;
        }

        trainingManager.restartTraining(player);
        return Command.SINGLE_SUCCESS;
    }

    private int handleToggleGhost(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("error.playerToExecuteCommand"));
            return 0;
        }

        trainingManager.toggleGhost(player);
        return Command.SINGLE_SUCCESS;
    }
}
