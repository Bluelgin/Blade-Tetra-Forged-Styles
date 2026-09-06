package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.network.MikageVisitorChoicePacket;
import dev.bladetetra.network.MikageVisitorDialoguePacket;
import dev.bladetetra.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Visual-novel presentation for Mikage's peaceful visitor conversations. */
public final class MikageVisitorScreen extends Screen {
    private static final int TEXTURE_WIDTH = 512;
    // Portrait assets are cropped to the only 560 rows this screen renders.
    private static final int TEXTURE_HEIGHT = 560;
    private static final int PORTRAIT_CROP_HEIGHT = 560;
    private static final ResourceLocation PORTRAIT_NEUTRAL = portrait("neutral");
    private static final ResourceLocation PORTRAIT_SOFT = portrait("soft");
    private static final ResourceLocation PORTRAIT_DISTANT = portrait("distant");
    private static final ResourceLocation PORTRAIT_SERIOUS = portrait("serious");

    private final MikageVisitorDialoguePacket dialogue;
    private int boxX;
    private int boxY;
    private int boxWidth;
    private int boxHeight;
    private int portraitX;
    private int portraitY;
    private int portraitWidth;
    private int portraitHeight;
    private int textX;
    private int textWidth;

    public MikageVisitorScreen(MikageVisitorDialoguePacket dialogue) {
        super(Component.translatable("screen.blade_tetra.mikage_visitor.title"));
        this.dialogue = dialogue;
    }

    private static ResourceLocation portrait(String expression) {
        return new ResourceLocation(BladeTetra.MOD_ID,
                "textures/gui/mikage_dialogue/mikage_" + expression + ".png");
    }

    @Override
    protected void init() {
        boxWidth = Math.min(960, width - 24);
        boxHeight = Math.min(142, height - 34);
        boxX = (width - boxWidth) / 2;
        boxY = height - boxHeight - 12;

        portraitHeight = Math.min(height - 4,
                Math.max(280, Math.round(height * 0.94F)));
        portraitWidth = Math.round(portraitHeight * (TEXTURE_WIDTH / (float) PORTRAIT_CROP_HEIGHT));
        portraitX = Math.max(0, boxX + Math.min(42, boxWidth / 18));
        portraitY = height - portraitHeight;

        textX = Math.max(boxX + 214,
                Math.min(boxX + portraitWidth - 12, boxX + boxWidth / 3));
        textWidth = boxX + boxWidth - textX - 22;

        List<MikageVisitorDialoguePacket.Option> options = dialogue.options();
        int columns = options.size() > 2 ? 2 : 1;
        int gap = 7;
        int optionWidth = columns == 1 ? Math.min(320, textWidth)
                : (textWidth - gap) / 2;
        int rows = (options.size() + columns - 1) / columns;
        int optionY = boxY + boxHeight - rows * 22 - 12;
        for (int i = 0; i < options.size(); i++) {
            MikageVisitorDialoguePacket.Option option = options.get(i);
            int column = i % columns;
            int row = i / columns;
            int optionX = columns == 1 ? boxX + boxWidth - optionWidth - 18
                    : textX + column * (optionWidth + gap);
            addRenderableWidget(new DialogueButton(optionX, optionY + row * 22,
                    optionWidth, 19, Component.translatable(option.labelKey()),
                    () -> ModNetwork.CHANNEL.sendToServer(
                            new MikageVisitorChoicePacket(option.id())), false));
        }
        addRenderableWidget(new DialogueButton(boxX + boxWidth - 25, boxY - 22,
                20, 18, Component.literal("×"), this::onClose, true));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x38000000);
        ResourceLocation portrait = expressionTexture();
        Minecraft.getInstance().getTextureManager().getTexture(portrait)
                .setFilter(false, false);
        graphics.blit(portrait, portraitX, portraitY,
                portraitWidth, portraitHeight, 0.0F, 0.0F,
                TEXTURE_WIDTH, PORTRAIT_CROP_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        graphics.fill(boxX - 2, boxY - 2, boxX + boxWidth + 2,
                boxY + boxHeight + 2, 0x77000000);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xD9181517);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFFE04450);
        graphics.fill(boxX, boxY + 2, boxX + 4, boxY + boxHeight, 0x9AD12C3C);

        int nameWidth = Math.min(116, textWidth / 3);
        graphics.fill(textX - 10, boxY - 20, textX + nameWidth, boxY + 5,
                0xEC271A1F);
        graphics.fill(textX - 10, boxY - 20, textX - 6, boxY + 5,
                0xFFE04450);
        graphics.drawString(font, Component.translatable(
                        "screen.blade_tetra.mikage_visitor.speaker"),
                textX + 2, boxY - 11, 0xFFFFE9E7, false);

        List<FormattedCharSequence> lines = font.split(
                Component.translatable(dialogue.textKey()), textWidth);
        int y = boxY + 18;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, textX, y, 0xFFF8F3EE, false);
            y += 13;
        }

        graphics.drawString(font, title, boxX + 13, boxY + boxHeight - 13,
                0xFF756568, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private ResourceLocation expressionTexture() {
        return switch (dialogue.expression()) {
            case "soft" -> PORTRAIT_SOFT;
            case "distant" -> PORTRAIT_DISTANT;
            case "serious" -> PORTRAIT_SERIOUS;
            default -> PORTRAIT_NEUTRAL;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class DialogueButton extends AbstractButton {
        private final Runnable action;
        private final boolean compact;

        DialogueButton(int x, int y, int width, int height, Component message,
                Runnable action, boolean compact) {
            super(x, y, width, height, message);
            this.action = action;
            this.compact = compact;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                float partialTick) {
            int background = isHoveredOrFocused() ? 0xDDBD3340 : 0xB82A2225;
            int accent = isHoveredOrFocused() ? 0xFFFFADB2 : 0xFF8A3B43;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            graphics.fill(getX(), getY(), getX() + (compact ? 1 : 3),
                    getY() + height, accent);
            int color = active ? 0xFFF8F1EC : 0xFF7F7577;
            graphics.drawCenteredString(font, getMessage(),
                    getX() + width / 2, getY() + (height - 8) / 2, color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
