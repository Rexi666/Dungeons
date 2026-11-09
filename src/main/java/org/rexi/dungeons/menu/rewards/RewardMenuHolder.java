package org.rexi.dungeons.menu.rewards;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class RewardMenuHolder implements InventoryHolder {

    private final UUID playerUuid;

    public RewardMenuHolder(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}