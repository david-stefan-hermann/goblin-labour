package goblinlabour.client;

import goblinlabour.item.Handbook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Goblin Handbook in the goblin look: a book-sized panel with a light page, chapter tabs on the left, arrows and
 * the page number below. The screen lays the chapters' sections out and turns the pages itself: a section that does
 * not fit on the page starts the next one. Headings are dark green, names in curly braces gold, warnings red.
 */
public class HandbookScreen extends Screen {
    private static final int PANEL_W = 232, PANEL_H = 214;
    private static final int TAB_W = 26, TAB_H = 24, TAB_GAP = 3;
    private static final int PAGE_MARGIN = 8;
    private static final int TEXT_MARGIN = 6;
    private static final int HEADER_H = 20;
    private static final int FOOTER_H = 24;

    private static final int PAGE = 0xFFEEF2DD;
    private static final int PAGE_EDGE = 0xFF9DB68A;
    private static final int TITLE = 0xFF1F4F16;
    private static final int HEADING = 0xFF2E6B1F;
    private static final int BODY = GoblinUi.LABEL;
    private static final int NAME = 0xFF8A5A00;
    private static final int WARNING = 0xFFA3261F;
    private static final int ARROW = 0xFF4E6B42;

    private final List<Handbook.Chapter> chapters = Handbook.chapters();
    /** Laid out in {@link #init}: every page with its chapter and its sections, each with its wrapped lines. */
    private final List<Page> pages = new ArrayList<>();
    private int page;
    private int left, top;
    private GoblinUi.GreenButton previous, next;

    private record Placed(Handbook.Section section, List<FormattedCharSequence> lines, int height) {
    }

    private record Page(int chapter, List<Placed> sections) {
    }

    public HandbookScreen(int firstPage) {
        super(Component.translatable("item.goblinlabour.goblin_handbook"));
        this.page = Math.max(0, firstPage);
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2 + TAB_W / 2;
        top = (height - PANEL_H) / 2;
        layOut();
        page = Math.min(page, pages.size() - 1);
        for (int i = 0; i < chapters.size(); i++) {
            int chapter = i;
            Handbook.Chapter c = chapters.get(i);
            addRenderableWidget(new TabButton(left - TAB_W + 3, top + 8 + i * (TAB_H + TAB_GAP), c,
                    button -> turnTo(firstPageOf(chapter))));
        }
        int buttonY = top + PANEL_H - FOOTER_H + 2;
        previous = addRenderableWidget(GoblinUi.button(Component.literal("<"), button -> turnTo(page - 1), left + PAGE_MARGIN, buttonY, 22, 18));
        next = addRenderableWidget(GoblinUi.button(Component.literal(">"), button -> turnTo(page + 1), left + PANEL_W - PAGE_MARGIN - 22, buttonY, 22, 18));
        updateButtons();
    }

    private int textWidth() {
        return PANEL_W - 2 * PAGE_MARGIN - 2 * TEXT_MARGIN;
    }

    private int contentHeight() {
        return PANEL_H - 2 * PAGE_MARGIN - HEADER_H - FOOTER_H;
    }

    /**
     * Breaks every chapter into pages: sections go onto a page until the next one would not fit. A recipe stays on
     * the page of the line after it, which explains what it makes.
     */
    private void layOut() {
        pages.clear();
        int width = textWidth();
        for (int c = 0; c < chapters.size(); c++) {
            List<Placed> current = new ArrayList<>();
            int used = 0;
            List<Handbook.Section> sections = chapters.get(c).sections();
            for (int i = 0; i < sections.size(); i++) {
                Placed placed = place(sections.get(i), width);
                int needed = placed.height();
                // a heading or a recipe never ends a page: it moves over together with what follows it
                for (int j = i; j + 1 < sections.size() && (sections.get(j) instanceof Handbook.Heading
                        || sections.get(j) instanceof Handbook.Recipe); j++) {
                    needed += place(sections.get(j + 1), width).height();
                }
                if (!current.isEmpty() && used + needed > contentHeight()) {
                    pages.add(new Page(c, current));
                    current = new ArrayList<>();
                    used = 0;
                }
                current.add(placed);
                used += placed.height();
            }
            pages.add(new Page(c, current));
        }
    }

    private Placed place(Handbook.Section section, int width) {
        return switch (section) {
            case Handbook.Heading heading -> {
                List<FormattedCharSequence> lines = font.split(Component.translatable(heading.key()).withColor(HEADING), width);
                yield new Placed(section, lines, lines.size() * 10 + 5);
            }
            case Handbook.Text text -> {
                List<FormattedCharSequence> lines = font.split(styled(text.key(), BODY), width);
                yield new Placed(section, lines, lines.size() * 9 + 5);
            }
            case Handbook.Warning warning -> {
                List<FormattedCharSequence> lines = font.split(styled(warning.key(), WARNING), width);
                yield new Placed(section, lines, lines.size() * 9 + 5);
            }
            case Handbook.ItemLine line -> {
                List<FormattedCharSequence> lines = font.split(styled(line.key(), BODY), width - 20);
                yield new Placed(section, lines, Math.max(17, lines.size() * 9 + 1) + 4);
            }
            case Handbook.Recipe recipe -> new Placed(section, List.of(), 22);
        };
    }

    /** The translated text in {@code colour}, with every {name} in gold (the braces left out). */
    private static Component styled(String key, int colour) {
        String raw = Component.translatable(key).getString();
        MutableComponent out = Component.empty();
        int at = 0;
        while (at < raw.length()) {
            int open = raw.indexOf('{', at);
            int close = open < 0 ? -1 : raw.indexOf('}', open);
            if (open < 0 || close < 0) {
                out.append(Component.literal(raw.substring(at)).withColor(colour));
                break;
            }
            if (open > at) out.append(Component.literal(raw.substring(at, open)).withColor(colour));
            out.append(Component.literal(raw.substring(open + 1, close)).withColor(NAME));
            at = close + 1;
        }
        return out;
    }

    private int firstPageOf(int chapter) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).chapter() == chapter) return i;
        }
        return 0;
    }

    private void turnTo(int target) {
        int clamped = Math.clamp(target, 0, pages.size() - 1);
        if (clamped != page && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
        page = clamped;
        updateButtons();
    }

    private void updateButtons() {
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = page < pages.size() - 1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GoblinUi.drawPanel(graphics, left, top, PANEL_W, PANEL_H);
        int x0 = left + PAGE_MARGIN, y0 = top + PAGE_MARGIN;
        int x1 = left + PANEL_W - PAGE_MARGIN, y1 = top + PANEL_H - FOOTER_H;
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, PAGE_EDGE);
        graphics.fill(x0, y0, x1, y1, PAGE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Page current = pages.get(page);
        Handbook.Chapter chapter = chapters.get(current.chapter());
        int x = left + PAGE_MARGIN + TEXT_MARGIN;
        int y = top + PAGE_MARGIN + 3;
        graphics.item(chapter.icon(), x, y);
        graphics.text(font, Component.translatable(chapter.titleKey()).withColor(TITLE), x + 20, y + 4, TITLE, false);
        graphics.fill(x, y + HEADER_H - 4, x + textWidth(), y + HEADER_H - 3, PAGE_EDGE);

        ItemStack hovered = ItemStack.EMPTY;
        int cursorY = top + PAGE_MARGIN + HEADER_H + 2;
        for (Placed placed : current.sections()) {
            switch (placed.section()) {
                case Handbook.Heading heading -> drawLines(graphics, placed.lines(), x, cursorY + 3, 10);
                case Handbook.Text text -> drawLines(graphics, placed.lines(), x, cursorY, 9);
                case Handbook.Warning warning -> drawLines(graphics, placed.lines(), x, cursorY, 9);
                case Handbook.ItemLine line -> {
                    graphics.item(line.icon(), x, cursorY);
                    if (over(mouseX, mouseY, x, cursorY)) hovered = line.icon();
                    int textY = cursorY + (placed.lines().size() == 1 ? 4 : 0);
                    drawLines(graphics, placed.lines(), x + 20, textY, 9);
                }
                case Handbook.Recipe recipe -> {
                    int ix = x;
                    for (ItemStack in : recipe.in()) {
                        graphics.item(in, ix, cursorY);
                        if (over(mouseX, mouseY, ix, cursorY)) hovered = in;
                        ix += 18;
                        if (in.getCount() > 1) {
                            // dark and beside the icon: vanilla's white count is lost on the light page
                            String count = "\u00d7" + in.getCount();
                            graphics.text(font, count, ix, cursorY + 8, BODY, false);
                            ix += font.width(count) + 1;
                        }
                        ix += 4;
                    }
                    drawArrow(graphics, ix + 1, cursorY + 7);
                    ix += 18;
                    graphics.item(recipe.out(), ix, cursorY);
                    if (over(mouseX, mouseY, ix, cursorY)) hovered = recipe.out();
                }
            }
            cursorY += placed.height();
        }
        graphics.centeredText(font, Component.literal((page + 1) + " / " + pages.size()).withColor(BODY),
                left + PANEL_W / 2, top + PANEL_H - FOOTER_H + 7, BODY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
    }

    private void drawLines(GuiGraphicsExtractor graphics, List<FormattedCharSequence> lines, int x, int y, int lineHeight) {
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x, y, BODY, false);
            y += lineHeight;
        }
    }

    /** A small arrow pointing right, from the ingredients to the result. */
    private static void drawArrow(GuiGraphicsExtractor graphics, int x, int midY) {
        graphics.fill(x, midY - 1, x + 9, midY + 1, ARROW);
        for (int i = 0; i < 4; i++) graphics.fill(x + 9 + i, midY - 4 + i, x + 10 + i, midY + 4 - i, ARROW);
    }

    private static boolean over(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    /** A chapter tab: the chapter's icon on a green tab, lighter for the open chapter; the title as tooltip. */
    private class TabButton extends GoblinUi.GreenButton {
        private final Handbook.Chapter chapter;

        TabButton(int x, int y, Handbook.Chapter chapter, Button.OnPress onPress) {
            super(x, y, TAB_W, TAB_H, Component.translatable(chapter.titleKey()), onPress);
            this.chapter = chapter;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(chapter.titleKey())));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean open = !pages.isEmpty() && chapters.get(pages.get(page).chapter()) == chapter;
            int x0 = getX(), y0 = getY(), x1 = x0 + getWidth(), y1 = y0 + getHeight();
            graphics.fill(x0, y0, x1, y1, GoblinUi.BORDER);
            graphics.fill(x0 + 1, y0 + 1, x1 - (open ? 0 : 1), y1 - 1, open ? GoblinUi.PANEL : isHoveredOrFocused() ? GoblinUi.BUTTON_HOVER : GoblinUi.SLOT);
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, GoblinUi.LIGHT);
            graphics.item(chapter.icon(), x0 + 5, y0 + 4);
        }
    }
}
