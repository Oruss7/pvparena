package net.slipcor.pvparena.commands;

import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.core.Help;
import net.slipcor.pvparena.core.Help.HELP;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.managers.StatisticsManager;
import net.slipcor.pvparena.managers.StatisticsManager.Type;
import net.slipcor.pvparena.updater.ModulesUpdater;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * <pre>PVP Arena STATS Command class</pre>
 * <p/>
 * A command to display the player statistics
 *
 * @author slipcor
 * @version v0.10.0
 */

public class PAI_Stats extends AbstractArenaCommand {

    public PAI_Stats() {
        super(new String[]{"pvparena.user", "pvparena.cmds.stats"});
    }

    @Override
    public void commit(final Arena arena, final CommandSender sender, final String[] args) {
        if (!this.hasPerms(sender, arena)) {
            return;
        }

        if (args.length >= 1) {
            switch (args[0]) {
                case "top":
                    final Type statType = Type.getByString(args[0]);

                    if (statType == null) {
                        Arena.pmsg(sender, Language.parse(arena, MSG.STATS_TYPENOTFOUND, StringParser.joinArray(Type.values(), ", ").replace("NULL, ", "")));
                        return;
                    }

                    int max = 10;

                    if (args.length > 1) {
                        try {
                            max = Integer.parseInt(args[1]);
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    getTopPlayersForStat(sender, statType, arena, max);
                    break;

                default:
                    Player player = Bukkit.getPlayer(args[0]);
                    if (player == null) {
                        Arena.pmsg(sender, Language.parse(arena, MSG.ERROR_PLAYER_NOTFOUND, args[0]));
                        return;
                    }
                    String arenaName = null;
                    if (args.length > 1) {
                        arenaName = args[1];
                    }
                    ArenaPlayer arenaPlayer = ArenaPlayer.parsePlayer(player.getName());
                    Arena.pmsg(sender, String.format("Stats for %s%s", player.getDisplayName(), arenaName != null ? " and arena " + arenaName : ""));
                    Arena.pmsg(sender, "-----------------------------------");
                    StatisticsManager.getPlayerArenaStats(player, arenaName).getMap()
                            .forEach((key, value) -> Arena.pmsg(sender, key + ": " + value));
            }
        }

    }

    private void getTopPlayersForStat(CommandSender sender, Type statType, Arena arena, int max){

        Map<String, Integer> playersStats = StatisticsManager.getStats(arena, statType);

        final String s2 = Language.parse(arena, MSG.getByName("STATTYPE_" + statType.name()));
        final String s1 = Language.parse(arena, MSG.STATS_HEAD, String.valueOf(max), s2);

        Arena.pmsg(sender, s1);

        playersStats.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(max)
                .forEach(stat -> Arena.pmsg(sender, stat.getKey() + " : " + stat.getValue()));
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public void displayHelp(final CommandSender sender) {
        Arena.pmsg(sender, Help.parse(HELP.STATS));
    }

    @Override
    public List<String> getMain() {
        return Collections.singletonList("stats");
    }

    @Override
    public List<String> getShort() {
        return Collections.singletonList("-s");
    }

    @Override
    public CommandTree<String> getSubs(final Arena arena) {
        final CommandTree<String> result = new CommandTree<>(null);
        for (final Type val : Type.values()) {
            result.define(new String[]{val.name()});
        }
        return result;
    }
}
