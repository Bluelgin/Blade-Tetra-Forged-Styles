package dev.bladetetra.customstyle;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.blocks.scroll.ScrollData;
import se.mickelus.tetra.blocks.scroll.ScrollItem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Native Tetra scroll payload. ScrollTile only retains ScrollData's codec fields:
 * extra ItemStack tags disappear when the scroll is unfolded. The validated
 * authored spec therefore lives in the optional details field, NOT outside it.
 */
public final class CustomStyleScrolls {
    private static final String PREFIX = "BTCS1:";
    private static final String KEY = "blade_tetra.custom_style";
    public static final List<ResourceLocation> UNLOCKS = List.of(
            new ResourceLocation("tetra", "slashblade/custom_style_short"),
            new ResourceLocation("tetra", "slashblade/custom_style_standard"),
            new ResourceLocation("tetra", "slashblade/custom_style_long"));
    public static final ResourceLocation CRAFTING_EFFECT =
            new ResourceLocation("tetra", "blade_tetra/custom_style_craft");
    private static final int MAX_PAYLOAD_LENGTH = 2048;

    public static ItemStack create(CustomStyleSpec spec) {
        ItemStack stack = new ItemStack(ScrollItem.instance);
        String payload = encodeDetails(spec);
        new ScrollData(KEY, Optional.of(payload), true,
                0xE4D9C3, 0x704259, List.of(3, 10, 5, 12),
                UNLOCKS, List.of(CRAFTING_EFFECT)).write(stack);
        return stack;
    }

    public static CustomStyleSpec read(ItemStack stack) {
        return read(ScrollData.read(stack));
    }

    public static CustomStyleSpec read(ScrollData data) {
        if (data == null || !KEY.equals(data.key) || data.details == null
                || !data.schematics.containsAll(UNLOCKS)
                || !data.craftingEffects.contains(CRAFTING_EFFECT)) {
            return null;
        }
        return decodeDetails(data.details);
    }

    static String encodeDetails(CustomStyleSpec spec) {
        byte[] bytes;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DataOutputStream writer = new DataOutputStream(output)) {
            writer.writeInt(CustomStyleSpec.VERSION);
            writer.writeUTF(spec.name());
            writer.writeInt(spec.actions().size());
            for (ResourceLocation action : spec.actions()) writer.writeUTF(action.toString());
            writer.flush();
            bytes = output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize style", e);
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static CustomStyleSpec decodeDetails(String details) {
        if (!details.startsWith(PREFIX) || details.length() > MAX_PAYLOAD_LENGTH) return null;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(details.substring(PREFIX.length()));
            try (DataInputStream reader = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (reader.readInt() != CustomStyleSpec.VERSION) return null;
                String name = reader.readUTF();
                int count = reader.readInt();
                if (count < 1 || count > CustomStyleSpec.MAX_ACTIONS) return null;
                java.util.ArrayList<ResourceLocation> actions = new java.util.ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    String raw = reader.readUTF();
                    if (raw.length() > 128) return null;
                    ResourceLocation id = ResourceLocation.tryParse(raw);
                    if (id == null) return null;
                    actions.add(id);
                }
                if (reader.available() != 0) return null;
                return new CustomStyleSpec(name, actions);
            }
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private CustomStyleScrolls() {}
}
