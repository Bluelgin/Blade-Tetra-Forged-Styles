package dev.bladetetra.easteregg;

import dev.bladetetra.combat.ModComboStates;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;

/** Local SA nodes retain the audited native attacks, with enough time for their final hits. */
public final class SakuraDanceCombos {
    private static final List<RegistryObject<ComboState>> NODES = new ArrayList<>();
    static {
        for (int i=1;i<=6;i++) {
            final int stage=i;
            NODES.add(ModComboStates.COMBOS.register("sakura_dance_b"+i, () -> create(stage)));
        }
    }
    public static void register() {}
    public static boolean compatible() {
        return net.minecraftforge.fml.ModList.get().getModContainerById("slashblade")
                .map(mod -> "1.9.63".equals(mod.getModInfo().getVersion().toString())).orElse(false);
    }
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
        builder.addTickAction(user -> SakuraInfiniteDanceSA.nativeTick(user,stage));
        return builder.build();
    }
    /** Audited 1.9.63 B1-B6 timeline, using the exact public upstream attack callbacks.
     * The original TimeLineTickAction has a shared mutable offset; forwarding that
     * consumer would allow one player's late first tick to truncate another SA.
     * No upstream consumer, offset, node or damage formula is mutated here.
     */
    static void attackTick(LivingEntity user,int stage,int elapsed) {
        if(stage==1&&elapsed==6) {
            mods.flammpfeil.slashblade.util.AttackManager.doSlash(user,-30,false,false,.244F);
            mods.flammpfeil.slashblade.util.AttackManager.doSlash(user,145,true,false,.244F);
            return;
        }
        if(stage==1?(elapsed<7||elapsed>14):(elapsed<0||elapsed>6)) return;
        boolean reverse=((elapsed-(stage==1?7:0))&1)!=0;
        float roll=(reverse?90:-90)+180*user.getRandom().nextFloat();
        double ratio=stage==3&&elapsed==2?.24F:.244F;
        mods.flammpfeil.slashblade.util.AttackManager.doSlash(user,roll,
                mods.flammpfeil.slashblade.util.AttackManager.genRushOffset(user),reverse,false,ratio);
    }
    private SakuraDanceCombos() {}
}
