package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in benchmark of the actual 36-slot inventory, not the small gallery page. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID,value=Dist.CLIENT)
public final class ArtInventoryBenchmark {
    private static long start;
    private static int frames;
    private static boolean complete;
    private static MaterialTextureCache.BuildStats before;
    private static final List<Double> millis=new ArrayList<>();
    private static boolean enabled(ScreenEvent event) {
        return !complete && Boolean.getBoolean("blade_tetra.artInventoryBenchmark")
                && event.getScreen() instanceof InventoryScreen;
    }
    @SubscribeEvent public static void pre(ScreenEvent.Render.Pre event) {
        if(enabled(event)) start=System.nanoTime();
    }
    @SubscribeEvent public static void post(ScreenEvent.Render.Post event) {
        if(!enabled(event)) return;
        double elapsed=(System.nanoTime()-start)/1_000_000D;
        if(++frames==60) before=MaterialTextureCache.buildStats();
        if(frames<=60) return;
        millis.add(elapsed);
        if(millis.size()<300) return;
        complete=true;
        var after=MaterialTextureCache.buildStats();
        millis.sort(Double::compareTo);
        double average=millis.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double p95=millis.get((int)(millis.size()*.95)-1);
        String report=String.format(Locale.ROOT,
                "{\"sampleFrames\":300,\"inventoryRenderMeanMs\":%.3f,\"inventoryRenderP95Ms\":%.3f,"
                +"\"materialBuildsAfterWarmup\":%d,\"emissionBuildsAfterWarmup\":%d}",average,p95,
                after.materials()-before.materials(),after.emissions()-before.emissions());
        LogUtils.getLogger().info("BLADE_INVENTORY_BENCHMARK: {}",report);
        try {
            Path directory=Path.of(System.getProperty("blade_tetra.artPreviewOutput"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("inventory-performance.json"),report);
            event.getGuiGraphics().flush();
            try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(directory.resolve("inventory-performance.png"));
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
