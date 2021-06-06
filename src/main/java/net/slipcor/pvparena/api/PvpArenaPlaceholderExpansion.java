package net.slipcor.pvparena.api;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.managers.ArenaManager;
import net.slipcor.pvparena.managers.StatisticsManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PvpArenaPlaceholderExpansion extends PlaceholderExpansion {

    private static final String STATS = "stats";

    private PVPArena plugin;

    /**
     * Since we register the expansion inside our own plugin, we
     * can simply use this method here to get an instance of our
     * plugin.
     *
     * @param plugin The instance of our plugin.
     */
    public PvpArenaPlaceholderExpansion(PVPArena plugin) {
        this.plugin = plugin;
    }

    /**
     * Because this is an internal class,
     * you must override this method to let PlaceholderAPI know to not unregister your expansion class when
     * PlaceholderAPI is reloaded
     *
     * @return true to persist through reloads
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * This method should always return true unless we
     * have a dependency we need to make sure is on the server
     * for our placeholders to work!
     *
     * @return always true since we do not have any dependencies.
     */
    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * The name of the person who created this expansion should go here.
     *
     * @return The name of the author as a String.
     */
    @Override
    public @NotNull String getAuthor() {
        return "Eredrim";
    }

    /**
     * The placeholder identifier should go here.
     * <br>This is what tells PlaceholderAPI to call our onRequest
     * method to obtain a value if a placeholder starts with our
     * identifier.
     * <br>The identifier has to be lowercase and can't contain _ or %
     *
     * @return The identifier in {@code %<identifier>_<value>%} as String.
     */
    @Override
    public @NotNull String getIdentifier() {
        return "pvparena";
    }

    /**
     * This is the version of this expansion.
     * <br>You don't have to use numbers, since it is set as a String.
     *
     * @return The version as a String.
     */
    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    /**
     * This is the method called when a placeholder with our identifier
     * is found and needs a value.
     * <br>We specify the value identifier in this method.
     * <br>Since version 2.9.1 can you use OfflinePlayers in your requests.
     *
     * @param player     A {@link org.bukkit.OfflinePlayer OfflinePlayer}.
     * @param identifier A String containing the identifier/value.
     * @return Possibly-null String of the requested identifier.
     */
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {

        /*
         * We first replace any appearance of placeholders with the right value.
         * We need to use setBracketPlaceholders, to check for {placeholders} and we also need to use [prc]
         * as a replacement of the % symbol, because of how PlaceholderAPI handles placeholders.
         */
        identifier = PlaceholderAPI.setBracketPlaceholders(player, identifier);
        identifier = identifier.replace("[prc]", "%");

        String[] args = identifier.split("_");

        if (args[0].equals(STATS)) {
            return getStats(Arrays.copyOfRange(args, 1, args.length));
        }

        // We return null if an invalid placeholder (f.e. %pvparena_placeholder3%)
        // was provided
        return "pvparena identifier unknown: " + identifier;
    }

    /**
     * Return stats.
     * <p>
     * Examples:
     * <p>
     * Get the top 1 winner with format "<player> :<value>": %pvparena_stats_wins_1%
     * Get the top 1 winner name "<player>": %pvparena_stats_wins_name_1%
     * Get the top 1 winner name "<value>": %pvparena_stats_wins_value_1%
     * <p>
     * Get the top 1 winner for arena "myarena" with format "<player> :<value>": %pvparena_stats_myarena_wins_1%
     * Get the top 1 winner name "<player>" for arena "myarena": %pvparena_stats_myarena_wins_name_1%
     */
    private String getStats(String[] args) {

        int nextArg = 0;
        Arena arena = null;
        StatisticsManager.Type statType = StatisticsManager.Type.getByString(args[nextArg]);

        // if not a stats type, maybe it's an arena name
        if (statType == null) {
            arena = ArenaManager.getArenaByExactName(args[nextArg]);
            if (arena == null) {
                return "Error: " + args[nextArg] + "is not a valid stats type or an arena.";
            }
            nextArg++;
            statType = StatisticsManager.Type.getByString(args[nextArg]);
            if (statType == null) {
                return "Error: " + args[nextArg] + "is not a valid stats type.";
            }
        }
        nextArg++;

        int position;
        final String format;
        if("name".equals(args[nextArg]) || "value".equals(args[nextArg])){
            format = args[nextArg];
            nextArg++;
        } else {
            format = "all";
        }

        try {
            position = Integer.parseInt(args[nextArg]);
        } catch (NumberFormatException ignored) {
            return "Error: " + args[nextArg] + " must be an integer.";
        }

        if (position < 1) {
            return "Error: " + position + " must be greater than 0.";
        }

        Map<String, Integer> playersStats = StatisticsManager.getStats(arena, statType);

        List<String> playersStatsList = playersStats.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(100)
                .map(stringIntegerEntry -> {
                    if (format.equals("name")) {
                        return stringIntegerEntry.getKey();
                    } else if (format.equals("value")) {
                        return stringIntegerEntry.getValue().toString();
                    } else {
                        return stringIntegerEntry.getKey() + ": " + stringIntegerEntry.getValue();
                    }
                })
                .collect(Collectors.toList());

        return playersStatsList.size() >= position ? playersStatsList.get(position - 1) : "-";
    }

}
