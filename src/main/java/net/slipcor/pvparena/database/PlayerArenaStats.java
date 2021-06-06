package net.slipcor.pvparena.database;

import net.slipcor.pvparena.managers.StatisticsManager;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an object storing all player-related data, which can load and save it.
 */
@Entity(name = "pvparena_player_arena_stats")
public class PlayerArenaStats {

    enum Columns {
        UUID("uuid"),
        ARENA("arena"),
        LOSSES("losses"),
        WINS("wins"),
        KILLS("kills"),
        DEATHS("deaths"),
        DAMAGE("damage"),
        MAX_DAMAGE("maxDamage"),
        DAMAGE_TAKE("damageTake"),
        MAX_DAMAGE_TAKE("maxDamageTake");

        private final String name;

        Columns(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String uuid;

    @Column(nullable = false)
    private String arena;

    @Column(nullable = false)
    private Long losses;

    @Column(nullable = false)
    private Long wins;

    @Column(nullable = false)
    private Long kills;

    @Column(nullable = false)
    private Long deaths;

    @Column(nullable = false)
    private Long damage;

    @Column(nullable = false)
    private Long maxDamage;

    @Column(nullable = false)
    private Long damageTake;

    @Column(nullable = false)
    private Long maxDamageTake;

    public PlayerArenaStats() {
        this.setDamage(0L);
        this.setDamageTake(0L);
        this.setDeaths(0L);
        this.setWins(0L);
        this.setLosses(0L);
        this.setKills(0L);
        this.setMaxDamage(0L);
        this.setMaxDamageTake(0L);
    }

    public PlayerArenaStats(Long id, String uuid, Long losses, Long wins, Long kills, Long deaths, Long damage, Long maxDamage, Long damageTake, Long maxDamageTake) {
        this.id = id;
        this.uuid = uuid;
        this.losses = losses;
        this.wins = wins;
        this.kills = kills;
        this.deaths = deaths;
        this.damage = damage;
        this.maxDamage = maxDamage;
        this.damageTake = damageTake;
        this.maxDamageTake = maxDamageTake;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return this.uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getArena() {
        return this.arena;
    }

    public void setArena(String arena) {
        this.arena = arena;
    }

    public Long getLosses() {
        return this.losses;
    }

    public void setLosses(Long losses) {
        this.losses = losses;
    }

    public void addLosses(Long losses) {
        this.losses += losses;
    }

    public Long getWins() {
        return this.wins;
    }

    public void setWins(Long wins) {
        this.wins = wins;
    }

    public void addWins(Long wins) {
        this.wins += wins;
    }

    public Long getKills() {
        return this.kills;
    }

    public void setKills(Long kills) {
        this.kills = kills;
    }

    public void addKills(Long kills) {
        this.kills += kills;
    }

    public Long getDeaths() {
        return this.deaths;
    }

    public void setDeaths(Long deaths) {
        this.deaths = deaths;
    }

    public void addDeaths(Long deaths) {
        this.deaths += deaths;
    }

    public Long getDamage() {
        return this.damage;
    }

    public void setDamage(Long damage) {
        this.damage = damage;
    }

    public void addDamage(Long damage) {
        this.damage += damage;
    }

    public Long getMaxDamage() {
        return this.maxDamage;
    }

    public void setMaxDamage(Long maxDamage) {
        this.maxDamage = maxDamage;
    }

    public void addMaxDamage(Long maxDamage) {
        this.maxDamage += maxDamage;
    }

    public Long getDamageTake() {
        return this.damageTake;
    }

    public void setDamageTake(Long damageTake) {
        this.damageTake = damageTake;
    }

    public void addDamageTake(Long damageTake) {
        this.damageTake += damageTake;
    }

    public Long getMaxDamageTake() {
        return this.maxDamageTake;
    }

    public void setMaxDamageTake(Long maxDamageTake) {
        this.maxDamageTake = maxDamageTake;
    }

    public void addMaxDamageTake(Long maxDamageTake) {
        this.maxDamageTake += maxDamageTake;
    }

    public Map<StatisticsManager.Type, Long> getMap() {
        Map<StatisticsManager.Type, Long> map = new HashMap<>();
        map.put(StatisticsManager.Type.DEATHS, this.deaths);
        map.put(StatisticsManager.Type.KILLS, this.kills);
        map.put(StatisticsManager.Type.WINS, this.wins);
        map.put(StatisticsManager.Type.LOSSES, this.losses);
        return map;
    }
}
