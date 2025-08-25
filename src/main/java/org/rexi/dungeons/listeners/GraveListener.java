package org.rexi.dungeons.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.rexi.dungeons.Dungeons;

import java.util.*;

public class GraveListener implements Listener {

    private final Dungeons plugin;
    private final Map<Location, GraveData> graves = new HashMap<>();

    public GraveListener(Dungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        FileConfiguration config = plugin.getConfig();

        String graveWorld = config.getString("graves.world");
        if (graveWorld == null) return;
        if (!player.getWorld().getName().equalsIgnoreCase(graveWorld)) return;

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (drops.isEmpty()) return;
        event.getDrops().clear(); // Evita que caigan al suelo

        // Crear tumba
        String materialName = config.getString("graves.material", "OBSIDIAN");
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) mat = Material.OBSIDIAN;

        Location loc = player.getLocation().getBlock().getLocation();
        Block block = loc.getBlock();
        if (materialName.equalsIgnoreCase("JUGADOR")) {
            block.setType(Material.PLAYER_HEAD);
            if (block.getState() instanceof org.bukkit.block.Skull skull) {
                skull.setOwningPlayer(player);
                skull.update(true);
            }
        } else {
            block.setType(mat);
        }

        long creationTime = System.currentTimeMillis();

        // Crear holograma encima de la tumba
        ArmorStand holo = spawnHologram(loc.add(0.5, 1.2, 0.5),
                config.getString("graves.holo", "&cTumba de {player} - {time}")
                        .replace("{player}", player.getName())
                        .replace("{time}", formatTime(5 * 60)));

        // Guardar tumba
        GraveData data = new GraveData(player.getUniqueId(), drops, creationTime, holo);
        graves.put(block.getLocation(), data);

        // ⚡ Actualizar holograma cada 1 segundo
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!graves.containsKey(block.getLocation())) {
                    cancel(); // tumba ya no existe
                    return;
                }

                long timePassed = (System.currentTimeMillis() - creationTime) / 1000;
                long timeLeft = 300 - timePassed;

                if (timeLeft <= 0) {
                    // Tiempo agotado, eliminar tumba
                    if (block.getType() != Material.AIR) block.setType(Material.AIR);
                    if (holo != null && !holo.isDead()) holo.remove();
                    graves.remove(block.getLocation());
                    cancel();
                    return;
                }

                // Actualizar nombre del holograma
                String newText = config.getString("graves.holo", "&cTumba de {player} - {time}")
                        .replace("{player}", player.getName())
                        .replace("{time}", formatTime(timeLeft));
                holo.setCustomName(ChatColor.translateAlternateColorCodes('&', newText));
            }
        }.runTaskTimer(plugin, 20L, 20L); // cada 1 segundo (20 ticks)

        player.sendMessage(deserialize(
                config.getString("messages.grave_created", "&6Se ha creado tu tumba. Tienes 5 minutos para recuperarla.")));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Location loc = event.getClickedBlock().getLocation();
        if (!graves.containsKey(loc)) return;

        Player player = event.getPlayer();
        GraveData data = graves.get(loc);

        long timePassed = (System.currentTimeMillis() - data.creationTime) / 1000;
        boolean isOwner = data.owner.equals(player.getUniqueId());

        if (timePassed < 300 && !isOwner) {
            player.sendMessage(deserialize(plugin.getConfig()
                    .getString("messages.no_grave", "&cAún no puedes abrir esta tumba. Solo el dueño puede hacerlo mientras tenga tiempo.")));
            return;
        }

        // ✅ Dar items, si no hay espacio → tirarlos al suelo
        for (ItemStack item : data.items) {
            if (item == null) continue;
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }

        // Borrar tumba
        loc.getBlock().setType(Material.AIR);
        if (data.hologram != null) data.hologram.remove();
        graves.remove(loc);

        player.sendMessage(deserialize(plugin.getConfig()
                .getString("messages.grave_taken", "&aHas recogido los objetos de la tumba")));
    }

    private ArmorStand spawnHologram(Location loc, String text) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, armorStand -> {
            armorStand.setVisible(false);
            armorStand.setCustomNameVisible(true);
            armorStand.setCustomName(ChatColor.translateAlternateColorCodes('&', text));
            armorStand.setGravity(false);
            armorStand.setMarker(true);
        });
        return stand;
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public void clearAllGraves() {
        for (Map.Entry<Location, GraveData> entry : graves.entrySet()) {
            Location loc = entry.getKey();
            GraveData data = entry.getValue();

            // Quitar bloque de la tumba
            if (loc.getBlock().getType() != Material.AIR) {
                loc.getBlock().setType(Material.AIR);
            }

            // Quitar holograma
            if (data.hologram != null && !data.hologram.isDead()) {
                data.hologram.remove();
            }
        }
        graves.clear();
    }

    private static class GraveData {
        private final UUID owner;
        private final List<ItemStack> items;
        private final long creationTime;
        private final ArmorStand hologram;

        public GraveData(UUID owner, List<ItemStack> items, long creationTime, ArmorStand hologram) {
            this.owner = owner;
            this.items = items;
            this.creationTime = creationTime;
            this.hologram = hologram;
        }
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
