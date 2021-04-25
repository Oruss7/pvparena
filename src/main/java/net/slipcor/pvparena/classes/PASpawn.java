package net.slipcor.pvparena.classes;

import org.bukkit.util.Vector;

import java.util.Objects;

public class PASpawn {

    public static final String SPAWN = "spawn";
    public static final String SPECTATOR = "spectator";
    public static final String OLD = "old";
    public static final String EXIT = "exit";

    private final PALocation location;
    private final String name;
    private final String teamName;
    private final String className;
    private Vector offset;

    public PASpawn(final PALocation loc, final String name, final String teamName, final String className) {
        this.location = loc;
        this.name = name;
        this.teamName = teamName;
        this.className = className;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof PASpawn) {
            final PASpawn other = (PASpawn) o;
            return this.name.equals(other.name)
                    && this.location.equals(other.location)
                    && Objects.equals(this.teamName, other.teamName);
        }
        return false;
    }

    public PALocation getPALocation() {
        return this.location;
    }

    public String getName() {
        return this.name;
    }

    public String getTeamName() {
        return this.teamName;
    }

    public String getClassName() {
        return this.className;
    }

    public Vector getOffset() {
        return this.offset;
    }

    public void setOffset(Vector offset) {
        this.offset = offset;
    }

    @Override
    public String toString() {
        return "PASpawn{" +
                "location=" + this.location +
                ", spawnName='" + this.name + '\'' +
                ", teamName='" + this.teamName + '\'' +
                ", className='" + this.className + '\'' +
                ", offset=" + this.offset +
                '}';
    }
}
