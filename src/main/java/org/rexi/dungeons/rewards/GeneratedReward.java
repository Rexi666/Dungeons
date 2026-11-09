package org.rexi.dungeons.rewards;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GeneratedReward {

    private final String id;
    private final RewardType type;
    private final int amount;
    private final ItemStack displayItem;
    private final ItemStack rewardItem;
    private final List<String> commands;

    public GeneratedReward(
            String id,
            RewardType type,
            int amount,
            ItemStack displayItem,
            ItemStack rewardItem,
            List<String> commands
    ) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.displayItem = displayItem == null
                ? null
                : displayItem.clone();
        this.rewardItem = rewardItem == null
                ? null
                : rewardItem.clone();
        this.commands = commands == null
                ? List.of()
                : new ArrayList<>(commands);
    }

    public String getId() {
        return id;
    }

    public RewardType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public ItemStack getDisplayItem() {
        return displayItem == null
                ? null
                : displayItem.clone();
    }

    public ItemStack getRewardItem() {
        return rewardItem == null
                ? null
                : rewardItem.clone();
    }

    public List<String> getCommands() {
        return Collections.unmodifiableList(commands);
    }
}