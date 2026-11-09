package org.rexi.dungeons.menu.rewards;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.rexi.dungeons.Dungeons;
import org.rexi.dungeons.rewards.GeneratedChest;
import org.rexi.dungeons.rewards.PlayerRewardSession;
import org.rexi.dungeons.rewards.DungeonRewardManager;

public final class RewardMenuListener
        implements Listener {

    private final Dungeons plugin;
    private final DungeonRewardManager manager;
    private final RewardChestMenu chestMenu;
    private final RewardPreviewMenu previewMenu;

    public RewardMenuListener(
            Dungeons plugin,
            DungeonRewardManager manager,
            RewardChestMenu chestMenu,
            RewardPreviewMenu previewMenu
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.chestMenu = chestMenu;
        this.previewMenu = previewMenu;
    }

    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent event
    ) {
        if (!(event.getWhoClicked()
                instanceof Player player)) {
            return;
        }

        if (event.getView()
                .getTopInventory()
                .getHolder()
                instanceof RewardMenuHolder holder) {

            handleChestMenu(
                    event,
                    player,
                    holder
            );
            return;
        }

        if (event.getView()
                .getTopInventory()
                .getHolder()
                instanceof RewardPreviewHolder holder) {

            handlePreviewMenu(
                    event,
                    player,
                    holder
            );
        }
    }

    private void handleChestMenu(
            InventoryClickEvent event,
            Player player,
            RewardMenuHolder holder
    ) {
        event.setCancelled(true);

        if (!holder.getPlayerUuid()
                .equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        if (event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        PlayerRewardSession session =
                manager.getSession(
                        player.getUniqueId()
                );

        if (session == null) {
            player.closeInventory();
            return;
        }

        String base =
                "rewards." + session.getDungeon();

        int rawSlot = event.getRawSlot();

        int closeSlot =
                plugin.getConfig().getInt(
                        base + ".menu.close.slot",
                        49
                );

        if (rawSlot == closeSlot) {
            player.closeInventory();
            return;
        }

        for (GeneratedChest chest :
                session.getChests().values()) {

            int chestSlot =
                    plugin.getConfig().getInt(
                            base
                                    + ".chests."
                                    + chest.getId()
                                    + ".slot",
                            -1
                    );

            if (rawSlot != chestSlot) {
                continue;
            }

            if (!chest.isUnlocked()) {
                player.sendMessage(manager.message(
                        "messages.rewards.locked",
                        "&cNo tienes puntuación suficiente."
                ));
                return;
            }

            if (chest.isClaimed()) {
                player.sendMessage(manager.message(
                        "messages.rewards.already_claimed",
                        "&cYa has reclamado este cofre."
                ));
                return;
            }

            previewMenu.open(
                    player,
                    chest.getId()
            );
            return;
        }
    }

    private void handlePreviewMenu(
            InventoryClickEvent event,
            Player player,
            RewardPreviewHolder holder
    ) {
        event.setCancelled(true);

        if (!holder.getPlayerUuid()
                .equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        if (event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        PlayerRewardSession session =
                manager.getSession(
                        player.getUniqueId()
                );

        if (session == null) {
            player.closeInventory();
            return;
        }

        String base =
                "rewards." + session.getDungeon();

        int slot = event.getRawSlot();

        int confirmSlot =
                plugin.getConfig().getInt(
                        base
                                + ".preview-menu.confirm.slot",
                        40
                );

        int backSlot =
                plugin.getConfig().getInt(
                        base
                                + ".preview-menu.back.slot",
                        48
                );

        int closeSlot =
                plugin.getConfig().getInt(
                        base
                                + ".preview-menu.close.slot",
                        50
                );

        if (slot == backSlot) {
            chestMenu.open(player);
            return;
        }

        if (slot == closeSlot) {
            player.closeInventory();
            return;
        }

        if (slot != confirmSlot) {
            return;
        }

        DungeonRewardManager.PurchaseResult result =
                manager.purchase(
                        player,
                        holder.getChestId()
                );

        handlePurchaseResult(
                player,
                session,
                holder.getChestId(),
                result
        );
    }

    private void handlePurchaseResult(
            Player player,
            PlayerRewardSession session,
            String chestId,
            DungeonRewardManager.PurchaseResult result
    ) {
        switch (result) {
            case SUCCESS -> {
                GeneratedChest chest =
                        session.getChest(chestId);

                String message =
                        plugin.getConfig().getString(
                                "messages.rewards.claimed",
                                "&aHas reclamado el cofre "
                                        + "&f{chest}&a."
                        );

                if (chest != null) {
                    message = message.replace(
                            "{chest}",
                            chest.getDisplayName()
                    );
                }

                player.sendMessage(
                        org.rexi.dungeons.manager.GameManager
                                .deserialize(message)
                );

                chestMenu.open(player);
            }

            case PROCESSING ->
                    send(player, "processing",
                            "&cYa se está procesando otra compra.");

            case NO_SESSION ->
                    send(player, "unavailable",
                            "&cNo tienes recompensas pendientes.");

            case INVALID_CHEST ->
                    send(player, "invalid_chest",
                            "&cEse cofre no está disponible.");

            case LOCKED ->
                    send(player, "locked",
                            "&cNo tienes puntuación suficiente.");

            case ALREADY_CLAIMED ->
                    send(player, "already_claimed",
                            "&cYa has reclamado este cofre.");

            case MAXIMUM_CLAIMS ->
                    send(player, "maximum_claims",
                            "&cYa has reclamado el máximo.");

            case NO_KEY ->
                    send(player, "no_key",
                            "&cNecesitas una llave adicional.");

            case NO_MONEY -> {
                GeneratedChest chest =
                        session.getChest(chestId);

                String message =
                        plugin.getConfig().getString(
                                "messages.rewards.no_money",
                                "&cNo tienes dinero suficiente. "
                                        + "Necesitas &6{price} monedas."
                        );

                if (chest != null) {
                    message = message.replace(
                            "{price}",
                            String.valueOf(chest.getPrice())
                    );
                }

                player.sendMessage(
                        org.rexi.dungeons.manager.GameManager
                                .deserialize(message)
                );
            }

            case PAYMENT_ERROR ->
                    send(player, "payment_error",
                            "&cNo se pudo procesar el pago.");

            case INVENTORY_FULL ->
                    send(player, "inventory_full",
                            "&cNo tienes espacio en el inventario.");

            case DELIVERY_ERROR ->
                    send(player, "payment_error",
                            "&cNo se pudieron entregar las recompensas.");
        }
    }

    private void send(
            Player player,
            String key,
            String fallback
    ) {
        player.sendMessage(manager.message(
                "messages.rewards." + key,
                fallback
        ));
    }
}