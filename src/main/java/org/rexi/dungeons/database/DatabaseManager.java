package org.rexi.dungeons.database;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.rexi.dungeons.Dungeons;

import java.io.File;
import java.sql.*;

public class DatabaseManager implements Listener {
    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        String type = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        if (type.equals("mysql")) {
            String host = plugin.getConfig().getString("database.mysql.host");
            int port = plugin.getConfig().getInt("database.mysql.port");
            String db = plugin.getConfig().getString("database.mysql.database");
            String user = plugin.getConfig().getString("database.mysql.username");
            String pass = plugin.getConfig().getString("database.mysql.password");

            connection = DriverManager.getConnection(
                    "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false",
                    user,
                    pass
            );
        } else {
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }

        plugin.getLogger().info("Connected to " + type.toUpperCase() + " database!");
        createTable();
    }

    private void createTable() throws SQLException {
        // Tabla player_class
        String sql1 = "CREATE TABLE IF NOT EXISTS player_class (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "chosen_class TEXT" +
                ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql1);
        }

        // Tabla dungeon_level
        String sql2 = "CREATE TABLE IF NOT EXISTS dungeon_level (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "level INT" +
                ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql2);
        }

        String sql3 = "CREATE TABLE IF NOT EXISTS dungeon_cooldown (" +
                "uuid VARCHAR(36) NOT NULL," +
                "dungeon TEXT NOT NULL," +
                "last_join BIGINT NOT NULL," +
                "PRIMARY KEY (uuid, dungeon)" +
                ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql3);
        }
    }

    public void setPlayerClass(String uuid, String chosen_class) throws SQLException {
        String sql = "REPLACE INTO player_class (uuid, chosen_class) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, chosen_class);
            ps.executeUpdate();
        }
    }

    public String getPlayerClass(String uuid) throws SQLException {
        String sql = "SELECT chosen_class FROM player_class WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("chosen_class");
            }
        }
        return "";
    }

    public void setDungeonLevel(String uuid, int level) throws SQLException {
        String sql = "REPLACE INTO dungeon_level (uuid, level) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setInt(2, level);
            ps.executeUpdate();
        }
    }

    public int getDungeonLevel(String uuid) throws SQLException {
        String sql = "SELECT level FROM dungeon_level WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("level");
            }
        }
        return 0; // por defecto nivel 0 si no existe
    }

    public void setDungeonJoin(String uuid, String dungeon) throws SQLException {
        String sql = "REPLACE INTO dungeon_cooldown (uuid, dungeon, last_join) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, dungeon);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public Long getLastDungeonJoin(String uuid, String dungeon) throws SQLException {
        String sql = "SELECT last_join FROM dungeon_cooldown WHERE uuid = ? AND dungeon = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, dungeon);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("last_join");
            }
        }
        return null;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Dungeons.getInstance().getPlayerClassFromDB(player);
        Dungeons.getInstance().getDungeonLevelFromDB(player);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
    }
}
