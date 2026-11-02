package dev.bladetetra.storage;

import com.mojang.logging.LogUtils;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in real Forge lifecycle check, in an isolated world; never touches a player's save. */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class BladeStorageSmoke {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) throws Exception {
        String inputPath = System.getProperty("blade_tetra.storageSmokeInput");
        if (inputPath == null) return;
        try (var input = Files.newInputStream(Path.of(inputPath))) {
            var fixtures = NbtIo.readCompressed(input).getList("Inventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < fixtures.size(); i++) {
                ItemStack original = ItemStack.of(fixtures.getCompound(i));
                check(!original.isEmpty(), "legacy item loaded " + i);
                int durability = original.getMaxDamage();
                CompoundTag shared = original.getShareTag().copy();
                CompoundTag state = original.getCapability(ModularSlashBladeItem.BLADESTATE)
                        .orElseThrow(IllegalStateException::new).serializeNBT();
                CompoundTag saved = new CompoundTag();
                original.save(saved); // Also exercise callers ignoring the return value.
                check(saved.getCompound("tag").contains(BladeStackStorage.KEY), "save mixin active " + i);
                check(!original.getTag().contains(BladeStackStorage.KEY), "live tag remains standard " + i);
                verify(ItemStack.of(saved), shared, state, durability, "disk round trip " + i);
                verify(original.copy(), shared, state, durability, "copy " + i);
                ItemStack received = new ItemStack(ModItems.MODULAR_SLASHBLADE.get());
                received.readShareTag(shared.copy());
                verify(received, shared, state, durability, "share tag round trip " + i);
                SimpleContainer chest = new SimpleContainer(1);
                chest.setItem(0, original.copy());
                SimpleContainer restoredChest = new SimpleContainer(1);
                restoredChest.fromTag(chest.createTag());
                verify(restoredChest.getItem(0), shared, state, durability, "container round trip " + i);
            }
            LogUtils.getLogger().info("BLADE_STORAGE_SMOKE_PASS: {} real saved blades; actual save/of mixins, capabilities, durability, copy, share tags and container round trips", fixtures.size());
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void verify(ItemStack stack, CompoundTag tag, CompoundTag state, int durability, String label) {
        check(stack.getMaxDamage() == durability, label + " durability");
        check(stack.getShareTag().equals(tag), label + " tag");
        check(stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                .orElseThrow(IllegalStateException::new).serializeNBT().equals(state), label + " capability");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("Blade storage regression: " + label);
    }

    private BladeStorageSmoke() { }
}
