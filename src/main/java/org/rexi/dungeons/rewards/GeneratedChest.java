package org.rexi.dungeons.rewards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GeneratedChest {

    private final String id;
    private final String displayName;
    private final long price;
    private final int requiredScore;
    private final boolean unlocked;
    private final List<GeneratedReward> rewards;

    private boolean claimed;

    public GeneratedChest(
            String id,
            String displayName,
            long price,
            int requiredScore,
            boolean unlocked,
            List<GeneratedReward> rewards
    ) {
        this.id = id;
        this.displayName = displayName;
        this.price = Math.max(0L, price);
        this.requiredScore = Math.max(0, requiredScore);
        this.unlocked = unlocked;
        this.rewards = new ArrayList<>(rewards);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getPrice() {
        return price;
    }

    public int getRequiredScore() {
        return requiredScore;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public List<GeneratedReward> getRewards() {
        return Collections.unmodifiableList(rewards);
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }
}