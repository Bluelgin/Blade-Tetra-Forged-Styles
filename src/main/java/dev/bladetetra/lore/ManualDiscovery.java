package dev.bladetetra.lore;

import dev.bladetetra.BladeTetra;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla advancement mirrors for Patchouli; original lore NBT remains authoritative. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class ManualDiscovery {
    private static final java.util.List<String> SOULS = java.util.List.of("akatsuki", "senbonzakura", "raikiri", "kyouka");
    public static void sync(ServerPlayer player) {
        for (String clue : SmithingLore.CLUES) {
            if (SmithingLore.knows(player, clue) || SmithingLore.completed(player, clue)) award(player, clue);
        }
        if (Integer.bitCount(SmithingLore.completionMask(player)) >= 3 || SmithingLore.nbtSageCompleted(player))
            award(player, SmithingLore.NBT_SAGE);
        var readable = new java.util.HashSet<String>();
        for (String soul : SOULS) if (SmithingLore.completed(player, soul)) readable.add(soul);
        for (var stack : java.util.stream.Stream.concat(player.getInventory().items.stream(),
                player.getInventory().offhand.stream()).toList()) {
            if (stack.getItem() instanceof dev.bladetetra.item.AwakenedSoulFragmentItem relic) readable.add(relic.soul());
            if (stack.getItem() instanceof dev.bladetetra.item.ModularSlashBladeItem) {
                for (var soul : dev.bladetetra.easteregg.SoulLegacyState.inscribed(stack)) {
                    if (dev.bladetetra.easteregg.SoulLegacyState.isUnlocked(stack, soul))
                        readable.add(soul.name().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        for (String soul : readable) if (SOULS.contains(soul)) award(player, "guide/" + soul);
    }
    private static void award(ServerPlayer player, String clue) {
        var advancement = player.server.getAdvancements().getAdvancement(
                new ResourceLocation(BladeTetra.MOD_ID, "manual/" + clue));
        if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone())
            player.getAdvancements().award(advancement, "discovered");
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % 100 == 0)
            sync(player);
    }
    @SubscribeEvent public static void earned(net.minecraftforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) {
        var id = event.getAdvancement().getId();
        if (event.getEntity() instanceof ServerPlayer player && id.getNamespace().equals(BladeTetra.MOD_ID)
                && !id.getPath().startsWith("manual/")) sync(player);
    }
    private ManualDiscovery() {}
}
