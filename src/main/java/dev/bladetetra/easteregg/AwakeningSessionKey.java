package dev.bladetetra.easteregg;

import java.util.UUID;
/** Pure session binding, deliberately independent of inventory/NBT mutation. */
public record AwakeningSessionKey(UUID player, String blade, String dimension, int slot, long started) {
    public boolean live(UUID currentPlayer, String currentBlade, String currentDimension,
            int currentSlot, boolean alive, long now) {
        return alive && player.equals(currentPlayer) && blade.equals(currentBlade)
                && dimension.equals(currentDimension) && slot == currentSlot
                && now >= started && now - started < AwakeningPresentationTimeline.WORLD_END;
    }
}
