package dev.bladetetra.item;

import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyCatalog;
import dev.bladetetra.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.items.modular.IModularItem;

import static dev.bladetetra.item.ModularSlashBladeItem.BLADE_SLOT;
import static dev.bladetetra.item.ModularSlashBladeItem.SAYA_SLOT;
import static dev.bladetetra.item.ModularSlashBladeItem.TSUBA_SLOT;

public final class SaltyFishParts {
    public enum Length {
        SHORT("short", -207, -219),
        STANDARD("standard", -282, -294),
        LONG("long", -355, -366);

        public final String id;
        public final int bladeTip;
        public final int sayaTip;

        Length(String id, int bladeTip, int sayaTip) {
            this.id = id;
            this.bladeTip = bladeTip;
            this.sayaTip = sayaTip;
        }

        public String part(String part) {
            return "fish_" + part + "_" + id;
        }

        public String module(String part) {
            return "slashblade/" + part(part);
        }

        public static Length random(RandomSource random) {
            return switch (random.nextInt(3)) {
                case 0 -> SHORT;
                case 1 -> STANDARD;
                default -> LONG;
            };
        }
    }

    private static final String GUARD_MODULE = "slashblade/fish_salvaged_tsuba";
    private static final String GUARD_SOURCE = "blade_tetra_salvaged_guard";

    public static Length blade(ItemStack stack) {
        return length(stack.getTag(), BLADE_SLOT, "blade");
    }

    public static Length saya(ItemStack stack) {
        return length(stack.getTag(), SAYA_SLOT, "saya");
    }

    private static Length length(CompoundTag tag, String slot, String part) {
        if (tag == null) return null;
        String key = tag.getString(slot);
        if (key.equals("slashblade/fish_" + part + "_short")) return Length.SHORT;
        if (key.equals("slashblade/fish_" + part + "_standard")) return Length.STANDARD;
        if (key.equals("slashblade/fish_" + part + "_long")) return Length.LONG;
        return null;
    }

    public static LegacyImprintKind salvagedGuard(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !GUARD_MODULE.equals(tag.getString(TSUBA_SLOT))) return null;
        String id = tag.getString(GUARD_SOURCE);
        return id.isEmpty() ? null : NamedLegacyCatalog.get(id);
    }

    static void discardReplacedGuard(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(GUARD_SOURCE)
                && !GUARD_MODULE.equals(tag.getString(TSUBA_SLOT))) {
            tag.remove(GUARD_SOURCE);
        }
    }

    public static ItemStack create(Length blade, Length saya, LegacyImprintKind guard) {
        ModularSlashBladeItem item = ModItems.MODULAR_SLASHBLADE.get();
        ItemStack stack = item.createDefaultStack();
        BladeModuleMigration.installModule(stack, BLADE_SLOT, blade.module("blade"),
                blade.part("blade") + "/bone");
        BladeModuleMigration.installModule(stack, SAYA_SLOT, saya.module("saya"),
                saya.part("saya") + "/salted_fish");
        if (guard != null) {
            BladeModuleMigration.installModule(stack, TSUBA_SLOT, GUARD_MODULE,
                    "fish_salvaged_tsuba/iron");
            // Presentation only. The named-imprint and inheritance readers never use this key.
            stack.getOrCreateTag().putString(GUARD_SOURCE, guard.id());
        }
        IModularItem.updateIdentifier(stack);
        item.syncDerivedBladeState(stack);
        return stack;
    }

    private SaltyFishParts() {}
}
