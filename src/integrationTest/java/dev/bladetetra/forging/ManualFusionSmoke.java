package dev.bladetetra.forging;

import com.mojang.authlib.GameProfile;
import dev.bladetetra.combat.LegacyFusionHandler;
import dev.bladetetra.combat.ProgrammaticFusionPlan;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.lore.ManualDiscovery;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import se.mickelus.tetra.module.SchematicRegistry;
import net.minecraft.resources.ResourceLocation;

/** Focused checks run inside the existing opt-in named-ingot integration suite. */
final class ManualFusionSmoke {
    static void verify(ServerPlayer player) throws se.mickelus.tetra.module.schematic.InvalidSchematicException {
        var sources = NamedLegacyCatalog.values().stream().toList();
        ItemStack input = null;
        outer: for (var saya : sources) for (var guard : sources) {
            var test = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            fit(test, "saya", saya); fit(test, "tsuba", guard);
            if (ProgrammaticFusionPlan.candidate(test) != null) { input = test; break outer; }
        }
        check(input != null, "No generic fusion test pair");
        var blade = input;
        var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new);
        LegacyFusionHandler.sync(blade, state);
        var previous = state.getSlashArtsKey();
        check(ProgrammaticFusionPlan.from(blade) == null, "New pair automatically activated");
        var schematic = SchematicRegistry.getSchematic(new ResourceLocation("tetra", ProgrammaticFusionSchematic.KEY));
        check(schematic != null && schematic.isApplicableForSlot(ModularSlashBladeItem.BLADE_SLOT, blade), "Missing blade operation");
        check(java.util.Arrays.stream(SchematicRegistry.getSchematics(blade, ModularSlashBladeItem.BLADE_SLOT))
                .anyMatch(entry -> entry.getKey().equals(schematic.getKey())), "Native menu omitted fusion operation");
        check(!schematic.isApplicableForSlot(ModularSlashBladeItem.SAYA_SLOT, blade), "Operation exposed on saya");
        var preview = schematic.applyUpgrade(blade, new ItemStack[0], false, ModularSlashBladeItem.BLADE_SLOT, player);
        check(ProgrammaticFusionPlan.from(preview) != null && ProgrammaticFusionPlan.from(blade) == null, "Preview mutated input or failed");
        check(java.util.Objects.equals(previous, state.getSlashArtsKey()), "Preview replaced original SA");
        var result = schematic.applyUpgrade(blade, new ItemStack[0], true, ModularSlashBladeItem.BLADE_SLOT, player);
        check(ProgrammaticFusionPlan.from(result) != null && !schematic.isRelevant(result), "Fusion confirmation not durable");
        var saved = ItemStack.of(result.save(new net.minecraft.nbt.CompoundTag()));
        check(ProgrammaticFusionPlan.from(saved) != null, "Fusion lost on save/reload");
        var definition = se.mickelus.tetra.data.DataManager.instance.schematicData.getData(
                new ResourceLocation("tetra", "slashblade/basic_saya"));
        var replacement = new se.mickelus.tetra.module.schematic.ConfigSchematic(definition);
        var sameIngot = dev.bladetetra.item.NamedBladeIngotItem.create(NamedLegacyParts.fromStack(result).saya());
        sameIngot.setCount(64);
        var samePair = replacement.applyUpgrade(result, new ItemStack[]{sameIngot}, false, ModularSlashBladeItem.SAYA_SLOT, player);
        check(ProgrammaticFusionPlan.from(samePair) != null, "Unchanged pair lost attunement");
        var differentIngot = dev.bladetetra.item.NamedBladeIngotItem.create(NamedLegacyParts.fromStack(result).tsuba());
        differentIngot.setCount(64);
        var changed = replacement.applyUpgrade(result, new ItemStack[]{differentIngot}, false, ModularSlashBladeItem.SAYA_SLOT, player);
        check(ProgrammaticFusionPlan.from(changed) == null, "Changed source retained fusion");
        var restored = replacement.applyUpgrade(changed, new ItemStack[]{sameIngot}, false, ModularSlashBladeItem.SAYA_SLOT, player);
        check(ProgrammaticFusionPlan.from(restored) == null, "Restoring a source silently reactivated removed fusion");
        var old = blade.copy();
        old.getTag().remove("blade_tetra_fusion_confirmation_version");
        old.getTag().putString("blade_tetra_named_ability_owner", "programmatic:" + ProgrammaticFusionPlan.candidate(old).key());
        old.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(s -> {
            s.setSlashArtsKey(ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId());
            LegacyFusionHandler.sync(old, s);
        });
        check(ProgrammaticFusionPlan.from(old) != null, "Previously active fusion not migrated");
        var authored = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        fit(authored, "saya", LegacyImprintKind.BLACK_FOX); fit(authored, "tsuba", LegacyImprintKind.WHITE_FOX);
        check(ProgrammaticFusionPlan.candidate(authored) == null && !schematic.isRelevant(authored), "Authored fusion bypassed");
        check(player.level().getRecipeManager().byKey(new ResourceLocation("blade_tetra", "modular_slashblade")).isEmpty(),
                "Retired sword conversion recipe still loaded");
        check(player.level().getRecipeManager().byKey(new ResourceLocation("blade_tetra", "iron_katana")).isPresent(),
                "Wooden Puppet entry recipe missing");
        var recipe = player.level().getRecipeManager().byKey(new ResourceLocation("blade_tetra", "blade_guide"));
        boolean patchouliLoaded = ModList.get().isLoaded("patchouli");
        check(recipe.isPresent() == patchouliLoaded, "Optional book recipe condition failed");
        verifyStarterManual(player.serverLevel(), patchouliLoaded);
        dev.bladetetra.lore.SmithingLore.discover(player, "akatsuki");
        check(dev.bladetetra.lore.SmithingLore.knows(player, "akatsuki"), "Original clue lost");
        com.mojang.logging.LogUtils.getLogger().info("MANUAL_FUSION_SMOKE_PASS: preview, confirm, save, source replacement, old active migration, authored priority, optional recipe");
    }

    private static void verifyStarterManual(ServerLevel level, boolean patchouliLoaded) {
        var receipt = level.getServer().getAdvancements()
                .getAdvancement(ResourceLocation.fromNamespaceAndPath("blade_tetra", "manual/starter_manual"));
        check(receipt != null, "Starter manual advancement missing");
        var wire = new FakePlayer(level, new GameProfile(java.util.UUID.randomUUID(), "ManualWire"));
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(java.util.UUID.randomUUID(), "StarterManual"));
        player.connection = wire.connection;
        var login = new PlayerEvent.PlayerLoggedInEvent(player);
        var progress = player.getAdvancements().getOrStartProgress(receipt);
        var setting = GameplayConfig.GIVE_STARTER_MANUAL;
        boolean original = setting.get();
        try {
            setting.set(false);
            ManualDiscovery.login(login);
            check(player.getInventory().isEmpty() && !progress.isDone(), "Disabled starter manual consumed the receipt or gave an item");
            setting.set(true);
            if (patchouliLoaded) {
                for (int slot = 0; slot < player.getInventory().items.size(); slot++)
                    player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                ManualDiscovery.login(login);
                check(!progress.isDone(), "Full inventory consumed the receipt");
                player.getInventory().setItem(0, ItemStack.EMPTY);
            }
            ManualDiscovery.login(login);
            if (patchouliLoaded) {
                var manual = player.getInventory().getItem(0);
                check(manual.hasTag() && "blade_tetra:blade_guide".equals(manual.getTag().getString("patchouli:book"))
                        && progress.isDone(), "Login did not deliver the Bladecraft Manual and mark it received");
                player.getInventory().clearContent();
                ManualDiscovery.login(login);
                check(player.getInventory().isEmpty(), "Repeat login gave another starter manual");
            } else {
                check(player.getInventory().isEmpty() && !progress.isDone(), "Missing Patchouli consumed the receipt or gave an item");
            }
        } finally {
            setting.set(original);
        }
        com.mojang.logging.LogUtils.getLogger().info("STARTER_MANUAL_SMOKE_PASS: Patchouli loaded={}", patchouliLoaded);
    }

    static void fit(ItemStack stack, String part, LegacyImprintKind source) {
        var tag = stack.getOrCreateTag();
        String module = tag.getString("slashblade/" + part);
        tag.putString(module + "_material", module + "/named_blade_ingot");
        NamedLegacyImprintStorage.putSource(tag, part, source.id());
        NamedLegacyImprintStorage.putSnapshot(stack, part, source);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private ManualFusionSmoke() {}
}
