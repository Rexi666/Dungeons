package org.rexi.dungeons.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.rexi.dungeons.Dungeons;

public class MenuListener implements Listener {

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

        final int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        if (!title.equalsIgnoreCase(menuTitle)) return;

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
    }
}
