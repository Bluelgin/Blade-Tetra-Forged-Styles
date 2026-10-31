package dev.bladetetra.easteregg;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Vanilla effect synchronization drives the icon, timer and body arcs. */
public final class RaikiriThunderArmorEffect extends MobEffect {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,"blade_tetra");
    public static final RegistryObject<MobEffect> ARMOR = EFFECTS.register("thunder_armor",RaikiriThunderArmorEffect::new);
    private RaikiriThunderArmorEffect() {
        super(MobEffectCategory.BENEFICIAL,0x9ee8ff);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,"7e6e63d9-5dc6-4833-aa3b-b9c41e8d0619",
                .2,AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
