package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.commands.CommandTree;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.ColorUtils;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.TeamManager;
import net.slipcor.pvparena.runnables.EndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.slipcor.pvparena.config.Debugger.debug;


public abstract class AbstractFlagGoal extends ArenaGoal implements Listener {
    protected static final int PRIORITY = 7;
    protected static final String TOUCHDOWN = "touchdown";
    protected Map<String, String> flagMap;
    protected Map<String, ItemStack> headGearMap;
    protected String flagName = "";

    public AbstractFlagGoal(String sName) {
        super(sName);
    }

    protected abstract CFG getFlagTypeCfg();
    protected abstract CFG getFlagEffectCfg();
    protected abstract boolean hasWoolHead();

    protected Material getFlagType() {
        return this.arena.getArenaConfig().getMaterial(this.getFlagTypeCfg());
    }

    @Override
    public boolean allowsJoinInBattle() {
        return this.arena.getArenaConfig().getBoolean(CFG.PERMS_JOININBATTLE);
    }

    @Override
    public boolean checkCommand(final String string) {
        if ("flagtype".equalsIgnoreCase(string) || "flageffect".equalsIgnoreCase(string) || TOUCHDOWN.equalsIgnoreCase(string)) {
            return true;
        }

        return this.arena.getTeams().stream().anyMatch(team -> string.contains(team.getName() + "flag"));
    }

    @Override
    public List<String> getMain() {
        final List<String> result = Stream.of("flagtype", "flageffect", TOUCHDOWN).collect(Collectors.toList());
        if (this.arena != null) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                final String sTeam = team.getName();
                result.add(sTeam + "flag");
            }
        }
        return result;
    }

    @Override
    public CommandTree<String> getSubs(final Arena arena) {
        final CommandTree<String> result = new CommandTree<>(null);
        result.define(new String[]{"{Material}"});
        return result;
    }

    @Override
    public boolean checkEnd() {
        final int count = TeamManager.countActiveTeams(this.arena);

        if (count == 1) {
            return true; // yep. only one team left. go!
        } else if (count == 0) {
            debug(this.arena, "No teams playing!");
        }

        return false;
    }

    @Override
    public String checkForMissingSpawns(final Set<String> list) {
        final String team = this.checkForMissingTeamSpawn(list);
        if (team != null) {
            return team;
        }

        return this.checkForMissingTeamCustom(list, "flag");
    }



    protected void applyEffects(final Player player) {
        final String value = this.arena.getArenaConfig().getDefinedString(this.getFlagEffectCfg());

        if (value == null) {
            return;
        }

        final String[] split = value.split("x");

        int amp = 1;

        if (split.length > 1) {
            try {
                amp = Integer.parseInt(split[1]);
            } catch (final Exception ignored) {

            }
        }

        PotionEffectType pet = null;
        for (final PotionEffectType x : PotionEffectType.values()) {
            if (x == null) {
                continue;
            }
            if (x.getName().equalsIgnoreCase(split[0])) {
                pet = x;
                break;
            }
        }

        if (pet == null) {
            PVPArena.getInstance().getLogger().warning(
                    "Invalid Potion Effect Definition: " + value);
            return;
        }

        player.addPotionEffect(new PotionEffect(pet, 2147000, amp));
    }

    @Override
    public boolean checkSetBlock(final Player player, final Block block) {

        if (!PAA_Region.activeSelections.containsKey(player.getName())) {
            return false;
        }

        Material flagType = this.getFlagType();
        if (block == null || !ColorUtils.isSubType(block.getType(), flagType)) {
            return false;
        }

        return PVPArena.hasAdminPerms(player) || PVPArena.hasCreatePerms(player, this.arena);
    }

    @Override
    public void commitCommand(final CommandSender sender, final String[] args) {
        if ("flagtype".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                this.arena.msg(
                        sender,
                        Language.parse(this.arena, MSG.ERROR_INVALID_ARGUMENT_COUNT,
                                String.valueOf(args.length), "2"));
                return;
            }

            final Material mat = Material.getMaterial(args[1].toUpperCase());

            if (mat == null) {
                this.arena.msg(sender,
                        Language.parse(this.arena, MSG.ERROR_MAT_NOT_FOUND, args[1]));
                return;
            }

            this.arena.getArenaConfig().set(this.getFlagTypeCfg(), mat.name());

            this.arena.getArenaConfig().save();
            this.arena.msg(sender, Language.parse(this.arena, MSG.GOAL_FLAGS_TYPESET, this.getFlagTypeCfg().toString()));

        } else if ("flageffect".equalsIgnoreCase(args[0])) {

            // /pa [arena] flageffect SLOW 2
            if (args.length < 2) {
                this.arena.msg(
                        sender,
                        Language.parse(this.arena, MSG.ERROR_INVALID_ARGUMENT_COUNT,
                                String.valueOf(args.length), "2"));
                return;
            }

            if ("none".equalsIgnoreCase(args[1])) {
                this.arena.getArenaConfig().set(this.getFlagEffectCfg(), args[1]);

                this.arena.getArenaConfig().save();
                this.arena.msg(
                        sender,
                        Language.parse(this.arena, MSG.SET_DONE,
                                this.getFlagEffectCfg().getNode(), args[1]));
                return;
            }

            PotionEffectType pet = null;

            for (final PotionEffectType x : PotionEffectType.values()) {
                if (x == null) {
                    continue;
                }
                if (x.getName().equalsIgnoreCase(args[1])) {
                    pet = x;
                    break;
                }
            }

            if (pet == null) {
                this.arena.msg(sender, Language.parse(this.arena,
                        MSG.ERROR_POTIONEFFECTTYPE_NOTFOUND, args[1]));
                return;
            }

            int amp = 1;

            if (args.length == 5) {
                try {
                    amp = Integer.parseInt(args[2]);
                } catch (final Exception e) {
                    this.arena.msg(sender,
                            Language.parse(this.arena, MSG.ERROR_NOT_NUMERIC, args[2]));
                    return;
                }
            }
            final String value = args[1] + 'x' + amp;
            this.arena.getArenaConfig().set(this.getFlagEffectCfg(), value);

            this.arena.getArenaConfig().save();
            this.arena.msg(
                    sender,
                    Language.parse(this.arena, MSG.SET_DONE,
                            this.getFlagEffectCfg().getNode(), value));

        } else if (args[0].contains("flag")) {
            for (final ArenaTeam team : this.arena.getTeams()) {
                final String sTeam = team.getName();
                if (args[0].contains(sTeam + "flag")) {
                    this.flagName = args[0];
                    PAA_Region.activeSelections.put(sender.getName(), this.arena);

                    this.arena.msg(sender,
                            Language.parse(this.arena, MSG.GOAL_FLAGS_TOSET, this.flagName));
                }
            }
        } else if (TOUCHDOWN.equalsIgnoreCase(args[0])) {
            this.flagName = args[0] + "flag";
            PAA_Region.activeSelections.put(sender.getName(), this.arena);

            this.arena.msg(sender, Language.parse(this.arena, MSG.GOAL_FLAGS_TOSET, this.flagName));
        }
    }

    @Override
    public void commitEnd(final boolean force) {
        if (this.arena.realEndRunner != null) {
            debug(this.arena, "[FLAGS] already ending");
            return;
        }
        debug(this.arena, "[FLAGS]");

        final PAGoalEvent gEvent = new PAGoalEvent(this.arena, this, "");
        Bukkit.getPluginManager().callEvent(gEvent);
        ArenaTeam aTeam = null;

        for (final ArenaTeam team : this.arena.getTeams()) {
            for (final ArenaPlayer ap : team.getTeamMembers()) {
                if (ap.getStatus() == Status.FIGHT) {
                    aTeam = team;
                    break;
                }
            }
        }

        if (aTeam != null && !force) {
            ArenaModuleManager.announce(
                    this.arena,
                    Language.parse(this.arena, MSG.TEAM_HAS_WON, aTeam.getColor()
                            + aTeam.getName() + ChatColor.YELLOW), "END");

            ArenaModuleManager.announce(
                    this.arena,
                    Language.parse(this.arena, MSG.TEAM_HAS_WON, aTeam.getColor()
                            + aTeam.getName() + ChatColor.YELLOW), "WINNER");
            this.arena.broadcast(Language.parse(this.arena, MSG.TEAM_HAS_WON, aTeam.getColor()
                    + aTeam.getName() + ChatColor.YELLOW));
        }

        if (ArenaModuleManager.commitEnd(this.arena, aTeam)) {
            return;
        }
        new EndRunnable(this.arena, this.arena.getArenaConfig().getInt(CFG.TIME_ENDCOUNTDOWN));
    }

    @Override
    public boolean commitSetFlag(final Player player, final Block block) {

        debug(this.arena, player, "trying to set a flag");

        // command : /pa redflag1
        // location: red1flag:

        if(this.flagName == null || this.flagName.isEmpty()) {
            this.arena.msg(player, Language.parse(this.arena, MSG.ERROR_ERROR, "Flag you are trying to set has no name."));
        } else {
            SpawnManager.setBlock(this.arena, new PABlockLocation(block.getLocation()), this.flagName);
            this.arena.msg(player, Language.parse(this.arena, MSG.GOAL_FLAGS_SET, this.flagName));
        }

        PAA_Region.activeSelections.remove(player.getName());
        this.flagName = "";

        return true;
    }

    @Override
    public void commitStart() {
        // empty to kill the error ;)
    }

    @Override
    public void configParse(final YamlConfiguration config) {
        Bukkit.getPluginManager().registerEvents(this, PVPArena.getInstance());
    }

    protected Map<String, String> getFlagMap() {
        if (this.flagMap == null) {
            this.flagMap = new HashMap<>();
        }
        return this.flagMap;
    }

    protected Material getFlagOverrideTeamMaterial(final Arena arena, final String team) {
        if (arena.getArenaConfig().getUnsafe("flagColors." + team) == null) {
            if (TOUCHDOWN.equals(team)) {
                return ColorUtils.getWoolMaterialFromChatColor(ChatColor.BLACK);
            }
            return ColorUtils.getWoolMaterialFromChatColor(arena.getTeam(team).getColor());
        }
        return ColorUtils.getWoolMaterialFromDyeColor(
                (String) arena.getArenaConfig().getUnsafe("flagColors." + team));
    }

    @Override
    public int getLives(ArenaPlayer aPlayer) {
        return this.getLifeMap().getOrDefault(aPlayer.getArenaTeam().getName(), 0);
    }

    protected Map<String, ItemStack> getHeadGearMap() {
        if (this.headGearMap == null) {
            this.headGearMap = new HashMap<>();
        }
        return this.headGearMap;
    }

    /**
     * get the team name of the flag a player holds
     *
     * @param player the player to check
     * @return a team name
     */
    protected String getHeldFlagTeam(final Player player) {
        if (this.getFlagMap().isEmpty()) {
            return null;
        }

        debug(player, "getting held FLAG of player {}", player);
        for (final String sTeam : this.getFlagMap().keySet()) {
            debug(player, "team {} is in {}s hands", sTeam, this.getFlagMap().get(sTeam));
            if (player.getName().equals(this.getFlagMap().get(sTeam))) {
                return sTeam;
            }
        }
        return null;
    }

    @Override
    public boolean hasSpawn(final String string) {
        for (final String teamName : this.arena.getTeamNames()) {
            if (string.toLowerCase().equals(teamName.toLowerCase() + "flag")) {
                return true;
            }
            if (string.toLowerCase().startsWith(teamName.toLowerCase() + "spawn")) {
                return true;
            }

            if (this.arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
                for (final ArenaClass aClass : this.arena.getClasses()) {
                    if (string.toLowerCase().startsWith(teamName.toLowerCase() +
                            aClass.getName().toLowerCase() + "spawn")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected void removeEffects(final Player player) {
        final String value = this.arena.getArenaConfig().getDefinedString(this.getFlagEffectCfg());

        if (value == null) {
            return;
        }

        PotionEffectType pet = null;

        final String[] split = value.split("x");

        for (final PotionEffectType x : PotionEffectType.values()) {
            if (x == null) {
                continue;
            }
            if (x.getName().equalsIgnoreCase(split[0])) {
                pet = x;
                break;
            }
        }

        if (pet == null) {
            PVPArena.getInstance().getLogger().warning("Invalid Potion Effect Definition: " + value);
            return;
        }

        player.removePotionEffect(pet);
        player.addPotionEffect(new PotionEffect(pet, 0, 1));
    }

    @Override
    public void setDefaults(final YamlConfiguration config) {
        if (this.arena.isFreeForAll()) {
            return;
        }

        if (config.get("teams.free") != null) {
            config.set("teams", null);
        }
        if (config.get("teams") == null) {
            debug(this.arena, "no teams defined, adding custom red and blue!");
            config.addDefault("teams.red", ChatColor.RED.name());
            config.addDefault("teams.blue", ChatColor.BLUE.name());
        }
        if (this.hasWoolHead() || config.get("flagColors") == null) {
            debug(this.arena, "no flag colors defined, adding red and blue!");
            config.addDefault("flagColors.red", DyeColor.RED.name());
            config.addDefault("flagColors.blue", DyeColor.BLUE.name());
        }
    }

    protected void commit(final Arena arena, final String sTeam, final boolean win) {
        if (arena.realEndRunner == null) {
            debug(arena, "[CTF] committing end: " + sTeam);
            debug(arena, "win: " + win);

            String winteam = sTeam;

            for (final ArenaTeam team : arena.getTeams()) {
                if (team.getName().equals(sTeam) == win) {
                    continue;
                }
                for (final ArenaPlayer ap : team.getTeamMembers()) {
                    ap.setStatus(Status.LOST);
                }
            }
            for (final ArenaTeam team : arena.getTeams()) {
                for (final ArenaPlayer ap : team.getTeamMembers()) {
                    if (ap.getStatus() != Status.FIGHT) {
                        continue;
                    }
                    winteam = team.getName();
                    break;
                }
            }

            if (arena.getTeam(winteam) != null) {

                ArenaModuleManager
                        .announce(
                                arena,
                                Language.parse(arena, MSG.TEAM_HAS_WON,
                                        arena.getTeam(winteam).getColor()
                                                + winteam + ChatColor.YELLOW),
                                "WINNER");
                arena.broadcast(Language.parse(arena, MSG.TEAM_HAS_WON,
                        arena.getTeam(winteam).getColor() + winteam
                                + ChatColor.YELLOW));
            }

            this.getLifeMap().clear();
            new EndRunnable(arena, arena.getArenaConfig().getInt(CFG.TIME_ENDCOUNTDOWN));
        } else {
            debug(arena, "[CTF] already ending");
        }

    }

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (final ArenaTeam team : this.arena.getTeams()) {
            double score = this.getLifeMap().getOrDefault(team.getName(), 0);
            if (scores.containsKey(team.getName())) {
                scores.put(team.getName(), scores.get(team.getName()) + score);
            } else {
                scores.put(team.getName(), score);
            }
        }

        return scores;
    }

    @Override
    public void unload(final Player player) {
        this.disconnect(ArenaPlayer.parsePlayer(player.getName()));
        if (this.allowsJoinInBattle()) {
            this.arena.hasNotPlayed(ArenaPlayer.parsePlayer(player.getName()));
        }
    }

    protected boolean isIrrelevantInventoryClickEvent(InventoryClickEvent event) {
        final Player player = (Player) event.getWhoClicked();
        final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();

        if (arena == null || !arena.getName().equals(this.arena.getName())) {
            return true;
        }

        if (event.isCancelled() || this.getHeldFlagTeam(player) == null) {
            return true;
        }

        if (event.getInventory().getType() == InventoryType.CRAFTING && event.getRawSlot() != 5) {
             return true;
        }

        return event.getCurrentItem() == null || !InventoryType.PLAYER.equals(event.getInventory().getType());
    }

    protected void reduceLivesCheckEndAndCommit(final Arena arena, final String team) {
        debug(arena, "reducing lives of team " + team);
        if (this.getLifeMap().get(team) == null) {
            if (team.contains(":")) {
                final String realTeam = team.split(":")[1];
                final int iLives = this.getLifeMap().get(realTeam) - 1;
                if (iLives > 0) {
                    this.getLifeMap().put(realTeam, iLives);
                } else {
                    this.getLifeMap().remove(realTeam);
                    this.commit(arena, realTeam, true);
                }
            }
        } else {
            if (this.getLifeMap().get(team) != null) {
                final int iLives = this.getLifeMap().get(team) - 1;
                if (iLives > 0) {
                    this.getLifeMap().put(team, iLives);
                } else {
                    this.getLifeMap().remove(team);
                    this.commit(arena, team, false);
                }
            }
        }
    }

    protected PABlockLocation getTeamFlagLoc(String teamName) {
        return SpawnManager.getBlockByExactName(this.arena, teamName + "flag");
    }
}
