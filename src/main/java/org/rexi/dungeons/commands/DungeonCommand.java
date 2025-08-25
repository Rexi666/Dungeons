package org.rexi.dungeons.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.rexi.dungeons.Dungeons;
import org.rexi.dungeons.menu.ClassMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DungeonCommand implements CommandExecutor, TabCompleter {

    private final Dungeons plugin;
    private final NamespacedKey chestKey;
    private final NamespacedKey keyItem;

    public DungeonCommand(Dungeons plugin) {
        this.plugin = plugin;
        this.chestKey = new NamespacedKey(plugin, "dungeon_chest");
        this.keyItem = new NamespacedKey(plugin, "dungeon_key");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dungeon.command")) {
            sender.sendMessage(deserialize(
                    plugin.getConfig().getString("messages.no_permission", "&cNo tienes permiso para ejecutar ese comando")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(deserialize(
                    plugin.getConfig().getString("messages.usage", "Uso: /dungeon <reload|givekey|spawnchests>")));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(deserialize(
                        plugin.getConfig().getString("messages.reload", "&aConfiguración recargada")));
            }
            case "givekey" -> {
                if (args.length < 3) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.usage_key", "&cUso: /dungeon givekey <jugador> <key> [cantidad]")));
                }

                Player player = Bukkit.getPlayerExact(args[1]);
                if (player == null) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.wrong_player", "&cEse jugador no está conectado")));
                    return true;
                }

                String keyType = args[2];
                if (!plugin.getConfig().isConfigurationSection("keys." + keyType)) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.wrong_key_type", "&cEsa llave no existe")));
                    return true;
                }

                String matName = plugin.getConfig().getString("keys." + keyType + ".material", "TRIPWIRE_HOOK");
                Material material = Material.matchMaterial(matName);
                if (material == null) material = Material.TRIPWIRE_HOOK;

                ItemStack key = new ItemStack(material);
                ItemMeta meta = key.getItemMeta();

                // Nombre
                meta.displayName(deserialize(plugin.getConfig().getString("keys." + keyType + ".name", "&fLlave")));

                // Lore
                List<String> lore = plugin.getConfig().getStringList("keys." + keyType + ".lore");
                List<Component> loreComp = new ArrayList<>();
                for (String l : lore) {
                    loreComp.add(deserialize(l));
                }
                meta.lore(loreComp);

                // Marcar llave con PersistentData
                meta.getPersistentDataContainer().set(keyItem, PersistentDataType.STRING, keyType);

                key.setItemMeta(meta);

                int cantidadFinal;
                if (args.length != 4) {
                    cantidadFinal = 1;
                } else {
                    try {
                        cantidadFinal = Integer.parseInt(args[3]);
                    } catch (Exception e) {
                        cantidadFinal = 1;
                    }
                }
                for (int i = 0; i < cantidadFinal; i++) {
                    player.getInventory().addItem(key);
                }

                sender.sendMessage(deserialize(
                        plugin.getConfig().getString("messages.key", "&aHas dado una llave {key} a {player}")
                                .replace("{player}", args[1])
                                .replace("{key}", keyType)));
            }
            case "spawnchests" -> {
                if (args.length < 5) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.usage_chest", "&cUso: /dungeon spawnchests <x> <y> <z> <world>")));
                    return true;
                }

                try {
                    double x = Double.parseDouble(args[1]);
                    double y = Double.parseDouble(args[2]);
                    double z = Double.parseDouble(args[3]);
                    String worldName = args[4];

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.wrong_world", "&cEl mundo especificado no existe")));
                        return true;
                    }

                    // Coordenadas para los 4 cofres alrededor
                    int[][] offsets = {
                            {2, 0},   // x+2, z
                            {-2, 0},  // x-2, z
                            {0, 2},   // x, z+2
                            {0, -2}   // x, z-2
                    };

                    for (int[] offset : offsets) {
                        Location loc = new Location(world, x + offset[0], y, z + offset[1]);
                        world.getBlockAt(loc).setType(org.bukkit.Material.CHEST);

                        Chest chestBlock = (Chest) world.getBlockAt(loc).getState();
                        chestBlock.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 1);
                        chestBlock.update();
                    }

                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.chest_spawned", "&aSe han spawneado 4 cofres alrededor de {x},{y},{z} en el mundo {world}")
                                    .replace("{x}", String.valueOf(x))
                                    .replace("{y}", String.valueOf(y))
                                    .replace("{z}", String.valueOf(z))
                                    .replace("{world}", worldName)));

                } catch (NumberFormatException e) {
                    sender.sendMessage(deserialize(
                            plugin.getConfig().getString("messages.wrong_coords", "&cLas coordenadas deben ser números válidos")));
                }
            }
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    if (args.length < 2) {
                        sender.sendMessage(deserialize(
                                plugin.getConfig().getString("messages.usage_menu", "&cUso: /dungeon menu [jugador]")));
                        return true;
                    } else {
                        Player target = Bukkit.getPlayerExact(args[1]);
                        ClassMenu.openMenu(target);
                    }
                } else {
                    if (args.length == 1) {
                        ClassMenu.openMenu(player);
                        return true;
                    } else {
                        Player target = Bukkit.getPlayerExact(args[1]);
                        ClassMenu.openMenu(target);
                        return true;
                    }
                }
            }
            default -> sender.sendMessage(deserialize(
                    plugin.getConfig().getString("messages.usage", "Uso: /dungeon <reload|givekey|spawnchests>")));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dungeon.command")) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("reload", "givekey", "spawnchests", "menu");
        }

        if (args[0].equalsIgnoreCase("givekey")) {
            if (args.length == 2) {
                // autocompletar jugadores online
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    players.add(p.getName());
                }
                return players;
            } else if (args.length == 3) {
                if (plugin.getConfig().isConfigurationSection("keys")) {
                    return new ArrayList<>(plugin.getConfig().getConfigurationSection("keys").getKeys(false));
                }
            }
        }

        if (args[0].equalsIgnoreCase("menu")) {
            if (args.length == 2) {
                // autocompletar jugadores online
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    players.add(p.getName());
                }
                return players;
            }
        }

        if (args[0].equalsIgnoreCase("spawnchests")) {
            if (args.length == 5) {
                // mundos disponibles
                List<String> worlds = new ArrayList<>();
                for (World w : Bukkit.getWorlds()) {
                    worlds.add(w.getName());
                }
                return worlds;
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
        if (input.contains("<") && input.contains(">")) {
            try {
                return MiniMessage.miniMessage().deserialize(input)
                        .decoration(TextDecoration.ITALIC, false); // <- desactiva itálica
            } catch (Exception e) {
                return Component.text(input).decoration(TextDecoration.ITALIC, false);
            }
        }
        return LEGACY_HEX_SERIALIZER.deserialize(input)
                .decoration(TextDecoration.ITALIC, false);
    }
}
