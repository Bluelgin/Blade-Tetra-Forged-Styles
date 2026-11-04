package dev.bladetetra.storage;

import com.mojang.logging.LogUtils;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opt-in, real AE2 AEItemKey NBT persistence test for 11 pre-upgrade saved blades.
 *
 * This intentionally does NOT assert that an ME drive/cell/terminal round trip has
 * occurred: AE2 key encoding and actual ME network storage are different boundaries.
 * Reflection keeps AE2 an optional runtime-only dev dependency, not a required mod.
 */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class AE2KeySmoke {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        String fixture = System.getProperty("blade_tetra.ae2KeySmokeInput");
        if (fixture == null) return;
        try {
            check(ModList.get().isLoaded("ae2"), "AE2 was not loaded");
            Class<?> keyType = Class.forName("appeng.api.stacks.AEItemKey");
            Method of = keyType.getMethod("of", ItemStack.class);
            Method toTag = keyType.getMethod("toTag");
            Method fromTag = keyType.getMethod("fromTag", CompoundTag.class);
            Method toStack = keyType.getMethod("toStack");
            Method matches = keyType.getMethod("matches", ItemStack.class);
            int count = 0;
            try (var input = Files.newInputStream(Path.of(fixture))) {
                var items = NbtIo.readCompressed(input).getList("Inventory", Tag.TAG_COMPOUND);
                check(!items.isEmpty(), "missing saved-blade fixtures");
                for (int i = 0; i < items.size(); i++) {
                    ItemStack original = ItemStack.of(items.getCompound(i));
                    check(!original.isEmpty(), "failed to load saved blade " + i);
                    var state = original.getCapability(ModularSlashBladeItem.BLADESTATE)
                            .orElseThrow(IllegalStateException::new).serializeNBT();
                    var share = original.getShareTag().copy();
                    Object key = of.invoke(null, original);
                    check(key != null, "AE2 rejected item key " + i);
                    CompoundTag keyNbt = (CompoundTag) toTag.invoke(key);
                    Object decodedKey = fromTag.invoke(null, keyNbt);
                    check(decodedKey != null && key.equals(decodedKey), "AE2 key NBT changed " + i);
                    ItemStack restored = (ItemStack) toStack.invoke(decodedKey);
                    check(!restored.isEmpty() && restored.is(original.getItem()), "AE2 item identity changed " + i);
                    check(Boolean.TRUE.equals(matches.invoke(decodedKey, original)), "AE2 key mismatches original " + i);
                    check(Boolean.TRUE.equals(matches.invoke(key, restored)), "AE2 reconstructed stack mismatch " + i);
                    check(restored.getShareTag().equals(share), "AE2 share tag changed " + i);
                    check(restored.getCapability(ModularSlashBladeItem.BLADESTATE)
                            .orElseThrow(IllegalStateException::new).serializeNBT().equals(state),
                            "AE2 blade capability changed " + i);
                    // Ensure AE2's reconstructed stack still participates in compact disk saves.
                    CompoundTag saved = new CompoundTag();
                    restored.save(saved);
                    check(saved.getCompound("tag").contains(BladeStackStorage.KEY),
                            "AE2 reconstructed blade lost compact-save hook " + i);
                    count++;
                }
            }
            LogUtils.getLogger().info("AE2_KEY_NBT_PASS: {} saved blade fixtures survived real AE2 AEItemKey toTag/fromTag/toStack, capabilities and compact saves; ME network NOT tested", count);
        } catch (Throwable failure) {
            LogUtils.getLogger().error("AE2_KEY_NBT_FAIL", failure);
            throw new IllegalStateException("AE2 item-key persistence regression failed", failure);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new IllegalStateException(reason);
    }

    private AE2KeySmoke() {}
}
