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

/** Adds one specific native Tetra technique scroll to its themed structure. */
public final class TechniqueScrollLootModifier extends LootModifier {
    public static final Codec<TechniqueScrollLootModifier> CODEC =
            RecordCodecBuilder.create(instance -> codecStart(instance)
                    .and(Codec.STRING.fieldOf("kind")
                            .forGetter(modifier -> modifier.kind.name()))
                    .apply(instance, TechniqueScrollLootModifier::new));

    private final ForgingScrolls.Kind kind;

    public TechniqueScrollLootModifier(
            LootItemCondition[] conditions,
            String kind) {
        super(conditions);
        this.kind = ForgingScrolls.Kind.valueOf(kind);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(
            ObjectArrayList<ItemStack> generatedLoot,
            LootContext context) {
        if (context.getRandom().nextDouble()
                < GameplayConfig.TECHNIQUE_SCROLL_CHANCE.get()) {
            generatedLoot.add(ForgingScrolls.create(kind));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
