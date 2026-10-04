package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hit-only formation control. Right click, charging and SA belong to Resharped. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class DangakuFormationHandler {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hit(SlashBladeEvent.HitEvent event) {
        if (event.isCanceled() || event.getUser().level().isClientSide()
                || !(event.getBlade().getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(event.getBlade()) != BladeStyle.DANGAKU) return;
        // Read-only save migration: never create the obsolete per-target mark.
        var data = event.getTarget().getPersistentData();
        data.remove("blade_tetra_broken_stance_owner");
        data.remove("blade_tetra_broken_stance_ready");
        data.remove("blade_tetra_broken_stance_until");
        var phase = BranchingStyleCombos.phase(event.getSlashBladeState().getComboSeq());
        if (phase != StyleBranchRules.Phase.D_SWEEP && phase != StyleBranchRules.Phase.D_RETURN) return;
        Vec3 look = event.getUser().getViewVector(1);
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 1.0E-6) return;
        pullTowardFocus(event.getTarget(), event.getUser().position().add(horizontal.normalize().scale(2.7)), .40);
    }

    private static void pullTowardFocus(LivingEntity target, Vec3 focus, double strength) {
        Vec3 delta = new Vec3(focus.x - target.getX(), 0, focus.z - target.getZ());
        if (delta.lengthSqr() <= 1.0E-6) return;
        double resistance = Math.max(0, Math.min(1, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
        double effectiveStrength = strength * (1 - resistance);
        if (effectiveStrength <= .01) return; // Bosses are not forcibly dragged.
        double distance = delta.length();
        double speed = Math.min(.75, effectiveStrength + distance * .055);
        Vec3 direction = delta.scale(1 / distance);
        target.setDeltaMovement(direction.x * speed, Math.max(.04, target.getDeltaMovement().y * .35), direction.z * speed);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(target));
            target.hurtMarked = false;
        }
    }
    private DangakuFormationHandler() {}
}
