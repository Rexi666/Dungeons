package org.rexi.dungeons.rewards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.rexi.dungeons.Dungeons;
import org.rexi.dungeons.manager.GameManager;
import org.rexi.dungeons.menu.rewards.RewardChestMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DungeonRewardManager {

    private final Dungeons plugin;
    private final Map<UUID, PlayerRewardSession> sessions =
            new HashMap<>();
    private final Set<UUID> purchaseLocks = new HashSet<>();

    private final NamespacedKey extraClaimKey;

    private RewardChestMenu rewardChestMenu;

    public DungeonRewardManager(Dungeons plugin) {
        this.plugin = plugin;
        this.extraClaimKey = new NamespacedKey(
                plugin,
                "dungeon-extra-chest-key"
        );
    }

    public void setRewardChestMenu(
            RewardChestMenu rewardChestMenu
    ) {
        this.rewardChestMenu = rewardChestMenu;
    }

    public PlayerRewardSession getSession(UUID uuid) {
        PlayerRewardSession session = sessions.get(uuid);

        if (session != null && session.isExpired()) {
            sessions.remove(uuid);
            return null;
        }

        return session;
    }

    public boolean completeDungeon(String dungeon) {
        if (!GameManager.dungeonStarted.contains(dungeon)) {
            return false;
        }

        String basePath = "rewards." + dungeon;

        if (!plugin.getConfig().getBoolean(
                basePath + ".enabled",
                true
        )) {
            return false;
        }

        List<UUID> participants = new ArrayList<>(
                GameManager.getPlayersInDungeonList(dungeon)
        );

        if (participants.isEmpty()) {
            return false;
        }

        String difficulty =
                GameManager.dungeonType.getOrDefault(
                        dungeon,
                        "normal"
                );

        int score = calculateScore(dungeon, difficulty);

        int expirationSeconds = Math.max(
                60,
                plugin.getConfig().getInt(
                        basePath + ".expiration-seconds",
                        900
                )
        );

        int defaultClaims = Math.max(
                1,
                plugin.getConfig().getInt(
                        basePath + ".default-claims",
                        1
                )
        );

        long now = System.currentTimeMillis();
        long expiresAt =
                now + expirationSeconds * 1000L;

        for (UUID uuid : participants) {
            Map<String, GeneratedChest> generatedChests =
                    generateChests(
                            dungeon,
                            difficulty,
                            score
                    );

            PlayerRewardSession session =
                    new PlayerRewardSession(
                            uuid,
                            dungeon,
                            difficulty,
                            score,
                            now,
                            expiresAt,
                            defaultClaims,
                            generatedChests
                    );

            sessions.put(uuid, session);
        }

        GameManager.cancelDungeonTimer(dungeon);
        GameManager.stop(dungeon, true);

        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                continue;
            }

            player.sendMessage(message(
                    "messages.rewards.generated",
                    "&aTus cofres están disponibles. "
                            + "Usa &e/dungeon rewards&a."
            ));

            if (rewardChestMenu != null) {
                Bukkit.getScheduler().runTaskLater(
                        plugin,
                        () -> rewardChestMenu.open(player),
                        2L
                );
            }
        }

        return true;
    }

    private int calculateScore(
            String dungeon,
            String difficulty
    ) {
        String base =
                "rewards." + dungeon + ".scoring";

        int score = 0;

        score += plugin.getConfig().getInt(
                base + ".completion",
                100
        );

        score += plugin.getConfig().getInt(
                base + ".boss-kill",
                100
        );

        if (plugin.getConfig().getBoolean(
                base + ".remaining-time.enabled",
                true
        )) {
            int maximum = plugin.getConfig().getInt(
                    base + ".remaining-time.maximum",
                    150
            );

            /*
             * De momento se concede el máximo.
             * Más adelante puedes conectar aquí el tiempo real restante
             * almacenado por GameManager.
             */
            score += Math.max(0, maximum);
        }

        String modifierPath =
                "rewards."
                        + dungeon
                        + ".difficulty-modifiers."
                        + difficulty;

        double multiplier = plugin.getConfig().getDouble(
                modifierPath + ".score-multiplier",
                1.0
        );

        return Math.max(
                0,
                (int) Math.round(score * multiplier)
        );
    }

    private Map<String, GeneratedChest> generateChests(
            String dungeon,
            String difficulty,
            int score
    ) {
        Map<String, GeneratedChest> result =
                new LinkedHashMap<>();

        String chestsPath =
                "rewards." + dungeon + ".chests";

        ConfigurationSection section =
                plugin.getConfig()
                        .getConfigurationSection(chestsPath);

        if (section == null) {
            return result;
        }

        double priceMultiplier =
                plugin.getConfig().getDouble(
                        "rewards."
                                + dungeon
                                + ".difficulty-modifiers."
                                + difficulty
                                + ".price-multiplier",
                        1.0
                );

        for (String chestId : section.getKeys(false)) {
            String path = chestsPath + "." + chestId;

            if (!plugin.getConfig().getBoolean(
                    path + ".enabled",
                    true
            )) {
                continue;
            }

            int requiredScore =
                    plugin.getConfig().getInt(
                            path + ".minimum-score",
                            0
                    );

            long basePrice =
                    plugin.getConfig().getLong(
                            path + ".price",
                            0L
                    );

            long finalPrice = Math.max(
                    0L,
                    Math.round(basePrice * priceMultiplier)
            );

            String displayName =
                    plugin.getConfig().getString(
                            path + ".name",
                            chestId
                    );

            boolean unlocked = score >= requiredScore;

            List<GeneratedReward> rewards =
                    unlocked
                            ? generateLoot(path)
                            : List.of();

            result.put(
                    chestId,
                    new GeneratedChest(
                            chestId,
                            displayName,
                            finalPrice,
                            requiredScore,
                            unlocked,
                            rewards
                    )
            );
        }

        return result;
    }

    private List<GeneratedReward> generateLoot(
            String chestPath
    ) {
        ConfigurationSection lootSection =
                plugin.getConfig().getConfigurationSection(
                        chestPath + ".loot"
                );

        if (lootSection == null) {
            return List.of();
        }

        int minimumRolls = Math.max(
                1,
                plugin.getConfig().getInt(
                        chestPath + ".rolls.minimum",
                        1
                )
        );

        int maximumRolls = Math.max(
                minimumRolls,
                plugin.getConfig().getInt(
                        chestPath + ".rolls.maximum",
                        minimumRolls
                )
        );

        int rolls = ThreadLocalRandom.current().nextInt(
                minimumRolls,
                maximumRolls + 1
        );

        boolean allowDuplicates =
                plugin.getConfig().getBoolean(
                        chestPath + ".allow-duplicates",
                        false
                );

        List<String> availableIds =
                new ArrayList<>(lootSection.getKeys(false));

        List<GeneratedReward> generated =
                new ArrayList<>();

        for (int roll = 0; roll < rolls; roll++) {
            if (availableIds.isEmpty()) {
                break;
            }

            String selected = weightedSelection(
                    lootSection,
                    availableIds
            );

            if (selected == null) {
                break;
            }

            GeneratedReward reward =
                    createReward(
                            lootSection.getCurrentPath()
                                    + "."
                                    + selected,
                            selected
                    );

            if (reward != null
                    && reward.getType() != RewardType.NOTHING) {
                generated.add(reward);
            }

            if (!allowDuplicates) {
                availableIds.remove(selected);
            }
        }

        return generated;
    }

    private String weightedSelection(
            ConfigurationSection lootSection,
            List<String> availableIds
    ) {
        double totalWeight = 0.0;

        for (String id : availableIds) {
            totalWeight += Math.max(
                    0.0,
                    lootSection.getDouble(
                            id + ".weight",
                            1.0
                    )
            );
        }

        if (totalWeight <= 0.0) {
            return null;
        }

        double selectedValue =
                ThreadLocalRandom.current().nextDouble(
                        totalWeight
                );

        double cursor = 0.0;

        for (String id : availableIds) {
            cursor += Math.max(
                    0.0,
                    lootSection.getDouble(
                            id + ".weight",
                            1.0
                    )
            );

            if (selectedValue <= cursor) {
                return id;
            }
        }

        return availableIds.get(
                availableIds.size() - 1
        );
    }

    private GeneratedReward createReward(
            String path,
            String rewardId
    ) {
        RewardType type;

        try {
            type = RewardType.valueOf(
                    plugin.getConfig()
                            .getString(
                                    path + ".type",
                                    "ITEM"
                            )
                            .toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(
                    "Tipo de recompensa inválido en " + path
            );
            return null;
        }

        int minimumAmount = Math.max(
                0,
                plugin.getConfig().getInt(
                        path + ".amount.minimum",
                        1
                )
        );

        int maximumAmount = Math.max(
                minimumAmount,
                plugin.getConfig().getInt(
                        path + ".amount.maximum",
                        minimumAmount
                )
        );

        int amount = minimumAmount == maximumAmount
                ? minimumAmount
                : ThreadLocalRandom.current().nextInt(
                minimumAmount,
                maximumAmount + 1
        );

        ItemStack displayItem =
                createDisplayItem(path, type, amount);

        ItemStack rewardItem = null;

        if (type == RewardType.ITEM) {
            Material material = Material.matchMaterial(
                    plugin.getConfig().getString(
                            path + ".material",
                            "STONE"
                    )
            );

            if (material == null) {
                material = Material.STONE;
            }

            rewardItem = new ItemStack(
                    material,
                    Math.max(
                            1,
                            Math.min(
                                    amount,
                                    material.getMaxStackSize()
                            )
                    )
            );
        }

        List<String> commands =
                plugin.getConfig().getStringList(
                        path + ".commands"
                );

        return new GeneratedReward(
                rewardId,
                type,
                amount,
                displayItem,
                rewardItem,
                commands
        );
    }

    private ItemStack createDisplayItem(
            String path,
            RewardType type,
            int amount
    ) {
        String materialName =
                plugin.getConfig().getString(
                        path + ".display.material",
                        plugin.getConfig().getString(
                                path + ".material",
                                defaultMaterial(type)
                        )
                );

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

        String name = plugin.getConfig().getString(
                path + ".display.name",
                "&f" + type.name()
        );

        meta.displayName(GameManager.deserialize(
                replace(name, amount)
        ));

        List<Component> lore = new ArrayList<>();

        for (String line :
                plugin.getConfig().getStringList(
                        path + ".display.lore"
                )) {
            lore.add(GameManager.deserialize(
                    replace(line, amount)
            ));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());

        item.setItemMeta(meta);
        return item;
    }

    private String replace(String input, int amount) {
        return input.replace(
                "{amount}",
                String.valueOf(amount)
        );
    }

    private String defaultMaterial(RewardType type) {
        return switch (type) {
            case MONEY -> "GOLD_INGOT";
            case EXPERIENCE -> "EXPERIENCE_BOTTLE";
            case COMMAND -> "COMMAND_BLOCK";
            case NOTHING -> "GRAY_DYE";
            case ITEM -> "CHEST";
        };
    }

    public PurchaseResult purchase(
            Player player,
            String chestId
    ) {
        UUID uuid = player.getUniqueId();

        if (!purchaseLocks.add(uuid)) {
            return PurchaseResult.PROCESSING;
        }

        try {
            PlayerRewardSession session =
                    getSession(uuid);

            if (session == null) {
                return PurchaseResult.NO_SESSION;
            }

            GeneratedChest chest =
                    session.getChest(chestId);

            if (chest == null) {
                return PurchaseResult.INVALID_CHEST;
            }

            if (!chest.isUnlocked()) {
                return PurchaseResult.LOCKED;
            }

            if (chest.isClaimed()) {
                return PurchaseResult.ALREADY_CLAIMED;
            }

            boolean needsExtraClaim =
                    session.getClaimsUsed()
                            >= session.getDefaultClaims();

            String keyPath = "reward-key";

            boolean extraKeyEnabled =
                    plugin.getConfig().getBoolean(
                            keyPath + ".enabled",
                            true
                    );

            if (!extraKeyEnabled) {
                return PurchaseResult.MAXIMUM_CLAIMS;
            }

            int keySlot = -1;

            boolean consume =
                    plugin.getConfig().getBoolean(
                            keyPath + ".consume",
                            true
                    );

            if (needsExtraClaim) {
                if (consume) {
                    consumeKey(player, keySlot);
                }

                session.setExtraClaimUsed(true);
            }

            if (!canFitRewards(
                    player,
                    chest.getRewards()
            )) {
                return PurchaseResult.INVENTORY_FULL;
            }

            if (chest.getPrice() > 0L
                    && Dungeons.getEconomy()
                    .getBalance(player)
                    < chest.getPrice()) {
                return PurchaseResult.NO_MONEY;
            }

            EconomyResponse withdrawal = null;

            if (chest.getPrice() > 0L) {
                withdrawal = Dungeons.getEconomy()
                        .withdrawPlayer(
                                player,
                                chest.getPrice()
                        );

                if (!withdrawal.transactionSuccess()) {
                    return PurchaseResult.PAYMENT_ERROR;
                }
            }

            try {
                deliverRewards(
                        player,
                        chest.getRewards()
                );
            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "No se pudieron entregar recompensas a "
                                + player.getName()
                                + ": "
                                + exception.getMessage()
                );

                if (withdrawal != null
                        && withdrawal.transactionSuccess()) {
                    Dungeons.getEconomy().depositPlayer(
                            player,
                            chest.getPrice()
                    );
                }

                return PurchaseResult.DELIVERY_ERROR;
            }

            if (needsExtraClaim) {
                consumeKey(player, keySlot);
                session.setExtraClaimUsed(true);
            }

            chest.setClaimed(true);
            session.incrementClaims();

            return PurchaseResult.SUCCESS;
        } finally {
            purchaseLocks.remove(uuid);
        }
    }

    private void deliverRewards(
            Player player,
            List<GeneratedReward> rewards
    ) {
        for (GeneratedReward reward : rewards) {
            switch (reward.getType()) {
                case ITEM -> {
                    ItemStack item = reward.getRewardItem();

                    if (item != null) {
                        player.getInventory().addItem(item);
                    }
                }

                case MONEY ->
                        Dungeons.getEconomy().depositPlayer(
                                player,
                                reward.getAmount()
                        );

                case EXPERIENCE ->
                        player.giveExp(reward.getAmount());

                case COMMAND -> {
                    for (String raw :
                            reward.getCommands()) {
                        String command = raw
                                .replace(
                                        "{player}",
                                        player.getName()
                                )
                                .replace(
                                        "{uuid}",
                                        player.getUniqueId()
                                                .toString()
                                )
                                .replace(
                                        "{amount}",
                                        String.valueOf(
                                                reward.getAmount()
                                        )
                                );

                        Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                command
                        );
                    }
                }

                case NOTHING -> {
                }
            }
        }
    }

    private boolean canFitRewards(
            Player player,
            List<GeneratedReward> rewards
    ) {
        Inventory simulation = Bukkit.createInventory(
                null,
                36
        );

        ItemStack[] current =
                player.getInventory()
                        .getStorageContents();

        ItemStack[] cloned =
                new ItemStack[current.length];

        for (int i = 0; i < current.length; i++) {
            cloned[i] = current[i] == null
                    ? null
                    : current[i].clone();
        }

        simulation.setStorageContents(cloned);

        for (GeneratedReward reward : rewards) {
            if (reward.getType()
                    != RewardType.ITEM) {
                continue;
            }

            ItemStack item = reward.getRewardItem();

            if (item == null) {
                continue;
            }

            if (!simulation.addItem(item).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public ItemStack createExtraClaimKey(int amount) {
        String path = "reward-key";

        Material material = Material.matchMaterial(
                plugin.getConfig().getString(
                        path + ".material",
                        "TRIPWIRE_HOOK"
                )
        );

        if (material == null) {
            material = Material.TRIPWIRE_HOOK;
        }

        ItemStack item = new ItemStack(
                material,
                Math.max(1, amount)
        );

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(GameManager.deserialize(
                plugin.getConfig().getString(
                        path + ".name",
                        "&6Llave de cofre adicional"
                )
        ));

        List<Component> lore = new ArrayList<>();

        for (String line :
                plugin.getConfig().getStringList(
                        path + ".lore"
                )) {
            lore.add(GameManager.deserialize(line));
        }

        meta.lore(lore);

        int customModelData =
                plugin.getConfig().getInt(
                        path + ".custom-model-data",
                        0
                );

        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        meta.getPersistentDataContainer().set(
                extraClaimKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        item.setItemMeta(meta);
        return item;
    }

    private int findExtraClaimKey(Player player) {
        ItemStack[] contents =
                player.getInventory()
                        .getStorageContents();

        for (int slot = 0;
             slot < contents.length;
             slot++) {

            ItemStack item = contents[slot];

            if (item == null
                    || !item.hasItemMeta()) {
                continue;
            }

            Byte value = item.getItemMeta()
                    .getPersistentDataContainer()
                    .get(
                            extraClaimKey,
                            PersistentDataType.BYTE
                    );

            if (value != null && value == (byte) 1) {
                return slot;
            }
        }

        return -1;
    }

    private void consumeKey(
            Player player,
            int slot
    ) {
        ItemStack item =
                player.getInventory().getItem(slot);

        if (item == null) {
            return;
        }

        if (item.getAmount() <= 1) {
            player.getInventory().setItem(
                    slot,
                    null
            );
        } else {
            item.setAmount(item.getAmount() - 1);
        }
    }

    public void openMenu(Player player) {
        if (rewardChestMenu != null) {
            rewardChestMenu.open(player);
        }
    }

    public void clear() {
        sessions.clear();
        purchaseLocks.clear();
    }

    public Component message(
            String path,
            String defaultValue
    ) {
        return GameManager.deserialize(
                plugin.getConfig().getString(
                        path,
                        defaultValue
                )
        );
    }

    public enum PurchaseResult {
        SUCCESS,
        PROCESSING,
        NO_SESSION,
        INVALID_CHEST,
        LOCKED,
        ALREADY_CLAIMED,
        MAXIMUM_CLAIMS,
        NO_KEY,
        NO_MONEY,
        PAYMENT_ERROR,
        INVENTORY_FULL,
        DELIVERY_ERROR
    }
}