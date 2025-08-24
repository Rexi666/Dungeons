package org.rexi.dungeons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.rexi.dungeons.commands.DungeonCommand;
import org.rexi.dungeons.listeners.ChestListener;
import org.rexi.dungeons.listeners.GraveListener;

public final class Dungeons extends JavaPlugin {

    private GraveListener graveListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        DungeonCommand dungeonCommand = new DungeonCommand(this);
        this.getCommand("dungeon").setExecutor(dungeonCommand);
        this.getCommand("dungeon").setTabCompleter(dungeonCommand);

        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        graveListener = new GraveListener(this);
        Bukkit.getPluginManager().registerEvents(graveListener, this);

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
}
