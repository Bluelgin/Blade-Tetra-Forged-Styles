package dev.bladetetra.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.*;

/** Built-in Mikage rig, overridable by resource packs. UVs survive uniform model scaling. */
public class MikageTailoredModel<T extends LivingEntity> extends PlayerModel<T> {
    public static final ResourceLocation TEXTURE = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/entity/mikage_tailored.png");
    private static final ResourceLocation RIG = new ResourceLocation(BladeTetra.MOD_ID,
            "models/entity/mikage_tailored.json");
    private final List<Bone> roots = new ArrayList<>();
    private final Map<String, Bone> named = new HashMap<>();
    private final List<Bone> bones = new ArrayList<>();
    private float scale, age, walk;
    private float swordElbow, swordWrist;
    private Map<String, Vector3f> nativePose = Map.of();
    public void applyNativeCombo(Map<String, Vector3f> pose) { nativePose = pose; }
    public void applySwordRide() {
        leftLeg.xRot = -.28F; leftLeg.zRot = -.12F;
        rightLeg.xRot = .22F; rightLeg.zRot = .14F;
        leftPants.copyFrom(leftLeg); rightPants.copyFrom(rightLeg);
    }

    public void setSwordJoints(float elbow, float wrist) {
        swordElbow = elbow;
        swordWrist = wrist;
    }

    public void applySwordPose(MikageSingleSwordPose.Pose pose) {
        body.xRot = pose.lean(); body.yRot = pose.turn(); body.zRot = 0;
        rightArm.xRot = pose.armX(); rightArm.yRot = pose.armY(); rightArm.zRot = pose.armZ();
        leftArm.xRot = pose.leftX(); leftArm.yRot = pose.leftY(); leftArm.zRot = pose.leftZ();
        setSwordJoints(pose.elbow(), pose.wrist());
        hat.copyFrom(head); jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm); rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg); rightPants.copyFrom(rightLeg);
    }

    public MikageTailoredModel(ModelPart vanillaRoot) {
        super(vanillaRoot, true);
        var resources = Minecraft.getInstance().getResourceManager();
        if (resources.getResource(RIG).isEmpty() || resources.getResource(TEXTURE).isEmpty()) return;
        try (Reader reader = resources.getResource(RIG).orElseThrow().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unknown rig version");
            if (json.get("textureWidth").getAsInt() != 1024 || json.get("textureHeight").getAsInt() != 1024)
                throw new IllegalArgumentException("Expected 1024-pixel atlas");
            scale = json.get("scale").getAsFloat();
            if (!Float.isFinite(scale) || scale <= 0 || scale > 2) throw new IllegalArgumentException("Rig scale");
            for (var entry : json.getAsJsonArray("bones")) roots.add(new Bone(entry.getAsJsonObject(), null));
            for (String required : List.of("AllBody", "UpperBody", "AllHead", "LeftArm", "RightArm",
                    "LeftLeg", "RightLeg", "LeftHand", "RightHand"))
                if (!named.containsKey(required)) throw new IllegalArgumentException("Missing bone: " + required);
        } catch (Exception exception) {
            roots.clear(); named.clear(); bones.clear();
            org.slf4j.LoggerFactory.getLogger(MikageTailoredModel.class)
                    .warn("Could not load Mikage rig; using original model", exception);
        }
    }

    public boolean hasTailoredRig() { return !roots.isEmpty(); }

    @Override
    public void setupAnim(T entity, float swing, float amount, float ticks, float yaw, float pitch) {
        super.setupAnim(entity, swing, amount, ticks, yaw, pitch);
        age = ticks; walk = amount; nativePose = Map.of();
    }

    private void animate() {
        for (Bone bone : bones) {
            bone.part.xRot = -bone.rotation[0] * Mth.DEG_TO_RAD;
            bone.part.yRot = -bone.rotation[1] * Mth.DEG_TO_RAD;
            bone.part.zRot = bone.rotation[2] * Mth.DEG_TO_RAD;
        }
        if (!nativePose.isEmpty()) nativePose.forEach((name, v) ->
                rotate(name, -v.x * Mth.DEG_TO_RAD, -v.y * Mth.DEG_TO_RAD, v.z * Mth.DEG_TO_RAD));
        else {
        rotate("AllBody", 0, body.yRot, 0);
        rotate("UpperBody", body.xRot + Mth.sin(age * .08F) * .012F, 0, body.zRot);
        rotate("AllHead", head.xRot, head.yRot, head.zRot);
        rotate("RightArm", rightArm.xRot, rightArm.yRot, rightArm.zRot);
        rotate("LeftArm", leftArm.xRot, leftArm.yRot, leftArm.zRot);
        rotate("RightForeArm", swordElbow, 0, 0);
        rotate("RightHand", 0, swordWrist, 0);
        }
        rotate("RightLeg", rightLeg.xRot, rightLeg.yRot, rightLeg.zRot);
        rotate("LeftLeg", leftLeg.xRot, leftLeg.yRot, leftLeg.zRot);
        float sway = Mth.sin(age * .09F) * (.025F + walk * .06F);
        rotate("LongHair", sway + body.xRot * .12F, -body.yRot * .12F, sway * .55F);
        rotate("BaseHair", sway * .3F, 0, 0);
        rotate("clothe", -body.xRot * .12F, 0, sway * .25F);
    }

    private void rotate(String name, float x, float y, float z) {
        Bone bone = named.get(name);
        if (bone != null) { bone.part.xRot += x; bone.part.yRot += y; bone.part.zRot += z; }
    }

    private void modelSpace(PoseStack pose) {
        pose.translate(0, 1.5, 0);
        pose.scale(scale, scale, scale);
    }

    /** Applies the same parent chain as the body, so held items cannot float independently. */
    public void translateToSocket(String name, PoseStack pose) {
        animate();
        modelSpace(pose);
        Bone bone = named.get(name);
        if (bone != null) bone.transform(pose);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay,
            float red, float green, float blue, float alpha) {
        if (!hasTailoredRig()) { super.renderToBuffer(pose, vertices, light, overlay, red, green, blue, alpha); return; }
        animate();
        pose.pushPose(); modelSpace(pose);
        for (Bone root : roots) root.render(pose, vertices, light, overlay, red, green, blue, alpha);
        pose.popPose();
    }

    private static float[] vec(JsonObject json, String key) {
        JsonArray a = json.getAsJsonArray(key);
        float[] value = { a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat() };
        for (float v : value) if (!Float.isFinite(v) || Math.abs(v) > 2048)
            throw new IllegalArgumentException("Invalid rig vector: " + key);
        return value;
    }

    private final class Bone {
        final Bone parent;
        final float[] origin, rotation;
        final ModelPart part = new ModelPart(List.of(), Map.of());
        final List<Bone> children = new ArrayList<>();
        final List<RigCube> cubes = new ArrayList<>();
        Bone(JsonObject json, Bone parent) {
            if (bones.size() >= 1024) throw new IllegalArgumentException("Too many bones");
            int depth = 0;
            for (Bone b = parent; b != null; b = b.parent)
                if (++depth > 32) throw new IllegalArgumentException("Rig hierarchy too deep");
            this.parent = parent; origin = vec(json, "origin"); rotation = vec(json, "rotation");
            float[] p = parent == null ? new float[3] : parent.origin;
            part.setPos(-(origin[0] - p[0]), -(origin[1] - p[1]), origin[2] - p[2]);
            named.put(json.get("name").getAsString(), this);
            bones.add(this);
            if (json.getAsJsonArray("cubes").size() > 2048) throw new IllegalArgumentException("Too many cubes");
            for (var c : json.getAsJsonArray("cubes")) cubes.add(new RigCube(c.getAsJsonObject(), origin));
            for (var c : json.getAsJsonArray("children")) children.add(new Bone(c.getAsJsonObject(), this));
        }
        void transform(PoseStack pose) { if (parent != null) parent.transform(pose); part.translateAndRotate(pose); }
        void render(PoseStack pose, VertexConsumer v, int light, int overlay, float r, float g, float b, float a) {
            pose.pushPose(); part.translateAndRotate(pose);
            for (RigCube cube : cubes) cube.render(pose, v, light, overlay, r, g, b, a);
            for (Bone child : children) child.render(pose, v, light, overlay, r, g, b, a);
            pose.popPose();
        }
    }

    private static final class RigCube {
        private final ModelPart transform = new ModelPart(List.of(), Map.of());
        private final float[][][] corners;
        private final float[][][] tex = new float[6][][];
        private static final String[] KEYS = {"north", "south", "east", "west", "up", "down"};
        private static final float[][] NORMALS = {{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
        RigCube(JsonObject cube, float[] boneOrigin) {
        float[] from = vec(cube, "from"), to = vec(cube, "to"), pivot = vec(cube, "origin"), rot = vec(cube, "rotation");
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
        if (!Float.isFinite(inflate) || Math.abs(inflate) > 64)
            throw new IllegalArgumentException("Invalid cube inflation");
        for (int axis = 0; axis < 3; axis++) {
            float center = (from[axis] + to[axis]) / 2;
            float half = Math.max(0, (to[axis] - from[axis]) / 2 + inflate);
            from[axis] = center - half; to[axis] = center + half;
        }
        transform.setPos(-(pivot[0]-boneOrigin[0]), -(pivot[1]-boneOrigin[1]), pivot[2]-boneOrigin[2]);
        transform.xRot = -rot[0]*Mth.DEG_TO_RAD;
        transform.yRot = -rot[1]*Mth.DEG_TO_RAD;
        transform.zRot = rot[2]*Mth.DEG_TO_RAD;
        float x = from[0], X = to[0], y = from[1], Y = to[1], z = from[2], Z = to[2];
        corners = new float[][][] {
            {{X,Y,z},{x,Y,z},{x,y,z},{X,y,z}},
            {{x,Y,Z},{X,Y,Z},{X,y,Z},{x,y,Z}},
            {{X,Y,Z},{X,Y,z},{X,y,z},{X,y,Z}},
            {{x,Y,z},{x,Y,Z},{x,y,Z},{x,y,z}},
            {{x,Y,z},{X,Y,z},{X,Y,Z},{x,Y,Z}},
            {{x,y,Z},{X,y,Z},{X,y,z},{x,y,z}}
        };
        for (float[][] face : corners) for (float[] c : face) {
            c[0] = -(c[0]-pivot[0])/16; c[1] = -(c[1]-pivot[1])/16; c[2] = (c[2]-pivot[2])/16;
        }
        JsonObject faces = cube.getAsJsonObject("faces");
        for (int side = 0; side < 6; side++) {
            JsonObject face = faces.getAsJsonObject(KEYS[side]);
            if (face == null || face.get("texture") == null || face.get("texture").isJsonNull()) continue;
            JsonArray uv = face.getAsJsonArray("uv");
            float u1 = uv.get(0).getAsFloat()/1024, v1 = uv.get(1).getAsFloat()/1024;
            float u2 = uv.get(2).getAsFloat()/1024, v2 = uv.get(3).getAsFloat()/1024;
            tex[side] = new float[][] {{u1,v1},{u2,v1},{u2,v2},{u1,v2}};
        }
        }
        void render(PoseStack pose, VertexConsumer v, int light, int overlay, float r, float g, float b, float alpha) {
            pose.pushPose(); transform.translateAndRotate(pose);
            Vector3f n = new Vector3f();
            for (int side = 0; side < 6; side++) {
            if (tex[side] == null) continue;
            n.set(NORMALS[side]).mul(pose.last().normal());
            for (int i = 0; i < 4; i++) {
                float[] c = corners[side][i];
                v.vertex(pose.last().pose(), c[0], c[1], c[2])
                    .color(r,g,b,alpha).uv(tex[side][i][0],tex[side][i][1]).overlayCoords(overlay).uv2(light)
                    .normal(n.x,n.y,n.z).endVertex();
            }
        }
        pose.popPose();
        }
    }
}
