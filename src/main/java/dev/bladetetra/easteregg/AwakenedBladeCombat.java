package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeTechniqueHandler;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import dev.bladetetra.network.ModNetwork;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;


/** Shared admission and presentation helpers; each soul owns its combat lifecycle. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AwakenedBladeCombat {
    public static boolean replacesLegacy() {
        return GameplayConfig.ENABLE_AWAKENED_V2.get();
    }

    public static boolean isV2CombatSoul(SoulLegacyState.Legacy soul) {
        return soul != null && soul != SoulLegacyState.Legacy.NONE
                && soul != SoulLegacyState.Legacy.AKATSUKI;
    }

    public static void awakening(ServerPlayer player, SoulLegacyState.Legacy soul) {
        AwakeningPresentationService.success(player, soul);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRealDamage(LivingDamageEvent event) {
        if (!replacesLegacy() || !(event.getEntity().level() instanceof ServerLevel level)
                || !Float.isFinite(event.getAmount()) || event.getAmount() <= 0.0F
                || BladeTechniqueHandler.isTechniqueDamage(event.getSource())
                || SoulLegacyDamageGuard.isSecondary(event.getSource())) return;
        ServerPlayer player = attacker(event);
        if (player == null || !canAffect(player, event.getEntity())) return;
        ItemStack blade = player.getMainHandItem();
        SoulLegacyState.Legacy soul = SoulLegacyState.active(blade);
        if (!SoulLegacyState.isActive(blade, soul) || !isV2CombatSoul(soul)
                || soul == SoulLegacyState.Legacy.KYOUKA) return;
        if (soul == SoulLegacyState.Legacy.SENBONZAKURA) {
            SakuraCombatVisuals.confirm(player, blade, event.getSource().getDirectEntity(),
                    event.getEntity(), event.getAmount());
        }
    }

    private static ServerPlayer attacker(LivingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return null;
        if (direct == player) return player;
        if (direct instanceof IShootable sword && sword.getShooter() == player) return player;
        return null;
    }

    static boolean canAffect(ServerPlayer player, LivingEntity target) {
        if (player == target || !target.isAlive() || target instanceof Player
                || player.isAlliedTo(target) || target.isAlliedTo(player)
                || target instanceof TamableAnimal || target instanceof AbstractVillager
                || target instanceof AbstractGolem || target instanceof Animal
                || target instanceof WaterAnimal || target instanceof AmbientCreature)
            return false;
        if (target instanceof Enemy) return true;
        return target instanceof Mob mob && (mob.getTarget() == player
                || mob.getLastHurtByMob() == player);
    }

    static void visual(ServerLevel level, SoulLegacyState.Legacy soul,
            int type, Vec3 pos, Vec3 end, int variant) {
        AwakenedBladeVfxPacket packet = new AwakenedBladeVfxPacket(
                soul.ordinal(), type, pos.x, pos.y, pos.z,
                end.x, end.y, end.z, variant);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(pos) <= 64.0D * 64.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    static void visualTo(ServerPlayer viewer, int type, Vec3 pos, Vec3 end, int variant) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new AwakenedBladeVfxPacket(SoulLegacyState.Legacy.RAIKIRI.ordinal(), type,
                        pos.x,pos.y,pos.z,end.x,end.y,end.z,variant));
    }

    private AwakenedBladeCombat() { }
}
