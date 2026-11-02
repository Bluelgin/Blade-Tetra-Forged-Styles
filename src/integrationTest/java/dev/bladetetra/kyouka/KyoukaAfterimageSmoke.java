package dev.bladetetra.kyouka;

import com.mojang.logging.LogUtils;
import dev.bladetetra.easteregg.KyoukaAwakening;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Actual transformed movement and real Mob targets, rather than a parallel model of the feature. */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class KyoukaAfterimageSmoke {
    private static boolean protect;
    private static Zombie protectedTarget;
    @SubscribeEvent public static void attack(LivingAttackEvent event) {
        if (protect && event.getEntity() == protectedTarget) event.setCanceled(true);
    }
    static void verify(ServerPlayer player) {
        var original = player.getMainHandItem(); var level = player.serverLevel();
        var origin = player.position();
        double baseDamage=player.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
        var mobs = new ArrayList<Zombie>();
        var floor = new HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
        try {
            player.removeEffect(WaterMirrorEffect.ARMOR.get());
            var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            var tag = blade.getOrCreateTag();
            tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT, ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
            tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material", "awakened_soul_inscription/proudsoul_sphere");
            check(KyoukaAwakening.unlockForDebug(blade), "unlock afterimage blade");
            blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s -> s.setBroken(false));
            player.setItemSlot(EquipmentSlot.MAINHAND, blade); player.setPos(2,200,2);
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(100);
            var attacker = mob(player, mobs, 3,200,2); attacker.setTarget(player);
            var capture = KyoukaAfterimages.capture(player);
            KyoukaAfterimages.completed(capture);
            check(!KyoukaAfterimages.ACTIVE.containsKey(player.getUUID()), "no displacement means no image");
            SlayerStyleArts.getInstance().executeTrickUp(player);
            var images = KyoukaAfterimages.ACTIVE.get(player.getUUID());
            check(images != null && images.size()==1, "successful native V trick creates image");
            var first = images.getFirst();
            check(first.position().distanceToSqr(new Vec3(2,200,2)) < .001, "image remains at native departure");
            check(attacker.getTarget()==first, "hostile attacker actually targets the image");
            check(!first.hurt(player.damageSources().fall(), 1), "environment cannot detonate illusion");
            check(!first.hurt(player.damageSources().playerAttack(player), 1), "player cannot detonate illusion");
            float health = attacker.getHealth();
            check(attacker.doHurtTarget(first), "real hostile melee attack shatters illusion");
            check(first.isRemoved() && attacker.getTarget()==player, "shatter restores aggro");
            check(attacker.getHealth()<health && health-attacker.getHealth()<=6, "shatter has bounded real damage");
            float after = attacker.getHealth();
            check(!first.hurt(player.damageSources().mobAttack(attacker), 1) && attacker.getHealth()==after, "cannot detonate twice");
            for(int i=0;i<3;i++) SlayerStyleArts.getInstance().executeTrickUp(player);
            images = KyoukaAfterimages.ACTIVE.get(player.getUUID());
            check(images.size()==3, "three simultaneous images");
            var oldest = images.getFirst();
            SlayerStyleArts.getInstance().executeTrickUp(player);
            check(oldest.isRemoved() && images.size()==3 && attacker.getHealth()==after, "fourth harmlessly replaces oldest");
            var expired = images.getFirst();
            attacker.setTarget(expired);
            if (!expired.diverted.contains(attacker)) expired.diverted.add(attacker);
            expired.tickCount=60; expired.tick();
            check(expired.isRemoved() && attacker.getTarget()!=expired && attacker.getHealth()==after, "expiry restores target without damage");
            var remaining = images.getFirst();
            var other = mob(player,mobs,4,200,2);
            attacker.setTarget(remaining); remaining.diverted.add(attacker); attacker.setTarget(other);
            remaining.discard();
            check(attacker.getTarget()==other, "cleanup preserves a subsequently changed AI target");
            var last = images.getFirst();
            protectedTarget = mob(player,mobs,last.getX()+.5,last.getY(),last.getZ());
            protect=true; float protectedHealth=protectedTarget.getHealth();
            last.hurt(player.damageSources().mobAttack(protectedTarget), 1);
            check(protectedTarget.getHealth()==protectedHealth, "protection cancels secondary damage"); protect=false;
            SlayerStyleArts.getInstance().executeTrickUp(player);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            KyoukaAfterimages.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, () -> true, player.getServer()));
            check(!KyoukaAfterimages.ACTIVE.containsKey(player.getUUID()), "weapon switch clears all illusions");
            check(KyoukaAfterimages.capture(player)==null, "ordinary weapon never arms afterimages");
            player.setItemSlot(EquipmentSlot.MAINHAND, blade); player.setPos(2,200,2);
            player.setDeltaMovement(Vec3.ZERO);
            // The native dodge refuses an unsupported ledge; the earlier aerial checks need no floor.
            for(int x=0;x<=14;x++) for(int z=0;z<=4;z++) {
                var pos=new net.minecraft.core.BlockPos(x,199,z);
                floor.put(pos,level.getBlockState(pos));
                level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
            var movementState = player.getCapability(mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect.MOB_EFFECT)
                    .orElseThrow(IllegalStateException::new);
            movementState.setAvoidCount(0); movementState.setAvoidCooldown(Optional.empty());
            SlayerStyleArts.getInstance().handleSprintMove(player, EnumSet.of(
                    mods.flammpfeil.slashblade.util.InputCommand.SPRINT,
                    mods.flammpfeil.slashblade.util.InputCommand.FORWARD));
            check(KyoukaAfterimages.ACTIVE.containsKey(player.getUUID()), "ordinary directional V dodge creates image: pos="
                    +player.position()+" delta="+player.getDeltaMovement()+" avoid="+movementState.getAvoidCount());
            for (var echo : List.copyOf(KyoukaAfterimages.ACTIVE.get(player.getUUID()))) echo.discard();
            player.setPos(2,200,2);
            var airTarget = mob(player,mobs,10,200,2);
            player.getPersistentData().putInt("sb.airtrick.counter",1);
            player.getPersistentData().putInt("sb.airtrick.target",airTarget.getId());
            // Forge FakePlayer deliberately ignores teleports. Use the real listener for this native-path check.
            var connection=player.connection;
            try {
                player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(player.getServer(),
                        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
                SlayerStyleArts.getInstance().handleAirTrickCounter(player);
            } finally { player.connection=connection; }
            check(KyoukaAfterimages.ACTIVE.containsKey(player.getUUID()), "delayed native Air Trick completion creates image");
            var airImage=KyoukaAfterimages.ACTIVE.get(player.getUUID()).getFirst();
            check(airImage.position().distanceToSqr(new Vec3(2,200,2)) < .001, "delayed Air Trick image uses actual departure");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(WaterMirrorEffect.ARMOR.get(),200));
            check(KyoukaAfterimages.capture(player)==null, "water form cannot create normal-form images");
            airImage.tick();
            check(airImage.isRemoved(), "entering water form clears normal-form images");
            player.removeEffect(WaterMirrorEffect.ARMOR.get());
            LogUtils.getLogger().info("KYOUKA_AFTERIMAGE_SMOKE_PASS: native move, three-image cap, real aggro, single bounded hit, expiry, protection and weapon cleanup");
        } finally {
            protect=false; protectedTarget=null;
            var images = KyoukaAfterimages.ACTIVE.get(player.getUUID());
            if(images!=null) for(var echo:List.copyOf(images)) echo.discard();
            mobs.forEach(Zombie::discard);
            floor.forEach(level::setBlockAndUpdate);
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(baseDamage);
            player.setItemSlot(EquipmentSlot.MAINHAND, original); player.setPos(origin);
        }
    }
    private static Zombie mob(ServerPlayer p, List<Zombie> mobs, double x, double y, double z) {
        var mob = new Zombie(p.serverLevel()); mob.setNoAi(true); mob.setNoGravity(true);
        mob.setPos(x,y,z); mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        p.serverLevel().addFreshEntity(mob); mobs.add(mob); return mob;
    }
    private static void check(boolean condition, String message) { if(!condition) throw new IllegalStateException(message); }
    private KyoukaAfterimageSmoke() {}
}
