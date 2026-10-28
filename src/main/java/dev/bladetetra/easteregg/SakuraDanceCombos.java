package dev.bladetetra.easteregg;

import dev.bladetetra.combat.ModComboStates;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;

/** Local SA nodes delegate authored callbacks, with enough time for their final hits. */
public final class SakuraDanceCombos {
    private static boolean compatible = true;
    private static final List<RegistryObject<ComboState>> NODES = new ArrayList<>();
    static {
        for (int i=1;i<=6;i++) {
            final int stage=i;
            NODES.add(ModComboStates.COMBOS.register("sakura_dance_b"+i, () -> create(stage)));
        }
    }
    public static void register() {}
    public static boolean compatible() { return compatible; }
    public static ResourceLocation id(int stage) { return NODES.get(stage-1).getId(); }
    public static boolean owns(ResourceLocation id) { return NODES.stream().anyMatch(n -> n.getId().equals(id)); }
    public static ComboState nativeNode(int stage) {
        return switch(stage) {
            case 1 -> ComboStateRegistry.COMBO_B1.get(); case 2 -> ComboStateRegistry.COMBO_B2.get();
            case 3 -> ComboStateRegistry.COMBO_B3.get(); case 4 -> ComboStateRegistry.COMBO_B4.get();
            case 5 -> ComboStateRegistry.COMBO_B5.get(); default -> ComboStateRegistry.COMBO_B6.get();
        };
    }
    private static ComboState create(int stage) {
        ComboState original=nativeNode(stage);
        var builder=ComboState.Builder.newInstance().startAndEnd(original.getStartFrame(),original.getEndFrame())
                .motionLoc(original.getMotionLoc()).speed(original.getSpeed()).priority(0).timeout(1500)
                .next(user -> id(stage)).nextOfTimeout(user -> ComboStateRegistry.NONE.getId())
                .addHoldAction(original::holdAction).addHitEffect(original::hitEffect)
                .releaseAction(original::releaseAction)
                .clickAction(user -> SakuraInfiniteDanceSA.committed(user,stage));
        try {
            // Pinned upstream Builder only exposes addTickAction, with an
            // ArrowReflector installed. Replace once to avoid double reflection.
            // No global registry mutation or per-tick reflection.
            var field=ComboState.Builder.class.getDeclaredField("tickAction");
            if(field.getType()!=Consumer.class) throw new NoSuchFieldException("tickAction type");
            field.setAccessible(true);
            field.set(builder,(Consumer<LivingEntity>) user -> SakuraInfiniteDanceSA.nativeTick(user,stage));
        } catch(ReflectiveOperationException | RuntimeException unsupported) {
            compatible=false;
            com.mojang.logging.LogUtils.getLogger().error("Sakura SA disabled: upstream callback adapter unavailable",unsupported);
        }
        return builder.build();
    }
    private SakuraDanceCombos() {}
}
