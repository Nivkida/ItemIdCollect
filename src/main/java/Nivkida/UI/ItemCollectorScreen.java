package Nivkida.UI;

import Nivkida.Manager.ConfigManager;
import Nivkida.Manager.FileExporter;
import Nivkida.Manager.SelectedItemsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ItemCollectorScreen extends Screen {
    private final Minecraft mc = Minecraft.getInstance();
    private final List<Item> allItems;
    private List<Item> filtered;
    private int cols;
    private int rows;
    private int cellSize = 16;
    private final int margin = 12;
    private int startIndex = 0;
    private EditBox fileNameField;
    private EditBox idSearchBox;
    private EditBox advancedFilterBox;
    private int fileFieldX, fileFieldY;
    private int searchFieldX, searchFieldY;
    private int advFieldX, advFieldY;
    private final List<String> modList;
    private int modTabStart = 0;
    private String selectedMod = "all";
    private final LinkedHashMap<String, Boolean> typeState = new LinkedHashMap<>();
    private final Map<String, Button> typeButtons = new HashMap<>();
    private String lastIdSearch = "";
    private String lastAdvanced = "";
    private String lastSelectedMod = "all";
    private Set<String> lastActiveTypes = new HashSet<>();
    private final int rightPanelWidth = 300;
    private final int rightPanelPadding = 8;
    private int gridY0;
    private int gridX0;
    private Button modScrollLeft;
    private Button modScrollRight;
    private int visibleModTabs = 6;
    private List<Component> currentHelpText = null;

    public ItemCollectorScreen() {
        super(Component.translatable("screen.itemidcollect.title"));
        ConfigManager.ConfigData cfg = ConfigManager.load();
        this.cols = Math.max(4, cfg.cols);
        this.rows = Math.max(3, cfg.rows);
        this.allItems = ForgeRegistries.ITEMS.getValues().stream().collect(Collectors.toList());
        this.allItems.sort(Comparator.comparing(i -> {
            ResourceLocation k = ForgeRegistries.ITEMS.getKey(i);
            return k == null ? new ItemStack(i).getHoverName().getString() : k.toString();
        }));
        this.filtered = new ArrayList<>(allItems);
        Set<String> mods = this.allItems.stream()
                .map(it -> {
                    ResourceLocation k = ForgeRegistries.ITEMS.getKey(it);
                    return k == null ? "minecraft" : k.getNamespace();
                })
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> ml = new ArrayList<>();
        ml.add("all");
        ml.addAll(mods);
        this.modList = ml;
        this.selectedMod = cfg.lastMod != null ? cfg.lastMod : "all";
        String[] types = new String[]{"Sword", "Tools", "Armor", "Food", "Block", "Bow", "Shield", "Potion", "Other"};
        Set<String> selectedTypesFromConfig = cfg.selectedTypes != null ? cfg.selectedTypes : new HashSet<>();
        for (String t : types)
            typeState.put(t, selectedTypesFromConfig.contains(t));
    }

    @Override
    protected void init() {
        super.init();

        int leftAreaWidth = this.width - rightPanelWidth - margin * 2;
        int computed = Math.max(10, Math.min(24, leftAreaWidth / cols));
        cellSize = Math.max(10, (int) (computed * 0.9));

        // Calculate positions for UI elements to avoid overlap
        int currentY = 10;

        // Export buttons at the top
        this.addRenderableWidget(Button.builder(Component.translatable("button.export_txt"), b -> exportTxt())
                .bounds(margin, currentY, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("button.export_json"), b -> exportJson())
                .bounds(margin + 110, currentY, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("button.clear_selection"), b -> SelectedItemsManager.clear())
                .bounds(margin + 220, currentY, 120, 20).build());

        currentY += 30;

        // Mod tabs with scrolling
        addModTabButtons(margin, currentY, leftAreaWidth);
        currentY += 25;

        // Type toggles - calculate how many rows we need
        int typeButtonWidth = 78;
        int typeButtonHeight = 16;
        int typeButtonSpacing = 4;
        int typeButtonsPerRow = Math.max(1, (leftAreaWidth - margin) / (typeButtonWidth + typeButtonSpacing));
        int typeRows = (int) Math.ceil((double) typeState.size() / typeButtonsPerRow);

        int tx = margin;
        int ty = currentY;

        for (String t : typeState.keySet()) {
            boolean on = typeState.get(t);
            Button btn = Button.builder(Component.literal((on ? "[x] " : "[ ] ") + t), b -> {
                boolean cur = typeState.get(t);
                typeState.put(t, !cur);
                b.setMessage(Component.literal((!cur ? "[x] " : "[ ] ") + t));
                saveConfig();
                rebuildFilteredIfNeeded(true);
            }).bounds(tx, ty, typeButtonWidth, typeButtonHeight).build();
            typeButtons.put(t, btn);
            this.addRenderableWidget(btn);

            tx += typeButtonWidth + typeButtonSpacing;
            if (tx + typeButtonWidth > leftAreaWidth - margin) {
                tx = margin;
                ty += typeButtonHeight + 2;
            }
        }

        int typeButtonsBottomY = ty + typeButtonHeight;
        currentY = typeButtonsBottomY + 10;

        // Search fields
        searchFieldX = margin;
        searchFieldY = currentY;
        idSearchBox = new EditBox(this.font, searchFieldX, searchFieldY, leftAreaWidth - margin - 10, 18, Component.translatable("field.search"));
        idSearchBox.setValue(ConfigManager.load().lastSearch != null ? ConfigManager.load().lastSearch : "");
        this.addRenderableWidget(idSearchBox);

        currentY += 25;

        // Advanced filter
        advFieldX = margin;
        advFieldY = currentY;
        advancedFilterBox = new EditBox(this.font, advFieldX, advFieldY, leftAreaWidth - margin - 10, 18, Component.translatable("field.advanced"));
        advancedFilterBox.setValue("");
        this.addRenderableWidget(advancedFilterBox);

        currentY += 25;

        // Filename field
        fileFieldX = margin;
        fileFieldY = currentY;
        fileNameField = new EditBox(this.font, fileFieldX, fileFieldY, leftAreaWidth - margin - 10, 18, Component.translatable("field.filename"));
        fileNameField.setValue("items_dump");
        this.addRenderableWidget(fileNameField);

        currentY += 30;

        // Grid position
        gridX0 = (leftAreaWidth - (cols * cellSize)) / 2 + margin;
        gridY0 = currentY;

        // Prev/Next buttons
        int leftCenterX = gridX0 + (cols * cellSize) / 2;
        int btnW = 90;
        int spacing = 12;
        int btnY = gridY0 + rows * cellSize + 8;
        int leftX = leftCenterX - (btnW * 2 + spacing) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("button.prev"), b -> {
            startIndex = Math.max(0, startIndex - cols * rows);
        }).bounds(leftX, btnY, btnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("button.next"), b -> {
            startIndex = Math.min(Math.max(0, filtered.size() - cols * rows), startIndex + cols * rows);
        }).bounds(leftX + btnW + spacing, btnY, btnW, 20).build());

        // Initial filter
        rebuildFilteredIfNeeded(true);
    }

    private void addModTabButtons(int startX, int startY, int leftAreaWidth) {
        int x = startX;
        int y = startY;

        // Left scroll button
        modScrollLeft = Button.builder(Component.literal("◀"), b -> {
            modTabStart = Math.max(0, modTabStart - visibleModTabs);
            updateModTabButtons();
        }).bounds(x, y, 20, 16).build();
        this.addRenderableWidget(modScrollLeft);
        x += 22;

        // Calculate how many mod tabs we can show
        int modTabWidth = 70;
        int modTabSpacing = 2;
        visibleModTabs = Math.min((leftAreaWidth - 44) / (modTabWidth + modTabSpacing), modList.size());

        // Mod tabs
        for (int i = 0; i < visibleModTabs; i++) {
            final int idx = modTabStart + i;
            if (idx >= modList.size()) break;

            String modid = modList.get(idx);
            String displayName = modid.length() > 10 ? modid.substring(0, 10) + "..." : modid;
            Component label = Component.literal(modid.equals(selectedMod) ? "[" + displayName + "]" : displayName);

            Button modBtn = Button.builder(label, b -> {
                selectedMod = modid;
                saveConfig();
                rebuildFilteredIfNeeded(true);
            }).bounds(x, y, modTabWidth, 16).build();

            this.addRenderableWidget(modBtn);
            x += modTabWidth + modTabSpacing;
        }

        // Right scroll button
        modScrollRight = Button.builder(Component.literal("▶"), b -> {
            modTabStart = Math.min(modList.size() - visibleModTabs, modTabStart + visibleModTabs);
            updateModTabButtons();
        }).bounds(x, y, 20, 16).build();
        this.addRenderableWidget(modScrollRight);

        // Update button states
        updateModTabButtons();
    }

    private void updateModTabButtons() {
        modScrollLeft.active = modTabStart > 0;
        modScrollRight.active = modTabStart + visibleModTabs < modList.size();
    }

    private void exportTxt() {
        File file = FileExporter.exportToTxt(fileNameField.getValue(), SelectedItemsManager.getAll());
        if (file != null && mc.player != null) {
            String path = file.getParentFile().getAbsolutePath();
            Component comp = Component.translatable("message.export_success", file.getName()).withStyle(s -> s.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path)));
            mc.player.sendSystemMessage(comp);
        } else if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("message.export_failed").withStyle(ChatFormatting.RED));
        }
    }

    private void exportJson() {
        File file = FileExporter.exportToJson(fileNameField.getValue(), SelectedItemsManager.getAll());
        if (file != null && mc.player != null) {
            String path = file.getParentFile().getAbsolutePath();
            Component comp = Component.translatable("message.export_success", file.getName()).withStyle(s -> s.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path)));
            mc.player.sendSystemMessage(comp);
        } else if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("message.export_failed").withStyle(ChatFormatting.RED));
        }
    }

    private void saveConfig() {
        ConfigManager.ConfigData cd = new ConfigManager.ConfigData();
        cd.cols = this.cols;
        cd.rows = this.rows;
        cd.lastMod = this.selectedMod;
        cd.lastSearch = idSearchBox != null ? idSearchBox.getValue() : "";
        cd.selectedTypes = new HashSet<>(typeState.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toSet()));
        ConfigManager.save(cd);
    }

    private void rebuildFilteredIfNeeded(boolean force) {
        String idSearch = idSearchBox != null ? idSearchBox.getValue().trim() : "";
        String adv = advancedFilterBox != null ? advancedFilterBox.getValue().trim() : "";
        Set<String> activeTypes = typeState.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toSet());
        boolean changed = force || !Objects.equals(idSearch, lastIdSearch) || !Objects.equals(adv, lastAdvanced) || !Objects.equals(selectedMod, lastSelectedMod) || !Objects.equals(activeTypes, lastActiveTypes);
        if (!changed) return;

        lastIdSearch = idSearch;
        lastAdvanced = adv;
        lastSelectedMod = selectedMod;
        lastActiveTypes = new HashSet<>(activeTypes);

        filtered = allItems.stream().filter(item -> {
            ResourceLocation k = ForgeRegistries.ITEMS.getKey(item);
            String namespace = k == null ? "minecraft" : k.getNamespace();
            String id = k == null ? "" : k.toString();

            if (!"all".equals(selectedMod) && !namespace.equalsIgnoreCase(selectedMod)) return false;

            if (!idSearch.isEmpty()) {
                String display = new ItemStack(item).getHoverName().getString();
                if (!id.toLowerCase(Locale.ROOT).contains(idSearch.toLowerCase(Locale.ROOT)) &&
                        !display.toLowerCase(Locale.ROOT).contains(idSearch.toLowerCase(Locale.ROOT)))
                    return false;
            }

            if (!activeTypes.isEmpty()) {
                boolean ok = false;
                if (activeTypes.contains("Sword") && item instanceof SwordItem) ok = true;
                if (activeTypes.contains("Tools") && (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem || item instanceof HoeItem)) ok = true;
                if (activeTypes.contains("Armor") && item instanceof ArmorItem) ok = true;
                if (activeTypes.contains("Food") && item.isEdible()) ok = true;
                if (activeTypes.contains("Block") && item instanceof BlockItem) ok = true;
                if (activeTypes.contains("Bow") && (item instanceof BowItem || item instanceof CrossbowItem)) ok = true;
                if (activeTypes.contains("Shield") && item instanceof ShieldItem) ok = true;
                if (activeTypes.contains("Potion") && item instanceof PotionItem) ok = true;
                if (activeTypes.contains("Other") && !(item instanceof SwordItem) && !(item instanceof PickaxeItem) &&
                        !(item instanceof AxeItem) && !(item instanceof ShovelItem) && !(item instanceof HoeItem) &&
                        !(item instanceof ArmorItem) && !(item instanceof BlockItem) && !item.isEdible())
                    ok = true;
                if (!ok) return false;
            }

            if (!adv.isEmpty() && adv.startsWith("nbt:")) {
                String expr = adv.substring(4).trim();
                String keyPart = expr;
                String op = null;
                String val = null;

                if (expr.contains(">")) {
                    String[] sp = expr.split(">", 2);
                    keyPart = sp[0].trim();
                    op = ">";
                    val = sp[1].trim();
                } else if (expr.contains("<")) {
                    String[] sp = expr.split("<", 2);
                    keyPart = sp[0].trim();
                    op = "<";
                    val = sp[1].trim();
                } else if (expr.contains("=")) {
                    String[] sp = expr.split("=", 2);
                    keyPart = sp[0].trim();
                    op = "=";
                    val = sp[1].trim();
                } else {
                    keyPart = expr.trim();
                }

                ItemStack st = new ItemStack(item);
                CompoundTag tag = st.getTag();
                if (tag == null || !tag.contains(keyPart)) return false;

                if (op != null) {
                    String raw = tag.getString(keyPart);
                    if ((op.equals(">") || op.equals("<"))) {
                        try {
                            double dtag = Double.parseDouble(raw);
                            double dval = Double.parseDouble(val);
                            if (op.equals(">") && !(dtag > dval)) return false;
                            if (op.equals("<") && !(dtag < dval)) return false;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    } else {
                        if (!raw.equals(val)) return false;
                    }
                }
            }
            return true;
        }).collect(Collectors.toList());

        int pageSize = cols * rows;
        if (startIndex >= filtered.size()) startIndex = Math.max(0, filtered.size() - pageSize);
    }

    @Override
    public void tick() {
        rebuildFilteredIfNeeded(false);
        super.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);

        // Сбрасываем текст помощи
        currentHelpText = null;

        // Проверяем наведение на текстовые поля и устанавливаем соответствующий текст помощи
        if (isMouseOver(mouseX, mouseY, fileFieldX, fileFieldY, fileNameField.getWidth(), 18)) {
            currentHelpText = List.of(
                    Component.translatable("help.filename.line1"),
                    Component.translatable("help.filename.line2")
            );
        } else if (isMouseOver(mouseX, mouseY, searchFieldX, searchFieldY, idSearchBox.getWidth(), 18)) {
            currentHelpText = List.of(
                    Component.translatable("help.search.line1"),
                    Component.translatable("help.search.line2")
            );
        } else if (isMouseOver(mouseX, mouseY, advFieldX, advFieldY, advancedFilterBox.getWidth(), 18)) {
            currentHelpText = List.of(
                    Component.translatable("help.advanced.line1"),
                    Component.translatable("help.advanced.example1"),
                    Component.translatable("help.advanced.example2"),
                    Component.translatable("help.advanced.example3")
            );
        }

        // Draw grid
        int idx = startIndex;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = gridX0 + c * cellSize;
                int y = gridY0 + r * cellSize;
                if (idx >= filtered.size()) {
                    idx++;
                    continue;
                }
                Item item = filtered.get(idx);
                ItemStack stack = new ItemStack(item);

                // Cell background
                g.fill(x - 1, y - 1, x + cellSize - 1, y + cellSize - 1, 0xFF1E1E1E);

                // Item render
                g.renderItem(stack, x + 1, y + 1);

                // Selection highlight
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
                if (key != null && SelectedItemsManager.isSelected(key)) {
                    g.fill(x - 1, y - 1, x + cellSize - 1, y + cellSize - 1, 0x80FFFFFF);
                }
                idx++;
            }
        }

        // Draw widgets
        super.render(g, mouseX, mouseY, partialTicks);

        // RIGHT PANEL
        int rightX = this.width - rightPanelWidth - margin;
        int rightY = 20;
        int rightW = rightPanelWidth;
        int rightH = this.height - 40;

        // Panel background
        g.fill(rightX, rightY, rightX + rightW, rightY + rightH, 0xF0100010);
        g.fill(rightX + 2, rightY + 2, rightX + rightW - 2, rightY + rightH - 2, 0xCC000000);

        // Panel title
        g.drawString(this.font, Component.translatable("panel.details.title").getString(), rightX + rightPanelPadding, rightY + rightPanelPadding, 0xFFFFFF, false);

        // Hovered item details or instructions
        int hovered = getIndexAt(mouseX, mouseY);
        int contentY = rightY + rightPanelPadding + 14;
        int lineH = this.font.lineHeight + 2;

        if (hovered != -1 && hovered < filtered.size()) {
            Item item = filtered.get(hovered);
            ItemStack stack = new ItemStack(item);
            List<String> info = buildDetailedInfo(item, stack);

            for (String line : info) {
                if (contentY > rightY + rightH - 20) break;
                g.drawString(this.font, line, rightX + rightPanelPadding, contentY, 0xFFFFFF, false);
                contentY += lineH;
            }
        } else if (currentHelpText != null) {
            // Отображаем текст помощи вместо стандартного сообщения
            for (Component line : currentHelpText) {
                if (contentY > rightY + rightH - 20) break;
                g.drawString(this.font, line, rightX + rightPanelPadding, contentY, 0xBBBBBB, false);
                contentY += lineH;
            }
        } else {
            // Стандартное сообщение, когда нет наведения
            List<Component> help = List.of(
                    Component.translatable("panel.details.empty_line1"),
                    Component.translatable("panel.details.empty_line2"),
                    Component.translatable("panel.details.empty_line3")
            );

            for (Component line : help) {
                if (contentY > rightY + rightH - 20) break;
                g.drawString(this.font, line, rightX + rightPanelPadding, contentY, 0xBBBBBB, false);
                contentY += lineH;
            }
        }
    }

    private static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private List<String> buildDetailedInfo(Item item, ItemStack stack) {
        List<String> lines = new ArrayList<>();

        // Display name
        lines.add(stack.getHoverName().getString());

        // ID
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        lines.add("ID: " + (id == null ? "unknown" : id.toString()));

        // Model path
        if (id != null) lines.add("Model path: " + id.getNamespace() + ":item/" + id.getPath());

        // Basic properties
        lines.add("Max stack: " + item.getMaxStackSize(new ItemStack(item)));

        int maxDamage = stack.getMaxDamage();
        if (maxDamage > 0) lines.add("Max damage: " + maxDamage);

        // Armor slot
        if (item instanceof ArmorItem a) {
            EquipmentSlot slot = a.getType().getSlot();
            lines.add("Armor slot: " + a.getType().getName() + " (" + slot.getName() + ")");
        }

        // Food info
        if (item.isEdible()) {
            lines.add("Edible: yes");
            try {
                var props = item.getFoodProperties();
                if (props != null) {
                    lines.add(" Nutrition: " + props.getNutrition() + ", Saturation: " + props.getSaturationModifier());
                }
            } catch (Throwable ignored) {
            }
        }

        // Enchantments
        Map<Enchantment, Integer> ench = EnchantmentHelper.getEnchantments(stack);
        if (!ench.isEmpty()) {
            lines.add("Enchantments:");
            ench.forEach((en, lvl) -> lines.add(" " + en.getDescriptionId() + " lvl " + lvl));
        }

        // NBT preview
        CompoundTag tag = stack.getTag();
        if (tag != null && !tag.isEmpty()) {
            String s = tag.toString();
            if (s.length() > 800) s = s.substring(0, 800) + "...";
            lines.add("NBT: " + s);

            // Common NBT tags
            if (tag.contains("CustomModelData")) lines.add("CustomModelData: " + tag.getInt("CustomModelData"));
            if (tag.contains("Unbreakable")) lines.add("Unbreakable: " + tag.getBoolean("Unbreakable"));
            if (tag.contains("Damage")) lines.add("Damage (nbt): " + tag.getInt("Damage"));
        }

        // Other flags
        if (stack.isEnchanted()) lines.add("Has enchantments");
        if (stack.hasCustomHoverName()) lines.add("Has custom name");

        return lines;
    }

    private int getIndexAt(int mouseX, int mouseY) {
        if (mouseY < gridY0 || mouseY > gridY0 + rows * cellSize) return -1;
        if (mouseX < gridX0 || mouseX > gridX0 + cols * cellSize) return -1;

        int cx = (mouseX - gridX0) / cellSize;
        int cy = (mouseY - gridY0) / cellSize;

        if (cx < 0 || cx >= cols || cy < 0 || cy >= rows) return -1;

        int idx = startIndex + cy * cols + cx;
        if (idx < 0 || idx >= filtered.size()) return -1;

        return idx;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int idx = getIndexAt((int) mouseX, (int) mouseY);
        if (idx != -1 && idx < filtered.size()) {
            Item item = filtered.get(idx);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null) {
                SelectedItemsManager.toggle(id);
                saveConfig();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}