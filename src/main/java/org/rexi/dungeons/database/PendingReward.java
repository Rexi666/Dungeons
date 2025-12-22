package org.rexi.dungeons.database;

import java.util.List;

public class PendingReward {

    private final int id;
    private final int requiredSpace;
    private final List<String> commands;

    public PendingReward(int id, int requiredSpace, List<String> commands) {
        this.id = id;
        this.requiredSpace = requiredSpace;
        this.commands = commands;
    }

    public int getId() {
        return id;
    }

    public int getRequiredSpace() {
        return requiredSpace;
    }

    public List<String> getCommands() {
        return commands;
    }
}

