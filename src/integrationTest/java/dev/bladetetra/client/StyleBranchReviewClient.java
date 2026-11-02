package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.items.modular.IModularItem;

import java.nio.file.Files;

/** Opt-in local hands-on review. Never changes inventories in normal clients. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class StyleBranchReviewClient {
    private static boolean opened, equipped;
    private static int readyTicks;
    private static final String WORLD = "style-branch-review";
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("blade_tetra.styleBranchReview") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!opened && mc.screen instanceof TitleScreen) {
            opened = true;
            if (Files.exists(mc.gameDirectory.toPath().resolve("saves/" + WORLD + "/level.dat"))) {
                mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
            } else {
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("连舞与断岳 · 连段测试", GameType.SURVIVAL, false,
                                Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(158L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
        if (!equipped && mc.level != null && mc.player != null && mc.screen == null && ++readyTicks > 40) {
            equipped = true;
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            if (server == null) return;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player == null) return;
                if (!player.getPersistentData().getBoolean("blade_tetra_style_review_equipped")) {
                    player.getInventory().setItem(0, sample("wakizashi_blade", "测试 · 连舞流"));
                    player.getInventory().setItem(1, sample("nodachi_blade", "测试 · 断岳流"));
                    player.getInventory().setItem(2, sample("orthodox_blade", "对照 · 本传流"));
                    player.getInventory().setItem(3, new ItemStack(Items.COOKED_BEEF, 64));
                    player.getInventory().setItem(4, new ItemStack(Items.HUSK_SPAWN_EGG, 64));
                    player.getPersistentData().putBoolean("blade_tetra_style_review_equipped", true);
                    var level = player.serverLevel();
                    for (int i = 0; i < 3; i++) {
                        var target = new Husk(net.minecraft.world.entity.EntityType.HUSK, level);
                        target.moveTo(player.getX() + (i - 1) * 2, player.getY(), player.getZ() + 5, 180, 0);
                        target.setNoAi(true); target.setPersistenceRequired();
                        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
                        target.setHealth(500); target.setCustomName(Component.literal("可击飞 · 连段靶"));
                        level.addFreshEntity(target);
                    }
                    var target = new IronGolem(net.minecraft.world.entity.EntityType.IRON_GOLEM, level);
                    target.moveTo(player.getX() + 7, player.getY(), player.getZ() + 5, 180, 0);
                    target.setNoAi(true); target.setPersistenceRequired();
                    target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                    target.setHealth(1000); target.setCustomName(Component.literal("抗击退 · 地面路线靶"));
                    level.addFreshEntity(target);
                    player.inventoryMenu.broadcastChanges();
                }
                player.sendSystemMessage(Component.literal("连舞已恢复原生B系地面连击与本体空中攻击；无自动追身或击杀瞬移。疾跑保留刀光，每4 tick最多一次命中，基础伤害为普通B系常规刀光的一半（约0.104倍面板路径）；方向技和SA保留。"));
                player.sendSystemMessage(Component.literal("断岳地面与空中：横扫、反扫接Circle Slash回旋环斩；第二刀后稍停（约0.4秒）再右键仍为双段重斩。空中缓降、通用方向技与SA保留。"));
                player.sendSystemMessage(Component.literal("断岳新增：地面长按左键约0.3秒进入无间隔持续回旋，每0.6秒一圈，移动减速25%，每圈伤害为普通环斩的40%；不附加僵直，受伤不打断。消耗、弹反沿用本体；松手转完当前一圈再收势，离地不继续循环。"));
                LogUtils.getLogger().info("STYLE_BRANCH_REVIEW_READY: hands-on isolated world, two styles, native comparison, launchable and resistant targets");
            });
        }
    }
    private static ItemStack sample(String module, String name) {
        ItemStack blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        IModularItem.putModuleInSlot(blade, ModularSlashBladeItem.BLADE_SLOT, "slashblade/" + module,
                "slashblade/" + module + "_material", module + "/iron");
        IModularItem.updateIdentifier(blade);
        blade.enchant(Enchantments.UNBREAKING, 3);
        ModItems.MODULAR_SLASHBLADE.get().syncDerivedBladeState(blade);
        blade.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state -> state.setProudSoulCount(10000));
        blade.setHoverName(Component.literal(name));
        return blade;
    }
    private StyleBranchReviewClient() {}
}
