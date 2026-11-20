package dev.bladetetra.export;

import com.mojang.logging.LogUtils;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
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
    private Button modeButton;
    private StandaloneBladeExport.ExportMode exportMode = StandaloneBladeExport.ExportMode.SEALED;
    private StandaloneBladeExport.ExportMode requestedMode;
    private boolean confirmed;
    private BladeExportArchive.Metadata requestedMetadata;
    private BladeExportRecipe requestedRecipe;
    private Component status;
    private int left;
    private int top;

    private BladeSealScreen(BladeSealPackets.Preview preview) {
        super(Component.translatable("item.blade_tetra.blade_seal"));
        this.preview = preview;
    }

    static void open(BladeSealPackets.Preview preview) {
        Minecraft.getInstance().setScreen(new BladeSealScreen(preview));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = Math.max(0, (height - 236) / 2);
        name = field(left, top + 51, PANEL_WIDTH, 100, "blade_tetra.export.name",
                name == null ? preview.title().getString() : name.getValue());
        addon = field(left, top + 78, 222, 50, "blade_tetra.export.id",
                addon == null ? "blade_" + preview.token().toString().substring(0, 8) : addon.getValue());
        version = field(left + 228, top + 78, 72, 30, "blade_tetra.export.version",
                version == null ? "1.0.0" : version.getValue());
        author = field(left, top + 105, 105, 64, "blade_tetra.export.author",
                author == null ? "" : author.getValue());
        description = field(left + 111, top + 105, 189, 800, "blade_tetra.export.description",
                description == null ? "" : description.getValue());
        for (int index = 0; index < ingredients.length; index++) {
            String value = ingredients[index] != null ? ingredients[index].getValue()
                    : switch (index) {
                        case 1, 4 -> "minecraft:iron_ingot";
                        case 7 -> "minecraft:stick";
                        default -> "";
                    };
            ingredients[index] = field(left + 101 * (index % 3), top + 132 + ROW_HEIGHT * (index / 3),
                    98, 96, "blade_tetra.export.ingredient", value);
        }
        confirm = addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.confirm"),
                button -> request()).bounds(left, top + 186, 143, 20).build());
        confirm.active = !confirmed;
        modeButton = addRenderableWidget(Button.builder(modeLabel(), button -> {
            exportMode = exportMode == StandaloneBladeExport.ExportMode.SEALED
                    ? StandaloneBladeExport.ExportMode.NATIVE : StandaloneBladeExport.ExportMode.SEALED;
            button.setMessage(modeLabel());
        }).bounds(left + 151, top + 186, 149, 20).build());
        modeButton.active = !confirmed;
    }

    private Component modeLabel() {
        return Component.translatable(exportMode == StandaloneBladeExport.ExportMode.SEALED
                ? "blade_tetra.export.mode.sealed" : "blade_tetra.export.mode.native");
    }

    private EditBox field(int x, int y, int width, int limit, String key, String value) {
        var field = new EditBox(font, x, y, width, 15, Component.translatable(key));
        field.setMaxLength(limit);
        field.setValue(value);
        return addRenderableWidget(field);
    }

    private void request() {
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
            requestedMode = exportMode;
            confirmed = true;
            confirm.active = false;
            modeButton.active = false;
            status = Component.translatable("blade_tetra.export.waiting");
            ModNetwork.CHANNEL.sendToServer(new BladeSealPackets.Confirm(preview.token()));
        } catch (IllegalArgumentException error) {
            status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
        }
    }

    static void receive(BladeSealPackets.Result result) {
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || !(client.screen instanceof BladeSealScreen screen)
                || !screen.preview.token().equals(result.token()) || screen.requestedMetadata == null) return;
        var metadata = screen.requestedMetadata;
        var recipe = screen.requestedRecipe;
        var selectedMode = screen.requestedMode;
        screen.requestedMetadata = null;
        screen.requestedRecipe = null;
        screen.requestedMode = null;
        if (result.snapshot().isEmpty()) {
            screen.status = Component.translatable("blade_tetra.export.expired");
            return;
        }
        try {
            var snapshot = BladeSealPackets.decodeSnapshot(result.snapshot());
            var blade = new ItemStack(ModItems.MODULAR_SLASHBLADE.get());
            blade.setTag(TagParser.parseTag(result.appearanceTag()));
            var resources = client.getResourceManager();
            var art = BladeExportArt.bake(metadata.modId(), snapshot, blade, resources);
            var file = StandaloneBladeExport.write(client.gameDirectory.toPath(), resources,
                    snapshot, metadata, recipe, art, selectedMode);
            screen.status = Component.translatable("blade_tetra.export.saved");
            if (client.player != null) {
                client.player.sendSystemMessage(Component.translatable("blade_tetra.export.path", file.toAbsolutePath().normalize()));
            }
        } catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException error) {
            LogUtils.getLogger().warn("Failed to seal blade", error);
            screen.status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
        }
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
        graphics.drawWordWrap(font, Component.translatable("blade_tetra.export.placeholder"), left, top + 17, PANEL_WIDTH, 0xFFD480);
        graphics.drawString(font, Component.translatable("blade_tetra.export.name"), left, top + 40, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.id"), left, top + 67, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.version"), left + 228, top + 67, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.author"), left, top + 94, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.description"), left + 111, top + 94, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.recipe"), left, top + 121, 0xFFFFFF);
        if (status != null) {
            graphics.drawWordWrap(font, status, left, top + 210, PANEL_WIDTH, 0xFFFFFF);
        }
    }
}
