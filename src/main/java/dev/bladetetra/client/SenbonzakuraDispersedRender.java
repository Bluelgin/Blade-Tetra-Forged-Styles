package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.client.awakening.AwakeningVisualController;
import dev.bladetetra.client.awakening.AwakeningVisibility;
import dev.bladetetra.client.awakening.FloatingBladeRenderer;
import dev.bladetetra.easteregg.SakuraEndlessFlow;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A combat-only view of the real held weapon. Never substitutes a stock hilt. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class SenbonzakuraDispersedRender {
    private static Player renderingPlayer;
    private static boolean handPass;
    private static int retainedPartDepth;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void playerPre(RenderPlayerEvent.Pre e) { renderingPlayer = e.getEntity(); }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void playerPost(RenderPlayerEvent.Post e) { renderingPlayer = null; }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handPre(RenderHandEvent e) { handPass = true; }
    // RenderHandEvent precedes vanilla's TEISR draw; it is not a paired render scope.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void guiPre(net.minecraftforge.client.event.RenderGuiEvent.Pre e) { handPass = false; }
    @SubscribeEvent
    public static void levelStage(RenderLevelStageEvent e) {
        renderingPlayer = null;
        handPass = false;
    }

    static boolean hiltOnly(ItemStack stack, String target) {
        if (!AwakeningVisibility.isBladeTarget(target) || FloatingBladeRenderer.isRendering()
                || !SakuraEndlessFlow.eligible(stack)) return false;
        Player owner = renderingPlayer;
        if (owner == null && handPass) owner = Minecraft.getInstance().player;
        return shouldClip(true, owner != null && owner.getMainHandItem() == stack,
                owner != null && AwakeningVisualController.presenting(owner), false, target);
    }

    public static boolean shouldClip(boolean dispersed, boolean realHeldObject,
            boolean ritual, boolean floating, String target) {
        return dispersed && realHeldObject && !ritual && !floating
                && AwakeningVisibility.isBladeTarget(target);
    }

    // Legacy assembly already selects/calibrates real TSUBA/TSUKA parts. Do not
    // crop those nested views again using the complete sword's UV assumptions.
    static void retainedPart(Runnable render) {
        retainedPartDepth++;
        try { render.run(); } finally { retainedPartDepth--; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderOverride(RenderOverrideEvent e) {
        if (retainedPartDepth > 0 || !hiltOnly(e.getStack(), e.getOriginalTarget())) return;
        if (e.getOriginalTarget().endsWith("_luminous")
                && dev.bladetetra.forging.NamedLegacyParts.visualFromStack(e.getStack()).present()) {
            // The assembled parts already own their material/emissive nested passes.
            // Do not overlay the unassembled base model's grip on a custom legacy hilt.
            e.setCanceled(true); return;
        }
        var location = e.getStack().getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                .map(state -> state.getModel().orElse(null)).orElse(null);
        // UV ownership is guaranteed only for our modular atlas. Unknown third-party
        // OBJ layouts keep their full model; calibrated named assemblies bypass here.
        if (location == null || !BladeTetra.MOD_ID.equals(location.getNamespace())
                || !location.getPath().startsWith("model/modular/")) return;
        var view = DispersedBladeGeometry.hilt(e.getModel(), e.getTarget());
        if (view == null) e.setCanceled(true); else e.setModel(view);
        // Never uncancel an earlier handler and never touch native sheath passes.
    }
    private SenbonzakuraDispersedRender() {}
}
