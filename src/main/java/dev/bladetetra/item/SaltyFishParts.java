package dev.bladetetra.item;

import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyCatalog;
import dev.bladetetra.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.items.modular.IModularItem;

import static dev.bladetetra.item.ModularSlashBladeItem.BLADE_SLOT;
import static dev.bladetetra.item.ModularSlashBladeItem.CUSTOM_LONG_BLADE_MODULE;
import static dev.bladetetra.item.ModularSlashBladeItem.CUSTOM_SHORT_BLADE_MODULE;
import static dev.bladetetra.item.ModularSlashBladeItem.NODACHI_MODULE;
import static dev.bladetetra.item.ModularSlashBladeItem.SAYA_SLOT;
import static dev.bladetetra.item.ModularSlashBladeItem.TSUBA_SLOT;
import static dev.bladetetra.item.ModularSlashBladeItem.WAKIZASHI_MODULE;

public final class SaltyFishParts {
    public enum Length {
        SHORT("short", -201, -219),
        STANDARD("standard", -272, -294),
        LONG("long", -343, -366);

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
    }

    public static final String STANDARD_BLADE = "slashblade/fish_blade_standard";
    public static final String IAIDO_BLADE = "slashblade/fish_blade_iaido";
    public static final String SHORT_BLADE = "slashblade/fish_blade_short";
    public static final String LONG_BLADE = "slashblade/fish_blade_long";
    public static final String FISH_SAYA = "slashblade/fish_saya";

    public enum Blade {
        STANDARD(STANDARD_BLADE), IAIDO(IAIDO_BLADE), RENGEKI(SHORT_BLADE), DANGAKU(LONG_BLADE);

        public final String module;

        Blade(String module) {
            this.module = module;
        }
    }

    private static final String GUARD_MODULE = "slashblade/fish_salvaged_tsuba";
    private static final String GUARD_SOURCE = "blade_tetra_salvaged_guard";

    public static Length blade(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        return switch (tag.getString(BLADE_SLOT)) {
            case SHORT_BLADE -> Length.SHORT;
            case STANDARD_BLADE, IAIDO_BLADE -> Length.STANDARD;
            case LONG_BLADE -> Length.LONG;
            default -> null;
        };
    }

    public static Length saya(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        return switch (tag.getString(SAYA_SLOT)) {
            // Keep the appearance and module properties of existing fixed-length sheaths.
            case "slashblade/fish_saya_short" -> Length.SHORT;
            case "slashblade/fish_saya_standard" -> Length.STANDARD;
            case "slashblade/fish_saya_long" -> Length.LONG;
            case FISH_SAYA -> switch (tag.getString(BLADE_SLOT)) {
                case SHORT_BLADE, WAKIZASHI_MODULE, CUSTOM_SHORT_BLADE_MODULE -> Length.SHORT;
                case LONG_BLADE, NODACHI_MODULE, CUSTOM_LONG_BLADE_MODULE -> Length.LONG;
                default -> Length.STANDARD;
            };
            default -> null;
        };
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

    public static ItemStack create(Blade blade, LegacyImprintKind guard) {
        ModularSlashBladeItem item = ModItems.MODULAR_SLASHBLADE.get();
        ItemStack stack = item.createDefaultStack();
        BladeModuleMigration.installModule(stack, BLADE_SLOT, blade.module,
                blade.module.substring("slashblade/".length()) + "/bone");
        BladeModuleMigration.installModule(stack, SAYA_SLOT, FISH_SAYA, "fish_saya/salted_fish");
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
