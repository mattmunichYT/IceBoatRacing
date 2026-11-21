package fr.mattmunich.iceBoatRacing;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

/** Lets each player hide or restore their own race sidebar. */
public final class SidebarCommand implements BasicCommand {
    private final Main main;

    public SidebarCommand(Main main) {
        this.main = main;
    }

    @Override
    public void execute(@NonNull CommandSourceStack source, String @NonNull [] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("error.playerToExecuteCommand"));
            return;
        }

        boolean hidden = main.liveScoreManager.toggleSidebar(player);
        player.sendMessage(getMessage(hidden ? "sidebar.hidden" : "sidebar.visible"));
    }

    @Override
    public @Nullable String permission() {
        return "iceboatracing.command.sidebar";
    }
}
