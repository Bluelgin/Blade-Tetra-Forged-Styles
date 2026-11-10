package dev.bladetetra.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.forging.ForgingScrolls;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.LegacyModelAdapter;
import dev.bladetetra.forging.LegacyModelAnalysis;
import dev.bladetetra.forging.NamedLegacyCatalog;
import dev.bladetetra.item.SaltyFishParts;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class SaltyFishLootModifier extends LootModifier {
    public static final Codec<SaltyFishLootModifier> CODEC =
            RecordCodecBuilder.create(instance -> codecStart(instance)
                    .apply(instance, SaltyFishLootModifier::new));

    public SaltyFishLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,
            LootContext context) {
        RandomSource random = context.getRandom();
        boolean scroll = random.nextDouble() < GameplayConfig.SALTY_FISH_SCROLL_CHANCE.get();
        boolean blade = random.nextDouble() < GameplayConfig.SALTY_FISH_BLADE_CHANCE.get();
        if (scroll) loot.add(ForgingScrolls.create(ForgingScrolls.Kind.SALTY_FISH));
        if (blade) loot.add(catchBlade(random));
        return loot;
    }

    static ItemStack catchBlade(RandomSource random) {
        var blade = SaltyFishParts.Length.random(random);
        var saya = SaltyFishParts.Length.random(random);
        var guard = chooseGuard(random, new ArrayList<>(NamedLegacyCatalog.values()),
                SaltyFishLootModifier::hasPackagedGuard);
        ItemStack stack = SaltyFishParts.create(blade, saya, guard);
        stack.setDamageValue(Math.max(1, Math.round(stack.getMaxDamage()
                * (0.15F + 0.55F * random.nextFloat()))));
        return stack;
    }

    static LegacyImprintKind chooseGuard(RandomSource random, List<LegacyImprintKind> sources,
            Predicate<LegacyImprintKind> compatible) {
        var candidates = new ArrayList<>(sources);
        candidates.remove(LegacyImprintKind.WOODEN_PUPPET);
        candidates.sort(Comparator.comparing(LegacyImprintKind::id));
        // Draw without replacement until a usable source is found. No client state is
        // involved, and only candidates reached by this rare roll need OBJ inspection.
        while (!candidates.isEmpty()) {
            LegacyImprintKind kind = candidates.remove(random.nextInt(candidates.size()));
            if (compatible.test(kind)) return kind;
        }
        return null;
    }

    private static boolean hasPackagedGuard(LegacyImprintKind kind) {
        Path model = findAsset(kind.model());
        if (model == null || findAsset(kind.texture()) == null) return false;
        try (var reader = Files.newBufferedReader(model)) {
            return LegacyModelAnalysis.analyze(reader, LegacyModelAdapter.resolve(kind.model())).usable();
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static Path findAsset(ResourceLocation asset) {
        for (var info : ModList.get().getModFiles()) {
            Path path = info.getFile().findResource("assets", asset.getNamespace(), asset.getPath());
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
