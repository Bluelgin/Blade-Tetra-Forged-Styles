package dev.bladetetra.mixin;

import dev.bladetetra.compat.effects.ProviderEffectBytecodeBridge;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** No optional provider classes are loaded or linked by this plugin. */
public final class ProviderEffectCompatPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BladeTetra/ProviderEffects");

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith(".ProviderEffectCompatMixin")) return;
        var result = ProviderEffectBytecodeBridge.adapt(node);
        if (result.adapted() > 0) LOGGER.info("Adapted {} effect methods in {}", result.adapted(), target);
        if (result.unsupported() > 0) {
            LOGGER.warn("Left {} unsupported effect methods unchanged in {}", result.unsupported(), target);
        }
    }
}
