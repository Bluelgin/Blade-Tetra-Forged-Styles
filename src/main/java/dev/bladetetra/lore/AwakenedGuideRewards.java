package dev.bladetetra.lore;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.AwakenedSoulFragmentItem;
import dev.bladetetra.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/** Advancement eligibility and durable delivery receipts are intentionally separate. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class AwakenedGuideRewards {
    private static final String RECEIPTS = "BladeTetraAwakenedGuides";
    private static List<RegistryObject<AwakenedSoulFragmentItem>> guides() {
        return List.of(ModItems.AKATSUKI_SOUL_FRAGMENT, ModItems.SENBONZAKURA_SOUL_FRAGMENT,
                ModItems.RAIKIRI_SOUL_FRAGMENT, ModItems.KYOUKA_SOUL_FRAGMENT);
    }

    public static void deliver(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        CompoundTag receipts = receipts(player);
        for (var entry : guides()) {
            var item = entry.get();
            if (receipts.getBoolean(item.soul()) || !SmithingLore.completed(player, item.soul())) continue;
            // One unstackable item, main inventory only. Never mark delivery or drop on a full inventory.
            if (player.getInventory().getFreeSlot() < 0) return;
            var gift = item.getDefaultInstance();
            if (!player.getInventory().add(gift)) return;
            receipts.putBoolean(item.soul(), true);
            player.inventoryMenu.broadcastChanges();
            player.displayClientMessage(Component.translatable("message.blade_tetra.soul_fragment.received",
                    item.getDescription()), false);
        }
    }

    @SubscribeEvent public static void earned(AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getAdvancement().getId().getNamespace().equals(BladeTetra.MOD_ID)
                && guides().stream().anyMatch(g -> g.get().soul().equals(event.getAdvancement().getId().getPath())))
            deliver(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) deliver(player);
    }
    @SubscribeEvent public static void retry(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 100 == 0) deliver(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (old.contains(RECEIPTS)) persisted(event.getEntity()).put(RECEIPTS, old.getCompound(RECEIPTS).copy());
    }
    private static CompoundTag persisted(Player player) {
        var root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
    private static CompoundTag receipts(Player player) {
        var root = persisted(player);
        if (!root.contains(RECEIPTS)) root.put(RECEIPTS, new CompoundTag());
        return root.getCompound(RECEIPTS);
    }
    private AwakenedGuideRewards() {}
}
