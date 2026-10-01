package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.ForgedSlashArtPlan;
import dev.bladetetra.registry.ModItems;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;

/** Client-only tint over SlashBlade's own Proud Soul Sphere OBJ and texture. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgedSlashArtOrbColors {
    private static final Map<ItemStack, Integer> COLORS = new IdentityHashMap<>();
    private static long colorFrame = -1;

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? color(stack) : -1,
                ModItems.FORGED_SLASH_ART_ORB.get());
    }

    private static int color(ItemStack stack) {
        long now = Util.getMillis();
        long frame = now / 50L;
        // The native OBJ has many tinted faces. Read Tetra's modules once per
        // displayed stack per visual tick, rather than once for every face.
        if (frame != colorFrame || COLORS.size() > 128) {
            COLORS.clear();
            colorFrame = frame;
        }
        return COLORS.computeIfAbsent(stack, item -> {
            ForgedSlashArtPlan plan = ForgedSlashArtPlan.fromOrb(item);
            double blend = (1.0D - Math.cos((now % 8000L) * Math.PI / 4000.0D)) * 0.5D;
            if (plan == null) {
                return interpolate(0xE5E5E5, 0xFFFFFF, blend);
            }
            return interpolate(palette(plan.primary()), palette(plan.secondary()), blend);
        });
    }

    private static int palette(ForgedSlashArtPlan.Technique technique) {
        // Light tints retain the highlights and spectrum in the native texture.
        return switch (technique) {
            case JUDGEMENT_CUT -> 0xDEA0FF;
            case SAKURA_END -> 0xFF9DBD;
            case VOID_SLASH -> 0xAAA0FF;
            case CIRCLE_SLASH -> 0xFFE4A5;
            case DRIVE_VERTICAL -> 0xA1DAFF;
            case DRIVE_HORIZONTAL -> 0xA1FFDC;
            case WAVE_EDGE -> 0xBAFFF2;
            case PIERCING -> 0xFFB49D;
        };
    }

    private static int interpolate(int first, int second, double blend) {
        int red = channel(first, second, 16, blend);
        int green = channel(first, second, 8, blend);
        int blue = channel(first, second, 0, blend);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int channel(int first, int second, int shift, double blend) {
        int start = first >> shift & 255;
        int end = second >> shift & 255;
        return (int) Math.round(start + (end - start) * blend);
    }

    private ForgedSlashArtOrbColors() {
    }
}
