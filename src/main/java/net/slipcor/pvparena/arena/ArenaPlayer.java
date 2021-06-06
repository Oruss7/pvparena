package net.slipcor.pvparena.arena;

import org.jetbrains.annotations.NotNull;
import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PALocation;
import net.slipcor.pvparena.classes.PAStatMap;
import net.slipcor.pvparena.core.ColorUtils;
import net.slipcor.pvparena.core.Config;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.database.PlayerArenaStats;
import net.slipcor.pvparena.events.PAPlayerClassChangeEvent;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.ArenaManager;
import net.slipcor.pvparena.managers.InventoryManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.StatisticsManager.Type;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.util.*;

import static java.util.Optional.ofNullable;
import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Player class
 * </pre>
 * <p/>
 * contains Arena Player methods and variables for quicker access
 *
 * @author slipcor
 * @version v0.10.2
 */

public class ArenaPlayer {
    private static final Map<UUID, ArenaPlayer> arenaPlayersCache = new HashMap<>();

    private final Player player;

    private boolean telePass;
    private boolean ignoreAnnouncements;
    private boolean teleporting;
    private boolean mayDropInventory;

    private Boolean flying;

    private Arena arena;
    private ArenaClass aClass;
    private ArenaClass naClass;
    private PlayerState state;
    private PALocation location;
    private Status status = Status.NULL;

    private ItemStack[] savedInventory;
    private final Set<PermissionAttachment> tempPermissions = new HashSet<>();
    private final Map<String, PAStatMap> statistics = new HashMap<>();
    private PlayerArenaStats stats;

    private Scoreboard backupBoard;
    private String backupBoardTeam;

    /**
     * Status
     *
     * <pre>
     * - NULL = not part of an arena
     * - WARM = not part of an arena, warmed up
     * - LOUNGE = inside an arena lobby mode
     * - READY = inside an arena lobby mode, readied up
     * - FIGHT = fighting inside an arena
     * - WATCH = watching a fight from the spectator area
     * - DEAD = dead and soon respawning
     * - LOST = lost and thus spectating
     * </pre>
     */
    public enum Status {
        NULL, WARM, LOUNGE, READY, FIGHT, WATCH, DEAD, LOST
    }


    /**
     * PlayerPrevention
     *
     * <pre>
     * BREAK - Block break
     * PLACE - Block placement
     * TNT - TNT usage
     * TNTBREAK - TNT block break
     * DROP - dropping items
     * INVENTORY - accessing inventory
     * PICKUP - picking up stuff
     * CRAFT - crafting stuff
     * </pre>
     */
    public enum PlayerPrevention {
        BREAK, PLACE, TNT, TNTBREAK, DROP, INVENTORY, PICKUP, CRAFT;

        public static boolean has(int value, PlayerPrevention s) {
            return (((int) Math.pow(2, s.ordinal()) & value) > 0);
        }
    }

    private boolean publicChatting = true;
    private final PABlockLocation[] selection = new PABlockLocation[2];

    /**
     * Create new ArenaPlayer
     *
     * @param player bukkit player
     */
    private ArenaPlayer(@NotNull final Player player) {
        Objects.requireNonNull(player);
        this.player = player;
        this.stats = new PlayerArenaStats();
        this.stats.setUuid(player.getUniqueId().toString());
    }

    public Player getPlayer() {
        return this.player;
    }

    public static Set<ArenaPlayer> getAllArenaPlayers() {
        return new HashSet<>(arenaPlayersCache.values());
    }

    public boolean getFlyState() {
        return this.flying != null && this.flying;
    }

    public PlayerArenaStats getStats() {
        return this.stats;
    }

    /**
     * try to find the last damaging player
     *
     * @param eEvent the Event
     * @return the player instance if found, null otherwise
     */
    public static Player getLastDamagingPlayer(final Event eEvent, final Player damagee) {
        debug(damagee, "trying to get the last damaging player");
        if (eEvent instanceof EntityDamageByEntityEvent) {
            debug(damagee, "there was an EDBEE");
            final EntityDamageByEntityEvent event = (EntityDamageByEntityEvent) eEvent;

            Entity eDamager = event.getDamager();

            if (event.getCause() == DamageCause.PROJECTILE
                    && eDamager instanceof Projectile) {

                final ProjectileSource p = ((Projectile) eDamager).getShooter();

                if (p instanceof LivingEntity) {

                    eDamager = (LivingEntity) p;


                    debug(damagee, "killed by projectile, shooter is found");
                }
            }

            if (event.getEntity() instanceof Wolf) {
                final Wolf wolf = (Wolf) event.getEntity();
                if (wolf.getOwner() != null) {
                    eDamager = (Entity) wolf.getOwner();
                    debug(damagee, "tamed wolf is found");
                }
            }

            if (eDamager instanceof Player) {
                debug(damagee, "it was a player!");
                return (Player) eDamager;
            }
        }
        debug(damagee, "last damaging player is null");
        debug(damagee, "last damaging event: {}", ofNullable(eEvent).map(Event::getEventName).orElse("unknown cause"));
        return null;
    }

    /**
     * supply a player with class items and eventually wool head
     *
     * @param player the player to supply
     */
    public static void givePlayerFightItems(final Arena arena, final Player player) {
        final ArenaPlayer aPlayer = parsePlayer(player.getName());

        final ArenaClass playerClass = aPlayer.aClass;
        if (playerClass == null) {
            return;
        }
        debug(player, "giving items to player '{}', class '{}'", player, playerClass);

        playerClass.equip(player);

        if (arena.getArenaConfig().getBoolean(CFG.USES_WOOLHEAD)) {
            final ArenaTeam aTeam = aPlayer.getArenaTeam();
            final ChatColor color = aTeam.getColor();
            debug(player, "forcing woolhead: {}/{}", aTeam.getName(), color);
            player.getInventory().setHelmet(new ItemStack(ColorUtils.getWoolMaterialFromChatColor(color)));
        }
    }

    /**
     * get an ArenaPlayer from a online player name
     *
     * @param name the playername to use
     * @return an ArenaPlayer instance belonging to that player
     */
    public static ArenaPlayer parsePlayer(final String name) {
        synchronized (ArenaPlayer.class) {
            Player player = Bukkit.getPlayerExact(name);

            // Offline player or NPC
            if (player == null) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
                if (offlinePlayer.getPlayer() == null) {
                    throw new RuntimeException(String.format("Player %s not found", name));
                }
                player = offlinePlayer.getPlayer();
            }

            if (!arenaPlayersCache.containsKey(player.getUniqueId())) {
                ArenaPlayer ap = new ArenaPlayer(player);
                arenaPlayersCache.putIfAbsent(player.getUniqueId(), ap);
            }
            return arenaPlayersCache.get(player.getUniqueId());
        }
    }

    /**
     * add an ArenaPlayer (used to load statistics)
     *
     * @param player the player to use
     * @return an ArenaPlayer instance belonging to that player
     */
    public static ArenaPlayer addPlayer(final Player player) {
        synchronized (ArenaPlayer.class) {
            ArenaPlayer aPlayer = new ArenaPlayer(player);
            arenaPlayersCache.putIfAbsent(player.getUniqueId(), aPlayer);
            return arenaPlayersCache.get(player.getUniqueId());
        }
    }

    public static ArenaPlayer addPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            player = offlinePlayer.getPlayer();
            if (player == null) {
                throw new RuntimeException(String.format("Player with uuid %s not found", uuid));
            }
        }
        return addPlayer(player);
    }

    /**
     * prepare a player's inventory, back it up and clear it
     *
     * @param player the player to save
     */
    public static void backupAndClearInventory(final Arena arena, final Player player) {
        debug(player, "saving player inventory: {}", player);

        final ArenaPlayer aPlayer = parsePlayer(player.getName());
        aPlayer.savedInventory = player.getInventory().getContents().clone();
        InventoryManager.clearInventory(player);
    }

    public static void reloadInventory(final Arena arena, final Player player, final boolean instant) {

        if (player == null) {
            return;
        }

        debug(player, "resetting inventory");

        final ArenaPlayer aPlayer = parsePlayer(player.getName());

        if (arena.getArenaConfig().getYamlConfiguration().get(CFG.ITEMS_TAKEOUTOFGAME.getNode()) != null) {
            final ItemStack[] items =
                    arena.getArenaConfig().getItems(CFG.ITEMS_TAKEOUTOFGAME);

            final List<Material> allowedMats = new ArrayList<>();

            for (final ItemStack item : items) {
                allowedMats.add(item.getType());
            }

            final List<ItemStack> keepItems = new ArrayList<>();
            for (final ItemStack item : player.getInventory().getContents()) {
                if (item == null) {
                    continue;
                }
                if (allowedMats.contains(item.getType())) {
                    keepItems.add(item.clone());
                }
            }

            class GiveLater implements Runnable {

                @Override
                public void run() {
                    for (final ItemStack item : keepItems) {
                        player.getInventory().addItem(item.clone());
                    }
                    keepItems.clear();
                }

            }

            try {
                Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), new GiveLater(), 60L);
            } catch (final Exception e) {

            }
        }
        InventoryManager.clearInventory(player);

        if (aPlayer.savedInventory == null) {
            debug(player, "saved inventory null!");
            return;
        }
        // AIR AIR AIR AIR instead of contents !!!!

        if (instant) {

            debug(player, "adding saved inventory");
            player.getInventory().setContents(aPlayer.savedInventory);
        } else {
            class GiveLater implements Runnable {
                final ItemStack[] inv;

                GiveLater(final ItemStack[] inv) {
                    this.inv = inv.clone();
                }

                @Override
                public void run() {
                    debug(player, "adding saved inventory");
                    player.getInventory().setContents(this.inv);
                }
            }
            final GiveLater gl = new GiveLater(aPlayer.savedInventory);
            try {
                Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), gl, 60L);
            } catch (final Exception e) {
                gl.run();
            }
        }
    }

    public void addDeath() {
        this.getStatistics(this.arena).incStat(Type.DEATHS);
        this.stats.addDeaths(1L);
    }

    public void addKill() {
        this.getStatistics(this.arena).incStat(Type.KILLS);
        this.stats.addKills(1L);
    }

    public void addLosses() {
        PVPArena.getInstance().getLogger().fine("Add lose to player " + this.player.getName());
        this.getStatistics(this.arena).incStat(Type.LOSSES);
        this.stats.addLosses(1L);
    }

    public void addStatistic(final String arenaName, final Type type,
                             final int value) {
        if (!this.statistics.containsKey(arenaName)) {
            this.statistics.put(arenaName, new PAStatMap());
        }

        this.statistics.get(arenaName).incStat(type, value);
    }

    public void addWins() {
        PVPArena.getInstance().getLogger().fine("Add win to player " + this.player.getName());
        this.getStatistics(this.arena).incStat(Type.WINS);
        this.stats.addWins(1L);
    }

    private void clearDump() {
        debug(this.getPlayer(), "clearing dump of {}", this.player.getName());
        this.debugPrint();
        final File file = new File(PVPArena.getInstance().getDataFolder().getPath()
                + "/dumps/" + this.player.getName() + ".yml");
        if (!file.exists()) {
            return;
        }
        file.delete();
    }

    public void clearFlyState() {
        this.flying = null;
    }

    /**
     * save the player state
     *
     * @param player the player to save
     */
    public void createState(final Player player) {
        this.state = new PlayerState(player);
        this.mayDropInventory = true;
    }

    public boolean didValidSelection() {
        return this.selection[0] != null && this.selection[1] != null;
    }

    public void debugPrint() {
        if (this.status == null || this.location == null) {
            debug(this.getPlayer(), "DEBUG PRINT OUT:");
            debug(this.getPlayer(), this.player.getName());
            debug(this.getPlayer(), String.valueOf(this.status));
            debug(this.getPlayer(), String.valueOf(this.location));
            debug(this.getPlayer(), String.valueOf(this.selection[0]));
            debug(this.getPlayer(), String.valueOf(this.selection[1]));
            return;
        }
        debug(this.getPlayer(), "------------------");
        debug(this.getPlayer(), "Player: {}", this.player.getName());
        debug(this.getPlayer(), "telepass: {} | mayDropInv: {} | chatting: {}", this.telePass, this.mayDropInventory, this.publicChatting);
        debug(this.getPlayer(), "arena: {}", (this.arena == null ? "null" : this.arena.getName()));
        debug(this.getPlayer(), "aClass: {}", (this.aClass == null ? "null" : this.aClass.getName()));
        debug(this.getPlayer(), "location: {}", this.location);
        debug(this.getPlayer(), "status: {}", this.status.name());
        debug(this.getPlayer(), "tempPermissions:");
        for (final PermissionAttachment pa : this.tempPermissions) {
            debug(this.getPlayer(), "> {}", pa);
        }
        debug(this.getPlayer(), "------------------");
    }

    public void dump() {
        debug(this.getPlayer(), "dumping...");
        this.debugPrint();
        final File file = new File(PVPArena.getInstance().getDataFolder().getPath()
                + "/dumps/" + this.player.getName() + ".yml");
        try {
            file.createNewFile();
        } catch (final Exception e) {
            e.printStackTrace();
            return;
        }

        final YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("arena", this.arena.getName());
        if (this.state != null) {
            this.state.dump(cfg);
        }

        try {
            cfg.set("inventory", this.savedInventory);
            cfg.set("loc", Config.parseToString(this.location));

            cfg.save(file);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * return the arena
     *
     * @return the arena
     */
    public Arena getArena() {
        return this.arena;
    }

    /**
     * return the arena class
     *
     * @return the arena class
     */
    public ArenaClass getArenaClass() {
        return this.aClass;
    }

    public ArenaClass getNextArenaClass() {
        return this.naClass;
    }

    public ArenaTeam getArenaTeam() {
        if (this.arena == null) {
            return null;
        }
        for (final ArenaTeam team : this.arena.getTeams()) {
            if (team.getTeamMembers().contains(this)) {
                return team;
            }
        }
        return null;
    }

    public Scoreboard getBackupScoreboard() {
        return this.backupBoard;
    }

    public String getBackupScoreboardTeam() {
        return this.backupBoardTeam;
    }

    public PALocation getSavedLocation() {
        debug(this.getPlayer(), "reading loc!");
        if (this.location != null) {
            debug(this.getPlayer(), ": {}", this.location);
        }
        return this.location;
    }

    /**
     * return the player name
     *
     * @return the player name
     */
    public String getName() {
        return this.player.getName();
    }

    public PABlockLocation[] getSelection() {
        return this.selection.clone();
    }

    /**
     * return the player state
     *
     * @return the player state
     */
    public PlayerState getState() {
        return this.state;
    }

    public PAStatMap getStatistics() {
        return this.getStatistics(this.arena);
    }

    public PAStatMap getStatistics(final Arena arena) {
        if (arena == null) {
            return new PAStatMap();
        }
        if (this.statistics.get(arena.getName()) == null) {
            this.statistics.put(arena.getName(), new PAStatMap());
        }
        return this.statistics.get(arena.getName());
    }

    public Status getStatus() {
        return this.status;
    }

    /**
     * hand over a player's tele pass
     *
     * @return true if may pass, false otherwise
     */
    public boolean isTelePass() {
        return this.hasTelePass();
    }

    public boolean isTeleporting() {
        return this.teleporting;
    }

    public boolean mayDropInventory() {
        return this.mayDropInventory;
    }

    public Set<PermissionAttachment> getTempPermissions() {
        return this.tempPermissions;
    }

    public int getTotalStatistics(final Type statType) {
        int sum = 0;

        for (final PAStatMap stat : this.statistics.values()) {
            sum += stat.getStat(statType);
        }

        return sum;
    }

    public boolean hasBackupScoreboard() {
        return this.backupBoard != null;
    }

    public boolean hasTelePass() {
        return this.telePass;
    }

    public boolean isIgnoringAnnouncements() {
        return this.ignoreAnnouncements;
    }

    public boolean isPublicChatting() {
        return this.publicChatting;
    }

    public boolean hasCustomClass() {
        return this.getArenaClass() != null && "custom".equalsIgnoreCase(this.getArenaClass().getName());
    }

    public void readDump() {
        debug(this.getPlayer(), "reading dump: {}", this.player.getName());
        this.debugPrint();
        final File file = new File(PVPArena.getInstance().getDataFolder().getPath()
                + "/dumps/" + this.player.getName() + ".yml");
        if (!file.exists()) {
            debug(this.getPlayer(), "no dump!");
            return;
        }

        final YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
        } catch (final Exception e) {
            e.printStackTrace();
            return;
        }

        this.arena = ArenaManager.getArenaByName(cfg.getString("arena"));
        this.savedInventory = cfg.getList("inventory").toArray(new ItemStack[0]);
                /*StringParser.getItemStacksFromString(cfg.getString(
                "inventory", "AIR"));*/
        this.location = Config.parseLocation(cfg.getString("loc"));

        if (this.arena != null) {
            final String goTo = this.arena.getArenaConfig().getString(CFG.TP_EXIT);
            if (!"old".equals(goTo)) {
                this.location = SpawnManager.getSpawnByExactName(this.arena, "exit");
            }

            if (Bukkit.getPlayer(this.player.getName()) == null) {
                debug(this.getPlayer(), "player offline, OUT!");
                return;
            }
            this.state = PlayerState.undump(cfg, this.player.getName());
        }

        file.delete();
        this.debugPrint();
    }

    /**
     * save and reset a player instance
     */
    public void reset() {
        debug(this.getPlayer(), "destroying arena player {}", this.player.getName());
        this.debugPrint();
        final YamlConfiguration cfg = new YamlConfiguration();
        try {
            if (PVPArena.getInstance().getConfig().getBoolean("stats")) {

                if(this.arena != null) {
                    this.stats.setArena(this.arena.getName());
                    PVPArena.getInstance().getDatabase().addPlayerStats(this.stats);
                    this.stats = new PlayerArenaStats();
                    this.stats.setUuid(this.player.getUniqueId().toString());
                }

                final String file = PVPArena.getInstance().getDataFolder()
                        + "/players.yml";
                cfg.load(file);

                if (this.arena != null) {
                    final String arenaName = this.arena.getName();
                    cfg.set(arenaName + '.' + this.player.getName() + ".losses", this.getStatistics()
                            .getStat(Type.LOSSES)
                            + this.getTotalStatistics(Type.LOSSES));
                    cfg.set(arenaName + '.' + this.player.getName() + ".wins",
                            this.getStatistics()
                                    .getStat(Type.WINS)
                                    + this.getTotalStatistics(Type.WINS));
                    cfg.set(arenaName + '.' + this.player.getName() + ".kills",
                            this.getStatistics().getStat(
                                    Type.KILLS)
                                    + this.getTotalStatistics(Type.KILLS));
                    cfg.set(arenaName + '.' + this.player.getName() + ".deaths", this.getStatistics()
                            .getStat(Type.DEATHS)
                            + this.getTotalStatistics(Type.DEATHS));
                    cfg.set(arenaName + '.' + this.player.getName() + ".damage", this.getStatistics()
                            .getStat(Type.DAMAGE)
                            + this.getTotalStatistics(Type.DAMAGE));
                    cfg.set(arenaName + '.' + this.player.getName() + ".maxdamage",
                            this.getStatistics().getStat(
                                    Type.MAXDAMAGE)
                                    + this.getTotalStatistics(Type.MAXDAMAGE));
                    cfg.set(arenaName + '.' + this.player.getName() + ".damagetake",
                            this.getStatistics().getStat(
                                    Type.DAMAGETAKE)
                                    + this.getTotalStatistics(Type.DAMAGETAKE));
                    cfg.set(arenaName + '.' + this.player.getName() + ".maxdamagetake",
                            this.getStatistics().getStat(
                                    Type.MAXDAMAGETAKE)
                                    + this.getTotalStatistics(Type.MAXDAMAGETAKE));
                }

                cfg.save(file);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        if (this.getPlayer() == null) {
            debug(this.getPlayer(), "reset() ; out! null");
            return;
        }

        this.telePass = false;

        if (this.state != null) {
            this.state.reset();
            this.state = null;
        }

        this.setStatus(Status.NULL);
        this.naClass = null;

        if (this.arena != null) {
            final ArenaTeam team = this.getArenaTeam();
            if (team != null) {
                team.remove(this);
            }
        }
        this.arena = null;
        this.aClass = null;
        this.getPlayer().setFireTicks(0);
        try {
            Bukkit.getScheduler().runTaskLater(PVPArena.getInstance(), () -> {
                if (ArenaPlayer.this.getPlayer() != null && ArenaPlayer.this.getPlayer().getFireTicks() > 0) {
                    ArenaPlayer.this.getPlayer().setFireTicks(0);
                }
            }, 5L);
        } catch (Exception e) {
        }

        this.clearDump();
    }

    /**
     * set the player's arena
     *
     * @param arena the arena to set
     */
    public final void setArena(final Arena arena) {
        this.arena = arena;
    }

    /**
     * set the player's arena class
     *
     * @param aClass the arena class to set
     */
    public void setArenaClass(final ArenaClass aClass) {
        final PAPlayerClassChangeEvent event = new PAPlayerClassChangeEvent(this.arena, this.getPlayer(), aClass);
        Bukkit.getServer().getPluginManager().callEvent(event);
        this.aClass = event.getArenaClass();
        if (this.arena != null && this.getStatus() != Status.NULL) {
            ArenaModuleManager.parseClassChange(this.arena, this.getPlayer(), this.aClass);
        }
    }

    /**
     * set a player's arena class by name
     *
     * @param className an arena class name
     */
    public void setArenaClass(final String className) {

        for (final ArenaClass ac : this.arena.getClasses()) {
            if (ac.getName().equalsIgnoreCase(className)) {
                this.setArenaClass(ac);
                return;
            }
        }
        PVPArena.getInstance().getLogger().warning(
                String.format("[PA-debug] failed to set unknown class %s to player %s", className, this.player.getName()));
    }

    public void setBackupScoreboard(Scoreboard board) {
        this.backupBoard = board;
    }

    public void setBackupScoreboardTeam(String sbTeamName) {
        this.backupBoardTeam = sbTeamName;
    }

    public void setMayDropInventory(boolean value) {
        this.mayDropInventory = value;
    }

    public void setNextArenaClass(ArenaClass aClass) {
        this.naClass = aClass;
    }

    public void setFlyState(boolean flyState) {
        this.flying = flyState;
    }

    public void setIgnoreAnnouncements(final boolean value) {
        this.ignoreAnnouncements = value;
    }

    public void setLocation(final PALocation location) {
        this.location = location;
    }

    public void setPublicChatting(final boolean chatPublic) {
        this.publicChatting = chatPublic;
    }

    public void setSelection(final Location loc, final boolean second) {
        if (second) {
            this.selection[1] = new PABlockLocation(loc);
        } else {
            this.selection[0] = new PABlockLocation(loc);
        }
    }

    public void setStatistic(final String arenaName, final Type type,
                             final int value) {
        if (!this.statistics.containsKey(arenaName)) {
            this.statistics.put(arenaName, new PAStatMap());
        }

        final PAStatMap map = this.statistics.get(arenaName);
        map.setStat(type, value);
    }

    public void setStatus(final Status status) {
        debug(this.getPlayer(), "{}>{}", this.player.getName(), status.name());
        this.status = status;
    }

    /**
     * hand over a player's tele pass
     *
     * @param canTeleport true if may pass, false otherwise
     */
    public void setTelePass(final boolean canTeleport) {
        if (this.arena != null) {
            debug(this.arena, "TelePass := {}", canTeleport);
        }
        this.telePass = canTeleport;
    }

    public void setTeleporting(final boolean isTeleporting) {
        this.teleporting = isTeleporting;
    }

    public void showBloodParticles() {
        this.player.getLocation()
                .getWorld()
                .playEffect(this.player.getEyeLocation(), Effect.STEP_SOUND, Material.NETHER_WART_BLOCK);

    }

    @Override
    public String toString() {
        final ArenaTeam team = this.getArenaTeam();

        return team == null ? this.player.getName() : team.getColorCodeString() + this.player.getName() + ChatColor.RESET;
    }

    public void unsetSelection() {
        this.selection[0] = null;
        this.selection[1] = null;
    }
}
