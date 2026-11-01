package dev.bladetetra.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BladeStackStorageTest {
    @Test
    void readsFrozenVersionOneFilesAfterFutureEncoderChanges() throws Exception {
        try (var oldFile = getClass().getResourceAsStream("/storage/version-one-inventory.nbt");
             var originalFile = getClass().getResourceAsStream("/storage/awakened-inventory.nbt")) {
            assertNotNull(oldFile);
            assertNotNull(originalFile);
            ListTag stored = NbtIo.readCompressed(oldFile).getList("Inventory", Tag.TAG_COMPOUND);
            ListTag original = NbtIo.readCompressed(originalFile).getList("Inventory", Tag.TAG_COMPOUND);
            assertEquals(original.size(), stored.size());
            for (int i = 0; i < original.size(); i++) {
                assertEquals(original.getCompound(i), BladeStackStorage.decode(stored.getCompound(i)));
            }
        }
    }

    @Test
    void realSavedInventoryIsLosslessAndSmaller() throws Exception {
        ListTag items;
        try (var input = getClass().getResourceAsStream("/storage/awakened-inventory.nbt")) {
            assertNotNull(input);
            items = NbtIo.readCompressed(input).getList("Inventory", Tag.TAG_COMPOUND);
        }
        StringBuilder report = new StringBuilder("sample,before_bytes,after_bytes,reduction_percent,before_gzip,after_gzip\n");
        ListTag packedItems = new ListTag();
        int totalBefore = 0, totalAfter = 0;
        for (int i = 0; i < items.size(); i++) {
            CompoundTag original = items.getCompound(i);
            CompoundTag snapshot = original.copy();
            CompoundTag encoded = BladeStackStorage.encode(original);
            packedItems.add(encoded);
            assertEquals(snapshot, original, "encoding mutated the source item");
            assertEquals(original, BladeStackStorage.decode(encoded), "round trip sample " + i);
            assertEquals(encoded, BladeStackStorage.encode(encoded), "double encoding sample " + i);
            assertEquals(original, BladeStackStorage.decode(BladeStackStorage.decode(encoded)));
            int before = binary(original).length, after = binary(encoded).length;
            assertTrue(after < before, "no size reduction for real sample " + i);
            totalBefore += before;
            totalAfter += after;
            report.append(String.format(java.util.Locale.ROOT, "%d,%d,%d,%.1f,%d,%d%n",
                    i, before, after, 100.0 * (before - after) / before,
                    gzip(original), gzip(encoded)));
        }
        report.append(String.format(java.util.Locale.ROOT, "total,%d,%d,%.1f,,%n",
                totalBefore, totalAfter, 100.0 * (totalBefore - totalAfter) / totalBefore));
        Path output = Path.of("build/reports/nbt-storage-comparison.csv");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report);
        CompoundTag archive = new CompoundTag();
        archive.put("Inventory", packedItems);
        try (var stream = Files.newOutputStream(Path.of("build/reports/nbt-storage-optimized.nbt"))) {
            NbtIo.writeCompressed(archive, stream);
        }
    }

    @Test
    void divergentSnapshotsUnknownFieldsAndAllLegacyLayoutsSurvive() {
        for (int layout = 1; layout <= 3; layout++) {
            CompoundTag saved = blade();
            CompoundTag tag = new CompoundTag();
            tag.putString("foreign:custom_style", "keep me");
            CompoundTag state = new CompoundTag();
            state.putInt("killCount", 99);
            state.putLong("lastActionTime", 500);
            state.putInt("foreign:state", 8);
            CompoundTag nested = new CompoundTag();
            nested.putInt("old", 1);
            state.put("foreign:nested", nested);
            if ((layout & 1) != 0) tag.put("bladeState", state.copy());
            if ((layout & 2) != 0) {
                CompoundTag caps = new CompoundTag();
                caps.put("Parent", state.copy());
                caps.putString("foreign:capability", "keep this too");
                saved.put("ForgeCaps", caps);
            }
            if (layout == 3) {
                CompoundTag stale = tag.getCompound("bladeState");
                stale.putInt("killCount", 4);
                stale.remove("lastActionTime");
                CompoundTag different = new CompoundTag();
                different.putString("new", "snapshot");
                stale.put("foreign:nested", different);
            }
            tag.putInt("blade_tetra_selected_soul", 3);
            tag.putString("blade_tetra_future_unknown", "future data");
            tag.putString("slashblade/saya", "slashblade/legacy_saya");
            tag.putString("slashblade/legacy_saya_material", "legacy_saya/removed_addon/imprinted");
            saved.put("tag", tag);
            assertEquals(saved, BladeStackStorage.decode(BladeStackStorage.encode(saved)), "layout " + layout);
        }
    }

    @Test
    void neverChangesForeignItemsFutureVersionsOrAmbiguousModules() {
        CompoundTag foreign = blade();
        foreign.putString("id", "slashblade:slashblade");
        assertSame(foreign, BladeStackStorage.encode(foreign));
        CompoundTag future = blade();
        CompoundTag payload = new CompoundTag();
        payload.putByte("v", (byte) 99);
        future.put("tag", new CompoundTag());
        future.getCompound("tag").put(BladeStackStorage.KEY, payload);
        assertSame(future, BladeStackStorage.encode(future));
        assertSame(future, BladeStackStorage.decode(future));
        CompoundTag saved = blade();
        CompoundTag tag = new CompoundTag();
        tag.putString("slashblade/blade", "slashblade/shared");
        tag.putString("slashblade/saya", "slashblade/shared");
        tag.putString("slashblade/shared_material", "shared/odd/material");
        tag.putInt("slashblade/blade:settled", 8);
        saved.put("tag", tag);
        assertEquals(saved, BladeStackStorage.decode(BladeStackStorage.encode(saved)));
        assertEquals(blade(), BladeStackStorage.decode(BladeStackStorage.encode(blade())));
    }

    private static CompoundTag blade() {
        CompoundTag saved = new CompoundTag();
        saved.putString("id", BladeStackStorage.ITEM_ID);
        saved.putByte("Count", (byte) 1);
        return saved;
    }

    private static byte[] binary(CompoundTag tag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) { NbtIo.write(tag, output); }
        return bytes.toByteArray();
    }

    private static int gzip(CompoundTag tag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(bytes)) { output.write(binary(tag)); }
        return bytes.size();
    }
}
