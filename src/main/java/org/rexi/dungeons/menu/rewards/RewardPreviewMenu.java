package org.rexi.dungeons.menu.rewards;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.rexi.dungeons.Dungeons;
import org.rexi.dungeons.manager.GameManager;
import org.rexi.dungeons.rewards.GeneratedChest;
import org.rexi.dungeons.rewards.GeneratedReward;
import org.rexi.dungeons.rewards.PlayerRewardSession;
import org.rexi.dungeons.rewards.DungeonRewardManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RewardPreviewMenu {

    private final Dungeons plugin;
    private final DungeonRewardManager manager;

    public RewardPreviewMenu(
            Dungeons plugin,
            DungeonRewardManager manager
    ) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(
            Player player,
            String chestId
    ) {
        PlayerRewardSession session =
                manager.getSession(
                        player.getUniqueId()
                );

        if (session == null) {
            return;
        }

        GeneratedChest chest =
                session.getChest(chestId);

        if (chest == null || !chest.isUnlocked()) {
            return;
        }

        String base =
                "rewards." + session.getDungeon();

        int size = normalizeSize(
                plugin.getConfig().getInt(
                        base + ".preview-menu.size",
                        54
                )
        );

        String title =
                plugin.getConfig().getString(
                                base + ".preview-menu.title",
                                "&8Contenido: {chest}"
                        )
                        .replace(
                                "{chest}",
                                chest.getDisplayName()
                        );

        Inventory inventory = Bukkit.createInventory(
                new RewardPreviewHolder(
                        player.getUniqueId(),
                        chestId
                ),
                size,
                GameManager.deserialize(title)
        );

        int slot = 10;

        for (GeneratedReward reward :
                chest.getRewards()) {

            while (isControlSlot(slot, base)
                    && slot < size) {
                slot++;
            }

            if (slot >= Math.min(size, 36)) {
                break;
            }

            ItemStack display =
                    reward.getDisplayItem();

            if (display != null) {
                inventory.setItem(slot, display);
            }

            slot++;
        }

        int confirmSlot = plugin.getConfig().getInt(
                base + ".preview-menu.confirm.slot",
                40
        );

        inventory.setItem(
                confirmSlot,
                createConfirmItem(
                        base,
                        chest
                )
        );

        int backSlot = plugin.getConfig().getInt(
                base + ".preview-menu.back.slot",
                48
        );

        inventory.setItem(
                backSlot,
                simpleItem(
                        plugin.getConfig().getString(
                                base
                                        + ".preview-menu.back.material",
                                "ARROW"
                        ),
                        plugin.getConfig().getString(
                                base + ".preview-menu.back.name",
                                "&eVolver"
                        ),
                        List.of()
                )
        );

        int closeSlot = plugin.getConfig().getInt(
                base + ".preview-menu.close.slot",
                50
        );

        inventory.setItem(
                closeSlot,
                simpleItem(
                        plugin.getConfig().getString(
                                base
                                        + ".preview-menu.close.material",
                                "BARRIER"
                        ),
                        plugin.getConfig().getString(
                                base + ".preview-menu.close.name",
                                "&cCerrar"
                        ),
                        List.of()
                )
        );

        player.openInventory(inventory);
    }

    private ItemStack createConfirmItem(
            String base,
            GeneratedChest chest
    ) {
        List<String> source =
                plugin.getConfig().getStringList(
                        base
                                + ".preview-menu.confirm.lore"
                );

        String price = chest.getPrice() == 0
                ? "&aGratis"
                : "&6"
                + NumberFormat.getIntegerInstance(
                new Locale("es", "ES")
        ).format(chest.getPrice());

        List<String> lore = new ArrayList<>();

        for (String line : source) {
            lore.add(
                    line.replace("{price}", price)
            );
        }

        return simpleItem(
                plugin.getConfig().getString(
                        base
                                + ".preview-menu.confirm.material",
                        "LIME_DYE"
                ),
                plugin.getConfig().getString(
                        base
                                + ".preview-menu.confirm.name",
                        "&a&lReclamar cofre"
                ),
                lore
        );
    }

    private boolean isControlSlot(
            int slot,
            String base
    ) {
        return slot == plugin.getConfig().getInt(
                base + ".preview-menu.confirm.slot",
                40
        ) || slot == plugin.getConfig().getInt(
                base + ".preview-menu.back.slot",
                48
        ) || slot == plugin.getConfig().getInt(
                base + ".preview-menu.close.slot",
                50
        );
    }

    private ItemStack simpleItem(
            String materialName,
            String name,
            List<String> loreLines
    ) {
        Material material =
                Material.matchMaterial(materialName);

        if (material == null) {
            material = Material.CHEST;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(
                GameManager.deserialize(name)
        );

        List<Component> lore = new ArrayList<>();

        for (String line : loreLines) {
            lore.add(GameManager.deserialize(line));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());

        item.setItemMeta(meta);
        return item;
    }

    private int normalizeSize(int requested) {
        int size = Math.max(9, Math.min(54, requested));
        return ((size + 8) / 9) * 9;
    }
}