package org.rexi.dungeons.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.rexi.dungeons.Dungeons;

import java.util.Arrays;

public class MensajeCommand implements CommandExecutor {

    private final Dungeons plugin;

    public MensajeCommand(Dungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            player.sendMessage(deserialize(plugin.getConfig().getString("messages.no_permission", "&cNo tienes permiso para ejecutar ese comando")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(deserialize(plugin.getConfig().getString("messages.usage.mensaje", "&cUso: /mensaje <jugador> <mensaje>")));
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(deserialize(plugin.getConfig().getString("messages.player_not_found", "&cJugador no encontrado o no está en línea")));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (message.isEmpty()) {
            sender.sendMessage(deserialize(plugin.getConfig().getString("messages.no_message", "&cDebes proporcionar un mensaje")));
            return true;
        }

        target.sendMessage(deserialize(message));
        return true;
    }

    private static final LegacyComponentSerializer LEGACY_HEX_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors() // Habilita el soporte de hex
            .useUnusualXRepeatedCharacterHexFormat() // Soporta &x&r&r&g&g&b&b
            .build();

    public static Component deserialize(String input) {
        return LEGACY_HEX_SERIALIZER.deserialize(input);
    }
}
