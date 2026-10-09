package dev.bladetetra.challenge;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Adapted from Contract-Blade BlackFoxNativeCombo (bundled MIT). Registered B1–B7 own timing/VFX. */
public final class MikageCorridorNativeCombo {
    private final MikageEntity owner;
    private final MikageGateCorridorExecution release;
    private final Set<Entity> slashes = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final Map<Entity, net.minecraft.world.phys.Vec3> OFFSETS = new WeakHashMap<>();
    private ResourceLocation stage;
    private boolean executing;
    private long lastTick = Long.MIN_VALUE;
    MikageCorridorNativeCombo(MikageEntity owner, MikageGateCorridorExecution release) {
        this.owner = owner; this.release = release;
    }
    void tick() {
        long now = owner.level().getGameTime();
        if (lastTick == now) return;
        lastTick = now;
        slashes.removeIf(Entity::isRemoved);
        if (stage == null) change(new ResourceLocation("slashblade", "combo_b1"));
        ComboState combo = Objects.requireNonNull(ComboStateRegistry.REGISTRY.get().getValue(stage));
        var next = combo.getNext(owner);
        if (!next.equals(stage) && (next.getPath().matches("combo_b[2-7]")
                || stage.getPath().equals("combo_b7") && next.getPath().equals("none"))) {
            change(next);
            if (finished()) return;
            combo = Objects.requireNonNull(ComboStateRegistry.REGISTRY.get().getValue(stage));
        } else if (elapsed(owner) * 50 > combo.getTimeoutMS()) {
            change(combo.getNextOfTimeout(owner));
            if (!stage.getPath().matches("combo_b[1-7]")) { change(new ResourceLocation("slashblade", "none")); return; }
            combo = Objects.requireNonNull(ComboStateRegistry.REGISTRY.get().getValue(stage));
        }
        executing = true;
        try { combo.tickAction(owner); }
        finally { executing = false; owner.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); }
    }
    private void change(ResourceLocation next) {
        stage = next;
        owner.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(
                () -> new IllegalStateException("Mikage blade state missing")).updateComboSeq(owner, next);
        owner.setItemSlot(EquipmentSlot.MAINHAND, owner.getMainHandItem().copy());
        owner.setNativeCombo(next.getPath().matches("combo_b[1-7]") ? next.getPath().charAt(7) - '0' : 0,
                owner.level().getGameTime());
    }
    boolean capture(Entity entity) {
        if (!(entity instanceof EntitySlashEffect slash)) return false;
        slash.setColor(0xFF1838);
        OFFSETS.put(entity, entity.position().subtract(owner.position()));
        slashes.add(entity); return true;
    }
    boolean finished() { return stage != null && stage.getPath().equals("none"); }
    void stop() {
        slashes.forEach(entity -> { OFFSETS.remove(entity); entity.discard(); }); slashes.clear();
        if (stage != null) change(new ResourceLocation("slashblade", "none"));
        stage = null; lastTick = Long.MIN_VALUE;
        owner.setNativeCombo(0, 0);
        owner.getPersistentData().remove("sb_yrot"); owner.getPersistentData().remove("sb_yrot_prev");
    }
    public static void follow(Entity entity) {
        if (!(entity instanceof EntitySlashEffect slash) || !(slash.getShooter() instanceof MikageEntity boss)) return;
        if (!boss.isRidingPhantomSword() || boss.getNativeComboStage() == 0) { OFFSETS.remove(entity); return; }
        if (!entity.level().isClientSide() && (boss.encounter().corridor() == null
                || !boss.encounter().corridor().combo.slashes.contains(entity))) return;
        var offset = OFFSETS.computeIfAbsent(entity, e -> e.position().subtract(boss.position()));
        var at = boss.position().add(offset); entity.setPos(at.x, at.y, at.z);
    }
    public static long elapsed(LivingEntity entity) {
        return entity.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getElapsedTime(entity)).orElse(0L);
    }
    public static boolean driving(LivingEntity entity) {
        return entity instanceof MikageEntity boss && boss.encounter().corridor() != null
                && boss.encounter().corridor().combo.executing;
    }
}
