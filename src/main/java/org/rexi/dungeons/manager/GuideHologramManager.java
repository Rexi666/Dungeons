package org.rexi.dungeons.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.rexi.dungeons.Dungeons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuideHologramManager implements Listener {

    private final Dungeons plugin;

    /*
     * Índice del punto que está siguiendo cada jugador.
     *
     * Ejemplo:
     * 0 = primer punto
     * 1 = segundo punto
     * 2 = tercer punto
     */
    private final Map<UUID, Integer> playerGuideIndex = new HashMap<>();

    /*
     * Holograma individual de cada jugador.
     */
    private final Map<UUID, TextDisplay> playerHolograms = new HashMap<>();

    private BukkitTask updateTask;

    public GuideHologramManager(Dungeons plugin) {
        this.plugin = plugin;
    }

    /**
     * Inicia la tarea encargada de actualizar los hologramas.
     */
    public void start() {
        stopTaskOnly();

        long updateTicks = Math.max(
                1L,
                plugin.getConfig().getLong(
                        "guide_hologram.default-update-ticks",
                        10L
                )
        );

        updateTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::updateAllPlayers,
                updateTicks,
                updateTicks
        );
    }

    /**
     * Recarga el sistema después de usar /dungeon reload.
     */
    public void reload() {
        clearAll();
        start();
    }

    /**
     * Detiene completamente el sistema.
     */
    public void shutdown() {
        stopTaskOnly();
        clearAll();
    }

    private void stopTaskOnly() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /**
     * Actualiza los hologramas de todos los jugadores que estén
     * participando en una dungeon iniciada.
     */
    private void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }

        /*
         * Limpia hologramas cuyos jugadores ya no están conectados.
         */
        List<UUID> offlinePlayers = new ArrayList<>();

        for (UUID uuid : playerHolograms.keySet()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                offlinePlayers.add(uuid);
            }
        }

        for (UUID uuid : offlinePlayers) {
            removeGuide(uuid);
        }
    }

    /**
     * Actualiza el punto guía de un jugador.
     */
    private void updatePlayer(Player player) {
        String dungeon = GameManager.getDungeonOfPlayer(player);

        /*
         * Si no pertenece a una dungeon o la partida todavía no está
         * iniciada, no debe ver el holograma.
         */
        if (dungeon == null
                || dungeon.isBlank()
                || !GameManager.dungeonStarted.contains(dungeon)) {

            removeGuide(player.getUniqueId());
            return;
        }

        String basePath =
                "dungeons.bosses." + dungeon + ".guide_hologram";

        if (!plugin.getConfig().getBoolean(
                basePath + ".enabled",
                true
        )) {
            removeGuide(player.getUniqueId());
            return;
        }

        List<Location> points = loadPoints(dungeon);

        if (points.isEmpty()) {
            removeGuide(player.getUniqueId());
            return;
        }

        int index = playerGuideIndex.getOrDefault(
                player.getUniqueId(),
                0
        );

        /*
         * Evita índices inválidos si se recarga la configuración
         * y se eliminan coordenadas.
         */
        if (index < 0 || index >= points.size()) {
            index = 0;
            playerGuideIndex.put(player.getUniqueId(), index);
        }

        Location currentPoint = points.get(index);

        /*
         * Si el punto está en otro mundo todavía no podemos calcular
         * correctamente la distancia.
         */
        if (currentPoint.getWorld() == null
                || !currentPoint.getWorld().equals(player.getWorld())) {

            removeHologramOnly(player.getUniqueId());
            return;
        }

        double activationRadius = Math.max(
                0.5,
                plugin.getConfig().getDouble(
                        basePath + ".activation-radius",
                        3.0
                )
        );

        double distanceToPoint =
                player.getLocation().distance(currentPoint);

        /*
         * Cuando el jugador llega al punto, pasa al siguiente.
         */
        if (distanceToPoint <= activationRadius) {
            if (index < points.size() - 1) {
                index++;

                playerGuideIndex.put(
                        player.getUniqueId(),
                        index
                );

                currentPoint = points.get(index);
            } else {
                showCompletedHologram(
                        player,
                        dungeon,
                        currentPoint
                );
                return;
            }
        }

        Location finalPoint = points.get(points.size() - 1);

        double distanceToFinal = calculateDistance(
                player.getLocation(),
                finalPoint
        );

        showGuideHologram(
                player,
                dungeon,
                currentPoint,
                index,
                points.size(),
                distanceToFinal
        );
    }

    /**
     * Crea o actualiza el holograma normal.
     */
    private void showGuideHologram(
            Player player,
            String dungeon,
            Location point,
            int pointIndex,
            int totalPoints,
            double distanceToFinal
    ) {
        String basePath =
                "dungeons.bosses." + dungeon + ".guide_hologram";

        double height = plugin.getConfig().getDouble(
                basePath + ".height",
                1.8
        );

        Location hologramLocation =
                point.clone().add(0.0, height, 0.0);

        TextDisplay hologram = getOrCreateHologram(
                player,
                hologramLocation
        );

        /*
         * Si cambia de mundo, recreamos el TextDisplay.
         */
        if (hologram.getWorld() != hologramLocation.getWorld()) {
            removeHologramOnly(player.getUniqueId());

            hologram = getOrCreateHologram(
                    player,
                    hologramLocation
            );
        } else {
            hologram.teleport(hologramLocation);
        }

        List<String> lines = plugin.getConfig().getStringList(
                basePath + ".text"
        );

        if (lines.isEmpty()) {
            lines = List.of(
                    "&e&lGUÍA DE LA DUNGEON",
                    "&7Sigue este punto",
                    "&b{distance} bloques &7hasta el destino"
            );
        }

        String distanceFormatted =
                String.format(java.util.Locale.US, "%.1f", distanceToFinal);

        List<Component> components = new ArrayList<>();

        for (String line : lines) {
            String parsed = line
                    .replace("{dungeon}", dungeon)
                    .replace(
                            "{distance}",
                            distanceFormatted
                    )
                    .replace(
                            "{point}",
                            String.valueOf(pointIndex + 1)
                    )
                    .replace(
                            "{total_points}",
                            String.valueOf(totalPoints)
                    );

            components.add(GameManager.deserialize(parsed));
        }

        hologram.text(joinLines(components));

        updateVisibility(player, hologram);
    }

    /**
     * Muestra el mensaje de recorrido completado.
     */
    private void showCompletedHologram(
            Player player,
            String dungeon,
            Location finalPoint
    ) {
        String basePath =
                "dungeons.bosses." + dungeon + ".guide_hologram";

        double height = plugin.getConfig().getDouble(
                basePath + ".height",
                1.8
        );

        Location hologramLocation =
                finalPoint.clone().add(0.0, height, 0.0);

        TextDisplay hologram = getOrCreateHologram(
                player,
                hologramLocation
        );

        if (hologram.getWorld() != hologramLocation.getWorld()) {
            removeHologramOnly(player.getUniqueId());

            hologram = getOrCreateHologram(
                    player,
                    hologramLocation
            );
        } else {
            hologram.teleport(hologramLocation);
        }

        List<String> lines = plugin.getConfig().getStringList(
                basePath + ".completed-text"
        );

        if (lines.isEmpty()) {
            lines = List.of(
                    "&a&lDESTINO ALCANZADO",
                    "&7Has llegado al final del recorrido"
            );
        }

        List<Component> components = new ArrayList<>();

        for (String line : lines) {
            components.add(
                    GameManager.deserialize(
                            line.replace("{dungeon}", dungeon)
                    )
            );
        }

        hologram.text(joinLines(components));

        updateVisibility(player, hologram);
    }

    /**
     * Obtiene el holograma del jugador o crea uno nuevo.
     */
    private TextDisplay getOrCreateHologram(
            Player owner,
            Location location
    ) {
        TextDisplay existing =
                playerHolograms.get(owner.getUniqueId());

        if (existing != null
                && existing.isValid()
                && !existing.isDead()) {
            return existing;
        }

        TextDisplay hologram = location.getWorld().spawn(
                location,
                TextDisplay.class,
                display -> {
                    display.setBillboard(
                            Display.Billboard.CENTER
                    );

                    display.setSeeThrough(true);
                    display.setShadowed(true);
                    display.setDefaultBackground(false);
                    display.setPersistent(false);
                    display.setInvulnerable(true);
                    display.setGravity(false);
                    display.setViewRange(48.0F);
                    display.setLineWidth(300);

                    display.addScoreboardTag(
                            "dungeons_guide_hologram"
                    );

                    display.addScoreboardTag(
                            "dungeons_guide_owner_"
                                    + owner.getUniqueId()
                    );
                }
        );

        playerHolograms.put(
                owner.getUniqueId(),
                hologram
        );

        updateVisibility(owner, hologram);

        return hologram;
    }

    /**
     * Hace que el holograma solamente sea visible para su propietario.
     */
    private void updateVisibility(
            Player owner,
            TextDisplay hologram
    ) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId()
                    .equals(owner.getUniqueId())) {

                onlinePlayer.showEntity(plugin, hologram);
            } else {
                onlinePlayer.hideEntity(plugin, hologram);
            }
        }
    }

    /**
     * Carga las coordenadas de una dungeon.
     *
     * Formato:
     * mundo;x;y;z
     */
    private List<Location> loadPoints(String dungeon) {
        String path =
                "dungeons.bosses."
                        + dungeon
                        + ".guide_hologram.points";

        List<String> rawPoints =
                plugin.getConfig().getStringList(path);

        List<Location> points = new ArrayList<>();

        for (int i = 0; i < rawPoints.size(); i++) {
            String raw = rawPoints.get(i);

            Location location = parseLocation(raw);

            if (location == null) {
                plugin.getLogger().warning(
                        "Coordenada inválida en "
                                + path
                                + "["
                                + i
                                + "]: "
                                + raw
                );
                continue;
            }

            points.add(location);
        }

        return points;
    }

    /**
     * Convierte "mundo;x;y;z" en Location.
     */
    private Location parseLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String[] parts = raw.split(";");

        if (parts.length < 4) {
            return null;
        }

        World world = Bukkit.getWorld(parts[0]);

        if (world == null) {
            return null;
        }

        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            return new Location(world, x, y, z);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Calcula la distancia sin provocar errores entre mundos diferentes.
     */
    private double calculateDistance(
            Location from,
            Location to
    ) {
        if (from.getWorld() == null
                || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return 0.0;
        }

        return from.distance(to);
    }

    /**
     * Une los componentes usando saltos de línea.
     */
    private Component joinLines(List<Component> components) {
        if (components.isEmpty()) {
            return Component.empty();
        }

        TextComponent.Builder builder = Component.text();

        for (int i = 0; i < components.size(); i++) {
            if (i > 0) {
                builder.append(Component.newline());
            }

            builder.append(components.get(i));
        }

        return builder.build();
    }

    /**
     * Reinicia el recorrido de un jugador.
     */
    public void resetGuide(Player player) {
        removeGuide(player.getUniqueId());
    }

    /**
     * Elimina índice y holograma.
     */
    public void removeGuide(UUID uuid) {
        playerGuideIndex.remove(uuid);
        removeHologramOnly(uuid);
    }

    /**
     * Elimina únicamente la entidad visual.
     */
    private void removeHologramOnly(UUID uuid) {
        TextDisplay hologram = playerHolograms.remove(uuid);

        if (hologram != null && hologram.isValid()) {
            hologram.remove();
        }
    }

    /**
     * Elimina todos los hologramas.
     */
    public void clearAll() {
        for (TextDisplay hologram :
                new ArrayList<>(playerHolograms.values())) {

            if (hologram != null && hologram.isValid()) {
                hologram.remove();
            }
        }

        playerHolograms.clear();
        playerGuideIndex.clear();

        /*
         * También elimina displays antiguos que hayan quedado después
         * de un crash o una recarga incorrecta.
         */
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display :
                    world.getEntitiesByClass(TextDisplay.class)) {

                if (display.getScoreboardTags().contains(
                        "dungeons_guide_hologram"
                )) {
                    display.remove();
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeGuide(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeHologramOnly(event.getPlayer().getUniqueId());
    }

    /**
     * Cuando entra un jugador, ocultamos los hologramas ajenos.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joinedPlayer = event.getPlayer();

        for (Map.Entry<UUID, TextDisplay> entry :
                playerHolograms.entrySet()) {

            TextDisplay hologram = entry.getValue();

            if (hologram == null || !hologram.isValid()) {
                continue;
            }

            if (entry.getKey().equals(
                    joinedPlayer.getUniqueId()
            )) {
                joinedPlayer.showEntity(plugin, hologram);
            } else {
                joinedPlayer.hideEntity(plugin, hologram);
            }
        }
    }
}