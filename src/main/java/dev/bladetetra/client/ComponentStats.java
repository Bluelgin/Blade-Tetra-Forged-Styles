package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.ComponentCombatValues;
import dev.bladetetra.combat.ComponentEffectResolver;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import se.mickelus.tetra.blocks.workbench.gui.WorkbenchStatsGui;
import se.mickelus.tetra.gui.stats.bar.GuiStatBar;
import se.mickelus.tetra.gui.stats.getter.IStatGetter;
import se.mickelus.tetra.gui.stats.getter.LabelGetterBasic;
import java.util.Locale;
import static dev.bladetetra.item.ModularSlashBladeItem.*;

/** Workbench-only conditional component readouts. Native Tetra bars own the
 * assembled weapon comparison; holosphere display is deliberately unchanged.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ComponentStats {
    private enum Effect {
        DRAW("draw", SAYA_SLOT, QUICKDRAW_SAYA_MODULE, 100 * ComponentCombatValues.DRAW_BONUS, "%.0f%%"),
        JUST("just", HABAKI_SLOT, PRECISION_HABAKI_MODULE, ComponentCombatValues.JUST_EXTRA_TICKS, "%.0f tick"),
        SPIRIT("spirit", SAYA_SLOT, SPIRIT_SAYA_MODULE, 100 * ComponentCombatValues.SPIRIT_BONUS, "%.0f%%"),
        GUARD("guard", TSUBA_SLOT, GUARD_TSUBA_MODULE, 100 * ComponentCombatValues.GUARD_REDUCTION, "%.0f%%"),
        RANK("rank", TSUKA_SLOT, STABLE_TSUKA_MODULE, 100D / ComponentCombatValues.RANK_UNIT_DIVISOR, "~%.0f%%"),
        TOSS("toss", KASHIRA_SLOT, HEAVY_KASHIRA_MODULE, 1, "%.0f");

        final String key, slot, module, format;
        final double amount;
        Effect(String key, String slot, String module, double amount, String format) {
            this.key = "stat.blade_tetra.component." + key;
            this.slot = slot;
            this.module = module;
            this.amount = amount;
            this.format = format;
        }
        double value(ItemStack stack, String selected) {
            return isBlade(stack) && (selected == null || selected.equals(slot))
                    && ComponentEffectResolver.hasModule(stack, slot, module) ? amount : 0;
        }
        String tooltip() {
            return I18n.get(key + ".tooltip", String.format(Locale.ROOT, "%.0f", amount),
                    String.format(Locale.ROOT, "%.1f", ComponentCombatValues.SPIRIT_TICKS / 20D),
                    Integer.toString(ComponentCombatValues.GUARD_DURABILITY_COST),
                    Integer.toString(ComponentCombatValues.RANK_UNIT_DIVISOR));
        }
    }

    private static boolean isBlade(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ModularSlashBladeItem;
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (Effect effect : Effect.values()) {
                WorkbenchStatsGui.addBar(new EffectBar(effect, 100));
            }
        });
    }

    private record EffectGetter(Effect effect) implements IStatGetter {
        @Override public double getValue(Player player, ItemStack stack) {
            return effect.value(stack, null);
        }
        @Override public double getValue(Player player, ItemStack stack, String slot) {
            return effect.value(stack, slot);
        }
        @Override public double getValue(Player player, ItemStack stack, String slot, String improvement) {
            return getValue(player, stack, slot);
        }
    }

    private static final class EffectBar extends GuiStatBar {
        private final Effect effect;
        EffectBar(Effect effect, int width) {
            super(0, 0, width, effect.key, 0, Math.max(1, effect.amount), false,
                    new EffectGetter(effect), new LabelGetterBasic(effect.format),
                    (player, stack) -> effect.tooltip());
            this.effect = effect;
        }
        @Override public boolean shouldShow(Player player, ItemStack stack, ItemStack preview, String slot, String improvement) {
            return effect.value(stack, null) != 0
                    || effect.value(preview, null) != 0;
        }
        @Override public void update(Player player, ItemStack stack, ItemStack preview, String slot, String improvement) {
            super.update(player, stack.copy(), preview.copy(), null, null);
            bar.setVisible(false); // Conditions are not progress/stat scaling bars.
            if (effect == Effect.TOSS) valueString.setString(I18n.get("stat.blade_tetra.component.conditional"));
        }
    }
    private ComponentStats() { }
}
