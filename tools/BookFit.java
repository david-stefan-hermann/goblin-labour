import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimates how many lines each handbook page takes in the vanilla book screen (114 px wide, 14 lines per page)
 * using the default font's glyph widths. Run from the project root: java tools/BookFit.java
 */
public class BookFit {
    static final int WIDTH = 114;
    static final int MAX_LINES = 14;

    public static void main(String[] args) throws Exception {
        for (String lang : new String[]{"en_us", "de_de"}) {
            String json = Files.readString(Path.of("src/main/resources/assets/goblinlabour/lang/" + lang + ".json"));
            Matcher m = Pattern.compile("\"goblinlabour\\.book\\.page\\.(\\d+)\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
            System.out.println("== " + lang);
            while (m.find()) {
                String text = m.group(2).replace("\\n", "\n").replace("\\\"", "\"");
                List<String> lines = wrap(text);
                String flag = lines.size() > MAX_LINES ? "  <-- TOO LONG" : "";
                System.out.printf("page %2s: %2d lines%s%n", m.group(1), lines.size(), flag);
                if (args.length > 0) lines.forEach(l -> System.out.println("    |" + l));
            }
        }
    }

    static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        boolean bold = false;
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            int lineWidth = 0;
            for (String word : paragraph.split(" ")) {
                int w = width(word, bold);
                bold = boldAfter(word, bold);
                int space = line.length() == 0 ? 0 : 4;
                if (lineWidth + space + w > WIDTH && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder();
                    lineWidth = 0;
                    space = 0;
                }
                if (space > 0) line.append(' ');
                line.append(word);
                lineWidth += space + w;
            }
            out.add(line.toString());
        }
        return out;
    }

    static boolean boldAfter(String word, boolean bold) {
        for (int i = 0; i + 1 < word.length(); i++) {
            if (word.charAt(i) == '§') bold = word.charAt(i + 1) == 'l';
        }
        return bold;
    }

    static int width(String word, boolean bold) {
        int w = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '§') {
                bold = i + 1 < word.length() && word.charAt(i + 1) == 'l';
                i++;
                continue;
            }
            w += glyph(c) + (bold ? 1 : 0);
        }
        return w;
    }

    static int glyph(char c) {
        return switch (c) {
            case 'i', '.', ',', ':', ';', '!', '\'', '|' -> 2;
            case 'l' -> 3;
            case 't', 'I', ' ', '(', ')', '[', ']', '"', '*' -> 4;
            case 'f', 'k', '<', '>' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
