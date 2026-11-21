package fr.mattmunich.iceBoatRacing;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.mattmunich.iceBoatRacing.race.Race;
import fr.mattmunich.iceBoatRacing.race.RaceManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

/** Small helpers shared by the Brigadier commands. */
public final class CommandUtil {
    private CommandUtil() {}

    /** True if the sender has ANY of the given permissions. */
    public static Predicate<CommandSourceStack> perm(String... permissions) {
        return source -> Arrays.stream(permissions).anyMatch(source.getSender()::hasPermission);
    }

    /** Same as {@link #perm}, but the sender must also be a player. */
    public static Predicate<CommandSourceStack> playerPerm(String... permissions) {
        Predicate<CommandSourceStack> any = perm(permissions);
        return source -> source.getSender() instanceof Player && any.test(source);
    }

    /** Only call this below a node guarded by {@link #playerPerm}. */
    public static Player player(CommandContext<CommandSourceStack> ctx) {
        return (Player) ctx.getSource().getSender();
    }

    /** A word argument that tab-completes race names. */
    public static RequiredArgumentBuilder<CommandSourceStack, String> raceArgument(String argName, RaceManager raceManager) {
        return Commands.argument(argName, StringArgumentType.word()).suggests((ctx, builder) -> suggest(
                raceManager.getRaces().stream().map(Race::getName).toList(), builder));
    }

    /** Resolves the race typed in the given argument, or tells the sender it doesn't exist. */
    public static Race getRace(CommandContext<CommandSourceStack> ctx, String argName, RaceManager raceManager) {
        Race race = raceManager.getRace(StringArgumentType.getString(ctx, argName));
        if (race == null) ctx.getSource().getSender().sendMessage(getMessage("race.notFound"));
        return race;
    }

    public static CompletableFuture<Suggestions> suggest(Collection<String> options, SuggestionsBuilder builder) {
        String input = builder.getRemainingLowerCase();
        options.stream()
                .filter(option -> option != null && option.toLowerCase().startsWith(input))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    public static int help(CommandContext<CommandSourceStack> ctx, String messageKey) {
        ctx.getSource().getSender().sendMessage(getMessage(messageKey));
        return Command.SINGLE_SUCCESS;
    }
}