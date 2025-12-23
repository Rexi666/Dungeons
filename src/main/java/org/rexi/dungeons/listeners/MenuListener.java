package org.rexi.dungeons.listeners;

import me.ulrich.clans.data.ClanData;
import me.ulrich.clans.interfaces.UClans;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.rexi.dungeons.Dungeons;
import org.rexi.dungeons.manager.GameManager;

import java.util.Optional;

import static org.rexi.dungeons.manager.GameManager.getDungeonOfPlayer;

public class MenuListener implements Listener {

    private UClans uclans;

    public MenuListener(UClans uclans) {
        this.uclans = uclans;
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Component deserialize(String input) {
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

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getCurrentItem() == null) return;

        // solo clicks en la GUI (arriba)
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        final var config = Dungeons.getInstance().getConfig();
        String title = PLAIN.serialize(event.getView().title());
        String menuTitle = PLAIN.serialize(deserialize(
                config.getString("menu.title", "&fElige tu clase")));
        String startTitle = PLAIN.serialize(deserialize(
                config.getString("start_menu.title", "&7Iniciar una dungeon")));

        final int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        if (title.equalsIgnoreCase(menuTitle)) {
            ItemStack clicked = event.getCurrentItem();
            String info = clicked.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(Dungeons.getInstance(), "dungeons-class"),
                    PersistentDataType.STRING
            );

            if (info != null && !info.isEmpty()) {
                Dungeons.getInstance().setPlayerClass(player, info);

                String name = config.getString("menu.classes."+info+".name",
                        config.getString("messages.ninguna_clase", "&cNinguna"));
                player.sendMessage(deserialize(
                        config.getString("messages.class_chosen", "&aHas escogido la clase {class}")
                                .replace("{class}", name)));
            }

            event.setCancelled(true);
            player.closeInventory();
        } else if (title.equalsIgnoreCase(startTitle)) {
            ItemStack clicked = event.getCurrentItem();

            String type = clicked.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(Dungeons.getInstance(), "dungeons-type"),
                    PersistentDataType.STRING
            );
            String dungeon = clicked.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(Dungeons.getInstance(), "dungeons-dungeon"),
                    PersistentDataType.STRING
            );
            String prizeString = clicked.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(Dungeons.getInstance(), "dungeons-prize"),
                    PersistentDataType.STRING
            );

            event.setCancelled(true);
            player.closeInventory();

            if ((type != null && !type.isEmpty()) && (dungeon != null && !dungeon.isEmpty()) && (prizeString != null && !prizeString.isEmpty())) {
                if (!GameManager.possibleDungeons.contains(dungeon)) {
                    player.sendMessage(deserialize(config
                            .getString("messages.join_nodungeon", "&cIntentaste entrar en la dungeon {dungeon} pero no existe")
                            .replace("{dungeon}", dungeon)));
                    return;
                }

                int dungeonLevel = config.getInt("dungeons.bosses."+dungeon);
                int level = (Dungeons.getInstance().getDungeonLevel(player)+1);

                if (level < dungeonLevel) {
                    player.sendMessage(deserialize(
                            config
                                    .getString("messages.start_no_level",
                                            "&cNo puedes iniciar esta dungeon porque no tienes el nivel suficiente: Tu nivel {level}, necesario {required}")
                                    .replace("{level}", String.valueOf(level))
                                    .replace("{required}", String.valueOf(dungeonLevel))));
                    return;
                }

                if (GameManager.dungeonStarted.contains(dungeon)
                        || GameManager.dungeonStarting.getOrDefault(dungeon, false)) {
                    player.sendMessage(deserialize(
                            config.getString("messages.start_already", "&cLa dungeon ya estaba iniciada")));
                    return;
                }

                String dungeonOfPlayer = getDungeonOfPlayer(player);
                if (!dungeonOfPlayer.isEmpty()) {
                    player.sendMessage(deserialize(config
                            .getString("messages.join_already", "&cYa estás en una dungeon. Usa &b/dungeon leave &cpara salir")));
                    return;
                }

                double money = Dungeons.getEconomy().getBalance(player);
                int prize;
                try {
                    prize = Integer.parseInt(prizeString);
                } catch (NumberFormatException e) {
                    player.sendMessage(deserialize("&cOcurrió un error"));
                    return;
                }

                if (money < prize) {
                    player.sendMessage(deserialize(config
                            .getString("no_money", "&cNo tienes dinero suficiente para comprar esto")));
                    return;
                }

                EconomyResponse r = Dungeons.getEconomy().withdrawPlayer(player, prize);
                if(!r.transactionSuccess()) {
                    player.sendMessage(deserialize(String.format("&cOcurrió un error: %s", r.errorMessage)));
                    return;
                }

                Bukkit.broadcast(deserialize(config
                        .getString("starting_dungeon", "&7[&dDUNGEONS&7] &b{player} &eestá iniciando la dungeon &b{dungeon} &een dificultad &b{type}")
                        .replace("{player}", player.getName())
                        .replace("{dungeon}", dungeon)
                        .replace("{type}", type)));

                Optional<ClanData> optional_clan = uclans.getPlayerAPI().getPlayerClan(player.getUniqueId());

                ClanData clan = null;

                if (optional_clan.isPresent()) {
                    clan = optional_clan.get();
                }

                GameManager.canStartDungeon.put(dungeon, true);
                GameManager.DungeonClan.put(dungeon, clan);
                GameManager.dungeonType.put(dungeon, type);
                GameManager.join(player, dungeon);
            }
        }
    }
}
