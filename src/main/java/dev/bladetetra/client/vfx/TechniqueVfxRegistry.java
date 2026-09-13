package dev.bladetetra.client.vfx;

import com.mojang.logging.LogUtils;
import dev.bladetetra.visual.TechniqueVfxData;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Client-side registry for resource-id addressed technique VFX.
 *
 * <p>Each visual family owns its own renderer/lifecycle class and registers one
 * or more ids here. This keeps addon visuals from growing the legacy
 * {@code BladeTechniqueVfxClient} switch indefinitely.</p>
 */
public final class TechniqueVfxRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, Consumer<TechniqueVfxData>> HANDLERS =
            new LinkedHashMap<>();

    public static synchronized Registration register(
            ResourceLocation effectId,
            Consumer<TechniqueVfxData> handler) {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.containsKey(effectId)) {
            throw new IllegalStateException(
                    "Technique VFX handler already registered for " + effectId);
        }
        HANDLERS.put(effectId, handler);
        return new Registration(effectId, handler);
    }

    /**
     * Dispatches one visual packet. Unknown ids are deliberately ignored after
     * a debug message so an optional addon being removed cannot crash a client.
     */
    public static boolean dispatch(TechniqueVfxData packet) {
        Consumer<TechniqueVfxData> handler;
        synchronized (TechniqueVfxRegistry.class) {
            handler = HANDLERS.get(packet.effectId());
        }
        if (handler == null) {
            LOGGER.debug("No client technique VFX handler registered for {}",
                    packet.effectId());
            return false;
        }
        handler.accept(packet);
        return true;
    }

    static synchronized int registeredCount() {
        return HANDLERS.size();
    }

    static synchronized void clearForTests() {
        HANDLERS.clear();
    }

    public static final class Registration implements AutoCloseable {
        private final ResourceLocation effectId;
        private final Consumer<TechniqueVfxData> handler;
        private boolean closed;

        private Registration(
                ResourceLocation effectId,
                Consumer<TechniqueVfxData> handler) {
            this.effectId = effectId;
            this.handler = handler;
        }

        @Override
        public void close() {
            synchronized (TechniqueVfxRegistry.class) {
                if (closed) {
                    return;
                }
                HANDLERS.remove(effectId, handler);
                closed = true;
            }
        }
    }

    private TechniqueVfxRegistry() {
    }
}
