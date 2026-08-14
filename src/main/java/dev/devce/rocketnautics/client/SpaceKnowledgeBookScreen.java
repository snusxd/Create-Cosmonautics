package dev.devce.rocketnautics.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Client-only reference book with a two-page spread. */
public class SpaceKnowledgeBookScreen extends Screen {
    private static final ResourceLocation PAGE_TEXTURE = RocketNautics.path("textures/gui/book/page.png");
    private static final String PAGE_KEY = "gui.rocketnautics.book_page.";
    private static final int BOOK_WIDTH = 266;
    private static final int BOOK_HEIGHT = 152;
    private static final int BOOK_VERTICAL_OFFSET = 12;
    private static final int TEXT_WIDTH = 107;
    private static final int TEXT_HEIGHT = 138;
    private static final int TEXT_PADDING = 3;
    private static final int TEXT_CONTENT_WIDTH = TEXT_WIDTH - TEXT_PADDING * 2;
    private static final int TEXT_CONTENT_HEIGHT = TEXT_HEIGHT - TEXT_PADDING * 2;
    private static final int LEFT_PAGE_X = 14;
    private static final int RIGHT_PAGE_X = 145;
    private static final int PAGE_Y = 3;
    private static final int TEXT_COLOR = 0xFF5C5948;
    private static final int MAX_PAGES = 512;
    private static final Pattern COLORED_TEXT = Pattern.compile("\\[([^\\]]*)]\\(#([0-9A-Fa-f]{6})\\)");

    private final int pageCount;
    private final Map<ResourceLocation, DrawingSize> drawingSizes = new HashMap<>();
    private int leftPage = 1;
    private PageButton previousPageButton;
    private PageButton nextPageButton;

    public SpaceKnowledgeBookScreen() {
        super(Component.translatable("item.rocketnautics.space_book"));
        this.pageCount = findPageCount();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new SpaceKnowledgeBookScreen());
    }

    @Override
    protected void init() {
        int bookX = (width - BOOK_WIDTH) / 2;
        int bookY = getBookY();

        previousPageButton = addRenderableWidget(new PageButton(
                bookX - 24, bookY + BOOK_HEIGHT / 2 - 10, false, button -> turnPage(-2), true));
        nextPageButton = addRenderableWidget(new PageButton(
                bookX + BOOK_WIDTH + 1, bookY + BOOK_HEIGHT / 2 - 10, true, button -> turnPage(2), true));

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 100, bookY + BOOK_HEIGHT + 8, 200, 20)
                .build());
        updatePageButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int bookX = (width - BOOK_WIDTH) / 2;
        int bookY = getBookY();
        graphics.blit(PAGE_TEXTURE, bookX, bookY, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, BOOK_WIDTH, BOOK_HEIGHT);

        renderPage(graphics, bookX + LEFT_PAGE_X, bookY + PAGE_Y, leftPage);
        renderPage(graphics, bookX + RIGHT_PAGE_X, bookY + PAGE_Y, leftPage + 1);
    }

    private void renderPage(GuiGraphics graphics, int x, int y, int page) {
        if (page > pageCount) {
            return;
        }

        Drawing drawing = drawingFor(page);
        if (drawing != null && drawing.mode == DrawingMode.WHOLE) {
            renderDrawing(graphics, drawing, x, y);
            return;
        }

        String text = I18n.get(PAGE_KEY + page);
        if (drawing != null && drawing.mode == DrawingMode.CENTER) {
            renderCenteredText(graphics, text, x + TEXT_PADDING, y + TEXT_PADDING);
        } else if (drawing != null && drawing.mode == DrawingMode.CENTER_DOWN) {
            renderHorizontallyCenteredText(graphics, text, x + TEXT_PADDING, y + TEXT_PADDING);
        } else {
            renderJustifiedText(graphics, text, x + TEXT_PADDING, y + TEXT_PADDING);
        }

        if (drawing != null) {
            renderDrawing(graphics, drawing, x, y);
        }
    }

    private void renderJustifiedText(GuiGraphics graphics, String text, int x, int y) {
        List<TextLine> lines = wrapText(text);
        for (int lineIndex = 0; lineIndex < lines.size() && lineIndex * font.lineHeight < TEXT_CONTENT_HEIGHT; lineIndex++) {
            TextLine line = lines.get(lineIndex);
            if (line.heading || line.words.isEmpty()) {
                drawCenteredLine(graphics, line, x, y + lineIndex * font.lineHeight);
                continue;
            }

            boolean lastLine = lineIndex == lines.size() - 1 || line.words.size() == 1;
            int wordsWidth = line.words.stream().mapToInt(word -> font.width(word.text)).sum();
            float gap = lastLine ? font.width(" ") : (float) (TEXT_CONTENT_WIDTH - wordsWidth) / (line.words.size() - 1);
            float cursor = x;

            for (StyledWord word : line.words) {
                graphics.drawString(font, word.text, (int) cursor, y + lineIndex * font.lineHeight, word.color, false);
                cursor += font.width(word.text) + gap;
            }
        }
    }

    private void renderCenteredText(GuiGraphics graphics, String text, int x, int y) {
        List<TextLine> lines = wrapText(text);
        int height = Math.min(lines.size() * font.lineHeight, TEXT_CONTENT_HEIGHT);
        int startY = y + (TEXT_CONTENT_HEIGHT - height) / 2;
        for (int lineIndex = 0; lineIndex < lines.size() && lineIndex * font.lineHeight < TEXT_CONTENT_HEIGHT; lineIndex++) {
            drawCenteredLine(graphics, lines.get(lineIndex), x, startY + lineIndex * font.lineHeight);
        }
    }

    private void renderHorizontallyCenteredText(GuiGraphics graphics, String text, int x, int y) {
        List<TextLine> lines = wrapText(text);
        for (int lineIndex = 0; lineIndex < lines.size() && lineIndex * font.lineHeight < TEXT_CONTENT_HEIGHT; lineIndex++) {
            drawCenteredLine(graphics, lines.get(lineIndex), x, y + lineIndex * font.lineHeight);
        }
    }

    private void drawCenteredLine(GuiGraphics graphics, TextLine line, int x, int y) {
        float cursor = x + (TEXT_CONTENT_WIDTH - lineWidth(line)) / 2.0f;
        for (StyledWord word : line.words) {
            graphics.drawString(font, word.text, (int) cursor, y, word.color, false);
            cursor += font.width(word.text) + font.width(" ");
        }
    }

    private int lineWidth(TextLine line) {
        int width = line.words.stream().mapToInt(word -> font.width(word.text)).sum();
        return width + Math.max(0, line.words.size() - 1) * font.width(" ");
    }

    private List<TextLine> wrapText(String text) {
        List<TextLine> lines = new ArrayList<>();
        for (String sourceLine : text.replace("\\r", "").split("\\n", -1)) {
            if (sourceLine.isEmpty()) {
                lines.add(new TextLine(List.of(), false));
                continue;
            }

            boolean heading = sourceLine.startsWith("#");
            List<StyledWord> line = new ArrayList<>();
            int lineWidth = 0;
            for (StyledWord word : parseWords(heading ? sourceLine.substring(1) : sourceLine, heading ? 0xFF000000 : TEXT_COLOR)) {
                int wordWidth = font.width(word.text);
                int proposedWidth = line.isEmpty() ? wordWidth : lineWidth + font.width(" ") + wordWidth;
                if (!line.isEmpty() && proposedWidth > TEXT_CONTENT_WIDTH) {
                    lines.add(new TextLine(line, heading));
                    line = new ArrayList<>();
                    lineWidth = 0;
                }

                if (wordWidth > TEXT_CONTENT_WIDTH) {
                    appendLongWord(lines, line, word, heading);
                    line = new ArrayList<>();
                    lineWidth = 0;
                } else {
                    line.add(word);
                    lineWidth += line.size() == 1 ? wordWidth : font.width(" ") + wordWidth;
                }
            }
            if (!line.isEmpty()) {
                lines.add(new TextLine(line, heading));
            }
        }
        return lines;
    }

    private List<StyledWord> parseWords(String text, int defaultColor) {
        List<StyledWord> words = new ArrayList<>();
        Matcher matcher = COLORED_TEXT.matcher(text);
        int previousEnd = 0;
        while (matcher.find()) {
            addWords(words, text.substring(previousEnd, matcher.start()), defaultColor);
            addWords(words, matcher.group(1), 0xFF000000 | Integer.parseInt(matcher.group(2), 16));
            previousEnd = matcher.end();
        }
        addWords(words, text.substring(previousEnd), defaultColor);
        return words;
    }

    private void addWords(List<StyledWord> words, String text, int color) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        for (String word : trimmed.split("\\s+")) {
            words.add(new StyledWord(word, color));
        }
    }

    private void appendLongWord(List<TextLine> lines, List<StyledWord> line, StyledWord word, boolean heading) {
        if (!line.isEmpty()) {
            lines.add(new TextLine(line, heading));
        }
        String remaining = word.text;
        while (!remaining.isEmpty()) {
            String part = font.plainSubstrByWidth(remaining, TEXT_CONTENT_WIDTH);
            lines.add(new TextLine(List.of(new StyledWord(part, word.color)), heading));
            remaining = remaining.substring(part.length());
        }
    }

    private void renderDrawing(GuiGraphics graphics, Drawing drawing, int x, int y) {
        ResourceLocation texture = resolveDrawingTexture(drawing.path);
        DrawingSize size = drawingSizes.computeIfAbsent(texture, this::loadDrawingSize);
        if (size == DrawingSize.MISSING) {
            return;
        }

        try {
            int drawX = x + (TEXT_WIDTH - size.width) / 2;
            int drawY = switch (drawing.mode) {
                case CENTER, WHOLE -> y + (TEXT_HEIGHT - size.height) / 2;
                case CENTER_DOWN, DOWN -> y + TEXT_HEIGHT - size.height;
            };
            graphics.blit(texture, drawX, drawY, 0, 0, size.width, size.height, size.width, size.height);
        } catch (Exception exception) {
            RocketNautics.LOGGER.warn("Could not render Space Knowledge Book drawing {}", texture, exception);
        }
    }

    private DrawingSize loadDrawingSize(ResourceLocation texture) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) {
            return DrawingSize.MISSING;
        }

        try (NativeImage image = NativeImage.read(resource.get().open())) {
            return new DrawingSize(image.getWidth(), image.getHeight());
        } catch (Exception exception) {
            RocketNautics.LOGGER.warn("Could not read Space Knowledge Book drawing {}", texture, exception);
            return DrawingSize.MISSING;
        }
    }

    private ResourceLocation resolveDrawingTexture(String path) {
        String locale = Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT);
        ResourceLocation localized = RocketNautics.path("textures/gui/book/drawings/" + locale + "/" + path + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(localized).isPresent()) {
            return localized;
        }
        return RocketNautics.path("textures/gui/book/drawings/" + path + ".png");
    }

    private Drawing drawingFor(int page) {
        String key = PAGE_KEY + page + ".drawing";
        String value = I18n.get(key);
        if (value.equals(key)) {
            return null;
        }

        String[] parts = value.split("/", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            RocketNautics.LOGGER.warn("Invalid Space Knowledge Book drawing '{}': expected <mode>/<name>", value);
            return null;
        }

        return DrawingMode.byName(parts[0]).map(mode -> new Drawing(mode, value)).orElse(null);
    }

    private int findPageCount() {
        String locale = Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT);
        ResourceLocation languageFile = RocketNautics.path("lang/" + locale + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(languageFile);
        if (resource.isEmpty()) {
            return 0;
        }

        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject entries = JsonParser.parseReader(reader).getAsJsonObject();
            int count = 0;
            while (count < MAX_PAGES && entries.has(PAGE_KEY + (count + 1))) {
                count++;
            }
            return count;
        } catch (Exception exception) {
            RocketNautics.LOGGER.warn("Could not read Space Knowledge Book pages from {}", languageFile, exception);
            return 0;
        }
    }

    private int getBookY() {
        return (height - BOOK_HEIGHT) / 2 - BOOK_VERTICAL_OFFSET;
    }

    private void turnPage(int amount) {
        int newPage = leftPage + amount;
        if (newPage < 1 || newPage > pageCount) {
            return;
        }
        leftPage = newPage;
        updatePageButtons();
    }

    private void playPageTurnSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    private void updatePageButtons() {
        if (previousPageButton != null) {
            previousPageButton.visible = leftPage > 1;
            nextPageButton.visible = leftPage + 1 < pageCount;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT && leftPage > 1) {
            playPageTurnSound();
            turnPage(-2);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT && leftPage + 2 <= pageCount) {
            playPageTurnSound();
            turnPage(2);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record StyledWord(String text, int color) {
    }

    private record TextLine(List<StyledWord> words, boolean heading) {
    }

    private record Drawing(DrawingMode mode, String path) {
    }

    private record DrawingSize(int width, int height) {
        private static final DrawingSize MISSING = new DrawingSize(0, 0);
    }

    private enum DrawingMode {
        CENTER,
        CENTER_DOWN,
        DOWN,
        WHOLE;

        private static Optional<DrawingMode> byName(String name) {
            try {
                return Optional.of(valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                RocketNautics.LOGGER.warn("Unknown Space Knowledge Book drawing mode '{}'", name);
                return Optional.empty();
            }
        }
    }
}
