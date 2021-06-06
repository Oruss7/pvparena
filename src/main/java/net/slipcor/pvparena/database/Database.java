package net.slipcor.pvparena.database;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.table.TableUtils;
import net.slipcor.pvparena.PVPArena;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;

/**
 * Abstract Database class, serves as a base for any connection method (MySQL,
 * SQLite, etc.)
 */
public abstract class Database {

    protected Plugin plugin;
    protected String prefix;
    protected JdbcConnectionSource connectionSource;

    // DAOs
    Dao<PlayerArenaStats, Long> playerArenaStatsDao;

    protected Database(final Plugin plugin) {
        this.plugin = plugin;
        this.prefix = plugin.getConfig().getString("mysql.prefix", "");
    }

    /**
     * Parse raw query results
     *
     * @param columnNames
     * @param resultColumns
     * @return PlayerArenaStats
     */
    private static PlayerArenaStats mapRow(String[] columnNames, String[] resultColumns) {
        return new PlayerArenaStats(
                Long.parseLong(resultColumns[0]),
                resultColumns[1],
                Long.parseLong(resultColumns[2]),
                Long.parseLong(resultColumns[3]),
                Long.parseLong(resultColumns[4]),
                Long.parseLong(resultColumns[5]),
                Long.parseLong(resultColumns[6]),
                Long.parseLong(resultColumns[7]),
                Long.parseLong(resultColumns[8]),
                Long.parseLong(resultColumns[9])
        );
    }

    public JdbcConnectionSource getConnection() {
        if (this.connectionSource == null) {
            this.connectionSource = openConnection();
        }
        return this.connectionSource;
    }

    protected abstract JdbcConnectionSource openConnection();

    public void closeConnection() {
        if (this.connectionSource != null) {
            try {
                this.connectionSource.close();
            } catch (final IOException e) {
                PVPArena.getInstance().getLogger().severe("There was an exception when closing database connection: " + e.getMessage());
            }
        }
        this.connectionSource = null;
    }

    public void init() {

        try {
            this.playerArenaStatsDao = DaoManager.createDao(this.connectionSource, PlayerArenaStats.class);
        } catch (SQLException exception) {
            PVPArena.getInstance().getLogger().severe("There was an exception creating database object: " + exception.getMessage());
        }

        // if you need to create the 'PlayerArenaStats' table make this call
        try {
            TableUtils.createTableIfNotExists(this.connectionSource, PlayerArenaStats.class);
        } catch (SQLException exception) {
            PVPArena.getInstance().getLogger().severe("There was an exception creating database table: " + exception.getMessage());
        }
    }

    public void addPlayerStats(@NotNull final PlayerArenaStats newPlayerArenaStats) {
        try {
            TransactionManager.callInTransaction(this.connectionSource,
                    () -> {
                        if (PVPArena.getInstance().getLogger().isLoggable(Level.FINE)) {
                            PVPArena.getInstance().getLogger().fine(String.format("Adding player %s stats for arena %s.",
                                    Bukkit.getPlayer(UUID.fromString(newPlayerArenaStats.getUuid())), newPlayerArenaStats.getArena()));
                        }
                        PlayerArenaStats current = getPlayerStats(newPlayerArenaStats.getUuid(), newPlayerArenaStats.getArena());
                        current.addLosses(newPlayerArenaStats.getLosses());
                        current.addWins(newPlayerArenaStats.getWins());
                        current.addKills(newPlayerArenaStats.getKills());
                        current.addDeaths(newPlayerArenaStats.getDeaths());
                        current.addDamage(newPlayerArenaStats.getDamage());
                        current.addMaxDamage(newPlayerArenaStats.getMaxDamage());
                        current.setDamageTake(newPlayerArenaStats.getDamageTake());
                        current.addMaxDamageTake(newPlayerArenaStats.getMaxDamageTake());

                        savePlayerStats(current);
                        return null;
                    });
        } catch (SQLException exception) {
            PVPArena.getInstance().getLogger().severe("A transaction error occurs when adding player stats: " + exception.getMessage());
        }
    }

    public void savePlayerStats(@NotNull PlayerArenaStats playerArenaStats) {
        if (PVPArena.getInstance().getLogger().isLoggable(Level.FINE)) {
            PVPArena.getInstance().getLogger().fine(String.format("Saving player %s stats for arena %s.",
                    Bukkit.getPlayer(UUID.fromString(playerArenaStats.getUuid())), playerArenaStats.getArena()));
        }
        try {
            this.playerArenaStatsDao.createOrUpdate(playerArenaStats);
        } catch (SQLException exception) {
            PVPArena.getInstance().getLogger().severe("Can't save or update to database: " + exception.getMessage());
        }
    }

    public PlayerArenaStats getPlayerStats(@NotNull String uuid) {
        PlayerArenaStats playerArenaStats = null;
        if (PVPArena.getInstance().getLogger().isLoggable(Level.FINE)) {
            PVPArena.getInstance().getLogger().fine(String.format("Getting player %s stats for ALL arena.",
                    Bukkit.getPlayer(UUID.fromString(uuid))));
        }
        try {
            QueryBuilder<PlayerArenaStats, Long> qb = this.playerArenaStatsDao.queryBuilder();
            // Get the sum of all player stats grouped by arena
            qb.selectRaw("id", "uuid", "SUM(losses)", "SUM(wins)", "SUM(kills)", "SUM(deaths)",
                    "SUM(damage)", "SUM(maxDamage)", "SUM(damageTake)", "SUM(maxDamageTake)")
                    .where()
                    .eq(PlayerArenaStats.Columns.UUID.getName(), new SelectArg());

            playerArenaStats = this.playerArenaStatsDao.queryRaw(
                    qb.prepareStatementString(),
                    // Parse raw query results
                    Database::mapRow,
                    uuid
            ).getFirstResult();

        } catch (SQLException exception) {
            PVPArena.getInstance().getLogger().severe("Can't get from database: " + exception.getMessage());
        }
        // player havn't any stats at this moment. Display 0.
        if (playerArenaStats == null) {
            if (PVPArena.getInstance().getLogger().isLoggable(Level.FINE)) {
                PVPArena.getInstance().getLogger().fine("Player %s stats not found.");
            }
            playerArenaStats = new PlayerArenaStats();
            playerArenaStats.setUuid(uuid);
        }
        return playerArenaStats;
    }

    public PlayerArenaStats getPlayerStats(@NotNull String uuid, @NotNull String arena) {
        PlayerArenaStats playerArenaStats = null;
        if (PVPArena.getInstance().getLogger().isLoggable(Level.FINE)) {
            PVPArena.getInstance().getLogger().fine(String.format("Getting player %s stats for arena %s.",
                    Bukkit.getPlayer(UUID.fromString(uuid)), arena));
        }
        try {
            PreparedQuery<PlayerArenaStats> preparedQuery = this.playerArenaStatsDao.queryBuilder().where()
                    .eq(PlayerArenaStats.Columns.UUID.getName(), uuid)
                    .and()
                    .eq(PlayerArenaStats.Columns.ARENA.getName(), arena).prepare();

            playerArenaStats = this.playerArenaStatsDao.queryForFirst(preparedQuery);

        } catch (SQLException exception) {
            PVPArena.getInstance().getLogger().severe("Can't save or update to database: " + exception.getMessage());
        }
        // player havn't any stats at this moment. Display 0.
        if (playerArenaStats == null) {
            if (PVPArena.getInstance().getLogger().isLoggable(Level.FINE)) {
                PVPArena.getInstance().getLogger().fine(String.format("Player %s stats for arena %s not found.",
                        Bukkit.getPlayer(UUID.fromString(uuid)), arena));
            }
            playerArenaStats = new PlayerArenaStats();
            playerArenaStats.setUuid(uuid);
            playerArenaStats.setArena(arena);
        }
        return playerArenaStats;
    }
}
