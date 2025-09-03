package org.rexi.dungeons.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.collect.ImmutableMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.rexi.dungeons.Dungeons;

import java.util.*;

public class StartMenu {

    private static FileConfiguration getConfig() {
        return Dungeons.getInstance().getConfig();
    }

    public static void openMenu(Player player, String dungeon) {
        String title = getConfig().getString("start_menu.title", "&7Iniciar una dungeon");
        Inventory inv = Bukkit.createInventory(null, 54, deserialize(title));

        ConfigurationSection itemsSec = getConfig().getConfigurationSection("start_menu.items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                String path = "start_menu.items." + key;
                String material = getConfig().getString(path + ".material", "CHEST");
                String name = getConfig().getString(path + ".name", "&fDificultad");
                boolean attributes = getConfig().getBoolean(path + ".hide_attributes", false);
                boolean glow = getConfig().getBoolean(path + ".glow", false);
                int slot = getConfig().getInt(path + ".slot", 0);
                int amount = getConfig().getInt(path + ".amount", 1);
                int prize = getConfig().getInt("dungeons.bosses."+dungeon+"."+key+".prize", 1);

                // Lore con placeholders extra
                List<String> lore = getConfig().getStringList(path + ".lore");
                List<String> replacedLore = new ArrayList<>();
                for (String line : lore) {
                    replacedLore.add(line
                            .replace("{prize}", String.valueOf(prize))
                    );
                }

                ItemStack item = createItem(material, name, replacedLore, attributes, glow, amount);

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(
                            new NamespacedKey(Dungeons.getInstance(), "dungeons-type"),
                            PersistentDataType.STRING,
                            key
                    );
                    meta.getPersistentDataContainer().set(
                            new NamespacedKey(Dungeons.getInstance(), "dungeons-dungeon"),
                            PersistentDataType.STRING,
                            dungeon
                    );
                    meta.getPersistentDataContainer().set(
                            new NamespacedKey(Dungeons.getInstance(), "dungeons-prize"),
                            PersistentDataType.STRING,
                            String.valueOf(prize)
                    );
                    item.setItemMeta(meta);
                }

                inv.setItem(slot, item);
            }
        }

        player.openInventory(inv);
    }

    /** ----------------- UTILS ----------------- **/
    private static ItemStack createItem(String material, String name, List<String> lore, boolean hideAttributes, boolean glow, int amount) {
        ItemStack item;
        String lower = Optional.ofNullable(material).orElse("CHEST").toLowerCase(Locale.ROOT);

        if (lower.startsWith("basehead-")) {
            String base64 = material.substring("basehead-".length());
            item = headFromBase64(base64);
        } else {
            Material mat;
            try {
                mat = Material.valueOf(material.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                mat = Material.CHEST;
            }
            item = new ItemStack(mat);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.displayName(deserialize(name));
            }

            if (lore != null && !lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(deserialize(line));
                }
                meta.lore(loreComponents);
            }

            if (glow) {
                applyGlow(meta, glow);
            } else if (hideAttributes) {
                applyHideAttributes(meta, hideAttributes);
            }

            item.setItemMeta(meta);
        }
        item.setAmount(Math.max(1, amount));
        return item;
    }

    private static void applyHideAttributes(ItemMeta meta, boolean hideAttributes) {
        if (hideAttributes) {
            for (final ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }
            meta.setAttributeModifiers(ImmutableMultimap.of());
        }
    }

    private static void applyGlow(ItemMeta meta, boolean glow) {
        if (glow) {
            meta.addEnchant(Enchantment.LOYALTY, 1, true);
            applyHideAttributes(meta, true);
        }
    }

    private static ItemStack headFromBase64(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isEmpty()) return head;

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return head;
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
