package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedBladeMaterial;
import dev.bladetetra.item.NamedBladeIngotItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.mutil.gui.GuiString;
import se.mickelus.tetra.gui.stats.getter.LabelGetterBasic;
import se.mickelus.tetra.items.modular.impl.holo.gui.craft.HoloMaterialStatGui;
import se.mickelus.tetra.module.data.MaterialData;

/** Opt-in real client check: no world or player save is opened. */
@Mod.EventBusSubscriber(modid = "blade_tetra", value = Dist.CLIENT)
public final class NamedMaterialSmokeClient {
    private static boolean opened;
    private static int ticks;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("blade_tetra.namedMaterialSmoke") || opened
                || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() == null && mc.screen != null && ++ticks > 40) {
            opened = true;
            mc.setScreen(new Review());
        }
    }

    private static final class StatProbe extends HoloMaterialStatGui {
        StatProbe() { super(0, 0, "primary", new LabelGetterBasic("%.1f"), m -> m.primary); }
        String displayed() throws ReflectiveOperationException {
            var field = GuiString.class.getDeclaredField("string");
            field.setAccessible(true);
            return (String) field.get(value);
        }
    }

    private static final class Review extends Screen {
        final ItemStack unknown = NamedBladeIngotItem.create(null);
        final ItemStack source = NamedBladeIngotItem.create(LegacyImprintKind.BLACK_FOX);
        int frames;
        Review() { super(Component.literal("Named ingot material smoke")); }
        @Override protected void init() {
            try {
                var material = new MaterialData(); material.key = NamedBladeMaterial.KEY; material.primary = 0F;
                var probe = new StatProbe(); probe.update(material, material);
                if (!probe.displayed().equals("0.0")) throw new IllegalStateException("Catalog leaked fixed stats");
                material.key = "iron"; material.primary = 5F; probe.update(material, material);
                if (!probe.displayed().equals("5.0")) throw new IllegalStateException("Ordinary stats hidden");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(0, 0, width, height, 0xff171820);
            g.drawCenteredString(font, "Named ingot: catalog / source appearance", width / 2, 25, 0xffffff);
            for (int i = 0; i < 2; i++) {
                g.pose().pushPose();
                g.pose().translate(width * (i + 1) / 3.0 - 32, height / 2.0 - 32, 0);
                g.pose().scale(4, 4, 4);
                g.renderItem(i == 0 ? unknown : source, 0, 0);
                g.pose().popPose();
            }
            g.drawCenteredString(font, "Catalog stats: 0   |   Forge hammer: 3", width / 2, height - 45, 0xff7777);
            if (++frames == 20) {
                try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    var path = Minecraft.getInstance().gameDirectory.toPath().resolve("named-material.png");
                    image.writeToFile(path);
                    LogUtils.getLogger().info("NAMED_MATERIAL_CLIENT_PASS: native holo zero stats and ordinary stats; source item renderer; {}", path);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
                Minecraft.getInstance().stop();
            }
        }
    }
    private NamedMaterialSmokeClient() {}
}
