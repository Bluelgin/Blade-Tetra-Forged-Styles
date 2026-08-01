package dev.bladetetra.registry;

import com.mojang.serialization.Codec;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.loot.SmithyClueLootModifier;
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

    private ModLootModifiers() {
    }
}
