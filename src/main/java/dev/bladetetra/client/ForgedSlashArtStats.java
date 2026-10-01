package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.ForgedSlashArtCore;
import dev.bladetetra.item.ForgedSlashArtOrbItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import se.mickelus.tetra.blocks.workbench.gui.WorkbenchStatsGui;
import se.mickelus.tetra.items.modular.impl.holo.gui.craft.HoloStatsGui;
import se.mickelus.tetra.gui.stats.bar.GuiStatBar;
import se.mickelus.tetra.gui.stats.getter.IStatGetter;
import se.mickelus.tetra.gui.stats.getter.LabelGetterBasic;
import se.mickelus.tetra.gui.stats.getter.TooltipGetterPercentageDecimal;

/** Uses the common 6.3+ addBar API, not the version-dependent extended constructors. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgedSlashArtStats {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (int stat = 0; stat < 3; stat++) {
                // Each GUI needs its own element instance (different parents).
                WorkbenchStatsGui.addBar(new CoreBar(stat, 100));
                HoloStatsGui.addBar(new CoreBar(stat, 60));
            }
        });
    }

    private static boolean isOrb(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ForgedSlashArtOrbItem;
    }

    private static final class CoreBar extends GuiStatBar {
        CoreBar(int stat, int width) {
            super(0, 0, width, key(stat), 0, stat == 0 ? 15 : 100, false,
                    new CoreGetter(stat), new LabelGetterBasic("%.1f%%"),
                    new TooltipGetterPercentageDecimal(key(stat) + ".tooltip", new CoreGetter(stat)));
        }

        @Override
        public boolean shouldShow(Player player, ItemStack stack, ItemStack preview,
                String slot, String improvement) {
            return isOrb(stack) || isOrb(preview);
        }

        @Override
        public void update(Player player, ItemStack stack, ItemStack preview,
                String slot, String improvement) {
            // Core scales describe the whole authored SA, not an additive slot
            // contribution. Keep Tetra's native old/new comparison without its
            // slot subtraction (which would otherwise zero these percentages).
            super.update(player, stack, preview, null, null);
        }
    }

    private static String key(int stat) {
        return "stat.blade_tetra.forged." + switch (stat) {
            case 0 -> "core_bonus";
            case 1 -> "primary_scale";
            default -> "secondary_scale";
        };
    }

    private record CoreGetter(int stat) implements IStatGetter {
        @Override
        public double getValue(Player player, ItemStack stack) {
            if (!isOrb(stack)) return 0;
            float bonus = ForgedSlashArtCore.bonus(ForgedSlashArtCore.orbVariant(stack));
            return 100 * (stat == 0 ? bonus : ForgedSlashArtCore.phaseScale(stat == 2, bonus));
        }

        @Override
        public double getValue(Player player, ItemStack stack, String slot) {
            return getValue(player, stack);
        }

        @Override
        public double getValue(Player player, ItemStack stack, String slot, String improvement) {
            return getValue(player, stack);
        }
    }

    private ForgedSlashArtStats() {}
}
