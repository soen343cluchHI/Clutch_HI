package sugarrush.infrastructure.json;

import java.math.BigDecimal;
import java.util.*;

public final class Json {
    private final String source;
    private int offset;

    private Json(String source) {
        this.source = Objects.requireNonNull(source);
    }

    public static Map<String, Object> object(String source) {
        Json reader = new Json(source);
        Object value = reader.value(0);
        reader.space();
        if (reader.offset != source.length() || !(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected one JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    private Object value(int depth) {
        if (depth > 32)
            throw bad();
        space();
        if (offset >= source.length())
            throw bad();
        char next = source.charAt(offset);
        if (next == '"')
            return string();
        if (next == '{')
            return map(depth + 1);
        if (next == '[')
            return list(depth + 1);
        if (source.startsWith("true", offset)) {
            offset += 4;
            return true;
        }
        if (source.startsWith("false", offset)) {
            offset += 5;
            return false;
        }
        if (source.startsWith("null", offset)) {
            offset += 4;
            return null;
        }
        int start = offset;
        while (offset < source.length() && "-+0123456789.eE".indexOf(source.charAt(offset)) >= 0)
            offset++;
        String number = source.substring(start, offset);
        if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
            throw bad();
        try {
            return new BigDecimal(number);
        } catch (NumberFormatException failure) {
            throw bad();
        }
    }

    private Map<String, Object> map(int depth) {
        offset++;
        Map<String, Object> result = new LinkedHashMap<>();
        space();
        if (take('}'))
            return result;
        do {
            space();
            String key = string();
            space();
            if (!take(':') || result.containsKey(key))
                throw bad();
            result.put(key, value(depth));
            space();
            if (take('}'))
                return result;
        } while (take(','));
        throw bad();
    }

    private List<Object> list(int depth) {
        offset++;
        List<Object> result = new ArrayList<>();
        space();
        if (take(']'))
            return result;
        do {
            result.add(value(depth));
            space();
            if (take(']'))
                return result;
        } while (take(','));
        throw bad();
    }

    private String string() {
        if (!take('"'))
            throw bad();
        StringBuilder result = new StringBuilder();
        while (offset < source.length()) {
            char c = source.charAt(offset++);
            if (c == '"')
                return result.toString();
            if (c < 32)
                throw bad();
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (offset >= source.length())
                throw bad();
            char escaped = source.charAt(offset++);
            switch (escaped) {
            case '"', '\\', '/' -> result.append(escaped);
            case 'b' -> result.append('\b');
            case 'f' -> result.append('\f');
            case 'n' -> result.append('\n');
            case 'r' -> result.append('\r');
            case 't' -> result.append('\t');
            case 'u' -> {
                if (offset + 4 > source.length())
                    throw bad();
                try {
                    result.append((char) Integer.parseInt(source.substring(offset, offset + 4), 16));
                } catch (NumberFormatException failure) {
                    throw bad();
                }
                offset += 4;
            }
            default -> throw bad();
            }
        }
        throw bad();
    }

    private boolean take(char c) {
        if (offset < source.length() && source.charAt(offset) == c) {
            offset++;
            return true;
        }
        return false;
    }

    private void space() {
        while (offset < source.length() && " \t\r\n".indexOf(source.charAt(offset)) >= 0)
            offset++;
    }

    private IllegalArgumentException bad() {
        return new IllegalArgumentException("Invalid JSON");
    }
}
