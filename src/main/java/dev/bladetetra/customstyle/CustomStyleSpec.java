package dev.bladetetra.customstyle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Versioned, bounded player-authored ordinary-combo sequence.
 * A spec is independent of blade length and of the author's inventory.
 */
public record CustomStyleSpec(String name, List<ResourceLocation> actions) {
    public static final int VERSION = 1;
    public static final int MAX_ACTIONS = 6;
    public static final int MAX_NAME_LENGTH = 48;
    public static final String STACK_KEY = "blade_tetra_custom_style";

    public CustomStyleSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(actions, "actions");
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH
                || actions.isEmpty() || actions.size() > MAX_ACTIONS
                || actions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid custom style");
        }
        actions = List.copyOf(actions);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putString("name", name);
        ListTag list = new ListTag();
        actions.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("actions", list);
        return tag;
    }

    public static CustomStyleSpec decode(CompoundTag tag) {
        if (tag == null || tag.getInt("version") != VERSION
                || !tag.contains("actions", Tag.TAG_LIST)
                || !tag.contains("name", Tag.TAG_STRING)) {
            return null;
        }
        String name = tag.getString("name");
        ListTag list = tag.getList("actions", Tag.TAG_STRING);
        if (list.isEmpty() || list.size() > MAX_ACTIONS
                || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            return null;
        }
        List<ResourceLocation> actions = new ArrayList<>(list.size());
        for (Tag entry : list) {
            String raw = entry.getAsString();
            if (raw.length() > 128) return null;
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) return null;
            actions.add(id);
        }
        return new CustomStyleSpec(name, actions);
    }
}
