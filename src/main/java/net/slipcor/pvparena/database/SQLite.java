package net.slipcor.pvparena.database;

import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import net.slipcor.pvparena.PVPArena;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Connects to and uses a SQLite database
 */
public class SQLite extends Database {
    private final String dbLocation;

    /**
     * Creates a new SQLite instance
     *
     * @param plugin     Plugin instance
     * @param dbLocation Location of the Database (Must end in .db)
     */
    public SQLite(final Plugin plugin, final String dbLocation) {
        super(plugin);
        this.dbLocation = dbLocation;
    }

    @Override
    public JdbcPooledConnectionSource openConnection() {
        if (!plugin.getDataFolder().exists()) {
            if(!plugin.getDataFolder().mkdirs()){
                PVPArena.getInstance().getLogger().severe( "Can't create pvpArena folder: " + plugin.getDataFolder());
                return null;
            }
        }
        final File file = new File(plugin.getDataFolder(), dbLocation);
        if (!(file.exists())) {
            try {
                if(!file.createNewFile()){
                    PVPArena.getInstance().getLogger().severe( "Can't create sqLite file: " + file.getPath());
                    return null;
                }
            } catch (final IOException e) {
                PVPArena.getInstance().getLogger().severe( "Unable to create SqLite database file: " + e.getMessage());
            }
        }
        JdbcPooledConnectionSource connection = null;
        try {
            // pooled connection source
            connection = new JdbcPooledConnectionSource("jdbc:sqlite:" + plugin.getDataFolder().toPath().toString() + "/" + dbLocation);
            // only keep the connections open for 5 minutes
            connection.setMaxConnectionAgeMillis(5 * 60 * 1000);

        } catch (SQLException e) {
            PVPArena.getInstance().getLogger().severe( "Can't open Sqlite connection: " + e.getMessage());
        }
        return connection;
    }
}
