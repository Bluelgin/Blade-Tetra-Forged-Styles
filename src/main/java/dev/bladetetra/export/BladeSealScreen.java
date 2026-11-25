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

import java.io.IOException;
import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;

final class BladeSealScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private final BladeSealPackets.Preview preview;
    private final BladeSealWorkbenchScreen workbench;
    private EditBox name;
    private EditBox modTitle;
    private EditBox chineseName;
    private EditBox lore;
    private EditBox chineseLore;
    private EditBox logo;
    private EditBox addon;
    private EditBox version;
    private EditBox author;
    private EditBox description;
    private Button confirm;
    private Button awakeningConsent;
    private boolean awakeningAcknowledged;
    private Button backplateModeButton;
    private EditBox backplatePath;
    private CustomBackplateImage.Mode backplateMode = CustomBackplateImage.Mode.ORIGINAL;
    private CustomBackplateImage.Selection requestedBackplate;
    private BladeExportSnapshot approvedSnapshot;
    private ItemStack approvedBlade;
    private boolean confirmed;
    private BladeExportArchive.Metadata requestedMetadata;
    private Component status;
    private Path published;
    private String acquireCommand;
    private int left;
    private int top;
    private int scrollPixels;
    private int panelWidth;
    private int authorColumnWidth;

    private BladeSealScreen(BladeSealPackets.Preview preview, BladeSealWorkbenchScreen workbench) {
        super(Component.translatable("blade_tetra.export.studio"));
        this.preview = preview;
        this.workbench = workbench;
    }

    static void open(BladeSealPackets.Preview preview) {
        var client = Minecraft.getInstance();
        if (client.screen instanceof BladeSealWorkbenchScreen workbench
                && workbench.getMenu().containerId == preview.menuId()) {
            client.setScreen(new BladeSealScreen(preview, workbench));
        }
    }

    @Override
    protected void init() {
        scrollPixels = 0;
        panelWidth = Math.max(120, Math.min(PANEL_WIDTH, width - 12));
        authorColumnWidth = Math.min(105, Math.max(60, panelWidth / 3));
        left = (width - panelWidth) / 2;
        top = Math.max(0, (height - (preview.omitsAwakening() ? 560 : 480)) / 2);
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
        modTitle = field(left, top + 132, panelWidth, 100, "blade_tetra.export.mod_name",
                modTitle == null ? preview.title().getString() : modTitle.getValue());
        chineseName = field(left, top + 159, panelWidth, 100, "blade_tetra.export.chinese_name",
                chineseName == null ? "" : chineseName.getValue());
        lore = field(left, top + 186, panelWidth, 800, "blade_tetra.export.lore",
                lore == null ? "" : lore.getValue());
        chineseLore = field(left, top + 213, panelWidth, 800, "blade_tetra.export.chinese_lore",
                chineseLore == null ? "" : chineseLore.getValue());
        logo = field(left, top + 240, panelWidth - 54, 1024, "blade_tetra.export.logo",
                logo == null ? "" : logo.getValue());
        addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.backplate.browse"),
                button -> chooseImage(true)).bounds(left + panelWidth - 49, top + 238, 49, 20).build());
        int modeWidth = Math.min(87, panelWidth / 3);
        int browseWidth = Math.min(48, panelWidth / 4);
        int pictureWidth = panelWidth - modeWidth - browseWidth - 8;
        backplateModeButton = addRenderableWidget(Button.builder(backplateLabel(),
                button -> cycleBackplateMode()).bounds(left, top + 269, modeWidth, 20).build());
        backplatePath = field(left + modeWidth + 4, top + 270, pictureWidth, 1024,
                "blade_tetra.export.backplate_file",
                backplatePath == null ? "" : backplatePath.getValue());
        addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.backplate.browse"),
                button -> chooseImage(false)).bounds(left + modeWidth + 4 + pictureWidth + 4,
                        top + 269, browseWidth, 20).build());
        if (preview.omitsAwakening()) {
            awakeningConsent = addRenderableWidget(Button.builder(awakeningConsentLabel(),
                    button -> {
                        awakeningAcknowledged = !awakeningAcknowledged;
                        awakeningConsent.setMessage(awakeningConsentLabel());
                        confirm.active = !confirmed && preview.ready() && awakeningAcknowledged;
                    }).bounds(left, top + 340, panelWidth, 20).build());
        }
        confirm = addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.confirm"),
                button -> request()).bounds(left, top + (preview.omitsAwakening() ? 366 : 298),
                        panelWidth, 20).build());
        int projectRow = top + (preview.omitsAwakening() ? 395 : 327);
        addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.project.save"),
                button -> saveProject()).bounds(left, projectRow, panelWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.project.open"),
                button -> minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(open -> {
                    minecraft.setScreen(this);
                    if (open) openProject();
                }, Component.translatable("blade_tetra.export.project.replace_form"),
                        Component.literal(addon.getValue())))).bounds(left + panelWidth / 2 + 2, projectRow,
                panelWidth - panelWidth / 2 - 2, 20).build());
        confirm.active = !confirmed && preview.ready()
                && (!preview.omitsAwakening() || awakeningAcknowledged);
        if (published != null) addResultButtons();
    }

    private void addResultButtons() {
        int resultRow = top + (preview.omitsAwakening() ? 502 : 434);
        String[] labels = {"directory", "path", "command"};
        Runnable[] actions = {
                () -> net.minecraft.Util.getPlatform().openFile(published.getParent().toFile()),
                () -> minecraft.keyboardHandler.setClipboard(published.toString()),
                () -> minecraft.keyboardHandler.setClipboard(acquireCommand)
        };
        for (int index = 0; index < labels.length; index++) {
            Runnable action = actions[index];
            int start = left + panelWidth * index / labels.length;
            int end = left + panelWidth * (index + 1) / labels.length;
            addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.result." + labels[index]),
                    button -> action.run()).bounds(start, resultRow, end - start - 2, 20).build());
        }
    }

    private Component awakeningConsentLabel() {
        return Component.translatable(awakeningAcknowledged
                ? "blade_tetra.export.awakening.acknowledged"
                : "blade_tetra.export.awakening.acknowledge");
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

    private void chooseImage(boolean logoOnly) {
        Thread picker = new Thread(() -> {
            try {
                FileDialog dialog = new FileDialog((Frame) null, null, FileDialog.LOAD);
                dialog.setVisible(true);
                String filename = dialog.getFile();
                String directory = dialog.getDirectory();
                dialog.dispose();
                if (filename != null && directory != null) {
                    String selected = Path.of(directory, filename).toAbsolutePath().toString();
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().screen != this) return;
                        if (logoOnly) {
                            logo.setValue(selected);
                        } else {
                            backplatePath.setValue(selected);
                            if (backplateMode == CustomBackplateImage.Mode.ORIGINAL) cycleBackplateMode();
                        }
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

    private void saveProject() {
        try {
            var project = new BladeStudioProject(1, addon.getValue().strip(), version.getValue().strip(),
                    modTitle.getValue().strip(), name.getValue().strip(), chineseName.getValue().strip(),
                    author.getValue().strip(), description.getValue().strip(), lore.getValue().strip(),
                    chineseLore.getValue().strip(), logo.getValue().strip(), backplateMode,
                    backplatePath.getValue().strip());
            var output = BladeStudioProject.file(minecraft.gameDirectory.toPath(), project.addon());
            if (java.nio.file.Files.exists(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(replace -> {
                    minecraft.setScreen(this);
                    if (replace) saveProject(project, output, true);
                }, Component.translatable("blade_tetra.export.project.replace"), Component.literal(project.addon())));
            } else {
                saveProject(project, output, false);
            }
        } catch (IOException | IllegalArgumentException failure) {
            status = Component.translatable("blade_tetra.export.blocked", failure.getMessage());
        }
    }

    private void saveProject(BladeStudioProject project, Path file, boolean replace) {
        try {
            project.save(file, replace);
            status = Component.translatable("blade_tetra.export.project.saved", project.addon());
        } catch (IOException failure) {
            status = Component.translatable("blade_tetra.export.blocked", failure.getMessage());
        }
    }

    private void openProject() {
        try {
            var input = BladeStudioProject.file(minecraft.gameDirectory.toPath(), addon.getValue().strip());
            var project = BladeStudioProject.load(input);
            version.setValue(project.version());
            modTitle.setValue(project.title());
            name.setValue(project.bladeName());
            chineseName.setValue(project.chineseName());
            lore.setValue(project.lore());
            chineseLore.setValue(project.chineseLore());
            logo.setValue(project.logoPath());
            author.setValue(project.author());
            description.setValue(project.description());
            backplateMode = project.backplateMode();
            backplateModeButton.setMessage(backplateLabel());
            backplatePath.setValue(project.backplatePath());
            status = Component.translatable("blade_tetra.export.project.opened", project.addon());
        } catch (IOException | IllegalArgumentException failure) {
            status = Component.translatable("blade_tetra.export.blocked", failure.getMessage());
        }
    }

    private void request() {
        if (!preview.ready() || preview.omitsAwakening() && !awakeningAcknowledged) return;
        try {
            var metadata = new BladeExportArchive.Metadata("bt_export_" + addon.getValue().strip(),
                    version.getValue().strip(), modTitle.getValue().strip(), author.getValue().strip(),
                    description.getValue().strip(), name.getValue().strip(), chineseName.getValue().strip(),
                    lore.getValue().strip(), chineseLore.getValue().strip(),
                    CustomBackplateImage.readLogo(logo.getValue().strip()));
            if (net.minecraftforge.fml.ModList.get().isLoaded(metadata.modId())) {
                throw new IllegalArgumentException(Component.translatable("blade_tetra.export.id_installed").getString());
            }
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
                ModNetwork.CHANNEL.sendToServer(
                        new BladeSealPackets.Confirm(preview.token(), awakeningAcknowledged));
            }
        } catch (IOException | IllegalArgumentException error) {
            status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
        }
    }

    static void receive(BladeSealPackets.Result result) {
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || !(client.screen instanceof BladeSealScreen screen)
                || !screen.preview.token().equals(result.token()) || screen.requestedMetadata == null) return;
        if (result.snapshot().isEmpty()) {
            screen.confirmed = false;
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
                || requestedMetadata == null) return;
        try {
            var resources = Minecraft.getInstance().getResourceManager();
            var art = BladeExportArt.bake(requestedMetadata.modId(), approvedSnapshot,
                    approvedBlade, resources, requestedBackplate);
            var file = StandaloneBladeExport.write(Minecraft.getInstance().gameDirectory.toPath(),
                    resources, approvedSnapshot, requestedMetadata, art);
            published = file.toAbsolutePath().normalize();
            acquireCommand = "/bladeseal give @s " + requestedMetadata.modId() + ":sealed_blade";
            status = Component.translatable("blade_tetra.export.path", published.getFileName());
            addResultButtons();
            requestedMetadata = null;
            requestedBackplate = null;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.translatable("blade_tetra.export.path", file.toAbsolutePath().normalize()));
            }
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("Failed to seal blade", error);
            status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
            confirmed = false;
            confirm.active = preview.ready()
                    && (!preview.omitsAwakening() || awakeningAcknowledged);
        }
    }

    /** Keep every field reachable at GUI scale 4 and smaller, without shrinking fonts. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maximum = Math.max(0, (preview.omitsAwakening() ? 560 : 480) - height + 20);
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
    public void onClose() {
        if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu == workbench.getMenu()) {
            minecraft.setScreen(workbench);
        } else {
            super.onClose();
        }
    }

    @Override
    public void tick() {
        if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu != workbench.getMenu()) {
            minecraft.setScreen(null);
        }
    }

    Component exportStatus() {
        return status;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.fill(left - 5, top, left + panelWidth + 5,
                top + (preview.omitsAwakening() ? 560 : 480), 0xe8251d18);
        graphics.fill(left - 5, top, left + panelWidth + 5, top + 2, 0xffb68757);
        super.render(graphics, mouseX, mouseY, delta);
        if (left >= 103 && !workbench.getMenu().blade().isEmpty()) {
            var stack = workbench.getMenu().blade();
            int x = left - 54;
            int y = Math.max(50, height / 2);
            graphics.fill(left - 99, y - 48, left - 9, y + 48, 0xe8251d18);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(4, 4, 1);
            graphics.renderItem(stack, -8, -8);
            graphics.pose().popPose();
            if (mouseX >= left - 99 && mouseX < left - 9 && Math.abs(mouseY - y) < 48) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
        graphics.drawString(font, title, left, top + 4, 0xFFFFFF);
        graphics.drawWordWrap(font, Component.translatable("blade_tetra.export.acquisition"), left, top + 17, panelWidth, 0xFFD480);
        graphics.drawString(font, Component.translatable("blade_tetra.export.name"), left, top + 40, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.id"), left, top + 67, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.version"), left + panelWidth - 72, top + 67, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.author"), left, top + 94, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.description"), left + authorColumnWidth + 6, top + 94, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.mod_name"),
                left, top + 121, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.chinese_name"),
                left, top + 148, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.lore"),
                left, top + 175, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.chinese_lore"),
                left, top + 202, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("blade_tetra.export.logo"),
                left, top + 229, 0xFFFFFF);
        if (preview.omitsAwakening()) {
            graphics.drawWordWrap(font,
                    Component.translatable("blade_tetra.export.awakening.notice"),
                    left, top + 298, panelWidth, 0xFFD480);
        }
        int offset = preview.omitsAwakening() ? 68 : 0;
        graphics.drawWordWrap(font,
                Component.translatable(preview.ready()
                        ? "blade_tetra.export.preflight.ready" : "blade_tetra.export.preflight.blocked",
                        preview.summary()), left, top + 356 + offset, panelWidth,
                preview.ready() ? 0xA6E3B0 : 0xFF9C9C);
        if (status != null) {
            graphics.drawWordWrap(font, status, left, top + 412 + offset, panelWidth, 0xFFFFFF);
        }
    }
}
