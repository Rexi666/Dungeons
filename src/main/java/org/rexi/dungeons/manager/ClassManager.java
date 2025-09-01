package org.rexi.dungeons.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.rexi.dungeons.Dungeons;

import java.util.UUID;

public class ClassManager implements Listener {

    public void HealerRegeneration() {
        for (String dungeon : GameManager.possibleDungeons) {
            for (UUID uuid : GameManager.getPlayersInDungeonList(dungeon)) {
                Player player = Bukkit.getPlayer(uuid);
                if (Dungeons.getInstance().getPlayerClass(player).equalsIgnoreCase("healer")) {
                    // lógica curar
                }
            }
        }
    }
}
