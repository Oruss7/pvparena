package net.slipcor.pvparena;

import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import net.slipcor.pvparena.api.PvpArenaPlaceholderExpansion;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.commands.*;
import net.slipcor.pvparena.config.Debugger;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Help;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.database.Database;
import net.slipcor.pvparena.database.MySQL;
import net.slipcor.pvparena.database.PlayerArenaStats;
import net.slipcor.pvparena.database.SQLite;
import net.slipcor.pvparena.listeners.BlockListener;
import net.slipcor.pvparena.listeners.EntityListener;
import net.slipcor.pvparena.listeners.InventoryListener;
import net.slipcor.pvparena.listeners.PlayerListener;
import net.slipcor.pvparena.loadables.ArenaGoalManager;
import net.slipcor.pvparena.loadables.ArenaModule;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.loadables.ArenaRegionShapeManager;
import net.slipcor.pvparena.managers.ArenaManager;
import net.slipcor.pvparena.managers.WorkflowManager;
import net.slipcor.pvparena.managers.StatisticsManager;
import net.slipcor.pvparena.managers.TabManager;
import net.slipcor.pvparena.updater.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Main Plugin class
 * </pre>
 * <p/>
 * contains central elements like plugin handlers and listeners
 *
 * @author slipcor
 */

public class PVPArena extends JavaPlugin {
    private static PVPArena instance;

    private static final int BSTATS_PLUGIN_ID = 5067;

    private ArenaGoalManager agm;
    private ArenaModuleManager amm;
    private ArenaRegionShapeManager arsm;

    private final List<AbstractArenaCommand> arenaCommands = new ArrayList<>();
    private final List<AbstractGlobalCommand> globalCommands = new ArrayList<>();

    private UpdateChecker updateChecker;
    private boolean shuttingDown;

    private Database database;
    private boolean mysql;

    public static PVPArena getInstance() {
        return instance;
    }

    /**
     * Hand over the ArenaGoalManager instance
     *
     * @return the ArenaGoalManager instance
     */
    public ArenaGoalManager getAgm() {
        return this.agm;
    }

    /**
     * Hand over the ArenaModuleManager instance
     *
     * @return the ArenaModuleManager instance
     */
    public ArenaModuleManager getAmm() {
        return this.amm;
    }

    /**
     * Hand over the ArenaRegionShapeManager instance
     *
     * @return the ArenaRegionShapeManager instance
     */
    public ArenaRegionShapeManager getArsm() {
        return this.arsm;
    }

    public List<AbstractArenaCommand> getArenaCommands() {
        return this.arenaCommands;
    }

    public List<AbstractGlobalCommand> getGlobalCommands() {
        return this.globalCommands;
    }

    public UpdateChecker getUpdateChecker() {
        return this.updateChecker;
    }

    /**
     * Returns the database instance
     *
     * @return Database instance
     */
    public Database getDatabase() {
        return database;
    }

    /**
     * Checks if MySQL is used or not
     *
     * @return if MySQL is used (false means that SQLite is being used)
     */
    public boolean isMysqlDatabase() {
        return mysql;
    }

    /**
     * Check if a CommandSender has admin permissions
     *
     * @param sender the CommandSender to check
     * @return true if a CommandSender has admin permissions, false otherwise
     */
    public static boolean hasAdminPerms(final CommandSender sender) {
        return sender.hasPermission("pvparena.admin");
    }

    /**
     * Check if a CommandSender has creation permissions
     *
     * @param sender the CommandSender to check
     * @param arena  the arena to check
     * @return true if the CommandSender has creation permissions, false
     * otherwise
     */
    public static boolean hasCreatePerms(final CommandSender sender,
                                         final Arena arena) {
        return sender.hasPermission("pvparena.create") && (arena == null || arena
                .getOwner().equals(sender.getName()));
    }

    public static boolean hasOverridePerms(final CommandSender sender) {
        if (sender instanceof Player) {
            return sender.hasPermission("pvparena.override");
        }

        return instance.getConfig().getBoolean("consoleoffduty")
                != sender.hasPermission("pvparena.override");
    }

    /**
     * Check if a CommandSender has permission for an arena
     *
     * @param sender the CommandSender to check
     * @param arena  the arena to check
     * @return true if explicit permission not needed or granted, false
     * otherwise
     */
    public static boolean hasPerms(final CommandSender sender, final Arena arena) {
        debug(arena, sender, "perm check.");
        if (arena.getArenaConfig().getBoolean(CFG.PERMS_EXPLICITARENA)) {
            debug(arena, sender, " - explicit: "
                                + sender.hasPermission("pvparena.join."
                                + arena.getName().toLowerCase()));
        } else {
            debug(arena, sender, sender.hasPermission("pvparena.user"));
        }

        return arena.getArenaConfig().getBoolean(CFG.PERMS_EXPLICITARENA) ? sender
                .hasPermission("pvparena.join." + arena.getName().toLowerCase())
                : sender.hasPermission("pvparena.user");
    }

    public boolean isShuttingDown() {
        return this.shuttingDown;
    }

    private void loadArenaCommands() {
        this.arenaCommands.add(new PAA_ArenaClassChest());
        this.arenaCommands.add(new PAA_BlackList());
        this.arenaCommands.add(new PAA_Check());
        this.arenaCommands.add(new PAA_Class());
        this.arenaCommands.add(new PAA_Disable());
        this.arenaCommands.add(new PAA_Edit());
        this.arenaCommands.add(new PAA_Enable());
        this.arenaCommands.add(new PAA_ForceWin());
        this.arenaCommands.add(new PAA_GameMode());
        this.arenaCommands.add(new PAA_Goal());
        this.arenaCommands.add(new PAA_PlayerJoin());
        this.arenaCommands.add(new PAA_PlayerClass());
        this.arenaCommands.add(new PAA_Protection());
        this.arenaCommands.add(new PAA_Regions());
        this.arenaCommands.add(new PAA_Region());
        this.arenaCommands.add(new PAA_RegionClear());
        this.arenaCommands.add(new PAA_RegionFlag());
        this.arenaCommands.add(new PAA_RegionType());
        this.arenaCommands.add(new PAA_Reload());
        this.arenaCommands.add(new PAA_Remove());
        this.arenaCommands.add(new PAA_Set());
        this.arenaCommands.add(new PAA_Setup());
        this.arenaCommands.add(new PAA_SetOwner());
        this.arenaCommands.add(new PAA_Spawn());
        this.arenaCommands.add(new PAA_Start());
        this.arenaCommands.add(new PAA_Stop());
        this.arenaCommands.add(new PAA_Teams());
        this.arenaCommands.add(new PAA_Teleport());
        this.arenaCommands.add(new PAA_Template());
        this.arenaCommands.add(new PAA_ToggleMod());
        this.arenaCommands.add(new PAA_WhiteList());
        this.arenaCommands.add(new PAG_Chat());
        this.arenaCommands.add(new PAG_Join());
        this.arenaCommands.add(new PAG_Leave());
        this.arenaCommands.add(new PAG_Spectate());
        this.arenaCommands.add(new PAI_List());
        this.arenaCommands.add(new PAI_Ready());
        this.arenaCommands.add(new PAI_Shutup());
        this.arenaCommands.add(new PAG_Arenaclass());
        this.arenaCommands.add(new PAI_Info());
        this.arenaCommands.add(new PAI_Stats());
    }

    private void loadGlobalCommands() {
        this.globalCommands.add(new PAA_Create());
        this.globalCommands.add(new PAA_Debug());
        this.globalCommands.add(new PAA_Duty());
        this.globalCommands.add(new PAA_Modules());
        this.globalCommands.add(new PAA_ReloadAll());
        this.globalCommands.add(new PAI_ArenaList());
        this.globalCommands.add(new PAI_GlobalStats());
        this.globalCommands.add(new PAI_Help());
        this.globalCommands.add(new PAI_Version());
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd,
                             final String commandLabel, final String[] args) {

        if (args.length < 1) {
            sender.sendMessage(ChatColor.COLOR_CHAR + "e"
                    + ChatColor.COLOR_CHAR + "l|-- PVP Arena --|");
            sender.sendMessage(ChatColor.COLOR_CHAR + "e"
                    + ChatColor.COLOR_CHAR + "o--By slipcor--");
            sender.sendMessage(ChatColor.COLOR_CHAR + "7"
                    + ChatColor.COLOR_CHAR + "oDo " + ChatColor.COLOR_CHAR
                    + "e/pa help " + ChatColor.COLOR_CHAR + '7'
                    + ChatColor.COLOR_CHAR + "ofor help.");
            return true;
        }

        if (args.length > 1 && sender.hasPermission("pvparena.admin")
                && "ALL".equalsIgnoreCase(args[0])) {
            final String[] newArgs = StringParser.shiftArrayBy(args, 1);
            for (final Arena arena : ArenaManager.getArenas()) {
                try {
                    Bukkit.getServer().dispatchCommand(
                            sender,
                            "pa " + arena.getName() + ' '
                                    + StringParser.joinArray(newArgs, " "));
                } catch (final Exception e) {
                    this.getLogger().warning("arena null!");
                }
            }
            return true;

        }

        AbstractGlobalCommand pacmd = null;
        for (final AbstractGlobalCommand agc : this.globalCommands) {
            if (agc.getMain().contains(args[0].toLowerCase()) || agc.getShort().contains(args[0].toLowerCase())) {
                pacmd = agc;
                break;
            }
        }
        final ArenaPlayer player = ArenaPlayer.parsePlayer(sender.getName());
        if (pacmd != null && !(player.getArena() != null && pacmd.hasVersionForArena())) {
            debug(sender, "committing: " + pacmd.getName());
            pacmd.commit(sender, StringParser.shiftArrayBy(args, 1));
            return true;
        }

        Arena tempArena = "l".equalsIgnoreCase(args[0])?player.getArena():ArenaManager.getIndirectArenaByName(sender, args[0]);

        final String name = args[0];

        if (tempArena == null && Arrays.asList(args).contains("vote")) {
            tempArena = ArenaManager.getArenaByName(args[0]); // arenavote shortcut hack
        }

        String[] newArgs = args;

        if (tempArena == null) {
            if (sender instanceof Player
                    && ArenaPlayer.parsePlayer(sender.getName()).getArena() != null) {
                tempArena = ArenaPlayer.parsePlayer(sender.getName())
                        .getArena();
            } else if (PAA_Setup.activeSetups.containsKey(sender.getName())) {
                tempArena = PAA_Setup.activeSetups.get(sender.getName());
            } else if (PAA_Edit.activeEdits.containsKey(sender.getName())) {
                tempArena = PAA_Edit.activeEdits.get(sender.getName());
            } else if (ArenaManager.count() == 1) {
                tempArena = ArenaManager.getFirst();
            } else if (ArenaManager.count() < 1) {
                Arena.pmsg(sender, Language.parse(MSG.ERROR_NO_ARENAS));
                return true;
            } else if (ArenaManager.countAvailable() == 1) {
                tempArena = ArenaManager.getAvailable();
            }
        } else {
            if (args.length > 1) {
                newArgs = StringParser.shiftArrayBy(args, 1);
            }
        }

        latelounge:
        if (tempArena == null) {
            for (final Arena ar : ArenaManager.getArenas()) {
                for (final ArenaModule mod : ar.getMods()) {
                    if (mod.hasSpawn(sender.getName())) {
                        tempArena = ar;
                        break latelounge;
                    }
                }
            }

            Arena.pmsg(sender, Language.parse(MSG.ERROR_ARENA_NOTFOUND, name));
            return true;
        }

        AbstractArenaCommand paacmd = null;
        for (final AbstractArenaCommand aac : this.arenaCommands) {
            if (aac.getMain().contains(newArgs[0].toLowerCase()) || aac.getShort().contains(newArgs[0].toLowerCase())) {
                paacmd = aac;
                break;
            }

            if (aac.getShort().contains("-l") && "l".equalsIgnoreCase(args[0])) {
                paacmd = aac;
                break;
            }
        }
        if (paacmd == null && WorkflowManager.handleCommand(tempArena, sender, newArgs)) {
            return true;
        }

        if (paacmd == null && tempArena.getArenaConfig().getBoolean(CFG.CMDS_DEFAULTJOIN) && args.length == 1) {
            paacmd = new PAG_Join();
            debug(tempArena, sender, "committing: " + paacmd.getName());
            paacmd.commit(tempArena, sender, new String[0]);
            return true;
        }

        if (paacmd != null) {
            debug(tempArena, sender, "committing: " + paacmd.getName());
            paacmd.commit(tempArena, sender,
                    StringParser.shiftArrayBy(newArgs, 1));
            return true;
        }
        debug(tempArena, sender, "cmd null");

        return false;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String alias, final String[] args) {
        return TabManager.getMatches(sender, this.arenaCommands, this.globalCommands, args);
    }

    @Override
    public void onDisable() {
        if(this.database != null) {
            this.database.closeConnection();
        }
        this.shuttingDown = true;
        ArenaManager.reset(true);
        Debugger.destroy();
        this.getUpdateChecker().runOnDisable();
        Language.logInfo(MSG.LOG_PLUGIN_DISABLED, this.getDescription().getFullName());
    }

    @Override
    public void onEnable() {
        this.shuttingDown = false;
        instance = this;

        // Small check to make sure that PlaceholderAPI is installed
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            new PvpArenaPlaceholderExpansion(this).register();
        }

        getCommand("test").setExecutor(new TestCommand());

        // TODO: Enable bStats
        // Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);

        this.initDefaultConfig();

        this.getDataFolder().mkdir();
        new File(this.getDataFolder().getPath() + "/arenas").mkdir();
        new File(this.getDataFolder().getPath() + "/goals").mkdir();
        new File(this.getDataFolder().getPath() + "/mods").mkdir();
        new File(this.getDataFolder().getPath() + "/regionshapes").mkdir();
        new File(this.getDataFolder().getPath() + "/dumps").mkdir();
        new File(this.getDataFolder().getPath() + "/files").mkdir();
        new File(this.getDataFolder().getPath() + "/templates").mkdir();

        FileConfiguration cfg = this.getConfig();
        List<String> toDelete = cfg.getStringList("todelete");
        if (!toDelete.isEmpty()){
            for (String jar : toDelete) {
                PAA_Modules.remove(jar);
            }
            cfg.set("todelete", null);
            this.saveConfig();
        }

        this.agm = new ArenaGoalManager(this);
        this.amm = new ArenaModuleManager(this);
        this.arsm = new ArenaRegionShapeManager(this);

        this.loadArenaCommands();
        this.loadGlobalCommands();

        Language.init(this.getConfig().getString("language", "en"));
        Help.init(this.getConfig().getString("language", "en"));

        this.getServer().getPluginManager()
                .registerEvents(new BlockListener(), this);
        this.getServer().getPluginManager().registerEvents(new EntityListener(),
                this);
        this.getServer().getPluginManager().registerEvents(new PlayerListener(),
                this);
        this.getServer().getPluginManager().registerEvents(new InventoryListener(),
                this);

        if (this.getConfig().getInt("ver", 0) < 1) {
            this.getConfig().options().copyDefaults(true);
            this.getConfig().set("ver", 1);
            this.saveConfig();
        }

        Debugger.load(this, Bukkit.getConsoleSender());
        ArenaClass.addGlobalClasses();
        ArenaManager.load_arenas();

        if (this.getConfig().getBoolean("use_shortcuts") ||
                this.getConfig().getBoolean("only_shortcuts")) {
            ArenaManager.readShortcuts(this.getConfig().getConfigurationSection("shortcuts"));
        }

        this.initDatabase();

        this.updateChecker = new UpdateChecker(this.getFile());

        StatisticsManager.initialize();

        Language.logInfo(MSG.LOG_PLUGIN_ENABLED, this.getDescription().getFullName());
    }

    /**
     * Set default values to config
     */
    private void initDefaultConfig() {

        boolean mustSave = false;

        this.saveDefaultConfig();
        if (!this.getConfig().contains("shortcuts")) {
            final List<String> ffa = new ArrayList<>();
            final List<String> teams = new ArrayList<>();

            ffa.add("arena1");
            ffa.add("arena2");

            teams.add("teamarena1");
            teams.add("teamarena2");

            this.getConfig().options().copyDefaults(true);
            this.getConfig().addDefault("shortcuts.freeforall", ffa);
            this.getConfig().addDefault("shortcuts.teams", teams);

            mustSave = true;
        }

        if (this.getConfig().contains("update.type") || this.getConfig().contains("update.mode")) {
            this.getConfig().set("update.plugin", this.getConfig().getString("update.mode", "announce"));
            this.getConfig().set("update.modules", this.getConfig().getBoolean("update.modules", true) ? "download" : "announce");
            this.getConfig().set("update.type", null);
            this.getConfig().set("update.mode", null);

            mustSave = true;
        }

        if (this.getConfig().contains("mysql.enabled") || this.getConfig().contains("mysql.host")) {
            this.getConfig().set("mysql.enabled", true);
            this.getConfig().set("mysql.host", "localhost");
            this.getConfig().set("mysql.port", 3306);
            this.getConfig().set("mysql.user", "root");
            this.getConfig().set("mysql.pass", "root");
            this.getConfig().set("mysql.base", "pvparena");
            this.getConfig().set("mysql.prefix", "pvparena_");

            mustSave = true;
        }

        if(mustSave){
            this.saveConfig();
        }
    }

    private void initDatabase(){
        // try to connect to database
        final boolean isMysqlEnabled = getConfig().getBoolean("mysql.enabled", true);
        if (isMysqlEnabled) {
            PVPArena.getInstance().getLogger().info("Connecting to MySQL database");
            this.database = new MySQL(this, getConfig().getString("mysql.host"),
                    getConfig().getString("mysql.port"),
                    getConfig().getString("mysql.base"), getConfig().getString("mysql.user"),
                    getConfig().getString("mysql.pass"));
            if (this.database.getConnection() != null) {
                this.mysql = true;
                PVPArena.getInstance().getLogger().info("Successfully connected to MySQL database.");
            }
        }
        if (!isMysqlEnabled || !this.mysql) {
            this.database = new SQLite(this, "database.db");
            if (isMysqlEnabled) {
                PVPArena.getInstance().getLogger().warning( "No connection to the mySQL Database! Using SQLite for storing data as fallback.");
            } else {
                PVPArena.getInstance().getLogger().info("Using SQLite for storing data.");
            }
            if (this.database.getConnection() != null) {
                this.mysql = false;
                PVPArena.getInstance().getLogger().info("Successfully connected to SQLite database.");
            }
        }
        this.database.init();
    }

}
