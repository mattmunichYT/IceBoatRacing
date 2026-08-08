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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static fr.mattmunich.iceBoatRacing.Messages.getMessage;

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
                    sender.sendMessage(getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
            } else {
                race = raceManager.races.getFirst();
            }
            if(race == null) {
                sender.sendMessage(getMessage("race.notFound"));
                return;
            }
            sender.sendMessage(getMessage("race.start", Messages.formatArguments("race", race.getName())));
            race.start();
        } else if(args.length >= 1 && args[0].equalsIgnoreCase("end")) {
            Race race;
            if(raceManager.activeRaces.size() != 1) {
                if(args.length!=2) {
                    sender.sendMessage(getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
            } else {
                race = raceManager.activeRaces.getFirst();
            }
            if(race == null) {
                sender.sendMessage(getMessage("race.notFound"));
                return;
            }

            try {
                race.end(true);
            } catch (Exception e) {
                switch (e.getMessage()) {
                    case "NO_RANKING" -> sender.sendMessage(getMessage("race.onEnd.error.noRanking"));
                    case "NO_RACERS" -> sender.sendMessage(getMessage("race.onEnd.error.noRacers"));
                    default -> {
                        sender.sendMessage(getMessage("error.unknown"));
                        main.err("Could not send ranking on /race end", e);
                    }
                }
            }
        } else if (args.length >=1 && args[0].equalsIgnoreCase("prepare")) {
            Race race;
            if(raceManager.races.size() != 1) {
                if(args.length!=2) {
                    sender.sendMessage(getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
            } else {
                race = raceManager.races.getFirst();
            }
            if(race == null) {
                sender.sendMessage(getMessage("race.notFound"));
                return;
            }
            race.togglePrepare(sender);
        } else if (args.length == 1 && args[0].equalsIgnoreCase("create")) {
            if(!(sender instanceof Player p)) {
                sender.sendMessage(getMessage("error.playerToExecuteCommand"));
                return;
            }
            raceCreator.createRace(p);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("delete")) {
            Race race;
            if(raceManager.races.size() != 1) {
                if(args.length!=2) {
                    sender.sendMessage(getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
            } else {
                race = raceManager.races.getFirst();
            }
            if(race == null) {
                sender.sendMessage(getMessage("race.notFound"));
                return;
            }

            String raceName = race.getName();
            Bukkit.getScheduler().runTask(main, () -> {
                boolean success = raceManager.deleteRace(race);
                if (success) sender.sendMessage(getMessage("race.deleted", Messages.formatArguments("race", raceName)));
                else sender.sendMessage(getMessage("error.unknown"));
            });
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("setLapCount")) {
            Race race;
            boolean definedRaceName = false;
            if(raceManager.races.size() != 1 || args.length == 3) {
                if(args.length < 2 || args.length > 3) {
                    sender.sendMessage(getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
                definedRaceName = true;
            } else {
                race = raceManager.races.getFirst();
            }
            if(race == null) {
                sender.sendMessage(getMessage("race.notFound"));
                return;
            }

            if (definedRaceName) {
                if(args.length == 2) {
                    sender.sendMessage(getMessage("race.currentLapCount", Messages.formatArguments(
                            "race", race.getName(),
                            "lapCount", race.getLapCount()
                    )));
                    return;
                }

                int lapCount;
                try {
                    lapCount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(getMessage("race.invalidLapCount"));
                    return;
                }

                Bukkit.getScheduler().runTask(main, () -> {
                    race.setLapCount(lapCount);
                    try {
                        race.saveConfig();
                    } catch (IOException e) {
                        sender.sendMessage(getMessage("error.unkown"));
                        return;
                    }
                    sender.sendMessage(getMessage("race.updatedLapCount", Messages.formatArguments(
                            "race", race.getName(),
                            "lapCount", race.getLapCount()
                    )));
                });
            } else {
                if(args.length == 1) {
                    sender.sendMessage(getMessage("race.currentLapCount", Messages.formatArguments(
                            "race", race.getName(),
                            "lapCount", race.getLapCount()
                    )));
                    return;
                }

                int lapCount;
                try {
                    lapCount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(getMessage("race.invalidLapCount"));
                    return;
                }

                Bukkit.getScheduler().runTask(main, () -> {
                    race.setLapCount(lapCount);
                    try {
                        race.saveConfig();
                    } catch (IOException e) {
                        sender.sendMessage(getMessage("error.unkown"));
                        return;
                    }
                    sender.sendMessage(getMessage("race.updatedLapCount", Messages.formatArguments(
                            "race", race.getName(),
                            "lapCount", race.getLapCount()
                    )));
                });
            }
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("setPitStops")) {
            Race race;
            boolean definedRaceName = false;
            if (raceManager.races.size() != 1 || args.length == 3) {
                if (args.length < 2 || args.length > 3) {
                    sender.sendMessage(getMessage("race.help"));
                    return;
                }
                String raceName = args[1];
                race = raceManager.getRace(raceName);
                definedRaceName = true;
            } else {
                race = raceManager.races.getFirst();
            }
            if (race == null) {
                sender.sendMessage(getMessage("race.notFound"));
                return;
            }

            Race finalRace = race;
            int valueIndex = definedRaceName ? 2 : 1;

            if (args.length <= valueIndex) {
                sender.sendMessage(getMessage("race.currentPitStops", Messages.formatArguments(
                        "race", finalRace.getName(),
                        "pitStops", finalRace.getRequiredPitStops()
                )));
                return;
            }

            int pitStops;
            try {
                pitStops = Integer.parseInt(args[valueIndex]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("race.invalidPitStops"));
                return;
            }

            Bukkit.getScheduler().runTask(main, () -> {
                finalRace.setRequiredPitStops(pitStops);
                try {
                    finalRace.saveConfig();
                } catch (IOException e) {
                    sender.sendMessage(getMessage("error.unknown"));
                    return;
                }
                sender.sendMessage(getMessage("race.updatedPitStops", Messages.formatArguments(
                        "race", finalRace.getName(),
                        "pitStops", finalRace.getRequiredPitStops()
                )));
            });
        } else {
            sender.sendMessage(getMessage("race.help"));
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

        List<String> baseList = List.of("start","prepare","end","create","delete", "setLapCount","setPitStops");
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
                    || args[0].equalsIgnoreCase("setPitStops")
            )
        ) {
            return raceList;
        } else if (args.length==3) {
            if(args[0].equalsIgnoreCase("setLapCount")) {
                return List.of("§7<lapCount>");
            } else if(args[0].equalsIgnoreCase("setPitStops")) {
                return List.of("§7<requiredPitStops>");
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
