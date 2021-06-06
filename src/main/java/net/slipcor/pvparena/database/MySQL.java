package net.slipcor.pvparena.database;

import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import net.slipcor.pvparena.PVPArena;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Connects to and uses a MySQL database
 */
public class MySQL extends Database {
    private final String user;
    private final String database;
    private final String password;
    private final String port;
    private final String hostname;

    /**
     * Creates a new MySQL instance
     *
     * @param plugin   Plugin instance
     * @param hostname Name of the host
     * @param port     Port number
     * @param database Database name
     * @param username Username
     * @param password Password
     */
    public MySQL(final Plugin plugin, final String hostname, final String port, final String database, final String username, final String password) {
        super(plugin);
        this.hostname = hostname;
        this.port = port;
        this.database = database;
        this.user = username;
        this.password = password;
    }

    @Override
    public JdbcPooledConnectionSource openConnection() {

        JdbcPooledConnectionSource connectionSource = null;
        try {
            // pooled connection source
            connectionSource =
                    new JdbcPooledConnectionSource("jdbc:mysql://" + this.hostname + ":" + this.port + "/" + this.database + "?&useSSL=false", this.user, this.password);

            // only keep the connections open for 5 minutes
            connectionSource.setMaxConnectionAgeMillis(5 * 60 * 1000);
            // change the check-every milliseconds from 30 seconds to 60
            // connectionSource.setCheckConnectionsEveryMillis(60 * 1000);
            // for extra protection, enable the testing of connections
            // right before they are handed to the user
            // connectionSource.setTestBeforeGet(true);

        } catch (SQLException e) {
            PVPArena.getInstance().getLogger().warning("Can't open Mysql connection: " + e.getMessage());
        }
        return connectionSource;
    }
}
