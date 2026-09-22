package manfred.bytedepth.adapter.web.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The small, deliberately opt-in metadata contract supported by enhanced code blocks.
 */
record CodeBlockMetadata(
        String language,
        Optional<String> title,
        boolean fold,
        Optional<String> tabGroup) {

    static CodeBlockMetadata parse(String info) {
        List<String> tokens = tokenize(info);
        if (tokens.isEmpty()) {
            return ordinary();
        }

        String language = tokens.get(0);
        String title = null;
        String tabGroup = null;
        boolean fold = false;
        for (String token : tokens.subList(1, tokens.size())) {
            if (token.equals("fold")) {
                fold = true;
                continue;
            }
            int separator = token.indexOf(':');
            if (separator <= 0 || separator == token.length() - 1) {
                continue;
            }
            String key = token.substring(0, separator).toLowerCase();
            String value = valueOf(token.substring(separator + 1));
            if (value.isBlank()) {
                continue;
            }
            if (key.equals("file") || (key.equals("title") && title == null)) {
                title = value;
            } else if (key.equals("tabs")) {
                tabGroup = value;
            }
        }
        return new CodeBlockMetadata(language, Optional.ofNullable(title), fold, Optional.ofNullable(tabGroup));
    }

    boolean enhanced() {
        return title.isPresent() || fold || tabGroup.isPresent();
    }

    private static CodeBlockMetadata ordinary() {
        return new CodeBlockMetadata("", Optional.empty(), false, Optional.empty());
    }

    private static String valueOf(String raw) {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1).replace("\\\"", "\"");
        }
        if (raw.startsWith("\"") || raw.endsWith("\"")) {
            return "";
        }
        return raw;
    }

    private static List<String> tokenize(String info) {
        if (info == null || info.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (char character : info.trim().toCharArray()) {
            if (escaped) {
                token.append(character);
                escaped = false;
            } else if (character == '\\' && quoted) {
                token.append(character);
                escaped = true;
            } else if (character == '"') {
                quoted = !quoted;
                token.append(character);
            } else if (Character.isWhitespace(character) && !quoted) {
                addToken(tokens, token);
            } else {
                token.append(character);
            }
        }
        addToken(tokens, token);
        return tokens;
    }

    private static void addToken(List<String> tokens, StringBuilder token) {
        if (token.length() > 0) {
            tokens.add(token.toString());
            token.setLength(0);
        }
    }
}
