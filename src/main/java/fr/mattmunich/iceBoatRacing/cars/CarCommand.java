package fr.mattmunich.iceBoatRacing.cars;

import fr.mattmunich.iceBoatRacing.Main;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static fr.mattmunich.iceBoatRacing.Main.c;
import static fr.mattmunich.iceBoatRacing.Messages.*;

public class CarCommand implements BasicCommand, Listener {

    private final Main main;
    private final CarManager carManager;
    private static final Map<Player, Integer> creatingCar = new HashMap<>();
    private static final Map<Player, Location> brokenBlock = new HashMap<>();
    private static final Map<Player, Location> carLocation = new HashMap<>();
    private static final Map<Player, UUID> tempOwner = new HashMap<>();

    public CarCommand(Main main, CarManager carManager) {
        this.main = main;
        this.carManager = carManager;
    }

    @Override
    public void execute(@NonNull CommandSourceStack source, String @NonNull [] args) {
        if(!(source.getSender() instanceof Player p)) {
            source.getSender().sendMessage(getMessage("error.playerToExecuteCommand"));
            return;
        }

        if(args.length == 1 && args[0].equalsIgnoreCase("create")) {
            Title title = Title.title(
                    getMessage("car.create.1.title"),
                    getMessage("car.create.1.subtitle",formatArguments(
                            "check",
                            getStringMessage("car.create.1.check")
                    ))
            );
            p.showTitle(title);
            p.sendMessage(getMessage("car.create.1.message",formatArguments("check", getStringMessage("car.create.1.check"))));
            creatingCar.put(p,1);
        } if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            if (carManager.getAll().isEmpty()) {
                p.sendMessage(getMessage("car.noCars"));
                return;
            }

            p.sendMessage(c("§bCars:"));

            for (Car car : carManager.getAll()) {

                Location loc = car.getStartingLocation();

                String ownerName = "Unknown";
                OfflinePlayer owner = Bukkit.getOfflinePlayer(car.getOwner());
                if (owner.getName() != null) {
                    ownerName = owner.getName();
                }

                // [x] REMOVE
                Component removeText = c("§c[x]");
                removeText = removeText.clickEvent(
                        ClickEvent.runCommand("/car remove " + car.getId())
                );

                // [→] TP
                Component tpText = c("§a[→]");
                tpText = tpText.clickEvent(
                        ClickEvent.runCommand(
                                "/tp "
                                        + loc.getBlockX() + " "
                                        + loc.getBlockY() + " "
                                        + loc.getBlockZ()
                        )
                );

                // [✎] CHANGE OWNER (suggest command)
                Component changeOwnerText = c("§e[✎]");
                changeOwnerText = changeOwnerText.clickEvent(
                        ClickEvent.suggestCommand(
                                "/car changeOwner " + car.getId() + " "
                        )
                );

                // Main line
                Component line = c(
                        "§e- #" + car.getId()
                                + " §7Owner: §f" + ownerName
                                + " §7Type: §f" + car.getBoatMaterial().name().replace("_BOAT", " ").replace("_RAFT","").toLowerCase()
                                + " §7[" + loc.getWorld().getName() + "] "
                                + "§fX: " + loc.getBlockX()
                                + ", Y: " + loc.getBlockY()
                                + ", Z: " + loc.getBlockZ()
                )
                        .append(c(" §8 "))
                        .append(removeText)
                        .append(c(" "))
                        .append(tpText)
                        .append(c(" "))
                        .append(changeOwnerText);

                p.sendMessage(line);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("changeOwner")) {
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                p.sendMessage(getMessage("error.invalidNumber"));
                return;
            }

            Car car = carManager.get(id);
            if(car == null) {
                p.sendMessage(getMessage("car.invalidID",formatArguments("id", String.valueOf(id))));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if(!target.hasPlayedBefore()) {
                p.sendMessage(getMessage("error.playerNotFound"));
                return;
            }

            carManager.changeOwner(car,target.getUniqueId());
            p.sendMessage(getMessage("car.changedOwner",formatArguments("owner", target.getName())));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                p.sendMessage(getMessage("error.invalidNumber"));
                return;
            }

            Car car = carManager.get(id);
            if(car == null) {
                p.sendMessage(getMessage("car.invalidID",formatArguments("id", String.valueOf(id))));
                return;
            }

            carManager.remove(car);
            p.sendMessage(getMessage("car.removed",formatArguments("id", String.valueOf(id))));
        } else {
            p.sendMessage(c("§aCar commands:"));
            p.sendMessage(c("§7- §f/checkpoint list"));
            p.sendMessage(c("§7- §f/checkpoint create"));
            p.sendMessage(c("§7- §f/checkpoint changeOwner"));
            p.sendMessage(c("§7- §f/checkpoint remove"));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack source, String @NonNull [] args) {
        ArrayList<String> suggestions = new ArrayList<>();

        if(args.length == 1) {
            suggestions.add("create");
            suggestions.add("list");
            suggestions.add("changeOwner");
            suggestions.add("remove");
        }
        if(!suggestions.isEmpty() && !args[args.length - 1].isEmpty()) {
            suggestions.removeIf(s -> !s.startsWith(args[args.length - 1]));
        }
        return suggestions;
    }

    @Override
    public boolean canUse(@NonNull CommandSender sender) {
        return sender.hasPermission("iceboatracing.command.car") && sender instanceof Player;
    }

    @Override
    public @Nullable String permission() {
        return "iceboatracing.command.car";
    }

    @EventHandler
    public void setCarSpawnLocation(PlayerInteractEvent e) {
        if(!e.getAction().equals(Action.LEFT_CLICK_BLOCK)) return;
        Player p = e.getPlayer();
        Integer step = creatingCar.get(p);
        if (step == null || step != 1) return;
        e.setCancelled(true);
        if(e.getClickedBlock() == null) return;
        brokenBlock.put(p, e.getClickedBlock().getLocation().add(0,1,0));
        p.sendMessage(getMessage("car.create.1.selected",formatArguments("check",getStringMessage("car.create.1.check"))));
    }

    @EventHandler
    public void confirmCarSpawnLocation(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingCar.get(p);
        if (step == null || step != 1) return;
        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();
        Bukkit.getScheduler().runTask(main, () -> {
            if(!message.equalsIgnoreCase(getStringMessage("car.create.1.check"))) {
                Title title = Title.title(
                        getMessage("car.create.1.title"),
                        getMessage("car.create.1.subtitle",formatArguments(
                                "check",
                                getStringMessage("car.create.1.check")
                        ))
                );
                p.showTitle(title);
                p.sendMessage(getMessage("car.create.1.message",formatArguments("check", getStringMessage("car.create.1.check"))));
                return;
            }

            if(brokenBlock.get(p)==null) {
                p.sendMessage(getMessage("car.create.1.noSelection"));
            }

            carLocation.put(p, brokenBlock.get(p));
            brokenBlock.remove(p);
            p.sendMessage(getMessage("car.create.1.completed"));

            //STEP 2
            Title title = Title.title(getMessage("car.create.2.title"),c(""));
            p.showTitle(title);
            p.sendMessage(getMessage("car.create.2.message"));
            creatingCar.replace(p, 2);
        });
    }

    @EventHandler
    public void setOwnerName(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingCar.get(p);
        if (step == null || step != 2) return;

        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();

        Bukkit.getScheduler().runTask(main, () -> {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(message);
            if (!owner.hasPlayedBefore()) {
                p.sendMessage(getMessage("error.playerNotFound"));
                return;
            }

            p.sendMessage(getMessage(
                    "car.create.2.completed",
                    formatArguments("owner", owner.getName())
            ));

            // Save owner temporarily
            creatingCar.replace(p, 3);
            tempOwner.put(p, owner.getUniqueId());

            // STEP 3
            Title title = Title.title(
                    getMessage("car.create.3.title"),
                    c("")
            );
            p.showTitle(title);
            p.sendMessage(getMessage("car.create.3.message", formatArguments("check",getStringMessage("car.create.3.check"))));
        });
    }
    @EventHandler
    public void selectBoatType(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Integer step = creatingCar.get(p);
        if (step == null || step != 3) return;

        e.setCancelled(true);
        String message = ((TextComponent) e.message()).content();

        Bukkit.getScheduler().runTask(main, () -> {
            if (!message.equalsIgnoreCase(
                    getStringMessage("car.create.3.check")
            )) {
                p.sendMessage(getMessage("car.create.3.message", formatArguments("check",getStringMessage("car.create.3.check"))));
                return;
            }

            ItemStack held = p.getInventory().getItemInMainHand();

            if (!held.getType().name().endsWith("_BOAT") && !held.getType().name().endsWith("_RAFT")) {
                p.sendMessage(getMessage("car.create.3.invalidItem"));
                return;
            }

            // FINAL SAVE
            carManager.saveCar(
                    tempOwner.get(p),
                    carLocation.get(p),
                    held
            );

            p.sendMessage(getMessage("car.create.completed.message"));
            Title title = Title.title(
                    getMessage("car.create.completed.title"),
                    getMessage("car.create.completed.subtitle")
            );
            p.showTitle(title);

            // cleanup
            creatingCar.remove(p);
            tempOwner.remove(p);
            carLocation.remove(p);
        });
    }
}
