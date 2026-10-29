package dev.bladetetra.client.awakening;

import dev.bladetetra.easteregg.AwakeningPresentationTimeline;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.AwakeningPresentationPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** World-local, ephemeral state. Never retained across a level replacement. */
public final class AwakeningSession {
    public final AwakeningPresentationPacket data;
    public final Player owner;
    public final ItemStack appearance;
    public final FloatingBladeRenderer.Prepared model;
    public boolean visible;
    public final NativeBladePose bladePose = new NativeBladePose();
    private boolean failed;
    private final dev.bladetetra.easteregg.AwakeningSessionKey key;

    AwakeningSession(AwakeningPresentationPacket data, Player owner,
            FloatingBladeRenderer.Prepared model) {
        this.data = data; this.owner = owner; this.model = model;
        appearance = owner.getMainHandItem().copy();
        key = new dev.bladetetra.easteregg.AwakeningSessionKey(data.player(),data.bladeId(),
                data.dimension().toString(),data.slot(),data.startedAt());
    }
    public float age(ClientLevel level, float partial) {
        return (float) (level.getGameTime() - data.startedAt()) + partial;
    }
    public boolean valid(ClientLevel level) {
        return !failed && owner.level() == level && owner.isAlive() && !owner.isRemoved()
                && level.getPlayerByUUID(data.player()) == owner
                && matches(data, owner)
                && key.live(owner.getUUID(),owner.getMainHandItem().getTag().getString("id"),
                        level.dimension().location().toString(),owner.getInventory().selected,
                        owner.isAlive(),level.getGameTime());
    }
    public static boolean matches(AwakeningPresentationPacket p, Player owner) {
        var blade = owner.getMainHandItem();
        return owner.getInventory().selected == p.slot()
                && blade.getItem() instanceof ModularSlashBladeItem
                && !dev.bladetetra.easteregg.SoulLegacyState.isBroken(blade)
                && blade.getTag() != null && p.bladeId().equals(blade.getTag().getString("id"));
    }
    public void fail() { failed = true; visible = false; }
}
