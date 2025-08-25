package org.rexi.dungeons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.rexi.dungeons.commands.DungeonCommand;
import org.rexi.dungeons.database.DatabaseManager;
import org.rexi.dungeons.listeners.ChestListener;
import org.rexi.dungeons.listeners.GraveListener;
import org.rexi.dungeons.listeners.MenuListener;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Dungeons extends JavaPlugin {

    private GraveListener graveListener;
    private static Dungeons instance;
    private DatabaseManager databaseManager;

    public final Map<UUID, String> playerClass = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

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

        // listeners
        getServer().getPluginManager().registerEvents(new ChestListener(this), this);

        graveListener = new GraveListener(this);
        Bukkit.getPluginManager().registerEvents(graveListener, this);

        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);

        Bukkit.getConsoleSender().sendMessage(Component.text("Dungeons se ha activado correctamente").color(NamedTextColor.GREEN));
        Bukkit.getConsoleSender().sendMessage(Component.text("Gracias por usar plugins de Rexi666 :D").color(NamedTextColor.BLUE));

    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (graveListener != null) {
            graveListener.clearAllGraves();
        }

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
            getLogger().severe("Failed to save player chat color on the database: " + e.getMessage());
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
}
