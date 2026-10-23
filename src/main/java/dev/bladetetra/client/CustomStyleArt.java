package dev.bladetetra.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Presentation helpers; no authoring or combat state lives here. */
final class CustomStyleArt {
    static ResourceLocation texture(String name) {
        return new ResourceLocation("blade_tetra", "textures/gui/custom_style/" + name + ".png");
    }

    static void icon(GuiGraphics g, String name, int x, int y) {
        g.blit(texture(name), x, y, 0, 0, 12, 12, 12, 12);
    }

    static String fit(Component text, int width) {
        var font = Minecraft.getInstance().font;
        String value = text.getString();
        return font.width(value) <= width ? value
                : font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }

    static final class ArtButton extends Button {
        private final String icon;
        private boolean pressed;

        ArtButton(int x, int y, int width, Component text, String icon, OnPress onPress) {
            super(x, y, width, 20, text, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            setTooltip(Tooltip.create(text));
        }

        @Override public boolean mouseClicked(double x, double y, int button) {
            boolean handled = super.mouseClicked(x, y, button);
            pressed = handled;
            return handled;
        }

        @Override public void onRelease(double x, double y) { pressed = false; }

        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (pressed && org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                    Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                    != org.lwjgl.glfw.GLFW.GLFW_PRESS) pressed = false;
            var texture = texture(!active ? "button_disabled" : pressed ? "button_pressed"
                    : isHoveredOrFocused() ? "button_hover" : "button_normal");
            // Three horizontal slices preserve the original three-pixel end caps.
            g.blit(texture, getX(), getY(), 0, 0, 3, 20, 24, 20);
            g.blit(texture, getX() + 3, getY(), width - 6, 20, 3, 0, 18, 20, 24, 20);
            g.blit(texture, getX() + width - 3, getY(), 21, 0, 3, 20, 24, 20);
            int inset = icon == null ? 5 : 19;
            if (icon != null) icon(g, icon, getX() + 5, getY() + 4);
            String label = fit(getMessage(), width - inset - 5);
            g.drawString(Minecraft.getInstance().font, label,
                    getX() + inset + (width - inset - 5 - Minecraft.getInstance().font.width(label)) / 2,
                    getY() + 6, active ? 0xF7EDDB : 0xC7B697, false);
            if (isFocused()) g.renderOutline(getX(), getY(), width, height, 0xFFDFBD79);
        }
    }

    private CustomStyleArt() {}
}
