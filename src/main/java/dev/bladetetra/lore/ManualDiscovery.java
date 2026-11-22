package dev.bladetetra.lore;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.GameplayConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.stream.Stream;

/** Patchouli manual delivery and advancement mirrors for existing lore progress. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class ManualDiscovery {
    private static final java.util.List<String> SOULS = java.util.List.of("akatsuki", "senbonzakura", "raikiri", "kyouka");
    private static final ResourceLocation STARTER_MANUAL =
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, "manual/starter_manual");
    private static final ResourceLocation PATCHOULI_BOOK_ITEM =
            ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book");
    private static final String BOOK_TAG = "patchouli:book";
    private static final String GUIDE_BOOK_ID = BladeTetra.MOD_ID + ":blade_guide";
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
        if (event.getEntity() instanceof ServerPlayer player) {
            giveStarterManual(player);
            sync(player);
        }
    }

    private static void giveStarterManual(ServerPlayer player) {
        if (!ModList.get().isLoaded("patchouli") || !GameplayConfig.GIVE_STARTER_MANUAL.get()) return;
        var receipt = player.server.getAdvancements().getAdvancement(STARTER_MANUAL);
        if (receipt == null || player.getAdvancements().getOrStartProgress(receipt).isDone()) return;

        var item = ForgeRegistries.ITEMS.getValue(PATCHOULI_BOOK_ITEM);
        if (item == null) return;
        var manual = new ItemStack(item);
        if (manual.isEmpty()) return;
        manual.getOrCreateTag().putString(BOOK_TAG, GUIDE_BOOK_ID);
        var inventory = player.getInventory();
        boolean alreadyHasManual = Stream.concat(inventory.items.stream(), inventory.offhand.stream())
                .anyMatch(stack -> stack.is(item) && stack.getTag() != null
                        && GUIDE_BOOK_ID.equals(stack.getTag().getString(BOOK_TAG)));
        if (alreadyHasManual || (inventory.getFreeSlot() >= 0 && inventory.add(manual))) {
            player.getAdvancements().award(receipt, "received");
        }
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
