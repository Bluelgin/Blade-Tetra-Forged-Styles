package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bladetetra.client.LegacyModelPartRenderer.Part;
import dev.bladetetra.client.LegacySourceMesh.Component;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Builds complete replacements before taking ownership of a native draw. */
final class NamedBladeRenderPlan {
    private record MountedKey(Object sourceIdentity, Component part,
                              WavefrontObject target, String state) {}
    record Mounted(LegacyRenderSources.Views views, LegacyMeshFrame.Mount mount) {}
    private record RemainingKey(Object source, String state, int step) {}
    private record StockKey(WavefrontObject model, Component part, String state) {}
    private record Draw(Component part, LegacyRenderSources.Views views, LegacyMeshFrame.Mount mount,
                        ResourceLocation texture, ResourceLocation materialGlow) {}

    private static final LegacyMeshCache<MountedKey, Mounted> MOUNTS =
            new LegacyMeshCache<>(480, 180_000, value -> value.views.triangles());
    private static final LegacyMeshCache<RemainingKey, LegacyRenderSources.Views> REMAINING =
            new LegacyMeshCache<>(64, 40_000, LegacyRenderSources.Views::triangles);
    private static final LegacyMeshCache<StockKey, WavefrontObject> STOCK =
            new LegacyMeshCache<>(320, 100_000, LegacyRenderSources::triangleCount);

    static boolean render(RenderOverrideEvent event, NamedLegacyParts parts, LegacyImprintKind blade,
            ResourceLocation ordinary, ResourceLocation emissive, String state, boolean nativeLuminous) {
        String nativeTarget = event.getOriginalTarget();
        List<Component> expected = switch (state) {
            case "sheath" -> List.of(Component.SAYA);
            case "blade_fragment" -> List.of(Component.BLADE);
            case "item_blade" -> List.of(Component.BLADE, Component.HILT, Component.SAYA);
            default -> List.of(Component.BLADE, Component.HILT);
        };
        float fraction = SenbonzakuraDispersedRender.bladeFraction(event.getStack(), state);
        List<Draw> plan = new ArrayList<>(expected.size());
        boolean inherited = false;
        for (Component part : expected) {
            if (fraction <= .001F && part == Component.BLADE) continue;
            LegacyImprintKind kind = switch (part) {
                case BLADE -> blade;
                case HILT -> parts.tsuba();
                case SAYA -> parts.saya();
            };
            var source = kind == null ? null : LegacyRenderSources.resolve(kind);
            var mounted = source == null ? null : mounted(source, part,
                    state.startsWith("item_") ? event.getModel() : event.getOriginalModel(), state);
            if (mounted != null && part == Component.BLADE && fraction < .999F) {
                var visible = remaining(source, state, fraction);
                mounted = visible == null ? null : new Mounted(visible, mounted.mount);
            }
            if (mounted != null) {
                inherited = true;
                if (!nativeLuminous) plan.add(new Draw(part, mounted.views, mounted.mount, kind.texture(), null));
                continue;
            }
            var fallback = stock(event.getModel(), part, state);
            if (fallback == null) return false;
            if (nativeLuminous) fallback = stock(event.getModel(), part, nativeTarget);
            if (fallback != null) plan.add(new Draw(part, new LegacyRenderSources.Views(fallback, null, 0),
                    null, ordinary, nativeLuminous ? null : emissive));
        }
        if (!inherited && (nativeLuminous || !state.equals("item_bladens")
                || LegacyModelPartRenderer.hasFaces(event.getModel(), state))) return false;
        event.setCanceled(true);
        for (Draw draw : plan) {
            Runnable pass = () -> draw(event, nativeTarget, draw);
            // Both inherited and modular hilt slices already own their geometry.
            if (draw.part != Component.BLADE || draw.mount != null) SenbonzakuraDispersedRender.retainedPart(pass);
            else pass.run();
        }
        return true;
    }

    static Mounted mounted(LegacyRenderSources.Source source, Component part, WavefrontObject target, String state) {
        if (source.mesh() == null) return null;
        return MOUNTS.get(new MountedKey(source.identity(), part, target, state), key -> {
            var displayed = LegacyRenderSources.geometry(source, key.part, key.state);
            if (displayed.empty()) return null;
            var views = LegacyRenderSources.views(source, key.part, key.state);
            if (views.empty() || (!displayed.base().isEmpty() && views.base() == null)
                    || (!displayed.luminous().isEmpty() && views.luminous() == null)) return null;
            var healthy = LegacyRenderSources.geometry(source, key.part, LegacyRenderSources.worldGroup(key.part));
            var blade = LegacyRenderSources.geometry(source, Component.BLADE, "blade");
            var mount = LegacyBladeMounts.fit(source.mesh(), key.part, healthy, blade, displayed, key.target, key.state);
            return mount == null ? null : new Mounted(views, mount);
        });
    }

    private static LegacyRenderSources.Views remaining(LegacyRenderSources.Source source, String state,
            float fraction) {
        int step = Math.max(1, Math.min(16, Math.round(fraction * 16)));
        return REMAINING.get(new RemainingKey(source.identity(), state, step), key -> {
            var mesh = source.mesh();
            var full = LegacyRenderSources.geometry(source, Component.BLADE, state);
            var span = mesh.frame.span(full.reference());
            if (!span.valid()) return null;
            float cutoff = span.at(1 - key.step / 16F);
            var base = LegacyRenderSources.view(state, mesh.clip(full.base(), cutoff, Float.POSITIVE_INFINITY));
            var glow = LegacyRenderSources.view(state + "_luminous", mesh.clip(full.luminous(), cutoff, Float.POSITIVE_INFINITY));
            var views = new LegacyRenderSources.Views(base, glow,
                    LegacyRenderSources.triangleCount(base) + LegacyRenderSources.triangleCount(glow));
            return views.empty() ? null : views;
        });
    }

    static void drawMounted(RenderOverrideEvent event, String state, Component part,
            LegacyImprintKind source, Mounted mounted) {
        SenbonzakuraDispersedRender.retainedPart(() -> draw(event, state,
                new Draw(part, mounted.views, mounted.mount, source.texture(), null)));
    }

    private static WavefrontObject stock(WavefrontObject model, Component part, String state) {
        return STOCK.get(new StockKey(model, part, state), key -> LegacyRenderSources.view(key.state,
                LegacyModelPartRenderer.modularFaces(key.model, key.state, stockPart(key.part))));
    }

    static void preview(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffer,
            LegacyImprintKind kind, dev.bladetetra.forging.LegacyCalibrationProfile profile, String state) {
        var target = mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager.getInstance().getModel(
                new ResourceLocation("blade_tetra", "model/modular/alpha9/katana_basic_simple_stable.obj"));
        var source = LegacyRenderSources.preview(kind, profile);
        var components = state.equals("sheath") ? List.of(Component.SAYA) : List.of(Component.BLADE, Component.HILT);
        for (var part : components) {
            Mounted mounted = mounted(source, part, target, state);
            poses.pushPose();
            try {
                BladeRenderState.resetCol();
                var stack = net.minecraft.world.item.ItemStack.EMPTY;
                int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
                if (mounted == null) {
                    var ordinary = stock(target, part, state);
                    if (ordinary != null) {
                        BladeRenderState.renderOverrided(stack, ordinary, state,
                                new ResourceLocation("blade_tetra", "model/modular/standard_256.png"),
                                poses, buffer, light);
                    }
                    continue;
                }
                mounted.mount.apply(poses);
                if (mounted.views.base() != null) {
                    BladeRenderState.renderOverrided(stack, mounted.views.base(), state, kind.texture(), poses, buffer, light);
                }
                if (mounted.views.luminous() != null) {
                    BladeRenderState.renderOverridedLuminous(stack, mounted.views.luminous(), state + "_luminous", kind.texture(),
                            poses, buffer, light);
                }
            } finally {
                poses.popPose();
                BladeRenderState.resetCol();
            }
        }
    }

    private static void draw(RenderOverrideEvent event, String state, Draw draw) {
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        try {
            if (draw.mount != null) draw.mount.apply(poses);
            BladeRenderState.resetCol();
            if (draw.views.base() != null) {
                BladeRenderState.renderOverrided(event.getStack(), draw.views.base(), state, draw.texture, poses,
                        event.getBuffer(), event.getPackedLightIn(), event.getGetRenderType(), event.isEnableEffect());
                if (draw.materialGlow != null) {
                    BladeRenderState.renderOverridedLuminous(event.getStack(), draw.views.base(), state,
                            draw.materialGlow, poses, event.getBuffer(), event.getPackedLightIn());
                }
            }
            if (draw.views.luminous() != null) {
                BladeRenderState.renderOverridedLuminous(event.getStack(), draw.views.luminous(), state + "_luminous",
                        draw.texture, poses, event.getBuffer(), event.getPackedLightIn());
            }
        } finally {
            poses.popPose();
            BladeRenderState.resetCol();
        }
    }

    private static Part stockPart(Component part) {
        return switch (part) {
            case BLADE -> Part.BLADE;
            case HILT -> Part.HILT;
            case SAYA -> Part.SAYA;
        };
    }

    static void clear() {
        MOUNTS.clear();
        STOCK.clear();
        REMAINING.clear();
    }

    private NamedBladeRenderPlan() {}
}
