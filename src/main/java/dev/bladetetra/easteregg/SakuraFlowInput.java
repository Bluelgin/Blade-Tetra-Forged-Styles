package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;

/** One real press belongs to one action. Queries cannot enqueue or consume it. */
final class SakuraFlowInput {
    private ResourceLocation combo;
    private long actionAt, expires;
    void queue(ResourceLocation combo, long actionAt, long now) {
        this.combo = combo;
        this.actionAt = actionAt;
        expires = now + SakuraFlowSession.GRACE_TICKS;
    }
    boolean pending(ResourceLocation current, long at, long now) {
        return combo != null && combo.equals(current) && actionAt == at && now <= expires && now >= actionAt;
    }
    void clear() { combo = null; }
}
