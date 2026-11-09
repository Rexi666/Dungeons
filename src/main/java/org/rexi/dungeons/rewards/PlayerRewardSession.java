package org.rexi.dungeons.rewards;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerRewardSession {

    private final UUID playerUuid;
    private final String dungeon;
    private final String difficulty;
    private final int score;
    private final long createdAt;
    private final long expiresAt;
    private final int defaultClaims;
    private final Map<String, GeneratedChest> chests;

    private int claimsUsed;
    private boolean extraClaimUsed;

    public PlayerRewardSession(
            UUID playerUuid,
            String dungeon,
            String difficulty,
            int score,
            long createdAt,
            long expiresAt,
            int defaultClaims,
            Map<String, GeneratedChest> chests
    ) {
        this.playerUuid = playerUuid;
        this.dungeon = dungeon;
        this.difficulty = difficulty;
        this.score = score;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.defaultClaims = Math.max(1, defaultClaims);
        this.chests = new LinkedHashMap<>(chests);
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getDungeon() {
        return dungeon;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public int getScore() {
        return score;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public int getDefaultClaims() {
        return defaultClaims;
    }

    public int getClaimsUsed() {
        return claimsUsed;
    }

    public boolean isExtraClaimUsed() {
        return extraClaimUsed;
    }

    public void setExtraClaimUsed(boolean extraClaimUsed) {
        this.extraClaimUsed = extraClaimUsed;
    }

    public void incrementClaims() {
        claimsUsed++;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public Map<String, GeneratedChest> getChests() {
        return Collections.unmodifiableMap(chests);
    }

    public GeneratedChest getChest(String id) {
        return chests.get(id);
    }
}