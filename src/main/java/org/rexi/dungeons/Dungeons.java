package org.rexi.dungeons;

import com.aquaticstudios.aquaclan.api.AquaClanAPI;
import com.aquaticstudios.aquaclan.api.AquaClanProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.rexi.dungeons.commands.DungeonCommand;
import org.rexi.dungeons.commands.MensajeCommand;
import org.rexi.dungeons.database.DatabaseManager;
import org.rexi.dungeons.database.PendingReward;
import org.rexi.dungeons.listeners.GraveListener;
import org.rexi.dungeons.listeners.MenuListener;
import org.rexi.dungeons.manager.ClassManager;
import org.rexi.dungeons.manager.GameManager;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.rexi.dungeons.manager.GameManager.deserialize;

public final class Dungeons extends JavaPlugin {

    private GraveListener graveListener;
    private static Dungeons instance;
    private DatabaseManager databaseManager;

    private static Economy econ = null;
    private AquaClanAPI aquaClan;

    public final Map<UUID, String> playerClass = new HashMap<>();
    public final Map<UUID, Integer> dungeonLevel = new HashMap<>();

    public Boolean DungeonsCleared = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!setupEconomy() ) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (Bukkit.getPluginManager().isPluginEnabled("AquaClan")) {
            this.aquaClan = AquaClanProvider.get();
        } else {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Database
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect();
        } catch (SQLException e) {
            getLogger().severe("Could not connect to database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(databaseManager, this);

        // comandos
        DungeonCommand dungeonCommand = new DungeonCommand(this);
        this.getCommand("dungeon").setExecutor(dungeonCommand);
        this.getCommand("dungeon").setTabCompleter(dungeonCommand);

        MensajeCommand mensajeCommand = new MensajeCommand(this);
        this.getCommand("mensaje").setExecutor(mensajeCommand);

        // listeners

        graveListener = new GraveListener(this);
        Bukkit.getPluginManager().registerEvents(graveListener, this);

        Bukkit.getPluginManager().registerEvents(new MenuListener(aquaClan), this);

        // Game manager

        Bukkit.getPluginManager().registerEvents(new GameManager(), this);
        GameManager.loadPossibleDungeons();

        Bukkit.getPluginManager().registerEvents(new ClassManager(), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> { ClassManager.Repetitive(); }, 0L, 60L);

        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.hasMetadata("grave_hologram")) {
                    stand.remove();
                }
            }
        }

        Bukkit.getConsoleSender().sendMessage(Component.text("Dungeons se ha activado correctamente").color(NamedTextColor.GREEN));
        Bukkit.getConsoleSender().sendMessage(Component.text("Gracias por usar plugins de Rexi666 :D").color(NamedTextColor.BLUE));

    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        graveListener.clearAllGraves();

        GameManager.dungeonClearAll();

        Bukkit.getConsoleSender().sendMessage(Component.text("Dungeons se ha desactivado correctamente").color(NamedTextColor.RED));
        Bukkit.getConsoleSender().sendMessage(Component.text("Gracias por usar plugins de Rexi666 :D").color(NamedTextColor.BLUE));
    }

    public static Dungeons getInstance() {
        return instance;
    }

    public void setPlayerClass(Player player, String chosenClass) {
        playerClass.put(player.getUniqueId(), chosenClass);
        try {
            databaseManager.setPlayerClass(player.getUniqueId().toString(), chosenClass);
        } catch (SQLException e) {
            getLogger().severe("Failed to save player class on the database: " + e.getMessage());
        }
    }

    public String getPlayerClass(Player player) {
        return playerClass.getOrDefault(player.getUniqueId(), "");
    }
    public void getPlayerClassFromDB(Player player) {
        try {
            String chosenClass = databaseManager.getPlayerClass(player.getUniqueId().toString());
            playerClass.put(player.getUniqueId(), chosenClass);
        } catch (SQLException e) {
            getLogger().severe("Failed to load player class on the database: " + e.getMessage());
        }
    }

    public void setDungeonLevel(Player player, int level) {
        dungeonLevel.put(player.getUniqueId(), level);
        try {
            databaseManager.setDungeonLevel(player.getUniqueId().toString(), level);
        } catch (SQLException e) {
            getLogger().severe("Failed to save player dungeon level on the database: " + e.getMessage());
        }
    }

    public Integer getDungeonLevel(Player player) {
        return dungeonLevel.getOrDefault(player.getUniqueId(), 0);
    }
    public void getDungeonLevelFromDB(Player player) {
        try {
            int level = databaseManager.getDungeonLevel(player.getUniqueId().toString());
            dungeonLevel.put(player.getUniqueId(), level);
        } catch (SQLException e) {
            getLogger().severe("Failed to load player dungeon level on the database: " + e.getMessage());
        }
    }

    public void addPendingReward(String uuid, int requiredSpace, List<String> cmds) {
        try {
            databaseManager.addPendingReward(uuid, requiredSpace, cmds);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PendingReward getPendingReward(String uuid) {
        try {
            return databaseManager.getLatestPendingReward(uuid);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void removePendingReward(int id) {
        try {
            databaseManager.deletePendingReward(id);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean ReviewLastDungeonJoin (Player player, String dungeon, long DUNGEON_COOLDOWN) {
        try {
            Long lastJoin = databaseManager.getLastDungeonJoin(player.getUniqueId().toString(), dungeon);

            if (lastJoin != null) {
                long now = System.currentTimeMillis();
                long remaining = (lastJoin + DUNGEON_COOLDOWN) - now;

                if (remaining > 0) {
                    long hours = remaining / (1000 * 60 * 60);
                    long minutes = (remaining / (1000 * 60)) % 60;

                    player.sendMessage(deserialize(
                            getConfig().getString("messages.join_cooldown",
                                            "&cDebes esperar {hours}h {minutes}m para volver a entrar en esta dungeon")
                                    .replace("{hours}", String.valueOf(hours))
                                    .replace("{minutes}", String.valueOf(minutes))
                    ));
                    return false;
                }
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    public void setLastDungeonJoin(String uuid, String dungeon) {
        try {
            databaseManager.setDungeonJoin(uuid, dungeon);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Economy getEconomy() {
        return econ;
    }
}
