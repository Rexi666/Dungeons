package org.rexi.dungeons.menu.rewards;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.rexi.dungeons.Dungeons;
import org.rexi.dungeons.manager.GameManager;
import org.rexi.dungeons.rewards.GeneratedChest;
import org.rexi.dungeons.rewards.PlayerRewardSession;
import org.rexi.dungeons.rewards.DungeonRewardManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RewardChestMenu {

    private final Dungeons plugin;
    private final DungeonRewardManager manager;

    public RewardChestMenu(
            Dungeons plugin,
            DungeonRewardManager manager
    ) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(Player player) {
        PlayerRewardSession session =
                manager.getSession(
                        player.getUniqueId()
                );

        if (session == null) {
            player.sendMessage(manager.message(
                    "messages.rewards.unavailable",
                    "&cNo tienes recompensas pendientes."
            ));
            return;
        }

        String base =
                "rewards." + session.getDungeon();

        int size = normalizeSize(
                plugin.getConfig().getInt(
                        base + ".menu.size",
                        54
                )
        );

        String title =
                plugin.getConfig().getString(
                                base + ".menu.title",
                                "&8Recompensas: &5{dungeon}"
                        )
                        .replace(
                                "{dungeon}",
                                session.getDungeon()
                        );

        Inventory inventory = Bukkit.createInventory(
                new RewardMenuHolder(
                        player.getUniqueId()
                ),
                size,
                GameManager.deserialize(title)
        );

        for (GeneratedChest chest :
                session.getChests().values()) {

            String chestPath =
                    base + ".chests." + chest.getId();

            int slot = plugin.getConfig().getInt(
                    chestPath + ".slot",
                    0
            );

            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            inventory.setItem(
                    slot,
                    createChestItem(
                            session,
                            chest,
                            chestPath
                    )
            );
        }

        int infoSlot = plugin.getConfig().getInt(
                base + ".menu.info.slot",
                4
        );

        if (infoSlot >= 0
                && infoSlot < inventory.getSize()) {
            inventory.setItem(
                    infoSlot,
                    createInfoItem(session, base)
            );
        }

        int closeSlot = plugin.getConfig().getInt(
                base + ".menu.close.slot",
                49
        );

        if (closeSlot >= 0
                && closeSlot < inventory.getSize()) {
            inventory.setItem(
                    closeSlot,
                    simpleItem(
                            plugin.getConfig().getString(
                                    base
                                            + ".menu.close.material",
                                    "BARRIER"
                            ),
                            plugin.getConfig().getString(
                                    base + ".menu.close.name",
                                    "&cCerrar"
                            ),
                            List.of()
                    )
            );
        }

        player.openInventory(inventory);
    }

    private ItemStack createChestItem(
            PlayerRewardSession session,
            GeneratedChest chest,
            String path
    ) {
        String configuredMaterial =
                plugin.getConfig().getString(
                        path + ".material",
                        "CHEST"
                );

        Material material;

        if (chest.isClaimed()) {
            material = Material.LIME_STAINED_GLASS_PANE;
        } else if (!chest.isUnlocked()) {
            material = Material.RED_STAINED_GLASS_PANE;
        } else {
            material = Material.matchMaterial(
                    configuredMaterial
            );

            if (material == null) {
                material = Material.CHEST;
            }
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(GameManager.deserialize(
                chest.getDisplayName()
        ));

        List<String> sourceLore;

        if (chest.isClaimed()) {
            sourceLore = List.of(
                    "&aCofre reclamado"
            );
        } else if (!chest.isUnlocked()) {
            sourceLore =
                    plugin.getConfig().getStringList(
                            path + ".locked-lore"
                    );

            if (sourceLore.isEmpty()) {
                sourceLore = List.of(
                        "&cCofre bloqueado",
                        "",
                        "&7Puntuación actual: &f{score}",
                        "&7Necesaria: &f{required_score}"
                );
            }
        } else {
            sourceLore =
                    plugin.getConfig()
                            .getStringList(
                                    path + ".lore"
                            );
        }

        List<Component> lore = new ArrayList<>();

        for (String line : sourceLore) {
            lore.add(GameManager.deserialize(
                    replace(
                            line,
                            session,
                            chest
                    )
            ));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(
            PlayerRewardSession session,
            String base
    ) {
        List<String> loreLines =
                plugin.getConfig().getStringList(
                        base + ".menu.info.lore"
                );

        List<String> replaced = new ArrayList<>();

        int maximumClaims = session.getDefaultClaims();

        if (plugin.getConfig().getBoolean(
                "reward-key.enabled",
                true
        )) {
            maximumClaims++;
        }

        for (String line : loreLines) {
            replaced.add(
                    line.replace(
                                    "{dungeon}",
                                    session.getDungeon()
                            )
                            .replace(
                                    "{difficulty}",
                                    session.getDifficulty()
                            )
                            .replace(
                                    "{score}",
                                    String.valueOf(
                                            session.getScore()
                                    )
                            )
                            .replace(
                                    "{claims}",
                                    String.valueOf(
                                            session.getClaimsUsed()
                                    )
                            )
                            .replace(
                                    "{maximum_claims}",
                                    String.valueOf(
                                            maximumClaims
                                    )
                            )
            );
        }

        return simpleItem(
                plugin.getConfig().getString(
                        base + ".menu.info.material",
                        "NETHER_STAR"
                ),
                plugin.getConfig().getString(
                        base + ".menu.info.name",
                        "&dResumen"
                ),
                replaced
        );
    }

    private String replace(
            String text,
            PlayerRewardSession session,
            GeneratedChest chest
    ) {
        String formattedPrice =
                chest.getPrice() == 0
                        ? "&aGratis"
                        : "&6"
                        + NumberFormat.getIntegerInstance(
                        new Locale("es", "ES")
                ).format(chest.getPrice());

        return text
                .replace(
                        "{price}",
                        formattedPrice
                )
                .replace(
                        "{required_score}",
                        String.valueOf(
                                chest.getRequiredScore()
                        )
                )
                .replace(
                        "{score}",
                        String.valueOf(
                                session.getScore()
                        )
                )
                .replace(
                        "{reward_count}",
                        String.valueOf(
                                chest.getRewards().size()
                        )
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