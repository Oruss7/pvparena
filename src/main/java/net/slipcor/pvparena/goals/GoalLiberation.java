package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.arena.PlayerStatus;
import net.slipcor.pvparena.classes.PABlock;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PASpawn;
import net.slipcor.pvparena.classes.PADeathInfo;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringUtils;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.InventoryManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.TeamManager;
import net.slipcor.pvparena.managers.WorkflowManager;
import net.slipcor.pvparena.runnables.EndRunnable;
import net.slipcor.pvparena.runnables.InventoryRefillRunnable;
import net.slipcor.pvparena.runnables.RespawnRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "Liberation"
 * </pre>
 * <p/>
 * Players have lives. When every life is lost, the player is teleported
 * to the killer's team's jail. Once every player of a team is jailed, the
 * team is out.
 *
 * @author slipcor
 */

public class GoalLiberation extends ArenaGoal {

    private static final String BUTTON = "button";
    private static final String JAIL = "jail";

    public GoalLiberation() {
        super("Liberation");
    }

    private EndRunnable endRunner;
    private String blockName;
    private String blockTeamName;
    private Map<Player, List<ItemStack>> keptItemsMap = new HashMap<>();

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    @Override
    public boolean checkCommand(String command) {
        return command.equalsIgnoreCase(BUTTON);
    }

    @Override
    public List<String> getGoalCommands() {
        return Collections.singletonList(BUTTON);
    }

    @Override
    public boolean checkEnd() {
        debug(this.arena, "checkEnd - " + this.arena.getName());
        final int count = TeamManager.countActiveTeams(this.arena);
        debug(this.arena, "count: " + count);

        return count <= 1; // yep. only one team left. go!
    }

    @Override
    public Set<PASpawn> checkForMissingSpawns(Set<PASpawn> spawns) {
        Set<PASpawn> missing = SpawnManager.getMissingTeamSpawn(this.arena, spawns);
        missing.addAll(SpawnManager.getMissingTeamCustom(this.arena, spawns, JAIL));
        return missing;
    }

    @Override
    public Set<PABlock> checkForMissingBlocks(Set<PABlock> blocks) {
        return SpawnManager.getMissingBlocksTeamCustom(this.arena, blocks, BUTTON);
    }

    /**
     * hook into an interacting player
     *
     * @param player the interacting player
     * @param event  the interact event
     * @return true if event has been handled
     */
    @Override
    public boolean checkInteract(final Player player, final PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return false;
        }
        debug(this.arena, player, String.format("[%s] checking interact", this.name));

        if (Tag.BUTTONS.isTagged(block.getType())) {
            debug(this.arena, player, "block, but not button");
            return false;
        }
        debug(this.arena, player, "button click!");

        final ArenaPlayer arenaPlayer = ArenaPlayer.fromPlayer(player);

        final ArenaTeam playerArenaTeam = arenaPlayer.getArenaTeam();
        if (playerArenaTeam == null) {
            return false;
        }

        Vector vFlag = null;
        for (ArenaTeam arenaTeam : this.arena.getNotEmptyTeams()) {

            if (arenaTeam.equals(playerArenaTeam)) {
                debug(this.arena, player, "equals!OUT! ");
                continue;
            }
            debug(this.arena, player, "checking for flag of team " + arenaTeam);
            Vector vLoc = block.getLocation().toVector();
            debug(this.arena, player, "block: " + vLoc);
            if (!SpawnManager.getBlocksStartingWith(this.arena, BUTTON, arenaTeam.getName()).isEmpty()) {
                vFlag = SpawnManager
                        .getBlockNearest(
                                SpawnManager.getBlocksStartingWith(this.arena, BUTTON, arenaTeam.getName()),
                                new PABlockLocation(player.getLocation()))
                        .toLocation().toVector();
            }
            if (vFlag != null && vLoc.distance(vFlag) < 2) {
                debug(this.arena, player, "button found!");
                debug(this.arena, player, "vFlag: " + vFlag);

                boolean success = false;

                for (ArenaPlayer jailedPlayer : playerArenaTeam.getTeamMembers()) {
                    if (jailedPlayer.getStatus() == PlayerStatus.DEAD) {
                        SpawnManager.respawn(jailedPlayer, null);
                        List<ItemStack> keptItems = ofNullable(this.keptItemsMap.remove(jailedPlayer.getPlayer()))
                                .orElse(emptyList());

                        new InventoryRefillRunnable(this.arena, jailedPlayer.getPlayer(), keptItems);
                        if (this.arena.getConfig().getBoolean(CFG.GOAL_LIBERATION_JAILED_SCOREBOARD)) {
                            player.getScoreboard().getObjective("lives").getScore(player.getName()).setScore(0);
                        }
                        success = true;
                    }
                }

                if (success) {

                    this.arena.broadcast(ChatColor.YELLOW + Language
                            .parse(MSG.GOAL_LIBERATION_LIBERATED,
                                    playerArenaTeam.getColoredName()
                                            + ChatColor.YELLOW));

                    final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "trigger:" + player.getName());
                    Bukkit.getPluginManager().callEvent(gEvent);
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public boolean checkSetBlock(final Player player, final Block block) {

        if (StringUtils.isBlank(this.blockName) || !PAA_Region.activeSelections.containsKey(player.getName())) {
            return false;
        }

        if (block == null || !Tag.BUTTONS.isTagged(block.getType())) {
            debug(player, "Block {} is not a button", block);
            return false;
        }

        return PVPArena.hasAdminPerms(player) || PVPArena.hasCreatePerms(player, this.arena);
    }

    @Override
    public Boolean shouldRespawnPlayer(Player player, PADeathInfo deathInfo) {
        ArenaPlayer arenaPlayer = ArenaPlayer.fromPlayer(player);
        ArenaTeam arenaTeam = arenaPlayer.getArenaTeam();
        final int pos = this.getTeamLifeMap().get(arenaTeam);
        debug(this.arena, player, "lives before death: " + pos);
        if (pos <= 1) {
            this.getTeamLifeMap().put(arenaTeam, 1);

            final ArenaPlayer aPlayer = ArenaPlayer.fromPlayer(player);
            final ArenaTeam team = aPlayer.getArenaTeam();
            boolean someoneAlive = false;

            for (ArenaPlayer temp : team.getTeamMembers()) {
                if (temp.getStatus() == PlayerStatus.FIGHT) {
                    someoneAlive = true;
                    break;
                }
            }

            return someoneAlive;
        }
        return true;
    }

    @Override
    public void commitCommand(final CommandSender sender, final String[] args) {
        if (args[0].equals(BUTTON)) {
            if (args.length >= 2) {
                String teamName = args[1];
                if (this.arena.getTeam(teamName) == null) {
                    this.arena.msg(sender, MSG.ERROR_TEAM_NOT_FOUND, this.blockName);
                    return;
                }
                this.blockTeamName = teamName;
            } else {
                this.blockTeamName = null;
            }
            this.blockName = args[0];
            PAA_Region.activeSelections.put(sender.getName(), this.arena);

            this.arena.msg(sender, MSG.GOAL_LIBERATION_TOSET, this.blockName);
        }
    }

    @Override
    public void commitEnd(final boolean force) {
        if (this.endRunner != null) {
            return;
        }
        if (this.arena.realEndRunner != null) {
            debug(this.arena, "[LIBERATION] already ending");
            return;
        }

        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "");
        Bukkit.getPluginManager().callEvent(gEvent);
        for (ArenaTeam arenaTeam : this.arena.getTeams()) {
            for (ArenaPlayer arenaPlayer : arenaTeam.getTeamMembers()) {
                if (arenaPlayer.getStatus() != PlayerStatus.FIGHT) {
                    continue;
                }
                ArenaModuleManager.announce(
                        this.arena,
                        Language.parse(MSG.TEAM_HAS_WON,
                                arenaTeam.getColoredName()), "WINNER");

                this.arena.broadcast(Language.parse(MSG.TEAM_HAS_WON,
                        arenaTeam.getColoredName()));
                break;
            }

            if (ArenaModuleManager.commitEnd(this.arena, arenaTeam)) {
                return;
            }
        }

        this.endRunner = new EndRunnable(this.arena, this.arena.getConfig().getInt(
                CFG.TIME_ENDCOUNTDOWN));
    }

    @Override
    public void commitPlayerDeath(final Player player, final boolean doesRespawn, PADeathInfo deathInfo) {

        final ArenaPlayer arenaPlayer = ArenaPlayer.fromPlayer(player);
        ArenaTeam arenaTeam = arenaPlayer.getArenaTeam();

        if (!this.getTeamLifeMap().containsKey(arenaTeam)) {
            debug(this.arena, player, "cmd: not in life map!");
            return;
        }
        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "playerDeath:" + player.getName());
        Bukkit.getPluginManager().callEvent(gEvent);
        int lives = this.getTeamLifeMap().get(arenaTeam);
        debug(this.arena, player, "lives before death: " + lives);

        if (lives <= 1) {
            this.getTeamLifeMap().put(arenaTeam, 1);

            arenaPlayer.setStatus(PlayerStatus.DEAD);

            final ArenaTeam team = arenaPlayer.getArenaTeam();

            boolean someoneAlive = false;

            for (ArenaPlayer temp : team.getTeamMembers()) {
                if (temp.getStatus() == PlayerStatus.FIGHT) {
                    someoneAlive = true;
                    break;
                }
            }

            if (someoneAlive) {

                if (this.arena.getConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                    this.broadcastSimpleDeathMessage(player, deathInfo);
                }

                if (this.arena.getConfig().getBoolean(CFG.PLAYER_DROPSINVENTORY)) {
                    List<ItemStack> keptItems = InventoryManager.drop(player);
                    this.keptItemsMap.put(player, keptItems);
                }

                player.getInventory().clear();

                String teamName = arenaPlayer.getArenaTeam().getName();

                new RespawnRunnable(this.arena, arenaPlayer, teamName + JAIL).runTaskLater(PVPArena.getInstance(), 1L);

                arenaPlayer.revive(deathInfo);

                if (this.arena.getConfig().getBoolean(CFG.GOAL_LIBERATION_JAILED_SCOREBOARD)) {
                    arenaPlayer.getPlayer().getScoreboard().getObjective("lives").getScore(arenaPlayer.getName()).setScore(101);
                }
            } else {
                this.getTeamLifeMap().remove(arenaTeam);

                arenaPlayer.setMayDropInventory(true);
                arenaPlayer.setMayRespawn(true);
                arenaPlayer.setStatus(PlayerStatus.LOST);

                if (this.arena.getConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                    this.broadcastSimpleDeathMessage(player, deathInfo);
                }

                WorkflowManager.handleEnd(this.arena, false);
            }

        } else {
            lives--;
            this.getTeamLifeMap().put(arenaTeam, lives);

            if (this.arena.getConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                this.broadcastDeathMessage(MSG.FIGHT_KILLED_BY_REMAINING, player, deathInfo, lives);
            }

            arenaPlayer.setMayDropInventory(true);
            arenaPlayer.setMayRespawn(true);
        }
    }

    @Override
    public boolean commitSetBlock(final Player player, final Block block) {

        debug(this.arena, player, "trying to set a button");

        // command : /pa button1 red
        // location: red.button1:

        SpawnManager.setBlock(this.arena, new PABlockLocation(block.getLocation()), this.blockName, this.blockTeamName);
        this.arena.msg(player, MSG.GOAL_LIBERATION_SET, this.blockName);

        PAA_Region.activeSelections.remove(player.getName());
        this.blockName = null;

        return true;
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        sender.sendMessage("lives: "
                + this.arena.getConfig().getInt(CFG.GOAL_LLIVES_LIVES));
    }

    @Override
    public int getLives(ArenaPlayer arenaPlayer) {
        return this.getTeamLifeMap().getOrDefault(arenaPlayer.getArenaTeam(), 0);
    }

    @Override
    public boolean hasSpawn(final String spawnName, final String spawnTeamName) {
        boolean hasSpawn = super.hasSpawn(spawnName, spawnTeamName);
        if (hasSpawn) {
            return true;
        }

        for (String teamName : this.arena.getTeamNames()) {
            if (spawnName.equalsIgnoreCase(JAIL) && spawnTeamName.equalsIgnoreCase(teamName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void initiate(final Player player) {
        ArenaPlayer arenaPlayer = ArenaPlayer.fromPlayer(player);
        ArenaTeam arenaTeam = arenaPlayer.getArenaTeam();
        this.getTeamLifeMap().put(arenaTeam, this.arena.getConfig().getInt(CFG.GOAL_LLIVES_LIVES));
    }

    @Override
    public void parseLeave(final Player player) {
        if (player == null) {
            PVPArena.getInstance().getLogger().warning(this.getName() + ": player NULL");
            return;
        }
        ArenaPlayer arenaPlayer = ArenaPlayer.fromPlayer(player);
        ArenaTeam arenaTeam = arenaPlayer.getArenaTeam();
        this.getTeamLifeMap().remove(arenaTeam);
    }

    @Override
    public void parseStart() {
        for (ArenaTeam team : this.arena.getTeams()) {
            for (ArenaPlayer arenaPlayer : team.getTeamMembers()) {
                this.getTeamLifeMap().put(arenaPlayer.getArenaTeam(),
                        this.arena.getConfig().getInt(CFG.GOAL_LLIVES_LIVES));
            }
        }
        if (this.arena.getConfig().getBoolean(CFG.GOAL_LIBERATION_JAILED_SCOREBOARD)) {
            this.arena.getScoreboard().addCustomEntry(null, Language.parse(MSG.GOAL_LIBERATION_SCOREBOARD_HEADING), 102);
            this.arena.getScoreboard().addCustomEntry(null, Language.parse(MSG.GOAL_LIBERATION_SCOREBOARD_SEPARATOR), 100);
        }
    }

    @Override
    public void reset(final boolean force) {
        this.endRunner = null;
        this.getTeamLifeMap().clear();
        this.keptItemsMap.clear();
        if (this.arena.getConfig().getBoolean(CFG.GOAL_LIBERATION_JAILED_SCOREBOARD)) {
            this.arena.getScoreboard().removeCustomEntry(null, 102);
            this.arena.getScoreboard().removeCustomEntry(null, 100);
        }
    }

    @Override
    public void setDefaults(final YamlConfiguration config) {
        if (config.get("teams") == null) {
            debug(this.arena, "no teams defined, adding custom red and blue!");
            config.addDefault("teams.red", ChatColor.RED.name());
            config.addDefault("teams.blue", ChatColor.BLUE.name());
        }
    }

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (ArenaPlayer arenaPlayer : this.arena.getFighters()) {
            double score = this.getTeamLifeMap().getOrDefault(arenaPlayer.getArenaTeam(), 0);
            if (arenaPlayer.getArenaTeam() == null) {
                continue;
            }
            if (scores.containsKey(arenaPlayer.getArenaTeam().getName())) {
                scores.put(arenaPlayer.getArenaTeam().getName(),
                        scores.get(arenaPlayer.getName()) + score);
            } else {
                scores.put(arenaPlayer.getArenaTeam().getName(), score);
            }
        }

        return scores;
    }
}
