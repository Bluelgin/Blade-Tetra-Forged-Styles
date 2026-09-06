package dev.bladetetra.forging;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Compact selection masks and independent installation transforms. */
public record LegacyCalibrationProfile(
        float tsubaCenter,
        float tsubaRadius,
        float tsukaCenter,
        float tsukaRadius,
        PartTransform tsubaTransform,
        PartTransform tsukaTransform,
        PartTransform sayaTransform,
        PartTransform bladeTransform,
        boolean flipped,
        long revision) {
    public LegacyCalibrationProfile(float tc, float tr, float hc, float hr,
            PartTransform t, PartTransform h, PartTransform s, boolean flip, long rev) {
        this(tc, tr, hc, hr, t, h, s, IDENTITY, flip, rev);
    }
    public static final PartTransform IDENTITY = new PartTransform(1.0F, 0.0F, 0.0F, 0.0F);
    public static final LegacyCalibrationProfile DEFAULT = new LegacyCalibrationProfile(
            0.772F, 0.025F, 0.895F, 0.105F,
            IDENTITY, IDENTITY, IDENTITY, false, 0L);

    public LegacyCalibrationProfile normalized() {
        return new LegacyCalibrationProfile(
                safe(tsubaCenter, 0.50F, 0.98F, DEFAULT.tsubaCenter),
                safe(tsubaRadius, 0.012F, 0.22F, DEFAULT.tsubaRadius),
                safe(tsukaCenter, 0.50F, 1.0F, DEFAULT.tsukaCenter),
                safe(tsukaRadius, 0.025F, 0.30F, DEFAULT.tsukaRadius),
                tsubaTransform == null ? IDENTITY : tsubaTransform.normalized(),
                tsukaTransform == null ? IDENTITY : tsukaTransform.normalized(),
                sayaTransform == null ? IDENTITY : sayaTransform.normalized(),
                bladeTransform == null ? IDENTITY : new PartTransform(1, 0, 0, 0,
                        safe(bladeTransform.length(), .85F, 1.20F, 1),
                        safe(bladeTransform.width(), .85F, 1.20F, 1)),
                flipped,
                Math.max(0L, revision));
    }

    public LegacyCalibrationProfile withRevision(long value) {
        return new LegacyCalibrationProfile(tsubaCenter, tsubaRadius,
                tsukaCenter, tsukaRadius, tsubaTransform, tsukaTransform,
                sayaTransform, bladeTransform, flipped, value).normalized();
    }

    public CompoundTag write() {
        LegacyCalibrationProfile value = normalized();
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 4);
        tag.putLong("revision", value.revision);
        tag.putFloat("tsuba_center", value.tsubaCenter);
        tag.putFloat("tsuba_radius", value.tsubaRadius);
        tag.putFloat("tsuka_center", value.tsukaCenter);
        tag.putFloat("tsuka_radius", value.tsukaRadius);
        tag.put("tsuba_transform", value.tsubaTransform.write());
        tag.put("tsuka_transform", value.tsukaTransform.write());
        tag.put("saya_transform", value.sayaTransform.write());
        tag.put("blade_transform", value.bladeTransform.write());
        tag.putBoolean("flipped", value.flipped);
        return tag;
    }

    public static LegacyCalibrationProfile read(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return DEFAULT;
        float tsubaCenter;
        float tsubaRadius;
        float tsukaCenter;
        float tsukaRadius;
        if (!tag.contains("tsuba_center") && tag.contains("blade_end")) {
            float bladeEnd = tag.getFloat("blade_end");
            float guardEnd = tag.getFloat("guard_end");
            tsubaCenter = (bladeEnd + guardEnd) * .5F;
            tsubaRadius = Math.max(.012F, (guardEnd - bladeEnd) * .5F);
            tsukaCenter = (guardEnd + 1.0F) * .5F;
            tsukaRadius = Math.max(.025F, (1.0F - guardEnd) * .5F);
        } else {
            tsubaCenter = tag.contains("tsuba_center")
                    ? tag.getFloat("tsuba_center") : DEFAULT.tsubaCenter;
            tsubaRadius = tag.contains("tsuba_radius")
                    ? tag.getFloat("tsuba_radius") : DEFAULT.tsubaRadius;
            tsukaCenter = tag.contains("tsuka_center")
                    ? tag.getFloat("tsuka_center") : DEFAULT.tsukaCenter;
            tsukaRadius = tag.contains("tsuka_radius")
                    ? tag.getFloat("tsuka_radius") : DEFAULT.tsukaRadius;
        }
        PartTransform legacy = tag.contains("scale")
                ? new PartTransform(tag.getFloat("scale"), tag.getFloat("offset_x"),
                        tag.getFloat("offset_y"), 0.0F).normalized()
                : IDENTITY;
        return new LegacyCalibrationProfile(
                tsubaCenter, tsubaRadius, tsukaCenter, tsukaRadius,
                tag.contains("tsuba_transform")
                        ? PartTransform.read(tag.getCompound("tsuba_transform")) : legacy,
                tag.contains("tsuka_transform")
                        ? PartTransform.read(tag.getCompound("tsuka_transform")) : legacy,
                tag.contains("saya_transform")
                        ? PartTransform.read(tag.getCompound("saya_transform")) : legacy,
                tag.contains("blade_transform") ? PartTransform.read(tag.getCompound("blade_transform")) : IDENTITY,
                tag.getBoolean("flipped"), tag.getLong("revision")).normalized();
    }

    public float tsubaMin() { return Mth.clamp(tsubaCenter - tsubaRadius, 0.0F, 1.0F); }
    public float tsubaMax() { return Mth.clamp(tsubaCenter + tsubaRadius, 0.0F, 1.0F); }
    public float tsukaMin() { return Mth.clamp(tsukaCenter - tsukaRadius, 0.0F, 1.0F); }
    public float tsukaMax() { return Mth.clamp(tsukaCenter + tsukaRadius, 0.0F, 1.0F); }

    private static float safe(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
    }

    public record PartTransform(float scale, float offsetX, float offsetY, float rotation,
            float length, float width) {
        public PartTransform(float scale, float x, float y, float rotation) {
            this(scale, x, y, rotation, 1, 1);
        }
        public PartTransform normalized() {
            return new PartTransform(
                    safe(scale, .70F, 1.35F, 1),
                    safe(offsetX, -.30F, .30F, 0),
                    safe(offsetY, -.30F, .30F, 0),
                    safe(rotation, -30.0F, 30.0F, 0),
                    safe(length, .65F, 1.50F, 1), safe(width, .65F, 1.50F, 1));
        }

        CompoundTag write() {
            PartTransform value = normalized();
            CompoundTag tag = new CompoundTag();
            tag.putFloat("scale", value.scale);
            tag.putFloat("offset_x", value.offsetX);
            tag.putFloat("offset_y", value.offsetY);
            tag.putFloat("rotation", value.rotation);
            tag.putFloat("length", value.length);
            tag.putFloat("width", value.width);
            return tag;
        }

        static PartTransform read(CompoundTag tag) {
            return new PartTransform(
                    tag.contains("scale") ? tag.getFloat("scale") : 1.0F,
                    tag.getFloat("offset_x"), tag.getFloat("offset_y"),
                    tag.getFloat("rotation"),
                    tag.contains("length") ? tag.getFloat("length") : 1,
                    tag.contains("width") ? tag.getFloat("width") : 1).normalized();
        }
    }
}
