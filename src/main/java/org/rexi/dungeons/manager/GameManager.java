package org.rexi.dungeons.manager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.papermc.paper.event.entity.EntityMoveEvent;
import me.ulrich.clans.data.ClanData;
import me.ulrich.clans.interfaces.UClans;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.rexi.dungeons.Dungeons;
import org.rexi.dungeons.database.PendingReward;
import org.rexi.dungeons.menu.ClassMenu;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.*;

public class GameManager implements Listener {

    private static UClans uclans;

    public GameManager(UClans uclans) {
        this.uclans = uclans;
    }

    private static FileConfiguration getConfig() {
        return Dungeons.getInstance().getConfig();
    }

    private static final Random random = new Random();

    public static final List<String> dungeonStarted = new ArrayList<>();

    public static final Map<String, List<UUID>> dungeonPlayers = new HashMap<>();

    public static final List<UUID> allDungeonPlayers = new ArrayList<>();

    public static final List<String> possibleDungeons = new ArrayList<>();

    public static final Map<UUID, String> joiningPlayers = new HashMap<>();

    public static final Map<String, Boolean> dungeonStarting = new HashMap<>();

    private static final Map<String, Integer> countdownTasks = new HashMap<>();

    public static final Map<String, Boolean> canStartDungeon = new HashMap<>();
    public static final Map<String, ClanData> DungeonClan = new HashMap<>();
    public static final Map<String, String> dungeonType = new HashMap<>();

    private static final int maxPlayers = getConfig().getInt("dungeons.players.max", 5);
    private static final int minPlayers = getConfig().getInt("dungeons.players.min", 1);

    private static final long DUNGEON_COOLDOWN = 24L * 60L * 60L * 1000L; // 24 horas en milisegundos

    //--------------------------------
    //           MÉTODOS             |
    //--------------------------------

    public static void join(Player player, String dungeon) {
        if (!possibleDungeons.contains(dungeon)) {
            player.sendMessage(deserialize(
                    getConfig().getString("messages.join_nodungeon", "&cIntentaste entrar en la dungeon {dungeon} pero no existe")
                            .replace("{dungeon}", dungeon)));
            return;
        }

        int dungeonLevel = getConfig().getInt("dungeons.bosses."+dungeon);
        int level = (Dungeons.getInstance().getDungeonLevel(player)+1);

        if (level < dungeonLevel) {
            player.sendMessage(deserialize(
                    getConfig()
                            .getString("messages.join_no_level",
                                    "&cNo puedes entrar en esta dungeon porque no tienes el nivel suficiente: Tu nivel {level}, necesario {required}")
                            .replace("{level}", String.valueOf(level))
                            .replace("{required}", String.valueOf(dungeonLevel))));
            return;
        }

        if (dungeonStarted.contains(dungeon)) {
            player.sendMessage(deserialize(
                    getConfig().getString("messages.join_started", "&cEsa dungeon ya está en una incursión activa")));
            return;
        }

        if (!getDungeonOfPlayer(player).isEmpty()) {
            player.sendMessage(deserialize(
                    getConfig().getString("messages.join_already", "&cYa estás en una dungeon. Usa &b/dungeon leave &cpara salir")));
            return;
        }

        if (!canStartDungeon.getOrDefault(dungeon, false)) {
            player.sendMessage(deserialize(getConfig()
                    .getString("messages.dungeon_not_started", "&cNo puedes entrar a esta dungeon, no está iniciada! Puedes iniciarla con &b/dungeon start")));
            return;
        }

        if (dungeonPlayers.containsKey(dungeon) && dungeonPlayers.get(dungeon).size() == maxPlayers) {
            player.sendMessage(deserialize(
                    getConfig().getString("messages.join_max", "&cLa dungeon a la que intentaste entrar está llena")));
            return;
        }

        Optional<ClanData> optional_clan = uclans.getPlayerAPI().getPlayerClan(player.getUniqueId());

        ClanData clan = null;

        if (optional_clan.isPresent()) {
            clan = optional_clan.get();
        }

        ClanData clandungeon = DungeonClan.get(dungeon);

        if (clandungeon != null) {
            if (clan == null || !Objects.equals(clandungeon.getId(), clan.getId())) {
                player.sendMessage(deserialize(
                        getConfig().getString("messages.dungeon_clan", "&cSolo los miembros del clan que inició la dungeon pueden unirse")));
                return;
            }
        }

        boolean checkCooldown = Dungeons.getInstance().ReviewLastDungeonJoin(player, dungeon, DUNGEON_COOLDOWN);

        if (!checkCooldown) {
            return;
        }

        ClassMenu.openMenu(player);
        joiningPlayers.put(player.getUniqueId(), dungeon);
    }

    private void joinPlayer(Player player) {
        String dungeon = joiningPlayers.get(player.getUniqueId());
        joiningPlayers.remove(player.getUniqueId());
        addPlayerToDungeonList(dungeon, player);
        start(dungeon, false);
        player.sendMessage(deserialize(
                getConfig().getString("messages.joined", "&aHas entrado en la dungeon &b{dungeon}")
                        .replace("{dungeon}", dungeon)));
        Bukkit.broadcast(deserialize(getConfig()
                .getString("messages.broadcast_join", "&7[&dDUNGEONS&7] &b{player} &eha entrado en la dungeon &b{dungeon} &7({current}/{max})")
                .replace("{player}", player.getName())
                .replace("{dungeon}", dungeon)
                .replace("{current}", String.valueOf(getPlayersInDungeonList(dungeon).size()))
                .replace("{max}", String.valueOf(maxPlayers))));
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = PLAIN.serialize(event.getView().title());
        String menuTitle = PLAIN.serialize(deserialize(
                getConfig().getString("menu.title", "&fElige tu clase")));

        if (!title.equalsIgnoreCase(menuTitle)) return;

        if (Dungeons.getInstance().getPlayerClass(player).isEmpty()) {
            Bukkit.getScheduler().runTaskLater(Dungeons.getInstance(), () -> {
                if (Dungeons.getInstance().getPlayerClass(player).isEmpty()) {
                    ClassMenu.openMenu(player);
                    player.sendMessage(deserialize(
                            getConfig().getString("messages.force_selectclass", "&cTienes que seleccionar una clase!")));
                }
            }, 1L);
        } else {
            joinPlayer(player);
        }
    }

    public static void leave(Player player, boolean message) {
        String dungeon = getDungeonOfPlayer(player);
        if (!dungeon.isEmpty()) {
            if (dungeonStarted.contains(dungeon)) {
                player.sendMessage(deserialize(getConfig()
                        .getString("leave_cant","&cNo puedes salir de una dungeon que ya ha iniciado!")));
                return;
            }
            stop(dungeon, false);
            removePlayerFromDungeonList(dungeon, player);
            Bukkit.broadcast(deserialize(getConfig()
                    .getString("messages.broadcast_leave", "&7[&dDUNGEONS&7] &b{player} &eha salido de la dungeon &b{dungeon} &7({current}/{max})")
                    .replace("{player}", player.getName())
                    .replace("{dungeon}", dungeon)
                    .replace("{current}", String.valueOf(getPlayersInDungeonList(dungeon).size()))
                    .replace("{max}", String.valueOf(maxPlayers))));

            if (message) {
                player.sendMessage(deserialize(
                        getConfig().getString("messages.leaved", "&aHas salido de la dungeon &b{dungeon}")
                                .replace("{dungeon}", dungeon)));
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        leave(player, false);
    }

    public static void start(String dungeon, boolean force) {
        if (dungeonStarting.getOrDefault(dungeon, false) && !force) return;
        if (dungeonStarted.contains(dungeon)) return;
        String type = dungeonType.getOrDefault(dungeon, "");
        if (type.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage(Component
                    .text("Error al intentar iniciar la dungeon "+dungeon+" ya que no está iniciada correctamente")
                    .color(NamedTextColor.RED));
            return;
        }

        int dungeonSize = dungeonPlayers.get(dungeon).size();
        if (!force && dungeonPlayers.containsKey(dungeon) && dungeonSize < minPlayers) {
            List<UUID> players = getPlayersInDungeonList(dungeon);
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) player.sendMessage(deserialize(getConfig()
                        .getString("messages.cant_start", "&cSe necesitan {more} jugadores más para empezar esta dungeon")
                                .replace("{more}", String.valueOf(minPlayers-dungeonSize))));
            }
            return;
        }

        if (!force) {
            startCountdown(dungeon, getConfig().getInt("times.pre_game", 25));
            dungeonStarting.put(dungeon, true);
            return;
        }

        clearDungeon(dungeon);
        List<UUID> players = getPlayersInDungeonList(dungeon);
        Location teleport = getLocation("dungeons.bosses."+dungeon+".teleport");
        if (teleport != null) {
            teleport.getChunk().load();
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) player.teleport(teleport);
            }
        }
        dungeonStarted.add(dungeon);
        dungeonStarting.put(dungeon, false);
        canStartDungeon.put(dungeon, false);
        DungeonClan.remove(dungeon);

        ClassManager.TankHealth(dungeon);

        for (UUID uuid : getPlayersInDungeonList(dungeon)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Dungeons.getInstance().setLastDungeonJoin(String.valueOf(uuid), dungeon);
            }
        }

        // cuenta atrás para comprobar si muere el boss o se acaba el tiempo
        startDungeonTimer(dungeon, getConfig().getInt("times.game", 300));

        List<String> commands = getConfig().getStringList("dungeons.bosses."+dungeon+"."+type+".start_commands");
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private static final Map<String, Integer> dungeonTimers = new HashMap<>();
    private static final Map<String, BossBar> dungeonBossBars = new HashMap<>();

    private static void startDungeonTimer(String dungeon, int seconds) {
        // Si ya había bossbar, ciérrala
        cancelDungeonTimer(dungeon);

        BossBar bar = Bukkit.createBossBar("Tiempo restante", BarColor.RED, BarStyle.SOLID);
        dungeonBossBars.put(dungeon, bar);

        for (UUID uuid : getPlayersInDungeonList(dungeon)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                bar.addPlayer(p);
            }
        }

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Dungeons.getInstance(), new Runnable() {
            int time = seconds;

            @Override
            public void run() {
                if (!dungeonStarted.contains(dungeon)) {
                    cancelDungeonTimer(dungeon);
                    return;
                }

                if (time <= 0) {
                    // Tiempo agotado -> derrota
                    cancelDungeonTimer(dungeon);
                    for (UUID uuid : getPlayersInDungeonList(dungeon)) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(deserialize(getConfig().getString("messages.time_over",
                                    "&c¡Se acabó el tiempo! Habéis perdido la dungeon.")));
                            p.sendTitle(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.time_over_title", "&cSe acabó el tiempo!")), "", 10, 40,10);
                        }
                    }
                    stop(dungeon, true);
                    return;
                }

                double progress = (double) time / seconds;
                bar.setProgress(Math.max(0, Math.min(1, progress)));

                // Texto con PlaceholderAPI
                int minutes = time / 60;
                int seconds = time % 60;

                String formatted;
                if (time >= 60) {
                    formatted = String.format("%02dm %02ds", minutes, seconds); // mm:ss
                } else {
                    formatted = seconds + "s";
                }

                String raw = getConfig().getString("messages.bossbar_timer", "&eTiempo restante: &b{time}");
                for (UUID uuid : getPlayersInDungeonList(dungeon)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        String parsed = raw.replace("{time}", formatted);
                        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                            parsed = PlaceholderAPI.setPlaceholders(p, parsed);
                        }
                        bar.setTitle(ChatColor.translateAlternateColorCodes('&', parsed));
                    }
                }

                time--;
            }
        }, 0L, 20L);

        dungeonTimers.put(dungeon, taskId);
    }


    public static void stop(String dungeon, boolean force) {
        if (dungeonStarting.getOrDefault(dungeon, false)) {
            int dungeonSize = dungeonPlayers.get(dungeon).size();
            if (!force && dungeonPlayers.containsKey(dungeon) && dungeonSize < minPlayers) {
                cancelCountdown(dungeon);
                return;
            }
        }

        if (!dungeonStarted.contains(dungeon)) return;

        String type = dungeonType.getOrDefault(dungeon, "");
        if (!type.isEmpty()) {
            List<String> commands = getConfig().getStringList("dungeons.bosses."+dungeon+"."+type+".stop_commands");
            for (String command : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        } else {
            Bukkit.getConsoleSender().sendMessage(Component
                    .text("No se ha podido ver el tipo de la dungeon "+dungeon+" y no se han dado recompensas")
                    .color(NamedTextColor.RED));
        }
        clearDungeon(dungeon);
        dungeonStarted.remove(dungeon);
        clearChests(dungeon);

        List<UUID> players = new ArrayList<>(getPlayersInDungeonList(dungeon));
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removePlayerFromDungeonList(dungeon, player);
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
                player.setHealth(20.0);
            }
        }
    }

    public static void giveReward(String dungeon) {
        if (!dungeonStarted.contains(dungeon)) {
            Bukkit.getConsoleSender().sendMessage(Component
                    .text("Se ha intentado dar la recompensa a la dungeon " + dungeon + " pero no estaba iniciada")
                    .color(NamedTextColor.RED));
            return;
        }

        String type = dungeonType.get(dungeon);

        ConfigurationSection dungeonSec = getConfig().getConfigurationSection("rewards."+dungeon+"."+type);

        if (dungeonSec != null) {
            List<String> possibilities = dungeonSec.getStringList("possibilities");
            int requiredSpace = dungeonSec.getInt("inv_space", 0);

            if (possibilities.isEmpty()) {
                Bukkit.getConsoleSender().sendMessage(Component
                        .text("No se han encontrado recompensas para la dungeon "+dungeon+ " "+ type)
                        .color(NamedTextColor.RED));
                return;
            }

            int dungeonLevel = getConfig().getInt("dungeons.bosses."+dungeon);

            for (UUID uuid : getPlayersInDungeonList(dungeon)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;

                int level = (Dungeons.getInstance().getDungeonLevel(player)+1);
                if (level == dungeonLevel) Dungeons.getInstance().setDungeonLevel(player, dungeonLevel);
            }

            for (UUID uuid : getPlayersInDungeonList(dungeon)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;

                int freeSlots = getFreeInventorySlots(player);
                if (freeSlots < requiredSpace) {
                    List<String> cmds = new ArrayList<>();
                    for (String cmd : possibilities) {
                        cmds.add(cmd.replace("{player}", player.getName()));
                    }

                    Dungeons.getInstance().addPendingReward(player.getUniqueId().toString(), requiredSpace, cmds);
                    player.sendMessage(deserialize(
                            getConfig().getString(
                                    "messages.no-inventory-space",
                                    "&cNo tienes espacio suficiente en el inventario. Usa &e/dungeon claimrewards"
                            )
                    ));
                    continue;
                }

                for (String cmd : possibilities) {
                    String command = cmd.replace("{player}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }

                player.sendMessage(deserialize(
                        getConfig().getString("messages.rewards", "&aHas recibido las recompensas de esta dungeon")));
            }
        } else {
            Bukkit.getConsoleSender().sendMessage(Component
                    .text("No se han encontrado recompensas para la dungeon "+dungeon+ " "+ type)
                    .color(NamedTextColor.RED));
        }
    }

    protected static void clearDungeon(String dungeon) {
        String worldName = getConfig().getString("classes.world");
        World world = (worldName != null) ? Bukkit.getWorld(worldName) : Bukkit.getWorlds().get(0);
        if (world == null) return;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));

        if (manager == null) return;
        ProtectedRegion region = manager.getRegion(dungeon);
        if (region == null) return;

        // Expulsar jugadores
        Location out = getLocation("dungeons.bosses." + dungeon + ".out_cords");
        if (out == null) out = world.getSpawnLocation();

        for (UUID uuid : new ArrayList<>(getPlayersInDungeonList(dungeon))) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.teleport(out);
        }
    }

    @EventHandler
    public void onEntityMove(EntityMoveEvent event) {
        Entity entity = event.getEntity();
        World world = entity.getWorld();

        if (!world.getName().equalsIgnoreCase(getConfig().getString("classes.world", "dungeon"))) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        // Si el bloque de destino es el mismo, ignoramos (para optimización)
        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        String fromRegion = getRegionName(from);
        String toRegion = getRegionName(to);

        if (fromRegion == null) fromRegion = "__global__";
        if (toRegion == null) toRegion = "__global__";

        if (fromRegion.equalsIgnoreCase(toRegion)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!world.getName().equalsIgnoreCase(getConfig().getString("classes.world", "dungeon"))) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        // Si el bloque de destino es el mismo, ignoramos (para optimización)
        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        String fromRegion = getRegionName(from);
        String toRegion = getRegionName(to);

        if (fromRegion == null) fromRegion = "__global__";
        if (toRegion == null) toRegion = "__global__";

        if (fromRegion.equalsIgnoreCase(toRegion)) return;

        if (dungeonStarted.contains(toRegion)) {
            if (!getDungeonOfPlayer(player).equalsIgnoreCase(toRegion)) {
                player.sendMessage(deserialize(getConfig()
                        .getString("messages.cant_enter", "&cNo puedes entrar aquí, hay una dungeon en curso!")));
                //event.setCancelled(true);
                player.teleport(from);
            } else {
                if (Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("tank")) {
                    if (getConfig().getBoolean("classes.tank.abilities.vida_defensa.enabled")) {
                        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(40.0); // 40 = 20 corazones
                        player.setHealth(40.0); // para curarlo al máximo

                    }
                }
            }
        } else if (dungeonStarted.contains(fromRegion)
                && getDungeonOfPlayer(player).equalsIgnoreCase(fromRegion)) {
            player.sendMessage(deserialize(getConfig()
                    .getString("messages.cant_leave", "&cNo puedes salir de aquí, estás en una dungeon activa!")));
            //event.setCancelled(true);
            player.teleport(from);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player player = null;

        if (event.getCause() == EntityDamageEvent.DamageCause.THORNS
                && event.getEntity().getWorld().getName().equals(getConfig().getString("classes.world", "dungeon"))) {
            event.setCancelled(true);
            return;
        }

        // Si el que daña es un jugador directamente
        if (event.getDamager() instanceof Player directPlayer) {
            player = directPlayer;
        }

        // Si es una flecha u otro proyectil disparado por un jugador
        else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                player = shooter;
            }
        }

        // Si no fue un jugador ni un proyectil disparado por jugador, ignorar
        if (player == null) return;

        Location location = event.getEntity().getLocation();
        String regionId = getRegionName(location);
        if (regionId == null) return;

        if (possibleDungeons.contains(regionId)
                && dungeonStarted.contains(regionId)
                && !getDungeonOfPlayer(player).equalsIgnoreCase(regionId)) {
            player.sendMessage(deserialize(getConfig()
                    .getString("messages.cant_damage", "&cNo puedes dañar entidades aquí")));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getPlayer().getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
    }

    private static final Map<String, Set<Location>> filledChests = new HashMap<>();

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.CHEST) return;

        Location loc = event.getInventory().getLocation();
        if (loc == null) return;

        String region = getRegionName(loc);
        if (region == null) return;

        // Solo dungeons activas
        if (!dungeonStarted.contains(region)) return;
        // Solo si el jugador está en esa dungeon
        if (!getDungeonOfPlayer(player).equalsIgnoreCase(region)) return;

        // Si ya lo rellenamos antes en esta dungeon
        Set<Location> set = filledChests.computeIfAbsent(region, k -> new HashSet<>());
        if (set.contains(loc)) return;

        // Marcarlo como rellenado
        set.add(loc);

        // Llenar con loot
        if (loc.getBlock().getState() instanceof Chest chest) {
            fillChest(chest.getInventory());
        }
    }

    private void fillChest(Inventory inv) {
        FileConfiguration config = Dungeons.getInstance().getConfig();
        int min = config.getInt("chests.min", 1);
        int max = config.getInt("chests.max", 3);

        int amount = random.nextInt(max - min + 1) + min;

        List<String> rewards = new ArrayList<>(config.getStringList("chests.rewards"));
        Collections.shuffle(rewards);

        for (int i = 0; i < Math.min(amount, rewards.size()); i++) {
            String[] parts = rewards.get(i).split(" ");
            Material mat = Material.matchMaterial(parts[0]);
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

            if (mat != null) {
                inv.addItem(new ItemStack(mat, count));
            }
        }
    }

    /** Limpia solo los cofres usados en esa dungeon */
    public static void clearChests(String dungeon) {
        Set<Location> set = filledChests.remove(dungeon);
        if (set == null || set.isEmpty()) return;

        for (Location loc : set) {
            Block block = loc.getBlock();
            if (block.getState() instanceof Chest chest) {
                chest.getInventory().clear();
                chest.update();
            }
        }
    }

    //--------------------------------
    //            UTILS              |
    //--------------------------------

    public static void addPlayerToDungeonList(String dungeon, Player player) {
        dungeonPlayers.computeIfAbsent(dungeon, k -> new ArrayList<>()).add(player.getUniqueId());
        allDungeonPlayers.add(player.getUniqueId());
    }

    public static void removePlayerFromDungeonList(String dungeon, Player player) {
        List<UUID> players = dungeonPlayers.get(dungeon);
        if (players != null) {
            players.remove(player.getUniqueId());
            allDungeonPlayers.remove(player.getUniqueId());
            if (players.isEmpty()) {
                dungeonPlayers.remove(dungeon); // elimina la dungeon si ya no hay nadie
            }
        }
    }

    public static List<UUID> getPlayersInDungeonList(String dungeon) {
        return dungeonPlayers.getOrDefault(dungeon, Collections.emptyList());
    }

    public static String getDungeonOfPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        for (Map.Entry<String, List<UUID>> entry : dungeonPlayers.entrySet()) {
            if (entry.getValue().contains(uuid)) {
                return entry.getKey(); // devuelve el nombre de la dungeon
            }
        }
        return ""; // si no está en ninguna dungeon
    }

    public static void loadPossibleDungeons() {
        ConfigurationSection bossSec = getConfig().getConfigurationSection("dungeons.bosses");

        if (bossSec != null) {
            for (String key : bossSec.getKeys(false)) possibleDungeons.add(key);
        }
    }

    public static Location getLocation(String config) {
        String raw = getConfig().getString(config);
        if (raw == null) return null;

        String[] parts = raw.split(";");
        if (parts.length < 6) return null;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;

        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = Float.parseFloat(parts[4]);
        float pitch = Float.parseFloat(parts[5]);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private static void startCountdown(String dungeon, int seconds) {
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Dungeons.getInstance(), new Runnable() {
            int time = seconds;
            @Override
            public void run() {
                // Comprobar mínimo de jugadores
                if (getPlayersInDungeonList(dungeon).size() < minPlayers) {
                    cancelCountdown(dungeon);
                    for (UUID uuid : getPlayersInDungeonList(dungeon)) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(deserialize(getConfig()
                                    .getString("messages.not_enough_players", "&cNo hay suficientes jugadores para empezar la dungeon")));
                        }
                    }
                    return;
                }

                if (time <= 0) {
                    Bukkit.getScheduler().cancelTask(countdownTasks.remove(dungeon));
                    start(dungeon, true);

                    for (UUID uuid : getPlayersInDungeonList(dungeon)) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(deserialize(getConfig()
                                    .getString("messages.start", "&eLa dungeon &b{dungeon} &eha comenzado!")
                                    .replace("{dungeon}", dungeon)));
                        }
                    }
                    return;
                }
                for (UUID uuid : getPlayersInDungeonList(dungeon)) {
                    if (time % 10 == 0 || time <= 5) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(deserialize(getConfig()
                                    .getString("messages.start_timer", "&eLa dungeon &b{dungeon} &eempieza en &b{time} &esegundos")
                                    .replace("{dungeon}", dungeon)
                                    .replace("{time}", String.valueOf(time))));
                        }
                    }
                }
                time--;
            }
        }, 0L, 20L);
        countdownTasks.put(dungeon, taskId);
    }

    private static void cancelCountdown(String dungeon) {
        Integer id = countdownTasks.remove(dungeon);
        dungeonStarting.put(dungeon, false);
        if (id != null) Bukkit.getScheduler().cancelTask(id);
    }

    private String getRegionName(Location location) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(location.getWorld()));
        if (manager == null) return null;

        ApplicableRegionSet set = manager.getApplicableRegions(BukkitAdapter.asBlockVector(location));
        return set.getRegions().stream().findFirst().map(r -> r.getId().toLowerCase()).orElse(null);
    }

    public static void cancelDungeonTimer(String dungeon) {
        Integer id = dungeonTimers.remove(dungeon);
        if (id != null) {
            Bukkit.getScheduler().cancelTask(id);
        }
        BossBar bar = dungeonBossBars.remove(dungeon);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private static int getFreeInventorySlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                free++;
            }
        }
        return free;
    }

    public static void claimRewards(Player player) {
        PendingReward reward;

        reward = Dungeons.getInstance().getPendingReward(player.getUniqueId().toString());

        if (reward == null) {
            player.sendMessage(deserialize(
                    Dungeons.getInstance().getConfig()
                            .getString("messages.no_pending_rewards", "&cNo tienes recompensas pendientes")
            ));
            return;
        }

        int freeSlots = getFreeInventorySlots(player);

        if (freeSlots < reward.getRequiredSpace()) {
            player.sendMessage(deserialize(
                    Dungeons.getInstance().getConfig()
                            .getString(
                                    "messages.no_inventory_space_claim",
                                    "&cNecesitas &e{space} &cespacios libres"
                            ).replace("{space}", String.valueOf(reward.getRequiredSpace()))
            ));
            return;
        }

        for (String cmd : reward.getCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        Dungeons.getInstance().removePendingReward(reward.getId());

        player.sendMessage(deserialize(
                Dungeons.getInstance().getConfig()
                        .getString("messages.rewards_claimed", "&aHas reclamado tus recompensas pendientes")
        ));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!allDungeonPlayers.contains(player.getUniqueId())) {
            return;
        }

        String message = event.getMessage().toLowerCase();
        String command = message.substring(1).split(" ")[0];

        List<String> allowedCommands = getConfig().getStringList("allowed_commands");

        if (allowedCommands.contains(command)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(deserialize("&cNo puedes usar comandos mientras estás en una dungeon."));
    }

    @EventHandler
    public void onEnderPearlUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!world.getName().equalsIgnoreCase(getConfig().getString("classes.world", "dungeon"))) {
            return;
        }

        if (event.getItem() == null) {
            return;
        }

        if (event.getItem().getType() == Material.ENDER_PEARL) {
            event.setCancelled(true);
            player.sendMessage(deserialize("&cNo puedes usar ender pearls en este mundo."));
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (!allDungeonPlayers.contains(player.getUniqueId())) {
            return;
        }

        String dungeon = getDungeonOfPlayer(player);
        Location teleport = getLocation("dungeons.bosses."+dungeon+".teleport");
        if (teleport != null) {
            teleport.getChunk().load();
            player.teleport(teleport);
        }
    }

    public static void dungeonClearAll() {
        ConfigurationSection bosses = getConfig().getConfigurationSection("dungeons.bosses");
        if (bosses == null) return;

        for (String dungeon : bosses.getKeys(false)) {
            ConfigurationSection dungeonSection = bosses.getConfigurationSection(dungeon);
            if (dungeonSection == null) continue;

            for (String type : dungeonSection.getKeys(false)) {
                List<String> commands = getConfig().getStringList(
                        "dungeons.bosses." + dungeon + "." + type + ".stop_commands"
                );

                for (String command : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        String worldName = getConfig().getString("classes.world");
        String playerWorld = event.getPlayer().getWorld().getName();
        if (!worldName.equalsIgnoreCase(playerWorld)) return;

        if (!Dungeons.getInstance().DungeonsCleared) {
            dungeonClearAll();
            Dungeons.getInstance().DungeonsCleared = true;
        }
    }

    public static Component deserialize(String input) {
        if (input.contains("<") && input.contains(">")) {
            try {
                return MiniMessage.miniMessage().deserialize(input);
            } catch (Exception e) {
                return Component.text(input);
            }
        }
        return LEGACY_HEX_SERIALIZER.deserialize(input);
    }
    private static final LegacyComponentSerializer LEGACY_HEX_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
}
