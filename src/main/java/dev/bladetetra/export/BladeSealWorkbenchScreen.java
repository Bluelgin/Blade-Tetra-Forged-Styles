package dev.bladetetra.export;

import dev.bladetetra.BladeTetra;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BladeSealWorkbenchScreen extends AbstractContainerScreen<BladeSealWorkbench.WorkbenchMenu> {
    private Button studio;

    public BladeSealWorkbenchScreen(BladeSealWorkbench.WorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @SubscribeEvent
    public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(BladeSealWorkbench.MENU.get(), BladeSealWorkbenchScreen::new));
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> { BladeStudioViewport.resourcesReloaded(); BladeWorkbenchRenderer.resourcesReloaded(); });
    }

    @Override
    protected void init() {
        super.init();
        titleLabelY = 7;
        inventoryLabelY = 73;
        studio = addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.studio"), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BladeSealWorkbench.WorkbenchMenu.OPEN_STUDIO);
            }
        }).bounds(leftPos + 32, topPos + 49, 112, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        studio.active = !menu.blade().isEmpty();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, BladeStudioStyle.TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, BladeStudioStyle.TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        BladeStudioStyle.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        graphics.fill(leftPos + 6, topPos + 17, leftPos + imageWidth - 6, topPos + 71, 0xff28211b);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xff4f402e);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xff322a21);
        }
    }
}
