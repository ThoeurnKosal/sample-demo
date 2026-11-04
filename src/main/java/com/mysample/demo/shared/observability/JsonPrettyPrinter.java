package com.mysample.demo.shared.observability;

/**
 * Indents a JSON text for humans. Character based (no JSON library): it never throws, keeps
 * the input's values exactly as they are, and leaves strings untouched, including escaped
 * quotes. Invalid JSON is still printed, just less nicely.
 */
final class JsonPrettyPrinter {

    private JsonPrettyPrinter() {
    }

    static boolean looksLikeJson(String text) {
        String trimmed = text.strip();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    /** Lines are separated by {@code \n}; the caller adds its own prefix to each line. */
    static String print(String json, String indent) {
        StringBuilder out = new StringBuilder(json.length() * 2);
        int level = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < json.length()) {
                    out.append(json.charAt(++i));      // escaped char, e.g. \" stays inside the string
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    out.append(c);
                }
                case '{', '[' -> {
                    char close = c == '{' ? '}' : ']';
                    int next = nextNonSpace(json, i + 1);
                    if (next < json.length() && json.charAt(next) == close) {
                        out.append(c).append(close);    // {} and [] stay on one line
                        i = next;
                    } else {
                        out.append(c);
                        newLine(out, ++level, indent);
                    }
                }
                case '}', ']' -> {
                    level = Math.max(0, level - 1);
                    newLine(out, level, indent);
                    out.append(c);
                }
                case ',' -> {
                    out.append(c);
                    newLine(out, level, indent);
                }
                case ':' -> out.append(": ");
                case ' ', '\t', '\r', '\n' -> {
                    // whitespace outside strings is replaced by our own layout
                }
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static int nextNonSpace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static void newLine(StringBuilder out, int level, String indent) {
        out.append('\n').append(indent.repeat(level));
    }
}
