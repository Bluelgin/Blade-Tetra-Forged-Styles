package dev.bladetetra.lore;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.bladetetra.item.AwakenedSoulFragmentItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.util.UUID;

/** Actual advancement events and inventory delivery, including old-save and death receipts. */
public final class AwakenedGuideSmoke {
    public static void verify(ServerLevel level) {
        var fresh = player(level, "GuideNew");
        AwakenedGuideRewards.deliver(fresh);
        check(count(fresh) == 0, "no advancement, no gift");
        award(fresh, "akatsuki");
        check(count(fresh) == 1, "new awakening immediately gives one guide");
        AwakenedGuideRewards.deliver(fresh);
        check(count(fresh) == 1, "repeated checks do not duplicate delivery");

        var old = player(level, "GuideOld");
        for (int i = 0; i < 36; i++) old.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        for (String soul : new String[]{"akatsuki", "senbonzakura", "raikiri", "kyouka"}) award(old, soul);
        AwakenedGuideRewards.login(new PlayerEvent.PlayerLoggedInEvent(old));
        check(count(old) == 0, "full inventory retains every outstanding gift");
        old.getInventory().setItem(0, ItemStack.EMPTY);old.getInventory().setItem(1, ItemStack.EMPTY);
        old.tickCount = 100;
        AwakenedGuideRewards.retry(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, old));
        check(count(old) == 2, "freeing two slots delivers exactly two old-save guides");
        old.getInventory().setItem(2, ItemStack.EMPTY);old.getInventory().setItem(3, ItemStack.EMPTY);
        AwakenedGuideRewards.login(new PlayerEvent.PlayerLoggedInEvent(old));
        check(count(old) == 4, "login backfills remaining completed awakenings");

        CompoundTag saved = old.getPersistentData().copy();
        old.getInventory().clearContent();
        for (var key : java.util.Set.copyOf(old.getPersistentData().getAllKeys())) old.getPersistentData().remove(key);
        old.getPersistentData().merge(saved);
        AwakenedGuideRewards.login(new PlayerEvent.PlayerLoggedInEvent(old));
        check(count(old) == 0, "saved receipts prevent duplicates after loss and relog");
        var respawn = player(level, "GuideRespawn");
        AwakenedGuideRewards.clone(new PlayerEvent.Clone(respawn, old, true));
        for (String soul : new String[]{"akatsuki", "senbonzakura", "raikiri", "kyouka"}) award(respawn, soul);
        check(count(respawn) == 0, "death clone retains delivery receipts");
        LogUtils.getLogger().info("AWAKENED_GUIDE_SMOKE_PASS: actual advancement grant, four old-save gifts, full inventory deferral, partial retry, relog persistence and death clone without duplicates");
    }
    private static ServerPlayer player(ServerLevel level, String name) {
        // Forge deliberately rejects advancement grants to FakePlayer. Use a real player actor,
        // with only its network connection stubbed; the advancement/inventory paths stay native.
        var wire = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GuideWire"));
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        player.connection = wire.connection;
        return player;
    }
    private static void award(ServerPlayer player, String soul) {
        var advancement = player.server.getAdvancements().getAdvancement(new ResourceLocation("blade_tetra", soul));
        check(advancement != null, "existing soul advancement registered: " + soul);
        for (var criterion : advancement.getCriteria().keySet()) player.getAdvancements().award(advancement, criterion);
    }
    private static long count(ServerPlayer player) {
        return player.getInventory().items.stream().filter(s -> s.getItem() instanceof AwakenedSoulFragmentItem).count();
    }
    private static void check(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
    private AwakenedGuideSmoke() {}
}
