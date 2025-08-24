package org.rexi.dungeons.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.rexi.dungeons.Dungeons;

import java.util.Collections;
import java.util.List;

public class ChestListener implements Listener {

    private final Dungeons plugin;
    private final NamespacedKey chestKey;
    private final NamespacedKey keyItem;

    public ChestListener(Dungeons plugin) {
        this.plugin = plugin;
        this.chestKey = new NamespacedKey(plugin, "dungeon_chest");
        this.keyItem = new NamespacedKey(plugin, "dungeon_key");
    }

    @EventHandler
    public void onChestOpen(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        if (block.getType() != Material.CHEST) return;

        Chest chest = (Chest) block.getState();
        PersistentDataContainer chestData = chest.getPersistentDataContainer();

        // Solo cofres dungeon
        if (!chestData.has(chestKey, PersistentDataType.BYTE)) return;

        Player player = event.getPlayer();

        ItemStack item = event.getItem(); // item en la mano
        if (item == null || !item.hasItemMeta()) {
            event.setCancelled(true);
            player.sendMessage(deserialize(
                    plugin.getConfig().getString("messages.key_needed", "&cNecesitas una llave para abrir este cofre")));
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        if (!data.has(keyItem, PersistentDataType.STRING)) {
            event.setCancelled(true);
            player.sendMessage(deserialize(
                    plugin.getConfig().getString("messages.key_needed", "&cNecesitas una llave para abrir este cofre")));
            return;
        }

        String keyType = data.get(keyItem, PersistentDataType.STRING);

        if (!plugin.getConfig().isConfigurationSection("keys." + keyType)) {
            event.setCancelled(true);
            player.sendMessage(deserialize(
                    plugin.getConfig().getString("messages.key_needed", "&cNecesitas una llave para abrir este cofre")));
            return;
        }

        // --- Recompensas ---
        int min = plugin.getConfig().getInt("keys." + keyType + ".rewards.min", 1);
        int max = plugin.getConfig().getInt("keys." + keyType + ".rewards.max", 1);
        List<String> possibilities = plugin.getConfig().getStringList("keys." + keyType + ".rewards.possibilities");

        if (possibilities.isEmpty()) {
            player.sendMessage(deserialize(
                    plugin.getConfig().getString("messages.no_rewards", "&cNo hay recompensas configuradas para esta llave")));
            return;
        }

        int amount = (int) (Math.random() * (max - min + 1)) + min;
        Collections.shuffle(possibilities);

        for (int i = 0; i < Math.min(amount, possibilities.size()); i++) {
            String cmd = possibilities.get(i).replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        // Eliminar llave
        item.setAmount(item.getAmount() - 1);

        // Eliminar cofre
        block.setType(Material.AIR);

        player.sendMessage(deserialize(
                plugin.getConfig().getString("messages.chest_opened", "&a¡Has abierto un cofre con tu llave!")));
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