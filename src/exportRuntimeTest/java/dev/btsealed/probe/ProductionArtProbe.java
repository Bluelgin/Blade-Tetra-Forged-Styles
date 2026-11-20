package dev.btsealed.probe;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "bt_export_probe", value = Dist.CLIENT)
public final class ProductionArtProbe {
    private static final boolean RUN = Boolean.getBoolean("bladetetra.probe.exportArt");
    private static final String ADDON = "bt_export_dangaku";
    private static final ResourceLocation ATLAS = ResourceLocation.parse(ADDON + ":model/sealed_blade.png");
    private static boolean icon;
    private static boolean firstPerson;
    private static boolean thirdPerson;
    private static int ticks;
    private static int waitTicks;
    private static boolean opened;

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!RUN || !(event.getEntity() instanceof ServerPlayer player)) return;
        player.getInventory().setItem(player.getInventory().selected, ExportRuntimeProbe.craft(player.serverLevel(), ADDON));
    }

    @SubscribeEvent
    public static void rendered(RenderOverrideEvent event) {
        if (!RUN || !ATLAS.equals(event.getOriginalTexture())) return;
        if (event.getOriginalModel().groupObjects.stream().noneMatch(group ->
                group.name.equals(event.getOriginalTarget()) && !group.faces.isEmpty())) return;
        var camera = Minecraft.getInstance().options.getCameraType();
        switch (event.getOriginalTarget()) {
            case "item_blade" -> icon = true;
            case "blade", "sheath" -> {
                if (camera.isFirstPerson()) firstPerson = true;
                else thirdPerson = true;
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!RUN || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        if (client.player == null) {
            if (!opened && client.screen instanceof TitleScreen title) {
                opened = true;
                client.createWorldOpenFlows().loadLevel(title, "sealed-art-review");
            }
            if (++waitTicks >= 400) {
                LogUtils.getLogger().error("BLADE_EXPORT_PRODUCTION_ART_FAIL: World did not open. Active screen: {}",
                        client.screen == null ? "none" : client.screen.getClass().getName());
                client.stop();
            }
            return;
        }
        if (++ticks == 5) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
        } else if (ticks == 40) {
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (ticks >= 60 && icon && firstPerson && thirdPerson || ticks >= 180) {
            try (var stream = client.getResourceManager().open(ATLAS);
                    var image = NativeImage.read(stream)) {
                if (ModList.get().isLoaded("tetra") || ModList.get().isLoaded("blade_tetra")
                        || !client.player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                                .map(state -> state.getTranslationKey().equals("item." + ADDON + ".sealed_blade")).orElse(false)
                        || image.getWidth() != 256 || image.getHeight() != 512
                        || !icon || !firstPerson || !thirdPerson) {
                    throw new IllegalStateException("Render coverage: icon=" + icon + ", firstPerson=" + firstPerson
                            + ", thirdPerson=" + thirdPerson);
                }
                LogUtils.getLogger().info("BLADE_EXPORT_PRODUCTION_ART_PASS");
            } catch (Exception error) {
                LogUtils.getLogger().error("BLADE_EXPORT_PRODUCTION_ART_FAIL", error);
            } finally {
                client.stop();
            }
        }
    }

    private ProductionArtProbe() {}
}
