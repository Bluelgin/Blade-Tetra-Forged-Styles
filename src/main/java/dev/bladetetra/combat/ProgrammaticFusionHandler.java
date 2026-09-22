package dev.bladetetra.combat;

import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * Executes mixed named-blade fusions. Exact allow-listed add-on presentations are
 * delegated first; the bounded Blade Tetra grammar remains the fallback path.
 */
final class ProgrammaticFusionHandler {
    static final float DRIVE_SPEED = ProceduralSlashArtExecutor.DRIVE_SPEED;
    static final int DRIVE_LIFETIME = ProceduralSlashArtExecutor.DRIVE_LIFETIME;
    static final double FAN_ANGLE_DEGREES = ProceduralSlashArtExecutor.FAN_ANGLE_DEGREES;

    static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event,
            ServerPlayer player, ItemStack blade, ISlashBladeState state) {
        if (!ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId()
                .equals(state.getSlashArtsKey())) {
            return;
        }
        ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(blade);
        if (plan == null) {
            return;
        }

        boolean delegatedRelease = ProgrammaticFusionPresentationRuntime.delegateRelease(
                event, player, plan);
        Vec3 forward = player.getLookAngle();
        if (!delegatedRelease && plan.primaryDriveDamage() > 0.0D) {
            ProceduralSlashArtExecutor.spawnDrive(player, forward,
                    plan.primaryDriveDamage(), 0, -90.0F, DRIVE_SPEED);
        }

        if (!ProgrammaticFusionPresentationRuntime.scheduleResponse(
                event, player, plan, delegatedRelease)) {
            executeSemanticResponse(player, plan);
        }
    }

    static void tick(TickEvent.ServerTickEvent event) {
        ProgrammaticFusionPresentationRuntime.tick(event);
    }

    static void onLevelUnload(ServerLevel level) {
        ProgrammaticFusionPresentationRuntime.onLevelUnload(level);
    }

    static void clear() {
        ProgrammaticFusionPresentationRuntime.clear();
    }

    static void executeSemanticResponse(ServerPlayer player, ProgrammaticFusionPlan plan) {
        ProgrammaticFusionProfile.Response response = plan.response().response();
        ProceduralSlashArtExecutor.execute(player, response, plan.responseCount(),
                plan.responseDriveDamage(), 0, 1.0D);
    }

    // Compatibility seams retained for focused geometry tests and older callers.
    static double responseYaw(ProgrammaticFusionProfile.Response response, int index) {
        return ProceduralSlashArtExecutor.responseYaw(
                response, index, response.projectileCount(), 1.0D);
    }

    static int responseDelay(ProgrammaticFusionProfile.Response response, int index) {
        return ProceduralSlashArtExecutor.responseDelay(response, index);
    }

    static float responseSpeed(ProgrammaticFusionProfile.Response response, int index) {
        return ProceduralSlashArtExecutor.responseSpeed(response, index);
    }

    static float responseRoll(ProgrammaticFusionProfile.Response response, int index) {
        return ProceduralSlashArtExecutor.responseRoll(
                response, index, response.projectileCount());
    }

    static double spreadOffset(int index, int count, double spacing) {
        return ProceduralSlashArtExecutor.spreadOffset(index, count, spacing);
    }

    static Vec3 rotateHorizontal(Vec3 direction, double degrees) {
        return ProceduralSlashArtExecutor.rotateHorizontal(direction, degrees);
    }

    private ProgrammaticFusionHandler() {
    }
}
