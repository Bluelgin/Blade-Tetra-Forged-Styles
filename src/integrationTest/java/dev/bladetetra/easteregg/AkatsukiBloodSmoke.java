package dev.bladetetra.easteregg;

import com.mojang.logging.LogUtils;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntitySpiralSwords;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.function.Consumer;

/** Exercise real native motion commits, orbit entities and the player's real health attribute. */
final class AkatsukiBloodSmoke {
    static void verify(ServerLevel level,ServerPlayer p) {
        var previous=p.getMainHandItem();float health=p.getHealth(),absorption=p.getAbsorptionAmount();
        var blade=ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();var tag=blade.getOrCreateTag();
        tag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT,ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
        tag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material","awakened_soul_inscription/blood_crystal");
        tag.putBoolean(AkatsukiAwakening.TAG_UNLOCKED,true);
        p.setItemSlot(EquipmentSlot.MAINHAND,blade);p.setHealth(20);p.setOnGround(true);p.setAbsorptionAmount(4);
        var state=blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
        state.setBroken(false);state.setSealed(false);
        var ring=new EntitySpiralSwords(SlashBlade.RegistryEvents.SpiralSwords,level);
        ring.setOwner(p);ring.setPos(p.position());ring.setDelay(10000);
        Consumer<BladeMotionEvent> cancel=e->{if(e.getEntity()==p)e.setCanceled(true);};
        var equipment=new AttributeModifier(java.util.UUID.randomUUID(),"blood smoke equipment",20,AttributeModifier.Operation.ADDITION);
        var target=new net.minecraft.world.entity.monster.Zombie(level);target.setNoAi(true);target.setNoGravity(true);
        target.moveTo(p.getX(),p.getY(),p.getZ()+3,0,0);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(2000);level.addFreshEntity(target);
        try {
            check(AkatsukiBloodBlade.supported(blade),"single Aka admitted");
            blade.enchant(Enchantments.UNBREAKING,3);
            check(AkatsukiAwakening.ensurePower(blade)&&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS,blade)==1,"missing Power granted");
            var high=blade.copy();var enchants=EnchantmentHelper.getEnchantments(high);enchants.put(Enchantments.POWER_ARROWS,5);EnchantmentHelper.setEnchantments(enchants,high);
            check(!AkatsukiAwakening.ensurePower(high)&&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS,high)==5
                    &&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING,high)==3,"existing enchantments preserved");
            inject(p,state);near(p.getHealth(),20,"no orbit no payment");
            ring.startRiding(p,true);level.addFreshEntity(ring);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,BladeMotionEvent.class,cancel);
            inject(p,state);near(p.getHealth(),20,"canceled native controls do not pay");
            MinecraftForge.EVENT_BUS.unregister(cancel);
            verifyReach(level,p,blade,target,0);
            float[] baseline=new float[6];
            for(int mode=0;mode<baseline.length;mode++)baseline[mode]=hit(p,target,ring,mode);
            for(int stage=1;stage<=3;stage++) {
                inject(p,state);float expected=stage==1?18:stage==2?15:11;
                near(p.getHealth(),expected,"tier health "+stage);near(p.getMaxHealth(),expected,"tier cap "+stage);
                near((float)AkatsukiBloodBlade.extraReach(p),stage*.75F,"tier reach "+stage);
                verifyReach(level,p,blade,target,stage);
                for(int mode=0;mode<baseline.length;mode++)near(hit(p,target,ring,mode),
                        baseline[mode]*(mode==0?1+new float[]{0,.10F,.20F,.35F}[stage]:1),"actual damage route "+mode+" tier "+stage);
            }
            near(p.getAbsorptionAmount(),4,"absorption untouched");
            var fixturePosition=target.position();target.setPos(fixturePosition.add(64,0,0));
            AkatsukiThreadSmoke.verify(level,p);target.setPos(fixturePosition);
            inject(p,state);near(p.getHealth(),11,"fourth cleave cannot pay");
            state.updateComboSeq(p,ComboStateRegistry.COMBO_B1.getId());
            ComboStateRegistry.COMBO_B1.get().tickAction(p);ComboStateRegistry.COMBO_B1.get().tickAction(p);
            state.updateComboSeq(p,ComboStateRegistry.NONE.getId());
            near((float)AkatsukiBloodBlade.extraReach(p),2.25F,"ordinary and idle nodes do not consume blood");
            p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);tick(p);
            near(p.getMaxHealth(),11,"unequip keeps escrow");near((float)AkatsukiBloodBlade.extraReach(p),0,"unequipped reach dormant");
            p.setItemSlot(EquipmentSlot.MAINHAND,blade);tick(p);
            AkatsukiBloodBlade.dimension(new PlayerEvent.PlayerChangedDimensionEvent(p,level.dimension(),level.dimension()));
            near((float)AkatsukiBloodBlade.extraReach(p),2.25F,"re-equipping and dimension event preserve bound blood");
            int oldSlot=p.getInventory().selected,otherSlot=(oldSlot+1)%9;
            var oldOther=p.getInventory().getItem(otherSlot);
            p.getInventory().setItem(oldSlot,ItemStack.EMPTY);p.getInventory().setItem(otherSlot,blade);p.getInventory().selected=otherSlot;tick(p);
            near((float)AkatsukiBloodBlade.extraReach(p),2.25F,"hotbar movement preserves blood");
            p.getInventory().setItem(otherSlot,oldOther);p.getInventory().setItem(oldSlot,blade);p.getInventory().selected=oldSlot;
            String material=ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE+"_material";
            tag.putString(material,"awakened_soul_inscription/akatsuki_raikiri");
            tag.putInt(SoulSkillState.SELECTED,SoulLegacyState.Legacy.RAIKIRI.ordinal());tick(p);
            near(p.getMaxHealth(),11,"soul switch keeps reserve");near((float)AkatsukiBloodBlade.extraReach(p),0,"inactive Aka has no reach");
            tag.putInt(SoulSkillState.SELECTED,SoulLegacyState.Legacy.AKATSUKI.ordinal());tick(p);
            near((float)AkatsukiBloodBlade.extraReach(p),2.25F,"returning to Aka restores blood");
            tag.putString(material,"awakened_soul_inscription/blood_crystal");
            ring.doFire();inject(p,state);near(p.getMaxHealth(),11,"fired swords cannot deposit");
            state.updateComboSeq(p,ComboStateRegistry.UPPERSLASH.getId());near(p.getMaxHealth(),11,"fired swords cannot recall");
            ring.discard();ring=new EntitySpiralSwords(SlashBlade.RegistryEvents.SpiralSwords,level);
            ring.setOwner(p);ring.setPos(p.position());ring.setDelay(10000);ring.startRiding(p,true);level.addFreshEntity(ring);
            p.setHealth(7);ring.stopRiding();state.updateComboSeq(p,ComboStateRegistry.UPPERSLASH.getId());
            near(p.getMaxHealth(),11,"upper without orbit cannot recall");ring.startRiding(p,true);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,BladeMotionEvent.class,cancel);
            state.updateComboSeq(p,ComboStateRegistry.UPPERSLASH.getId());near(p.getMaxHealth(),11,"canceled upper cannot refund");
            MinecraftForge.EVENT_BUS.unregister(cancel);
            // A different awakened Aka can recover the owner's reserve, but has no independent reach.
            var other=blade.copy();p.setItemSlot(EquipmentSlot.MAINHAND,other);
            near((float)AkatsukiBloodBlade.extraReach(p),0,"copied blade cannot acquire gameplay reach");
            var otherState=other.getCapability(ModularSlashBladeItem.BLADESTATE).orElseThrow(IllegalStateException::new);
            otherState.updateComboSeq(p,ComboStateRegistry.UPPERSLASH.getId());
            near(p.getMaxHealth(),20,"recall restores cap");near(p.getHealth(),16,"recall preserves four damage");
            otherState.updateComboSeq(p,ComboStateRegistry.UPPERSLASH.getId());near(p.getHealth(),16,"no duplicate refund");
            check(AkatsukiBloodBlade.flow(p,other,0).returning(),"recall flow on recovery blade");
            near(AkatsukiBloodBlade.flow(p,other,0).health(),9,"recovery cue contains actual refunded life");
            near(hit(p,target,ring,0),baseline[0],"recall immediately removes melee bonus");
            AkatsukiBloodBlade.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            p.setItemSlot(EquipmentSlot.MAINHAND,blade);p.setHealth(3);inject(p,state);
            near(p.getHealth(),3,"payment must leave one heart");near(p.getMaxHealth(),20,"rejected payment has no modifier");
            p.setHealth(20);inject(p,state);near(p.getHealth(),18,"new tier one");
            p.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(equipment);p.setHealth(36);
            inject(p,state);near(p.getMaxHealth(),30,"tier two applies to unreserved equipped cap");near(p.getHealth(),30,"second deposit costs six with equipped cap forty");
            p.getAttribute(Attributes.MAX_HEALTH).removeModifier(equipment);p.setHealth(10);
            AkatsukiBloodBlade.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            near(p.getMaxHealth(),20,"logout releases cap");near(p.getHealth(),18,"logout refunds actual two plus six, not changed cap percentage");
            check(!blade.getOrCreateTag().contains(AkatsukiBloodBlade.TAG),"logout clears visual snapshot");
            AkatsukiBloodBlade.login(new PlayerEvent.PlayerLoggedInEvent(p));near(p.getHealth(),18,"login cannot refund again");
            p.setHealth(20);inject(p,state);p.setHealth(0);
            AkatsukiBloodBlade.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(p,p.damageSources().generic()));
            near(p.getMaxHealth(),20,"death removes reserve");near(p.getHealth(),0,"death grants no refund");
            LogUtils.getLogger().info("AKATSUKI_BLOOD_SMOKE_PASS: real orbit gating, canceled commits, Power preservation, three persistent life tiers and melee bonuses, projectile/secondary/SA/execution exclusions, no attack consumption, equip/dimension persistence, copy protection, recovery blade, exact refund after damage/equipment changes, low life, logout and death");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);ring.discard();target.discard();
            p.getAttribute(Attributes.MAX_HEALTH).removeModifier(equipment);
            AkatsukiBloodBlade.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            p.setItemSlot(EquipmentSlot.MAINHAND,previous);p.setHealth(health);p.setAbsorptionAmount(absorption);
        }
    }
    private static void verifyReach(ServerLevel level,ServerPlayer p,ItemStack blade,net.minecraft.world.entity.LivingEntity fixture,int stage) {
        var position=fixture.position();fixture.setPos(position.add(64,0,0));
        try {AkatsukiBloodReachSmoke.verify(level,p,blade,stage);}
        finally {fixture.setPos(position);}
    }
    private static float hit(ServerPlayer p,net.minecraft.world.entity.LivingEntity target,EntitySpiralSwords ring,int mode) {
        var source=mode==1?p.damageSources().indirectMagic(ring,p):mode==5?
                new net.minecraft.world.damagesource.DamageSource(p.level().registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(
                                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                                        net.minecraft.resources.ResourceLocation.tryParse("blade_tetra:akatsuki_execution"))),p)
                :p.damageSources().playerAttack(p);
        float[] amount={0};
        Consumer<net.minecraftforge.event.entity.living.LivingHurtEvent> observe=e->{if(e.getEntity()==target&&e.getSource()==source)amount[0]=e.getAmount();};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,net.minecraftforge.event.entity.living.LivingHurtEvent.class,observe);
        java.util.function.BooleanSupplier hit=()->{target.invulnerableTime=0;return target.hurt(source,10);};
        try {
            switch(mode) {
                case 2->SoulLegacyDamageGuard.apply(hit);
                case 3->dev.bladetetra.combat.InheritedCombatDamage.apply(hit);
                case 4->dev.bladetetra.combat.CombatBalanceRuntime.slashArt(p,()->hit.getAsBoolean());
                default->hit.getAsBoolean();
            }
            check(amount[0]>0,"real Forge damage pipeline observed for route "+mode);return amount[0];
        } finally {MinecraftForge.EVENT_BUS.unregister(observe);}
    }
    private static void tick(ServerPlayer p) {AkatsukiBloodBlade.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));}
    private static void inject(ServerPlayer p,mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state) {
        state.updateComboSeq(p,ComboStateRegistry.AERIAL_CLEAVE.getId());state.updateComboSeq(p,ComboStateRegistry.AERIAL_CLEAVE_LANDING.getId());
    }
    private static void near(float actual,float expected,String message) {check(Math.abs(actual-expected)<.001,message+": "+actual+" expected "+expected);}
    private static void check(boolean okay,String message) {if(!okay)throw new IllegalStateException(message);}
    private AkatsukiBloodSmoke() {}
}
