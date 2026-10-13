package com.yagay.YFloat.hook;

/** Conservative OCR word extraction from Google's selected-word model. */
final class GoogleLiveWordTextPolicy {
    static String choose(String raw, String alternate) {
        String a = clean(raw), b = clean(alternate);
        if (a.isEmpty()) return b;
        if (b.isEmpty() || a.equals(b)) return a;
        // Short locale/language identifiers should not replace a longer word.
        if (a.length() <= 2 && b.length() >= 4 && isAsciiAlpha(a)) return b;
        return a;
    }

    private static boolean isAsciiAlpha(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) return false;
        }
        return !text.isEmpty();
    }

    private static String clean(String value) {
        if (value == null) return "";
        String s = value.replace('\u0000', ' ').trim();
        if (s.isEmpty() || s.length() > 180) return "";
        for (int i = 0; i < s.length(); i++) {
            if (Character.isISOControl(s.charAt(i))) return "";
        }
        return s;
    }

    static void append(StringBuilder output, String token) {
        if (output == null || token == null || token.isBlank() || output.length() > 8000) return;
        if (output.length() > 0 && !closesPunctuation(token)
                && !opensPunctuation(output.charAt(output.length() - 1))) {
            output.append(' ');
        }
        output.append(token);
    }

    private static boolean closesPunctuation(String text) {
        return text.length() == 1 && ".,;:!?)]}，。！？、；：".contains(text);
    }

    private static boolean opensPunctuation(char value) {
        return "([{（【「『".indexOf(value) >= 0;
    }

    private GoogleLiveWordTextPolicy() {}
}
