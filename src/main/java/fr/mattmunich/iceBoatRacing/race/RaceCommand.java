package fr.mattmunich.iceBoatRacing.race;

import fr.mattmunich.iceBoatRacing.Main;
import fr.mattmunich.iceBoatRacing.Messages;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RaceCommand implements BasicCommand {
    private final Main main;
    private final RaceManager raceManager;
    private final RaceCreator raceCreator;

    public RaceCommand(Main main, RaceManager raceManager, RaceCreator raceCreator) {
        this.main = main;
        this.raceManager = raceManager;
        this.raceCreator = raceCreator;
    }

    @Override
    public void execute(@NonNull CommandSourceStack source, String @NonNull [] args) {
        CommandSender sender = source.getSender();
        if(args.length >= 1 && args[0].equalsIgnoreCase("start")) {
            Race race;
            if(raceManager.races.size() != 1) {
                if(args.length!=2) {
                    sender.sendMessage(Messages.getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
            } else {
                race = raceManager.races.getFirst();
            }
            if(race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }
            sender.sendMessage(Messages.getMessage("race.start", Messages.formatArguments("race", race.getName())));
            race.start();
        } else if(args.length >= 1 && args[0].equalsIgnoreCase("end")) {
            Race race;
            if(raceManager.activeRaces.size() != 1) {
                if(args.length!=2) {
                    sender.sendMessage(Messages.getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
            } else {
                race = raceManager.activeRaces.getFirst();
            }
            if(race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            try {
                race.end(true);
            } catch (Exception e) {
                switch (e.getMessage()) {
                    case "NO_RANKING" -> sender.sendMessage(Messages.getMessage("race.onFinish.error.noRanking"));
                    case "NO_RACERS" -> sender.sendMessage(Messages.getMessage("race.onFinish.error.noRacers"));
                    default -> sender.sendMessage(Messages.getMessage("error.unknown"));
                }
            }
        } else if (args.length >=1 && args[0].equalsIgnoreCase("prepare")) {
            Race race;
            if(raceManager.races.size() != 1) {
                if(args.length!=2) {
                    sender.sendMessage(Messages.getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
            } else {
                race = raceManager.races.getFirst();
            }
            if(race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }
            race.togglePrepare(sender);
        } else if (args.length == 1 && args[0].equalsIgnoreCase("create")) {
            if(!(sender instanceof Player p)) {
                sender.sendMessage(Messages.getMessage("error.playerToExecuteCommand"));
                return;
            }
            raceCreator.createRace(p);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("delete")) {
            Race race;
            if(raceManager.races.size() != 1) {
                if(args.length!=2) {
                    sender.sendMessage(Messages.getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
            } else {
                race = raceManager.races.getFirst();
            }
            if(race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            String raceName = race.getName();
            Bukkit.getScheduler().runTask(main, () -> {
                boolean success = raceManager.deleteRace(race);
                if (success) sender.sendMessage(Messages.getMessage("race.deleted", Messages.formatArguments("race", raceName)));
                else sender.sendMessage(Messages.getMessage("error.unknown"));
            });
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("setLapCount")) {
            Race race;
            boolean definedRaceName = false;
            if(raceManager.races.size() != 1) {
                if(args.length < 2 || args.length > 3) {
                    sender.sendMessage(Messages.getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
                definedRaceName = true;
            } else {
                race = raceManager.races.getFirst();
            }
            if(race == null) {
                sender.sendMessage(Messages.getMessage("race.notFound"));
                return;
            }

            if (definedRaceName) {
                if(args.length == 2) {
                    sender.sendMessage(Messages.getMessage("race.currentLapCount", Messages.formatArguments(
                            "race", race.getName(),
                            "lapCount", race.getLapCount()
                    )));
                    return;
                }

                int lapCount;
                try {
                    lapCount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Messages.getMessage("race.invalidLapCount"));
                    return;
                }

                race.setLapCount(lapCount);
                sender.sendMessage(Messages.getMessage("race.updatedLapCount", Messages.formatArguments(
                        "race", race.getName(),
                        "lapCount", race.getLapCount()
                )));
            } else {
                if(args.length == 1) {
                    sender.sendMessage(Messages.getMessage("race.currentLapCount", Messages.formatArguments(
                            "race", race.getName(),
                            "lapCount", race.getLapCount()
                    )));
                    return;
                }

                int lapCount;
                try {
                    lapCount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Messages.getMessage("race.invalidLapCount"));
                    return;
                }

                race.setLapCount(lapCount);
                sender.sendMessage(Messages.getMessage("race.updatedLapCount", Messages.formatArguments(
                        "race", race.getName(),
                        "lapCount", race.getLapCount()
                )));
            }
        } else {
            sender.sendMessage(Messages.getMessage("race.help"));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack commandSourceStack, String @NonNull [] args) {
        List<Race> races =  raceManager.getRaces();
        List<String> raceList;
        if(!races.isEmpty()) {
            raceList = new ArrayList<>();
            races.forEach((race) -> raceList.add(race.getName()));
        } else raceList = List.of("§oNo races defined");

        List<String> baseList = List.of("start","prepare","end","create","delete", "setLapCount");
        if(args.length==0) {
            return baseList;
        } else if(args.length==1) {
            List<String> suggestions = new ArrayList<>();
            baseList.forEach((suggestion) -> {
                if(args[0] != null && suggestion.contains(args[0])) {
                    suggestions.add(suggestion);
                }
            });
            return suggestions;
        } else if(args.length==2
                && (
                    args[0].equalsIgnoreCase("start")
                    || args[0].equalsIgnoreCase("prepare")
                    || args[0].equalsIgnoreCase("end")
                    || args[0].equalsIgnoreCase("delete")
                    || args[0].equalsIgnoreCase("setLapCount")
            )
        ) {
            return raceList;
        } else if (args.length==3) {
            if(args[0].equalsIgnoreCase("setLapCount")) {
                return List.of("§7<lapCount>");
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
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
