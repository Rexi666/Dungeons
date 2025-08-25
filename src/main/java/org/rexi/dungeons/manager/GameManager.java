package org.rexi.dungeons.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.rexi.dungeons.Dungeons;
import org.rexi.dungeons.menu.ClassMenu;

import java.util.*;

public class GameManager implements Listener {

    private static FileConfiguration getConfig() {
        return Dungeons.getInstance().getConfig();
    }

    public static final Map<String, Boolean> dungeonStarted = new HashMap<>();

    public static final Map<String, List<UUID>> dungeonPlayers = new HashMap<>();

    public static final List<String> possibleDungeons = new ArrayList<>();

    public static final Map<UUID, String> joiningPlayers = new HashMap<>();

    private static final int maxPlayers = getConfig().getInt("dungeons.players.max", 5);
    private static final int minPlayers = getConfig().getInt("dungeons.players.min", 1);

    //--------------------------------
    //           MÉTODOS             |
    //--------------------------------

    public static void join(Player player, String dungeon) {
        if (!possibleDungeons.contains(dungeon)) {
            player.sendMessage(deserialize(
                    getConfig().getString("messages.join_nodungeon", "&cIntentaste entrar en la dungeon {dungeon} pero no existe")
                            .replace("{dungeon}", dungeon)));
            return;
        }

        if (dungeonStarted.getOrDefault(dungeon, false)) {
            player.sendMessage(deserialize(
                    getConfig().getString("messages.join_started", "&cEsa dungeon ya está en una incursión activa")));
            return;
        }

        if (getDungeonOfPlayer(player).isEmpty()) {
            player.sendMessage(deserialize(
                    getConfig().getString("messages.join_already", "&cYa estás en una dungeon. Usa &b/dungeon leave &cpara salir")));
            return;
        }

        if (dungeonPlayers.containsKey(dungeon) && dungeonPlayers.get(dungeon).size() == maxPlayers) {
            player.sendMessage(deserialize(
                    getConfig().getString("messages.join_max", "&cLa dungeon a la que intentaste entrar está llena")));
            return;
        }

        ClassMenu.openMenu(player);
        joiningPlayers.put(player.getUniqueId(), dungeon);
    }

    private void joinPlayer(Player player) {
        String dungeon = joiningPlayers.get(player.getUniqueId());
        joiningPlayers.remove(player.getUniqueId());
        addPlayerToDungeonList(dungeon, player);
        tryStart(dungeon);
        player.sendMessage(deserialize(
                getConfig().getString("messages.joined", "&aHas entrado en la dungeon &b{dungeon}")
                        .replace("{dungeon}", dungeon)));
        Bukkit.broadcast(deserialize(getConfig()
                .getString("messages.broadcast_join", "&7[&dDUNGEONS&7] &b{player} &eha entrado en la dungeon &b{dungeon} &7({current}/{max})")
                .replace("{player}", player.getName())
                .replace("{dungeon}", dungeon)
                .replace("{current}", String.valueOf(getPlayersInDungeonList(dungeon).size()))
                .replace("{max}", String.valueOf(maxPlayers))));
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = PLAIN.serialize(event.getView().title());
        String menuTitle = PLAIN.serialize(deserialize(
                getConfig().getString("menu.title", "&fElige tu clase")));

        if (!title.equalsIgnoreCase(menuTitle)) return;

        if (Dungeons.getInstance().getPlayerClass(player).isEmpty()) {
            ClassMenu.openMenu(player);
            player.sendMessage(deserialize(
                    getConfig().getString("messages.force_selectclass", "&cTienes que seleccionar una clase!")));
        } else {
            joinPlayer(player);
        }
    }

    public static void leave(Player player, boolean message) {
        String dungeon = getDungeonOfPlayer(player);
        if (!dungeon.isEmpty()) {
            removePlayerFromDungeonList(dungeon, player);
            tryStop(dungeon);
            Bukkit.broadcast(deserialize(getConfig()
                    .getString("messages.broadcast_leave", "&7[&dDUNGEONS&7] &b{player} &eha salido de la dungeon &b{dungeon} &7({current}/{max})")
                    .replace("{player}", player.getName())
                    .replace("{dungeon}", dungeon)
                    .replace("{current}", String.valueOf(getPlayersInDungeonList(dungeon).size()))
                    .replace("{max}", String.valueOf(maxPlayers))));

            if (message) {
                player.sendMessage(deserialize(
                        getConfig().getString("messages.leaved", "&aHas salido de la dungeon &b{dungeon}")
                                .replace("{dungeon}", dungeon)));
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        leave(player, false);
    }

    //--------------------------------
    //            UTILS              |
    //--------------------------------

    public static void addPlayerToDungeonList(String dungeon, Player player) {
        dungeonPlayers.computeIfAbsent(dungeon, k -> new ArrayList<>()).add(player.getUniqueId());
    }

    public static void removePlayerFromDungeonList(String dungeon, Player player) {
        List<UUID> players = dungeonPlayers.get(dungeon);
        if (players != null) {
            players.remove(player.getUniqueId());
            if (players.isEmpty()) {
                dungeonPlayers.remove(dungeon); // elimina la dungeon si ya no hay nadie
            }
        }
    }

    public static List<UUID> getPlayersInDungeonList(String dungeon) {
        return dungeonPlayers.getOrDefault(dungeon, Collections.emptyList());
    }

    public static String getDungeonOfPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        for (Map.Entry<String, List<UUID>> entry : dungeonPlayers.entrySet()) {
            if (entry.getValue().contains(uuid)) {
                return entry.getKey(); // devuelve el nombre de la dungeon
            }
        }
        return ""; // si no está en ninguna dungeon
    }

    public static void loadPossibleDungeons() {
        ConfigurationSection bossSec = getConfig().getConfigurationSection("dungeons.bosses");

        if (bossSec != null) {
            for (String key : bossSec.getKeys(false)) possibleDungeons.add(key);
        }
    }

    private static Component deserialize(String input) {
        if (input.contains("<") && input.contains(">")) {
            try {
                return MiniMessage.miniMessage().deserialize(input);
            } catch (Exception e) {
                return Component.text(input);
            }
        }
        return LEGACY_HEX_SERIALIZER.deserialize(input);
    }
    private static final LegacyComponentSerializer LEGACY_HEX_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
}
