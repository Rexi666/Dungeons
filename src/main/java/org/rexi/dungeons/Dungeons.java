package org.rexi.dungeons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.rexi.dungeons.commands.DungeonCommand;
import org.rexi.dungeons.listeners.ChestListener;
import org.rexi.dungeons.listeners.GraveListener;
import org.rexi.dungeons.listeners.MenuListener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Dungeons extends JavaPlugin {

    private GraveListener graveListener;
    private static Dungeons instance;

    public final Map<UUID, String> playerClass = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

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
    }

    public String getPlayerClass(Player player) {
        return playerClass.getOrDefault(player.getUniqueId(), "");
    }
}
