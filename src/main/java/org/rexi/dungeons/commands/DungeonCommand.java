package org.rexi.dungeons.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.rexi.dungeons.Dungeons;
import org.rexi.dungeons.manager.GameManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DungeonCommand implements CommandExecutor, TabCompleter {

    private final Dungeons plugin;

    public DungeonCommand(Dungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dungeon.command")) {
            sender.sendMessage(deserialize(
                    plugin.getConfig().getString("messages.no_permission", "&cNo tienes permiso para ejecutar ese comando")));
            return true;
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("dungeon.command.reload")) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.no_permission", "&cNo tienes permiso para ejecutar ese comando")));
                    return true;
                }
                plugin.reloadConfig();
                GameManager.loadPossibleDungeons();
                sender.sendMessage(deserialize(
                        plugin.getConfig().getString("messages.usage.reload", "&aConfiguración recargada")));
            }
            case "join" -> {
                if (!sender.hasPermission("dungeon.command.join")) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.no_permission", "&cNo tienes permiso para ejecutar ese comando")));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    if (args.length < 3) {
                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.usage.join_others", "&cUso: /dungeon join <dungeon> [jugador]")));
                        return true;
                    } else {
                        Player target = Bukkit.getPlayerExact(args[2]);
                        String dungeon = args[1];

                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.usage.join_forced", "&aForzando a &b{player} &aa unirse a la dungeon &b{dungeon}")
                                        .replace("{player}", target.getName())
                                        .replace("{dungeon}", dungeon)));
                        GameManager.join(target, dungeon);
                        return true;
                    }
                } else {
                    if (args.length < 2) {
                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.usage.join", "&cUso: /dungeon join <dungeon>")));
                        return true;
                    } else if (args.length == 2) {
                        String dungeon = args[1];

                        GameManager.join(player, dungeon);
                        return true;
                    } else {
                        String dungeon = args[1];
                        if (!player.hasPermission("dungeon.command.join.others")) {
                            GameManager.join(player, dungeon);
                            return true;
                        }
                        Player target = Bukkit.getPlayerExact(args[2]);

                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.usage.join_forced", "&aForzando a &b{player} &aa unirse a la dungeon &b{dungeon}")
                                        .replace("{player}", target.getName())
                                        .replace("{dungeon}", dungeon)));
                        GameManager.join(target, dungeon);
                        return true;
                    }
                }
            }
            case "leave" -> {
                if (!sender.hasPermission("dungeon.command.leave")) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.no_permission", "&cNo tienes permiso para ejecutar ese comando")));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    if (args.length < 2) {
                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.usage.leave_others", "&cUso: /dungeon leave [jugador]")));
                        return true;
                    } else {
                        Player target = Bukkit.getPlayerExact(args[1]);

                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.usage.leave_forced", "&aForzando a &b{player} &aa salir de la dungeon")
                                        .replace("{player}", target.getName())));
                        GameManager.leave(target, true);
                        return true;
                    }
                } else {
                    if (args.length == 1) {
                        GameManager.leave(player, true);
                        return true;
                    } else {
                        if (!player.hasPermission("dungeon.command.leave.others")) {
                            GameManager.leave(player, true);
                            return true;
                        }
                        Player target = Bukkit.getPlayerExact(args[1]);

                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.leave_forced", "&aForzando a &b{player} &aa salir de la dungeon")
                                        .replace("{player}", target.getName())));
                        GameManager.leave(target, true);
                        return true;
                    }
                }
            }
            case "start" -> {
                if (!sender.hasPermission("dungeon.command.start")) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.no_permission", "&cNo tienes permiso para ejecutar ese comando")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.usage.start", "&c/dungeon start <dungeon> <gamma/beta/alpha>")));
                    return true;
                } else {
                    String dungeon = args[1];
                    String type = args[2];

                    if (!type.equalsIgnoreCase("gamma")
                            && !type.equalsIgnoreCase("beta")
                            && !type.equalsIgnoreCase("alpha")) {
                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.usage.start", "&c/dungeon start <dungeon> <gamma/beta/alpha>")));
                        return true;
                    }

                    if (GameManager.dungeonStarted.getOrDefault(dungeon, false)
                            || GameManager.dungeonStarting.getOrDefault(dungeon, false)) {
                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.start_already_started", "&cNo puedes iniciar esa dungeon porque ya está iniciada!")));
                        return true;
                    }

                    GameManager.canStartDungeon.put(dungeon, true);
                    GameManager.dungeonType.put(dungeon, type);
                }
            }
            case "endgame" -> {
                if (!sender.hasPermission("dungeon.command.endgame")) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.no_permission", "&cNo tienes permiso para ejecutar ese comando")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.usage.endgame", "&c/dungeon endgame <dungeon> <gamma/beta/alpha>")));
                    return true;
                } else {
                    String dungeon = args[1];
                    String type = args[2];

                    GameManager.giveReward(dungeon, type);
                    GameManager.cancelDungeonTimer(dungeon);
                    GameManager.stop(dungeon, true);
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        if (sender.hasPermission("dungeon.command.reload")) sender.sendMessage(deserialize(plugin.getConfig()
                .getString("messages.usage.reload")));
        if (sender.hasPermission("dungeon.command.endgame")) sender.sendMessage(deserialize(plugin.getConfig()
                .getString("messages.usage.endgame")));
        if (sender.hasPermission("dungeon.command.join")) {
            if (sender.hasPermission("dungeon.command.join.others")) {
                sender.sendMessage(deserialize(plugin.getConfig()
                        .getString("messages.usage.join_others")));
            } else {
                sender.sendMessage(deserialize(plugin.getConfig()
                        .getString("messages.usage.join")));
            }
        }
        if (sender.hasPermission("dungeon.command.leave")) {
            if (sender.hasPermission("dungeon.command.leave.others")) {
                sender.sendMessage(deserialize(plugin.getConfig()
                        .getString("messages.usage.leave_others")));
            } else {
                sender.sendMessage(deserialize(plugin.getConfig()
                        .getString("messages.usage.leave")));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dungeon.command")) return Collections.emptyList();

        // Primer argumento: subcomando
        if (args.length == 1) {
            List<String> suggest = new ArrayList<>();
            if (sender.hasPermission("dungeon.command.reload")) suggest.add("reload");
            if (sender.hasPermission("dungeon.command.join")) suggest.add("join");
            if (sender.hasPermission("dungeon.command.leave")) suggest.add("leave");
            if (sender.hasPermission("dungeon.command.endgame")) suggest.add("endgame");
            if (sender.hasPermission("dungeon.command.start")) suggest.add("start");
            return suggest;
        }

        // Segundo argumento: dependiendo del subcomando
        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            switch (sub) {
                case "join", "endgame", "start" -> {
                    // Autocompleta con los nombres de las dungeons posibles
                    return new ArrayList<>(GameManager.possibleDungeons);
                }
                case "leave" -> {
                    List<String> players = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                    return players;
                }
            }
        }

        // Tercer argumento: jugadores (para join y leave)
        if (args.length == 3) {
            if (sub.equals("join") || sub.equals("leave")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                return players;
            }
            if (sub.equals("endgame") || sub.equals("start")) {
                // Tipos posibles para endgame
                return Arrays.asList("alpha", "beta", "gamma");
            }
        }

        return Collections.emptyList();
    }

    private static final LegacyComponentSerializer LEGACY_HEX_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors() // Habilita el soporte de hex
            .useUnusualXRepeatedCharacterHexFormat() // Soporta &x&r&r&g&g&b&b
            .build();

    public static Component deserialize(String input) {
        return LEGACY_HEX_SERIALIZER.deserialize(input);
    }
}
