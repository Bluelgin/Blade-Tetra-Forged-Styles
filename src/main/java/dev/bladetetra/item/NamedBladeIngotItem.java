package dev.bladetetra.item;

import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyCatalog;
import dev.bladetetra.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * One item id for every named blade. Its source id is data, not another registry entry.
 * Never deserialize a client-side model/texture on the logical server.
 */
public final class NamedBladeIngotItem extends Item {
    private static final String SOURCE = "blade_tetra_ingot_source";

    public NamedBladeIngotItem() {
        super(new Properties().stacksTo(64));
    }

    public static ItemStack create(LegacyImprintKind kind) {
        ItemStack result = new ItemStack(ModItems.NAMED_BLADE_INGOT.get());
        if (kind != null) {
            result.getOrCreateTag().putString(SOURCE, kind.id());
        }
        return result;
    }

    public static String sourceId(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof NamedBladeIngotItem)) return null;
        return storedSourceId(stack);
    }

    /** Read the source tag after a validated recipe consumes the final ingot. */
    public static String storedSourceId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(SOURCE, net.minecraft.nbt.Tag.TAG_STRING)) return null;
        String id = tag.getString(SOURCE);
        return !id.isBlank() && id.length() <= 256 ? id : null;
    }

    public static LegacyImprintKind source(ItemStack stack) {
        return sourceFromId(sourceId(stack));
    }

    public static LegacyImprintKind sourceFromId(String id) {
        if (id == null) return null;
        try {
            return NamedLegacyCatalog.get(id);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        LegacyImprintKind source = source(stack);
        return source == null ? super.getName(stack)
                : Component.translatable("item.blade_tetra.named_blade_ingot.named",
                        Component.translatable(source.translationKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        String source = sourceId(stack);
        if (source == null) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.named_blade_ingot.unbound")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.blade_tetra.named_blade_ingot.source_stats")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.blade_tetra.named_blade_ingot.parts")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.blade_tetra.named_blade_ingot.stats")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (source == null || source(stack) == null) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.named_blade_ingot.missing",
                    source == null ? "?" : source).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private dev.bladetetra.client.NamedBladeIngotRenderer renderer;
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new dev.bladetetra.client.NamedBladeIngotRenderer();
                return renderer;
            }
        });
    }
}
