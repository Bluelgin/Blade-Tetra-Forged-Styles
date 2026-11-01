package dev.bladetetra.kyouka;

import dev.bladetetra.combat.InheritedCombatDamage;
import dev.bladetetra.easteregg.*;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Only native trick completion creates illusions. Each owner has three, each illusion four targets. */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class KyoukaAfterimages {
    static final Map<UUID, ArrayDeque<KyoukaAfterimageEntity>> ACTIVE = new HashMap<>();
    public record Departure(ServerPlayer player, ItemStack blade, Vec3 origin, float yaw, float pitch,
            net.minecraft.server.level.ServerLevel level) {}
    public static Departure capture(Entity entity) {
        if (!(entity instanceof ServerPlayer p) || !eligible(p)) return null;
        return new Departure(p, p.getMainHandItem(), p.position(), p.getYRot(), p.getXRot(), p.serverLevel());
    }
    private static boolean eligible(ServerPlayer p) {
        return !p.isRemoved() && p.isAlive() && !p.isSpectator()
                && KyoukaMirrorSessions.eligible(p.getMainHandItem())
                && SoulSkillState.enabled(p.getMainHandItem(), SoulLegacyState.Legacy.KYOUKA)
                && !p.hasEffect(WaterMirrorEffect.ARMOR.get());
    }
    public static void completed(Departure departure) {
        if (departure == null) return;
        var p = departure.player();
        if (!eligible(p) || p.serverLevel() != departure.level() || p.getMainHandItem() != departure.blade()
                || p.position().distanceToSqr(departure.origin()) < .25) return;
        var images = ACTIVE.get(p.getUUID());
        if (images == null) {
            if (ACTIVE.size() >= 64) return;
            images = new ArrayDeque<>(3);
        }
        while (images.size() >= 3) images.getFirst().discard();
        var echo = ModEntities.KYOUKA_AFTERIMAGE.get().create(p.serverLevel());
        if (echo == null) return;
        echo.configure(p, departure.blade(), departure.origin(), departure.yaw(), departure.pitch());
        if (!p.serverLevel().addFreshEntity(echo)) return;
        ACTIVE.put(p.getUUID(), images); images.addLast(echo);
        p.serverLevel().getEntitiesOfClass(Mob.class, echo.getBoundingBox().inflate(6),
                mob -> mob.getTarget() == p && hostile(echo, mob) && echo.hasLineOfSight(mob))
                .stream().sorted(Comparator.comparingDouble(echo::distanceToSqr)).limit(4).forEach(mob -> {
                    mob.setTarget(echo);
                    if (mob.getTarget() == echo) echo.diverted.add(mob);
                });
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, echo.getX(), echo.getY()+1, echo.getZ(), 8, .2,.6,.2,.015);
        SoulActivityService.triggered(p, departure.blade(), SoulLegacyState.Legacy.KYOUKA);
    }
    static boolean valid(KyoukaAfterimageEntity echo) {
        var p = echo.owner;
        return p != null && eligible(p) && p.level() == echo.level() && p.getMainHandItem() == echo.blade
                && p.getInventory().selected == echo.slot;
    }
    static boolean hostile(KyoukaAfterimageEntity echo, Mob mob) {
        return mob != echo && !(mob instanceof KyoukaAfterimageEntity) && mob.isAlive() && mob.isAttackable()
                && !(mob instanceof TamableAnimal) && !(mob instanceof Animal) && !(mob instanceof WaterAnimal)
                && !mob.isAlliedTo(echo.owner) && !echo.owner.isAlliedTo(mob)
                && (mob instanceof Enemy || mob.getTarget() == echo.owner || mob.getTarget() == echo);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void target(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getNewTarget() instanceof ServerPlayer owner)) return;
        var images = ACTIVE.get(owner.getUUID());
        if (images == null) return;
        images.stream().filter(e -> !e.isRemoved() && valid(e) && hostile(e,mob)
                && (e.diverted.contains(mob) || e.diverted.size() < 4)
                && e.distanceToSqr(mob) <= 36 && e.hasLineOfSight(mob))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(mob))).ifPresent(e -> {
                    event.setNewTarget(e);
                    if (!e.diverted.contains(mob)) e.diverted.add(mob);
                });
    }
    static void release(KyoukaAfterimageEntity echo) {
        var p = echo.owner;
        if (p == null) return;
        var images = ACTIVE.get(p.getUUID());
        if (images != null) { images.remove(echo); if (images.isEmpty()) ACTIVE.remove(p.getUUID()); }
        for (var mob : echo.diverted) {
            if (!mob.isRemoved() && mob.getTarget() == echo)
                mob.setTarget(p.isAlive() && !p.isRemoved() && !p.isSpectator() && p.level() == mob.level()
                        && !mob.isAlliedTo(p) ? p : null);
        }
        echo.diverted.clear();
    }
    static void shatter(KyoukaAfterimageEntity echo) {
        var p = echo.owner; var level = p.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, echo.getX(), echo.getY()+1, echo.getZ(), 24,.55,.8,.55,.12);
        level.sendParticles(ParticleTypes.WAX_OFF, echo.getX(), echo.getY()+1, echo.getZ(), 16,.45,.7,.45,.05);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()),
                echo.getX(), echo.getY()+1, echo.getZ(), 18,.35,.65,.35,.09);
        level.playSound(null, echo.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, .8F, 1.25F);
        level.getEntitiesOfClass(Mob.class, echo.getBoundingBox().inflate(2.5), mob -> hostile(echo,mob)
                && mob.distanceToSqr(echo) <= 6.25 && echo.hasLineOfSight(mob))
                .stream().sorted(Comparator.comparingDouble(echo::distanceToSqr)).limit(4).forEach(mob -> {
                    if (mob.skipAttackInteraction(p) || MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(p,mob))) return;
                    int immunity = mob.invulnerableTime;
                    try {
                        mob.invulnerableTime = 0;
                        SoulLegacyDamageGuard.apply(() -> InheritedCombatDamage.apply(() -> mob.hurt(
                                KyoukaAwakening.mirrorDamage(level,p), echo.damage)));
                    } finally { mob.invulnerableTime = immunity; }
                });
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var images : List.copyOf(ACTIVE.values())) for (var echo : List.copyOf(images))
            if (echo.isRemoved() || !valid(echo)) { if (echo.isRemoved()) release(echo); else echo.discard(); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
    private KyoukaAfterimages() {}
}
