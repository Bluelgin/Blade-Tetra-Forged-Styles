package dev.bladetetra.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * Lossless, item-local disk format. Gameplay, share tags and capabilities always
 * see the original schema. No registry ordinals, world database or compression
 * stream is involved. Version-one tables are frozen; a new layout needs a new
 * version and must keep its old decoder.
 */
public final class BladeStackStorage {
    public static final String ITEM_ID = "blade_tetra:modular_slashblade";
    public static final String KEY = "blade_tetra:storage";
    private static final int VERSION = 1;
    private static final String STATE = "bladeState";
    private static final String CAPS = "ForgeCaps";
    private static final String PARENT = "Parent";

    private static final List<String> STATE_KEYS = List.of(
            "isSealed", "translationKey", "maxDamage", "SummonedSwordColor",
            "lastActionTime", "_onClick", "fallDecreaseRate", "currentCombo",
            "proudSoul", "killCount", "RepairCounter", "Damage", "ComboRoot",
            "adjustXYZ", "ModelName", "isDefaultBewitched", "AttackAmplifier",
            "TargetEntity", "SpecialAttackType", "TextureName",
            "SummonedSwordColorInverse", "isBroken", "baseAttackModifier", "StandbyRenderType");
    private static final List<String> OWN_KEYS = List.of(
            "blade_tetra_selected_soul", "blade_tetra_awakened_v2_id",
            "blade_tetra_nbt_sage_modules", "blade_tetra_akatsuki_execution_cooldown",
            "blade_tetra_akatsuki_execution_id", "blade_tetra_sakura_fracture",
            "blade_tetra_nbt_sage_materials", "blade_tetra_style_mastery_mask",
            "blade_tetra_akatsuki_unlocked", "blade_tetra_awakened_v2_sakura_dispersed",
            "blade_tetra_module_schema", "blade_tetra_nbt_sage_inscription",
            "blade_tetra_legacy_was_broken", "blade_tetra_legacy_last_blade_material");
    private static final List<String> SLOTS = List.of(
            "blade", "tsuka", "tsuba", "saya", "habaki", "kashira", "fuller", "inscription");

    /** Returns a detached saved item; the live item and caller's input stay untouched. */
    public static CompoundTag encode(CompoundTag saved) {
        if (!ITEM_ID.equals(saved.getString("id"))) return saved;
        CompoundTag oldTag = saved.getCompound("tag");
        // A future version or another writer's data must never be overwritten.
        if (oldTag.contains(KEY)) return saved;
        CompoundTag result = saved.copy();
        CompoundTag tag = result.getCompound("tag");
        CompoundTag payload = new CompoundTag();
        payload.putByte("v", (byte) VERSION);
        payload.putByte("h", (byte) (saved.contains("tag", Tag.TAG_COMPOUND) ? 1 : 0));
        packState(result, tag, payload);

        CompoundTag data = new CompoundTag();
        for (int i = 0; i < OWN_KEYS.size(); i++) {
            String key = OWN_KEYS.get(i);
            if (tag.contains(key)) data.put(index(i), tag.get(key));
            tag.remove(key);
        }
        if (!data.isEmpty()) payload.put("d", data);
        packModules(tag, payload);
        tag.put(KEY, payload);
        result.put("tag", tag);
        return result;
    }

    /** Runs before ItemStack initializes capabilities. Legacy items are already expanded. */
    public static CompoundTag decode(CompoundTag saved) {
        if (!ITEM_ID.equals(saved.getString("id"))) return saved;
        CompoundTag oldTag = saved.getCompound("tag");
        CompoundTag payload = oldTag.getCompound(KEY);
        if (payload.getByte("v") != VERSION) return saved;
        CompoundTag result = saved.copy();
        CompoundTag tag = result.getCompound("tag");
        tag.remove(KEY);
        CompoundTag data = payload.getCompound("d");
        for (int i = 0; i < OWN_KEYS.size(); i++) {
            if (data.contains(index(i))) tag.put(OWN_KEYS.get(i), data.get(index(i)).copy());
        }
        unpackModules(tag, payload);
        unpackState(result, tag, payload);
        if (payload.getByte("h") == 0 && tag.isEmpty()) result.remove("tag");
        else result.put("tag", tag);
        return result;
    }

    private static void packState(CompoundTag saved, CompoundTag tag, CompoundTag payload) {
        CompoundTag caps = saved.getCompound(CAPS);
        boolean hasTag = tag.contains(STATE, Tag.TAG_COMPOUND);
        boolean hasCap = caps.contains(PARENT, Tag.TAG_COMPOUND);
        payload.putByte("f", (byte) ((hasTag ? 1 : 0) | (hasCap ? 2 : 0)));
        if (!hasTag && !hasCap) return;
        // Capability data is current; bladeState may be an older network snapshot.
        // Preserve their exact difference, rather than guessing from kill count/time.
        CompoundTag base = hasCap ? caps.getCompound(PARENT) : tag.getCompound(STATE);
        payload.put("s", packKeys(base));
        if (hasTag && hasCap) {
            CompoundTag snapshot = tag.getCompound(STATE);
            CompoundTag patch = new CompoundTag();
            ListTag removed = new ListTag();
            for (String key : snapshot.getAllKeys()) {
                if (!snapshot.get(key).equals(base.get(key))) patch.put(key, snapshot.get(key));
            }
            for (String key : base.getAllKeys()) {
                if (!snapshot.contains(key)) removed.add(StringTag.valueOf(key));
            }
            if (!patch.isEmpty()) payload.put("p", packKeys(patch));
            if (!removed.isEmpty()) payload.put("r", removed);
        }
        if (hasTag) tag.remove(STATE);
        if (hasCap) {
            caps.remove(PARENT);
            if (caps.isEmpty()) saved.remove(CAPS);
        }
    }

    private static void unpackState(CompoundTag saved, CompoundTag tag, CompoundTag payload) {
        int flags = payload.getByte("f");
        if ((flags & 3) == 0) return;
        CompoundTag state = unpackKeys(payload.getCompound("s"));
        if ((flags & 2) != 0) {
            CompoundTag caps = saved.getCompound(CAPS);
            caps.put(PARENT, state.copy());
            saved.put(CAPS, caps);
        }
        if ((flags & 1) != 0) {
            CompoundTag patch = unpackKeys(payload.getCompound("p"));
            for (String key : patch.getAllKeys()) state.put(key, patch.get(key).copy());
            for (Tag key : payload.getList("r", Tag.TAG_STRING)) state.remove(key.getAsString());
            tag.put(STATE, state);
        }
    }

    private static CompoundTag packKeys(CompoundTag source) {
        CompoundTag known = new CompoundTag();
        CompoundTag unknown = source.copy();
        for (int i = 0; i < STATE_KEYS.size(); i++) {
            String key = STATE_KEYS.get(i);
            if (source.contains(key)) known.put(index(i), source.get(key).copy());
            unknown.remove(key);
        }
        CompoundTag result = new CompoundTag();
        if (!known.isEmpty()) result.put("k", known);
        if (!unknown.isEmpty()) result.put("u", unknown);
        return result;
    }

    private static CompoundTag unpackKeys(CompoundTag packed) {
        CompoundTag result = packed.getCompound("u").copy();
        CompoundTag known = packed.getCompound("k");
        for (int i = 0; i < STATE_KEYS.size(); i++) {
            if (known.contains(index(i))) result.put(STATE_KEYS.get(i), known.get(index(i)).copy());
        }
        return result;
    }

    private static void packModules(CompoundTag tag, CompoundTag payload) {
        CompoundTag modules = new CompoundTag();
        for (int i = 0; i < SLOTS.size(); i++) {
            String slot = "slashblade/" + SLOTS.get(i);
            if (!tag.contains(slot, Tag.TAG_STRING)) continue;
            String module = tag.getString(slot);
            if (!module.startsWith("slashblade/")) continue;
            String name = module.substring("slashblade/".length());
            String materialKey = module + "_material";
            String material = tag.getString(materialKey);
            // Preserve unusual/third-party layouts verbatim instead of inferring them.
            if (!tag.contains(materialKey, Tag.TAG_STRING) || !material.startsWith(name + "/")) continue;
            // Two slots sharing a material key cannot both remove the same field.
            long references = SLOTS.stream().filter(s -> module.equals(tag.getString("slashblade/" + s))).count();
            if (references != 1) continue;
            CompoundTag part = new CompoundTag();
            part.putString("m", name);
            part.putString("a", material.substring(name.length() + 1));
            if (tag.contains(slot + ":settled")) {
                part.put("s", tag.get(slot + ":settled"));
                tag.remove(slot + ":settled");
            }
            tag.remove(slot);
            tag.remove(materialKey);
            modules.put(index(i), part);
        }
        if (!modules.isEmpty()) payload.put("m", modules);
    }

    private static void unpackModules(CompoundTag tag, CompoundTag payload) {
        CompoundTag modules = payload.getCompound("m");
        for (int i = 0; i < SLOTS.size(); i++) {
            if (!modules.contains(index(i), Tag.TAG_COMPOUND)) continue;
            CompoundTag part = modules.getCompound(index(i));
            String slot = "slashblade/" + SLOTS.get(i);
            String name = part.getString("m");
            tag.putString(slot, "slashblade/" + name);
            tag.putString("slashblade/" + name + "_material", name + "/" + part.getString("a"));
            if (part.contains("s")) tag.put(slot + ":settled", part.get("s").copy());
        }
    }

    private static String index(int index) { return Integer.toString(index, 36); }
    private BladeStackStorage() { }
}
