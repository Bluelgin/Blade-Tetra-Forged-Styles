package dev.bladetetra.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.forging.ForgingScrolls;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

public final class ForgingScrollLootModifier extends LootModifier {
    public static final Codec<ForgingScrollLootModifier> CODEC =
            RecordCodecBuilder.create(instance -> codecStart(instance)
                    .apply(instance, ForgingScrollLootModifier::new));

    public ForgingScrollLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(
            ObjectArrayList<ItemStack> generatedLoot,
            LootContext context) {
        if (context.getRandom().nextDouble()
                < GameplayConfig.FORGING_SCROLL_CHANCE.get()) {
            ForgingScrolls.Kind[] kinds = {
                    ForgingScrolls.Kind.EDGE,
                    ForgingScrolls.Kind.CONSTRUCTION,
                    ForgingScrolls.Kind.ASSEMBLY
            };
            generatedLoot.add(ForgingScrolls.create(
                    kinds[context.getRandom().nextInt(kinds.length)]));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
