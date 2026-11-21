package dev.bladetetra.export;

import com.mojang.logging.LogUtils;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class BladeSealScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int ROW_HEIGHT = 17;
    private final BladeSealPackets.Preview preview;
    private final EditBox[] ingredients = new EditBox[9];
    private EditBox name;
    private EditBox addon;
    private EditBox version;
    private EditBox author;
    private EditBox description;
    private Button confirm;
    private Button backplateModeButton;
    private EditBox backplatePath;
    private CustomBackplateImage.Mode backplateMode = CustomBackplateImage.Mode.ORIGINAL;
    private CustomBackplateImage.Selection requestedBackplate;
    private BladeExportSnapshot approvedSnapshot;
    private ItemStack approvedBlade;
    private boolean confirmed;
    private BladeExportArchive.Metadata requestedMetadata;
    private BladeExportRecipe requestedRecipe;
    private Component status;
    private int left;
    private int top;
    private int scrollPixels;
    private int panelWidth;
    private int authorColumnWidth;

    private BladeSealScreen(BladeSealPackets.Preview preview) {
        super(Component.translatable("item.blade_tetra.blade_seal"));
        this.preview = preview;
    }

    static void open(BladeSealPackets.Preview preview) {
        Minecraft.getInstance().setScreen(new BladeSealScreen(preview));
    }

    @Override
    protected void init() {
        scrollPixels = 0;
        panelWidth = Math.max(140, Math.min(PANEL_WIDTH, width - 12));
        authorColumnWidth = Math.min(105, Math.max(60, panelWidth / 3));
        left = (width - panelWidth) / 2;
        top = Math.max(0, (height - 365) / 2);
        name = field(left, top + 51, panelWidth, 100, "blade_tetra.export.name",
                name == null ? preview.title().getString() : name.getValue());
        addon = field(left, top + 78, panelWidth - 78, 50, "blade_tetra.export.id",
                addon == null ? "blade_" + preview.token().toString().substring(0, 8) : addon.getValue());
        version = field(left + panelWidth - 72, top + 78, 72, 30, "blade_tetra.export.version",
                version == null ? "1.0.0" : version.getValue());
        author = field(left, top + 105, authorColumnWidth, 64, "blade_tetra.export.author",
                author == null ? "" : author.getValue());
        description = field(left + authorColumnWidth + 6, top + 105,
                panelWidth - authorColumnWidth - 6, 800, "blade_tetra.export.description",
                description == null ? "" : description.getValue());
        for (int index = 0; index < ingredients.length; index++) {
            String value = ingredients[index] != null ? ingredients[index].getValue()
                    : switch (index) {
                        case 1, 4 -> "minecraft:iron_ingot";
                        case 7 -> "minecraft:stick";
                        default -> "";
                    };
            int column = index % 3;
            int fieldX = left + panelWidth * column / 3;
            int fieldEnd = left + panelWidth * (column + 1) / 3;
            ingredients[index] = field(fieldX, top + 132 + ROW_HEIGHT * (index / 3),
                    fieldEnd - fieldX - 2, 96, "blade_tetra.export.ingredient", value);
        }
        int modeWidth = Math.min(87, panelWidth / 3);
        int browseWidth = Math.min(48, panelWidth / 4);
        int pictureWidth = panelWidth - modeWidth - browseWidth - 8;
        backplateModeButton = addRenderableWidget(Button.builder(backplateLabel(),
                button -> cycleBackplateMode()).bounds(left, top + 188, modeWidth, 20).build());
        backplatePath = field(left + modeWidth + 4, top + 189, pictureWidth, 1024,
                "blade_tetra.export.backplate_file",
                backplatePath == null ? "" : backplatePath.getValue());
        addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.backplate.browse"),
                button -> chooseBackplate()).bounds(left + modeWidth + 4 + pictureWidth + 4,
                        top + 188, browseWidth, 20).build());
        confirm = addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.confirm"),
                button -> request()).bounds(left, top + 214, panelWidth, 20).build());
        confirm.active = !confirmed && preview.ready();
    }

    private Component backplateLabel() {
        return Component.translatable("blade_tetra.export.backplate."
                + backplateMode.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void cycleBackplateMode() {
        backplateMode = switch (backplateMode) {
            case ORIGINAL -> CustomBackplateImage.Mode.REPLACE;
            case REPLACE -> CustomBackplateImage.Mode.OVERLAY;
            case OVERLAY -> CustomBackplateImage.Mode.ORIGINAL;
        };
        backplateModeButton.setMessage(backplateLabel());
    }

    private void chooseBackplate() {
        Thread picker = new Thread(() -> {
            try {
                FileDialog dialog = new FileDialog((Frame) null, "Select backplate PNG or JPEG", FileDialog.LOAD);
                dialog.setVisible(true);
                String filename = dialog.getFile();
                String directory = dialog.getDirectory();
                dialog.dispose();
                if (filename != null && directory != null) {
                    String selected = Path.of(directory, filename).toAbsolutePath().toString();
                    Minecraft.getInstance().execute(() -> {
                        if (backplatePath != null) backplatePath.setValue(selected);
                        if (backplateMode == CustomBackplateImage.Mode.ORIGINAL) cycleBackplateMode();
                    });
                }
            } catch (RuntimeException | java.awt.AWTError failure) {
                Minecraft.getInstance().execute(() ->
                    status = Component.literal("Image dialog unavailable; type a file path instead"));
            }
        }, "BladeSealBackplatePicker");
        picker.setDaemon(true);
        picker.start();
    }

    private EditBox field(int x, int y, int width, int limit, String key, String value) {
        var field = new EditBox(font, x, y, width, 15, Component.translatable(key));
        field.setMaxLength(limit);
        field.setValue(value);
        return addRenderableWidget(field);
    }

    private void request() {
        if (!preview.ready()) return;
        try {
            var metadata = new BladeExportArchive.Metadata("bt_export_" + addon.getValue().strip(), version.getValue().strip(),
                    name.getValue().strip(), author.getValue().strip(), description.getValue().strip());
            var items = new ArrayList<ResourceLocation>();
            int occupied = 0;
            for (int index = 0; index < ingredients.length; index++) {
                String text = ingredients[index].getValue().strip();
                if (!text.isEmpty()) {
                    var id = ResourceLocation.tryParse(text);
                    if (id == null) throw new IllegalArgumentException("Invalid item ID: " + text);
                    items.add(id);
                    occupied |= 1 << index;
                }
            }
            requestedRecipe = new BladeExportRecipe(List.copyOf(items), occupied);
            requestedMetadata = metadata;
            requestedBackplate = new CustomBackplateImage.Selection(backplateMode, backplatePath.getValue().strip());
            if (backplateMode != CustomBackplateImage.Mode.ORIGINAL && requestedBackplate.filename().isBlank()) {
                throw new IllegalArgumentException(
                        Component.translatable("blade_tetra.export.backplate.picture_required").getString());
            }
            confirmed = true;
            confirm.active = false;
            status = Component.translatable("blade_tetra.export.waiting");
            if (approvedSnapshot != null && approvedBlade != null) {
                exportApproved();
            } else {
                ModNetwork.CHANNEL.sendToServer(new BladeSealPackets.Confirm(preview.token()));
            }
        } catch (IllegalArgumentException error) {
            status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
        }
    }

    static void receive(BladeSealPackets.Result result) {
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || !(client.screen instanceof BladeSealScreen screen)
                || !screen.preview.token().equals(result.token()) || screen.requestedMetadata == null) return;
        if (result.snapshot().isEmpty()) {
            screen.confirmed = false;
            screen.confirm.active = screen.preview.ready();
            screen.status = Component.translatable("blade_tetra.export.expired");
            // A rejected/expired server authorization cannot be reused. Open a new seal.
            screen.confirm.active = false;
            return;
        }
        try {
            screen.approvedSnapshot = BladeSealPackets.decodeSnapshot(result.snapshot());
            screen.approvedBlade = new ItemStack(ModItems.MODULAR_SLASHBLADE.get());
            screen.approvedBlade.setTag(TagParser.parseTag(result.appearanceTag()));
            screen.exportApproved();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException error) {
            LogUtils.getLogger().warn("Failed to decode authorized blade", error);
            screen.status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
            screen.confirmed = false;
            screen.confirm.active = false;
        }
    }

    /** Reuse only the server-approved frozen stack on local packaging failures. */
    private void exportApproved() {
        if (approvedSnapshot == null || approvedBlade == null
                || requestedMetadata == null || requestedRecipe == null) return;
        try {
            var resources = Minecraft.getInstance().getResourceManager();
            var art = BladeExportArt.bake(requestedMetadata.modId(), approvedSnapshot,
                    approvedBlade, resources, requestedBackplate);
            var file = StandaloneBladeExport.write(Minecraft.getInstance().gameDirectory.toPath(),
                    resources, approvedSnapshot, requestedMetadata, requestedRecipe, art);
            status = Component.translatable("blade_tetra.export.saved");
            requestedMetadata = null;
            requestedRecipe = null;
            requestedBackplate = null;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.translatable("blade_tetra.export.path", file.toAbsolutePath().normalize()));
            }
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("Failed to seal blade", error);
            status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
            confirmed = false;
            confirm.active = preview.ready();
        }
    }

    /** Keep every field reachable at GUI scale 4 and smaller, without shrinking fonts. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maximum = Math.max(0, 365 - height + 20);
        if (maximum == 0) return super.mouseScrolled(mouseX, mouseY, delta);
        int next = Mth.clamp(scrollPixels - (int) Math.round(delta * 24), 0, maximum);
        int shift = scrollPixels - next;
        if (shift == 0) return true;
        scrollPixels = next;
        top += shift;
        for (var child : children()) {
            if (child instanceof AbstractWidget widget) {
                widget.setY(widget.getY() + shift);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawString(font, title, left, top + 4, 0xFFFFFF);
        graphics.drawWordWrap(font, Component.translatable("blade_tetra.export.placeholder"), left, top + 17, panelWidth, 0xFFD480);
        graphics.drawString(font, Component.translatable("blade_tetra.export.name"), left, top + 40, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.id"), left, top + 67, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.version"), left + panelWidth - 72, top + 67, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.author"), left, top + 94, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.description"), left + authorColumnWidth + 6, top + 94, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.recipe"), left, top + 121, 0xFFFFFF);
        graphics.drawWordWrap(font,
                Component.translatable(preview.ready()
                        ? "blade_tetra.export.preflight.ready" : "blade_tetra.export.preflight.blocked",
                        preview.summary()), left, top + 244, panelWidth, preview.ready() ? 0xA6E3B0 : 0xFF9C9C);
        if (status != null) {
            graphics.drawWordWrap(font, status, left, top + 311, panelWidth, 0xFFFFFF);
        }
    }
}
