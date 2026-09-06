package dev.bladetetra.registry;

import com.mojang.serialization.Codec;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.loot.ForgingScrollLootModifier;
import dev.bladetetra.loot.SmithyClueLootModifier;
import dev.bladetetra.loot.TechniqueScrollLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> CODECS =
            DeferredRegister.create(
                    ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    BladeTetra.MOD_ID);

    public static final RegistryObject<Codec<SmithyClueLootModifier>> SMITHY_CLUE =
            CODECS.register("smithy_clue", () -> SmithyClueLootModifier.CODEC);
    public static final RegistryObject<Codec<ForgingScrollLootModifier>> FORGING_SCROLL =
            CODECS.register("forging_scroll", () -> ForgingScrollLootModifier.CODEC);
    public static final RegistryObject<Codec<TechniqueScrollLootModifier>> TECHNIQUE_SCROLL =
            CODECS.register("technique_scroll", () -> TechniqueScrollLootModifier.CODEC);

    private ModLootModifiers() {
    }
}
