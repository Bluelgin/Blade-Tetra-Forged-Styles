package dev.bladetetra.kyouka;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.*;

/** Vanilla effect tracking is the only networked form marker. */
public final class WaterMirrorEffect extends MobEffect {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "blade_tetra");
    public static final RegistryObject<MobEffect> ARMOR = EFFECTS.register("water_mirror_armor", WaterMirrorEffect::new);
    private WaterMirrorEffect() { super(MobEffectCategory.BENEFICIAL, 0x91d9ef); }
}
