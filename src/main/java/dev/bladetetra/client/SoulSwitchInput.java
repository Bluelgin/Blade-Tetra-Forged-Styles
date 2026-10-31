package dev.bladetetra.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.easteregg.AwakenedBladeCombat;
import dev.bladetetra.easteregg.SoulSkillState;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.SoulSwitchPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class SoulSwitchInput {
    public static final KeyMapping SWITCH = new KeyMapping("key.blade_tetra.switch_soul",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.blade_tetra");

    @SubscribeEvent public static void key(InputEvent.Key e) {
        var mc = Minecraft.getInstance();
        // GLFW_REPEAT is deliberately ignored: holding the key never toggles back and forth.
        if (e.getAction() != GLFW.GLFW_PRESS || !SWITCH.matches(e.getKey(), e.getScanCode())
                || mc.screen != null || mc.player == null || mc.level == null || !mc.player.isAlive()
                || mc.player.isSpectator() || !AwakenedBladeCombat.replacesLegacy()) return;
        var blade = mc.player.getMainHandItem();
        var tag = blade.getTag();
        if (!SoulSkillState.fusion(blade) || tag == null || !tag.hasUUID(SoulSkillState.BLADE_ID)) return;
        ModNetwork.CHANNEL.sendToServer(new SoulSwitchPacket(mc.player.getInventory().selected,
                tag.getUUID(SoulSkillState.BLADE_ID), SoulSkillState.selected(blade).ordinal()));
    }

    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(SWITCH); }
        private Registration() {}
    }
    private SoulSwitchInput() {}
}
