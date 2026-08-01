package dev.bladetetra.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bladetetra.item.SmithingClueItem;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.lore.SmithingLore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

public final class SmithyClueLootModifier extends LootModifier {
    public static final Codec<SmithyClueLootModifier> CODEC =
            RecordCodecBuilder.create(instance -> codecStart(instance)
                    .apply(instance, SmithyClueLootModifier::new));

    public SmithyClueLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(
            ObjectArrayList<ItemStack> generatedLoot,
            LootContext context) {
        RandomSource random = context.getRandom();
        if (random.nextDouble() >= GameplayConfig.SMITHY_CLUE_CHANCE.get()) {
            return generatedLoot;
        }

        int roll = random.nextInt(100);
        String clue = roll < 22 ? SmithingLore.SHOSHIN
                : roll < 40 ? SmithingLore.BAIREN
                : roll < 58 ? SmithingLore.BANSHO
                : roll < 73 ? SmithingLore.RAIKIRI
                : roll < 83 ? SmithingLore.AKATSUKI
                : roll < 92 ? SmithingLore.KYOUKA
                : SmithingLore.SENBONZAKURA;
        generatedLoot.add(SmithingClueItem.create(clue));
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
