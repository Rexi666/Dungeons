package org.rexi.dungeons.manager;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.rexi.dungeons.Dungeons;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClassManager implements Listener {

    private static FileConfiguration getConfig() {
        return Dungeons.getInstance().getConfig();
    }

    public static void Repetitive() {
        HealerRegeneration();
        BerserkerEffects();
        TankResistance();
    }

    //--------------------------------
    //            HEALER             |
    //--------------------------------

    public static void HealerRegeneration() {
        for (String dungeon : GameManager.dungeonStarted) {
            for (UUID uuid : GameManager.getPlayersInDungeonList(dungeon)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                if (Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("healer")) {
                    if (getConfig().getBoolean("classes.healer.abilities.regeneracion.enabled")) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5*20, 2, false, false));
                    }

                    if (getConfig().getBoolean("classes.healer.abilities.circulo_curación.enabled")) {
                        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
                            if (entity instanceof Player nearbyPlayer) {
                                nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5*20, 1, false, false));
                            }
                        }
                    }
                }
            }
        }
    }

    //--------------------------------
    //             MAGE              |
    //--------------------------------

    private final Map<UUID, Long> ovejasCooldown = new HashMap<>();
    private final Map<UUID, Long> rayosCooldown = new HashMap<>();
    @EventHandler
    public void MageSwordClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("mage")) return;

        String dungeon = GameManager.getDungeonOfPlayer(player);
        if (dungeon.isEmpty() || !GameManager.dungeonStarted.contains(dungeon)) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getItem() == null) return;

            Material type = event.getItem().getType();
            if (type == Material.DIAMOND_SWORD || type == Material.WOODEN_SWORD ||
                    type == Material.STONE_SWORD || type == Material.IRON_SWORD ||
                    type == Material.GOLDEN_SWORD || type == Material.NETHERITE_SWORD) {

                UUID uuid = player.getUniqueId();
                long now = System.currentTimeMillis();

                if (player.isSneaking()) {
                    if (!getConfig().getBoolean("classes.mage.abilities.tormenta_electrica.enabled")) return;
                    // -----> HABILIDAD DE RAYOS <-----
                    long cooldown = getConfig().getLong("classes.mage.abilities.tormenta_electrica.cooldown", 20) * 1000;
                    if (rayosCooldown.containsKey(uuid)) {
                        long lastUse = rayosCooldown.get(uuid);
                        if (now - lastUse < cooldown) {
                            long timeLeft = (cooldown - (now - lastUse)) / 1000;
                            String wait = timeLeft+"s";
                            player.sendMessage(GameManager.deserialize(getConfig()
                                    .getString("messages.cooldown", "&cEsa habilidad está en cooldown! {cooldown}")
                                    .replace("{cooldown}", wait)));
                            return;
                        }
                    }

                    rayosCooldown.put(uuid, now);

                    // Disparar 5 rayos a donde el jugador está mirando
                    Location eye = player.getEyeLocation();
                    Vector dir = eye.getDirection().normalize();

                    for (int i = 1; i <= 5; i++) {
                        Location strikeLoc = eye.clone().add(dir.clone().multiply(i * 4)); // cada 4 bloques
                        player.getWorld().strikeLightning(strikeLoc);
                    }

                    String name = getConfig().getString("classes.mage.abilities.tormenta_electrica.name", "&cTormenta eléctrica");

                    player.sendMessage(GameManager.deserialize(getConfig()
                            .getString("messages.ability", "&aHas usado la habilidad: {ability}")
                            .replace("{ability}", name)));
                } else {
                    if (!getConfig().getBoolean("classes.mage.abilities.ovejas_guiadas.enabled")) return;
                    // -----> HABILIDAD DE OVEJA EXPLOSIVA <-----
                    long cooldown = getConfig().getLong("classes.mage.abilities.ovejas_guiadas.cooldown", 10) * 1000;
                    if (ovejasCooldown.containsKey(uuid)) {
                        long lastUse = ovejasCooldown.get(uuid);
                        if (now - lastUse < cooldown) {
                            long timeLeft = (cooldown - (now - lastUse)) / 1000;
                            String wait = timeLeft+"s";
                            player.sendMessage(GameManager.deserialize(getConfig()
                                    .getString("messages.cooldown", "&cEsa habilidad está en cooldown! {cooldown}")
                                    .replace("{cooldown}", wait)));
                            return;
                        }
                    }

                    ovejasCooldown.put(uuid, now);

                    // Lanzar oveja
                    Sheep sheep = (Sheep) player.getWorld().spawnEntity(
                            player.getEyeLocation().add(player.getLocation().getDirection().multiply(1)),
                            EntityType.SHEEP
                    );
                    sheep.setCustomName(ChatColor.translateAlternateColorCodes('&', "&cOveja Explosiva"));
                    sheep.setCustomNameVisible(true);
                    sheep.setInvulnerable(true);

                    sheep.setVelocity(player.getLocation().getDirection().multiply(1.2));

                    String name = getConfig().getString("classes.mage.abilities.ovejas_guiadas.name", "&cOvejas guiadas");

                    player.sendMessage(GameManager.deserialize(getConfig()
                            .getString("messages.ability", "&aHas usado la habilidad: {ability}")
                            .replace("{ability}", name)));

                    Bukkit.getScheduler().runTaskLater(Dungeons.getInstance(), () -> {
                        Location loc = sheep.getLocation();
                        sheep.remove();

                        double radius = 3.5;
                        for (Entity entity : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
                            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                                ((LivingEntity) entity).damage(8.0, player);
                            }
                        }

                        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3);
                        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

                    }, 40L); // 2 segundos
                }
            }
        }
    }

    //--------------------------------
    //           BERSERKER           |
    //--------------------------------

    private final Map<UUID, Long> hachaCooldown = new HashMap<>();
    @EventHandler
    public void BerserkerAxeClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("berserker")) return;

        String dungeon = GameManager.getDungeonOfPlayer(player);
        if (dungeon.isEmpty() || !GameManager.dungeonStarted.contains(dungeon)) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getItem() == null) return;

            Material type = event.getItem().getType();
            if (type == Material.DIAMOND_AXE || type == Material.WOODEN_AXE ||
                    type == Material.STONE_AXE || type == Material.IRON_AXE ||
                    type == Material.GOLDEN_AXE || type == Material.NETHERITE_AXE) {

                UUID uuid = player.getUniqueId();
                long now = System.currentTimeMillis();

                if (!getConfig().getBoolean("classes.berserker.abilities.tomahok.enabled")) return;
                // -----> HABILIDAD DE LANZAR HACHA <-----
                long cooldown = getConfig().getLong("classes.berserker.abilities.tomahok.cooldown", 15) * 1000;
                if (hachaCooldown.containsKey(uuid)) {
                    long lastUse = hachaCooldown.get(uuid);
                    if (now - lastUse < cooldown) {
                        long timeLeft = (cooldown - (now - lastUse)) / 1000;
                        String wait = timeLeft+"s";
                        player.sendMessage(GameManager.deserialize(getConfig()
                                .getString("messages.cooldown", "&cEsa habilidad está en cooldown! {cooldown}")
                                .replace("{cooldown}", wait)));
                        return;
                    }
                }

                hachaCooldown.put(uuid, now);

                ItemStack axe = new ItemStack(type);
                // Crear un armorstand invisible que lleve el hacha en la mano
                ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(player.getEyeLocation(), EntityType.ARMOR_STAND);
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setGravity(false);
                stand.getEquipment().setItemInMainHand(axe);

                // Dirección hacia donde se va a mover
                Vector direction = player.getLocation().getDirection().normalize().multiply(1.2);

                String name = getConfig().getString("classes.berserker.abilities.tomahok.name", "&eTomahok");

                player.sendMessage(GameManager.deserialize(getConfig()
                        .getString("messages.ability", "&aHas usado la habilidad: {ability}")
                        .replace("{ability}", name)));

                new BukkitRunnable() {
                    int ticks = 0;

                    @Override
                    public void run() {
                        if (stand.isDead() || ticks > 40) { // 2 segundos de vida
                            stand.remove();
                            cancel();
                            return;
                        }

                        // Mover el stand hacia adelante
                        stand.teleport(stand.getLocation().add(direction));

                        // Rotar el hacha (como si girase en el aire)
                        Location loc = stand.getLocation();
                        loc.setYaw(loc.getYaw() + 40); // velocidad de giro
                        stand.teleport(loc);

                        // Daño a entidades cercanas
                        for (Entity entity : stand.getNearbyEntities(1, 1, 1)) {
                            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                                ((LivingEntity) entity).damage(10.0, player);
                                stand.remove();
                                cancel();
                                return;
                            }
                        }

                        ticks++;
                    }
                }.runTaskTimer(Dungeons.getInstance(), 1, 1);
            }
        }
    }

    public static void BerserkerEffects() {
        for (String dungeon : GameManager.dungeonStarted) {
            for (UUID uuid : GameManager.getPlayersInDungeonList(dungeon)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                if (Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("berserker")) {
                    if (getConfig().getBoolean("classes.berserker.abilities.ragnarok.enabled")) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5*20, 1, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5*20, 1, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 5*20, 1, false, false));
                    }
                }
            }
        }
    }

    //--------------------------------
    //           FLETCHER            |
    //--------------------------------

    @EventHandler
    public void FletcherShoot(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;

        // Comprobar clase del jugador
        if (!Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("fletcher")) return;

        String dungeon = GameManager.getDungeonOfPlayer(player);
        if (dungeon.isEmpty() || !GameManager.dungeonStarted.contains(dungeon)) return;

        // Evitar que explote si golpea a un jugador
        if (event.getEntity() instanceof Player) return;

        // Probabilidad del 20%
        double probability = getConfig().getDouble("classes.fletcher.abilities.disparo_explosivo.probability", 20) / 100.0;
        if (Math.random() <= probability) {
            // Crear explosión (sin romper bloques, solo daño visual/entidades)
            Location loc = arrow.getLocation();
            for (Entity entity : arrow.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    ((LivingEntity) entity).damage(8.0, player);
                }
            }

            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

            String name = getConfig().getString("classes.fletcher.abilities.disparo_explosivo.name", "&bDisparo explosivo");
            player.sendMessage(GameManager.deserialize(
                    getConfig().getString("messages.ability", "&aHas usado la habilidad: {ability}")
                            .replace("{ability}", name)
            ));
        }
    }

    private final Map<UUID, Long> arcoCooldown = new HashMap<>();
    @EventHandler
    public void FletcherBowClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("fletcher")) return;

        String dungeon = GameManager.getDungeonOfPlayer(player);
        if (dungeon.isEmpty() || !GameManager.dungeonStarted.contains(dungeon)) return;

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (event.getItem() == null) return;

            Material type = event.getItem().getType();
            if (type == Material.BOW) {

                UUID uuid = player.getUniqueId();
                long now = System.currentTimeMillis();

                if (!getConfig().getBoolean("classes.fletcher.abilities.ametralladora.enabled")) return;
                // -----> HABILIDAD DE MUCHAS FLECHAS <-----
                long cooldown = getConfig().getLong("classes.fletcher.abilities.ametralladora.cooldown", 20) * 1000;
                if (arcoCooldown.containsKey(uuid)) {
                    long lastUse = arcoCooldown.get(uuid);
                    if (now - lastUse < cooldown) {
                        long timeLeft = (cooldown - (now - lastUse)) / 1000;
                        String wait = timeLeft+"s";
                        player.sendMessage(GameManager.deserialize(getConfig()
                                .getString("messages.cooldown", "&cEsa habilidad está en cooldown! {cooldown}")
                                .replace("{cooldown}", wait)));
                        return;
                    }
                }

                arcoCooldown.put(uuid, now);

                // lanzar 10 flechas cargadas en ráfaga
                int totalFlechas = getConfig().getInt("classes.fletcher.abilities.ametralladora.arrows", 10);
                long delayTicks = 6L; // intervalo entre flechas (2 ticks = 0.1s)

                for (int i = 0; i < totalFlechas; i++) {
                    int finalI = i;
                    Bukkit.getScheduler().runTaskLater(Dungeons.getInstance(), () -> {
                        Arrow arrow = player.launchProjectile(Arrow.class);
                        arrow.setVelocity(player.getLocation().getDirection().multiply(2)); // velocidad extra
                        arrow.setCritical(true); // como si estuviera cargada al máximo
                        arrow.setShooter(player);

                        // opcional: puedes marcarla con metadata si quieres diferenciarla después
                        arrow.setMetadata("ametralladora", new FixedMetadataValue(Dungeons.getInstance(), true));
                    }, finalI * delayTicks);
                }

                String name = getConfig().getString("classes.fletcher.abilities.ametralladora.name", "&bAmetralladora");

                player.sendMessage(GameManager.deserialize(getConfig()
                        .getString("messages.ability", "&aHas usado la habilidad: {ability}")
                        .replace("{ability}", name)));
            }
        }
    }

    //--------------------------------
    //             TANK              |
    //--------------------------------

    public static void TankResistance() {
        for (String dungeon : GameManager.dungeonStarted) {
            for (UUID uuid : GameManager.getPlayersInDungeonList(dungeon)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                if (Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("tank")) {
                    if (getConfig().getBoolean("classes.tank.abilities.vida_defensa.enabled")) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 5*20, 3, false, false));
                    }
                }
            }
        }
    }
    public static void TankHealth(String dungeon) {
        for (UUID uuid : GameManager.getPlayersInDungeonList(dungeon)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("tank")) {
                if (getConfig().getBoolean("classes.tank.abilities.vida_defensa.enabled")) {
                    player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0); // 40 = 20 corazones
                    player.setHealth(40.0); // para curarlo al máximo

                }
            }
        }
    }

    private final Map<UUID, Long> castilloCooldown = new HashMap<>();
    private final Map<UUID, Long> terremotoCooldown = new HashMap<>();
    @EventHandler
    public void TankSwordClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("tank")) return;

        String dungeon = GameManager.getDungeonOfPlayer(player);
        if (dungeon.isEmpty() || !GameManager.dungeonStarted.contains(dungeon)) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getItem() == null) return;

            Material type = event.getItem().getType();
            if (type == Material.DIAMOND_SWORD || type == Material.WOODEN_SWORD ||
                    type == Material.STONE_SWORD || type == Material.IRON_SWORD ||
                    type == Material.GOLDEN_SWORD || type == Material.NETHERITE_SWORD) {

                UUID uuid = player.getUniqueId();
                long now = System.currentTimeMillis();

                if (player.isSneaking()) {
                    if (!getConfig().getBoolean("classes.tank.abilities.sismo.enabled")) return;
                    // -----> TERREMOTO <-----
                    long cooldown = getConfig().getLong("classes.tank.abilities.sismo.cooldown", 20) * 1000;
                    if (terremotoCooldown.containsKey(uuid)) {
                        long lastUse = terremotoCooldown.get(uuid);
                        if (now - lastUse < cooldown) {
                            long timeLeft = (cooldown - (now - lastUse)) / 1000;
                            String wait = timeLeft + "s";
                            player.sendMessage(GameManager.deserialize(getConfig()
                                    .getString("messages.cooldown", "&cEsa habilidad está en cooldown! {cooldown}")
                                    .replace("{cooldown}", wait)));
                            return;
                        }
                    }

                    terremotoCooldown.put(uuid, now);

                    double radius = 7.5;
                    double damage = 10.0; // 5 corazones
                    double knockback = 1.5;

                    for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                            LivingEntity target = (LivingEntity) entity;

                            // Aplica daño
                            target.damage(damage, player);

                            // Knockback (hacia afuera del jugador)
                            Vector v = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(knockback);
                            v.setY(0.5); // un poco hacia arriba
                            target.setVelocity(v);

                            // Partículas de tierra
                            player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation(), 50, 1, 0.2, 1, Material.DIRT.createBlockData());
                        }
                    }

                    // Sonido fuerte tipo explosión
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 1f, 1f);

                    String name = getConfig().getString("classes.tank.abilities.sismo.name", "&4Sismo");

                    player.sendMessage(GameManager.deserialize(getConfig()
                            .getString("messages.ability", "&aHas usado la habilidad: {ability}")
                            .replace("{ability}", name)));
                } else {
                    if (!getConfig().getBoolean("classes.tank.abilities.castillo_piedra.enabled")) return;
                    // -----> RESISTENCIA <-----
                    long cooldown = getConfig().getLong("classes.tank.abilities.castillo_piedra.cooldown", 15) * 1000;
                    if (castilloCooldown.containsKey(uuid)) {
                        long lastUse = castilloCooldown.get(uuid);
                        if (now - lastUse < cooldown) {
                            long timeLeft = (cooldown - (now - lastUse)) / 1000;
                            String wait = timeLeft + "s";
                            player.sendMessage(GameManager.deserialize(getConfig()
                                    .getString("messages.cooldown", "&cEsa habilidad está en cooldown! {cooldown}")
                                    .replace("{cooldown}", wait)));
                            return;
                        }
                    }

                    castilloCooldown.put(uuid, now);

                    int duration = getConfig().getInt("classes.tank.abilities.castillo_piedra.time", 10);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration * 20, 3, false, false));

                    String name = getConfig().getString("classes.tank.abilities.castillo_piedra.name", "&4Castillo de piedra");

                    player.sendMessage(GameManager.deserialize(getConfig()
                            .getString("messages.ability", "&aHas usado la habilidad: {ability}")
                            .replace("{ability}", name)));
                }
            }
        }
    }
}
