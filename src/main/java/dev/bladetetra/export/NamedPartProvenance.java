package dev.bladetetra.export;

import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedBackplate;
import dev.bladetetra.forging.NamedLegacyImprintStorage;
import dev.bladetetra.forging.NamedLegacyParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Source identity of each installed named fitting, before resolving client resource packs. */
public record NamedPartProvenance(String slot, String sourceId, ResourceLocation namedBlade,
                                  ResourceLocation declaredModel, ResourceLocation declaredTexture) {
    static List<NamedPartProvenance> capture(ItemStack blade) {
        NamedLegacyParts fittings = NamedLegacyParts.fromStack(blade);
        List<NamedPartProvenance> sources = new ArrayList<>(4);
        add(sources, "blade", NamedLegacyImprintStorage.sourceId(blade, "blade"),
                NamedLegacyParts.bladeFromStack(blade));
        add(sources, "saya", NamedLegacyImprintStorage.sourceId(blade, "saya"), fittings.saya());
        add(sources, "tsuba", NamedLegacyImprintStorage.sourceId(blade, "tsuba"), fittings.tsuba());
        String backplateSource = NamedBackplate.sourceId(blade);
        if (backplateSource != null) {
            add(sources, "backplate", backplateSource, NamedBackplate.source(blade));
        }
        return List.copyOf(sources);
    }

    private static void add(List<NamedPartProvenance> sources, String slot, String sourceId,
                            LegacyImprintKind kind) {
        if (kind != null) {
            sources.add(new NamedPartProvenance(slot, kind.id(), kind.name(),
                    kind.model(), kind.texture()));
        } else if (sourceId != null) {
            sources.add(new NamedPartProvenance(slot, sourceId, null, null, null));
        }
    }
}
