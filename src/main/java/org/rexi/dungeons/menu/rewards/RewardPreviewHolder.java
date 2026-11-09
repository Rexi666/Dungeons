package org.rexi.dungeons.menu.rewards;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class RewardPreviewHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final String chestId;

    public RewardPreviewHolder(UUID playerUuid, String chestId) {
        this.playerUuid = playerUuid;
        this.chestId = chestId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getChestId() {
        return chestId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}