package dev.bladetetra.compat.effects;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.effect.ItemEffect;

/** Opt-in integration check against the provider's actual, transformed event handler. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class ProviderEffectSmokeTest {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.providerEffectSmoke")) return;
        try {
            var level = event.getServer().overworld();
            var player = FakePlayerFactory.getMinecraft(level);
            var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            check(ProviderEffectEligibility.accepts(blade.getItem()), "module blade accepted");
            check(!ProviderEffectEligibility.accepts(Items.DIAMOND_SWORD), "ordinary sword excluded");
            check(!ProviderEffectEligibility.accepts(null), "null excluded");
            var nativeSword = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tetra", "modular_sword"));
            check(nativeSword != null && ProviderEffectEligibility.accepts(nativeSword), "native Tetra item still accepted");
            if (!net.minecraftforge.fml.ModList.get().isLoaded("revelationfix")) {
                LogUtils.getLogger().info("PROVIDER_EFFECT_SMOKE_PASS: optional provider absent, base startup safe");
                return;
            }
            var tag = blade.getOrCreateTag();
            tag.putString("slashblade/kashira", "slashblade/socket_kashira");
            tag.putString("slashblade/socket_kashira_material", "sword_socket/socket_gr_ominous_orb");
            var modular = (se.mickelus.tetra.items.modular.IModularItem) blade.getItem();
            se.mickelus.tetra.items.modular.IModularItem.updateIdentifier(blade);
            check(modular.getEffectLevel(blade, ItemEffect.get("goety_revelation.vizir")) == 25,
                    "native socket contains provider's 25% effect");
            player.setItemSlot(EquipmentSlot.MAINHAND, blade);
            player.setPos(0.5, 80, 0.5);
            level.getChunk(0, 0);
            var target = EntityType.ARMOR_STAND.create(level);
            check(target != null, "target created");
            target.setPos(2.5, 80, 0.5);
            var type = Class.forName("com.mega.revelationfix.common.compat.tetra.effect.VizirEffect");
            for (String effect : java.util.List.of("Beelzebub", "CursedBlade", "Deicide", "Doom", "Fading",
                    "IceBlade", "MysteriousBlade", "PositionBlade", "RulerOfNocturnal", "ShadowWalk", "Vizir")) {
                Class.forName("com.mega.revelationfix.common.compat.tetra.effect." + effect + "Effect")
                        .getDeclaredMethods();
            }
            var handler = type.getConstructor().newInstance();
            var onHit = type.getMethod("onHurtEntity", LivingHurtEvent.class);
            var area = player.getBoundingBox().inflate(12);
            var entitiesBefore = level.getEntities(player, area).size();
            for (int attempt = 0; attempt < 128; attempt++) {
                onHit.invoke(handler, new LivingHurtEvent(target, level.damageSources().playerAttack(player), 1));
            }
            var entitiesAfter = level.getEntities(player, area).size();
            check(entitiesAfter > entitiesBefore, "original Vizir handler summons with our blade");
            var guardedSource = level.damageSources().playerAttack(player);
            var sourceContract = Class.forName("com.mega.revelationfix.safe.DamageSourceInterface");
            sourceContract.getMethod("giveSpecialTag", byte.class).invoke(guardedSource, (byte) 3);
            for (int attempt = 0; attempt < 128; attempt++) {
                onHit.invoke(handler, new LivingHurtEvent(target, guardedSource, 1));
            }
            check(level.getEntities(player, area).size() == entitiesAfter, "provider's recursion guard preserved");
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            for (int attempt = 0; attempt < 128; attempt++) {
                onHit.invoke(handler, new LivingHurtEvent(target, level.damageSources().playerAttack(player), 1));
            }
            check(level.getEntities(player, area).size() == entitiesAfter, "unrelated swords do not gain effect");
            LogUtils.getLogger().info("PROVIDER_EFFECT_SMOKE_PASS: native material, actual summoning, recursion guard, unrelated items");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("PROVIDER_EFFECT_SMOKE_FAIL", failure);
            throw new IllegalStateException("Provider effect diagnostic failed", failure);
        } finally { event.getServer().halt(false); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private ProviderEffectSmokeTest() {}
}
