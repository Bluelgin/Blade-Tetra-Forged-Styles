package dev.bladetetra.storage;

import com.mojang.logging.LogUtils;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.List;
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
            // Real AE2 64k item storage cell: insert 11 distinct NBT-bearing
            // blades, persist the cell ItemStack, reload, and extract each key.
            Class<?> cellsType = Class.forName("appeng.api.storage.StorageCells");
            Class<?> saveProviderType = Class.forName("appeng.api.storage.cells.ISaveProvider");
            Class<?> cellType = Class.forName("appeng.api.storage.cells.StorageCell");
            Class<?> aeKeyType = Class.forName("appeng.api.stacks.AEKey");
            Class<?> meStorageType = Class.forName("appeng.api.storage.MEStorage");
            Class<?> actionType = Class.forName("appeng.api.config.Actionable");
            Class<?> sourceType = Class.forName("appeng.api.networking.security.IActionSource");
            Method getCell = cellsType.getMethod("getCellInventory", ItemStack.class, saveProviderType);
            Method insert = meStorageType.getMethod("insert", aeKeyType, long.class, actionType, sourceType);
            Method extract = meStorageType.getMethod("extract", aeKeyType, long.class, actionType, sourceType);
            Method persist = cellType.getMethod("persist");
            Object mode = actionType.getField("MODULATE").get(null);
            Object actionSource = sourceType.getMethod("empty").invoke(null);
            var nativeCellItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2", "item_storage_cell_64k"));
            check(nativeCellItem != null, "AE2 64k storage cell missing");
            ItemStack cellStack = new ItemStack(nativeCellItem);
            Object cellStorage = getCell.invoke(null, cellStack, null);
            check(cellStorage != null, "AE2 did not initialize a native cell");
            List<Object> insertedKeys = new ArrayList<>();
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
                    check(((Number) insert.invoke(cellStorage, decodedKey, 1L, mode, actionSource)).longValue() == 1L,
                            "AE2 64k cell rejected blade " + i);
                    insertedKeys.add(decodedKey);
                    count++;
                }
            }
            LogUtils.getLogger().info("AE2_KEY_NBT_PASS: {} saved blade fixtures survived AEItemKey NBT, capabilities and compact saves", count);
            persist.invoke(cellStorage);
            CompoundTag cellData = new CompoundTag();
            cellStack.save(cellData);
            ItemStack restoredCell = ItemStack.of(cellData);
            Object loadedStorage = getCell.invoke(null, restoredCell, null);
            check(loadedStorage != null, "AE2 did not reload saved 64k cell");
            for (int i = 0; i < insertedKeys.size(); i++) {
                check(((Number) extract.invoke(loadedStorage, insertedKeys.get(i), 1L, mode, actionSource)).longValue() == 1L,
                        "AE2 cell lost blade NBT/identity after save+reload " + i);
            }
            persist.invoke(loadedStorage);
            ItemStack emptyReload = ItemStack.of(restoredCell.save(new CompoundTag()));
            Object emptyStorage = getCell.invoke(null, emptyReload, null);
            check(emptyStorage != null, "AE2 failed to reload emptied cell");
            for (int i = 0; i < insertedKeys.size(); i++) {
                check(((Number) extract.invoke(emptyStorage, insertedKeys.get(i), 1L, mode, actionSource)).longValue() == 0L,
                        "AE2 cell retained duplicate blade after extraction " + i);
            }
            LogUtils.getLogger().info("AE2_CELL_NBT_PASS: {} different blade keys survived a real AE2 64k item storage cell insert / persist / ItemStack reload / extract cycle; live ME network NOT tested", count);
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
