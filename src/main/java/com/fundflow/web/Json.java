package com.fundflow.web;

import java.math.BigDecimal;
import java.util.*;

/** Tiny JSON helper (no library needed): writes Maps/Lists, reads one flat JSON object. */
final class Json {
    private Json() {}

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    // ------------------------------------------------------------- write
    static String write(Object o) { StringBuilder sb = new StringBuilder(); w(sb, o); return sb.toString(); }

    private static void w(StringBuilder sb, Object o) {
        if (o == null) sb.append("null");
        else if (o instanceof BigDecimal b) sb.append(b.toPlainString());
        else if (o instanceof Number || o instanceof Boolean) sb.append(o);
        else if (o instanceof Map<?, ?> m) {
            sb.append('{'); boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey())); sb.append(':'); w(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Collection<?> c) {
            sb.append('['); boolean first = true;
            for (Object x : c) { if (!first) sb.append(','); first = false; w(sb, x); }
            sb.append(']');
        } else quote(sb, String.valueOf(o));
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------- read (flat objects only)
    static Map<String, Object> parse(String s) {
        Map<String, Object> m = new HashMap<>();
        if (s == null) return m;
        int[] p = {0};
        skip(s, p);
        if (p[0] >= s.length() || s.charAt(p[0]) != '{') return m;
        p[0]++;
        while (true) {
            skip(s, p);
            if (p[0] >= s.length()) break;
            char c = s.charAt(p[0]);
            if (c == '}') break;
            if (c == ',') { p[0]++; continue; }
            if (c != '"') break;
            String key = readString(s, p);
            skip(s, p); p[0]++;                       // the colon
            skip(s, p);
            if (p[0] < s.length() && s.charAt(p[0]) == '"') m.put(key, readString(s, p));
            else {
                int st = p[0];
                while (p[0] < s.length() && ",}".indexOf(s.charAt(p[0])) < 0) p[0]++;
                String raw = s.substring(st, p[0]).trim();
                try { m.put(key, new BigDecimal(raw)); } catch (NumberFormatException e) { m.put(key, raw); }
            }
        }
        return m;
    }

    private static void skip(String s, int[] p) { while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++; }

    private static String readString(String s, int[] p) {
        StringBuilder sb = new StringBuilder();
        p[0]++;                                       // opening quote
        while (p[0] < s.length() && s.charAt(p[0]) != '"') {
            char c = s.charAt(p[0]++);
            if (c == '\\' && p[0] < s.length()) {
                char n = s.charAt(p[0]++);
                sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else sb.append(c);
        }
        p[0]++;                                       // closing quote
        return sb.toString();
    }

    // ------------------------------------------------------------- typed getters
    static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString().trim();
    }
    static BigDecimal dec(Map<String, Object> m, String k) {
        try { return new BigDecimal(String.valueOf(m.get(k)).trim()); } catch (Exception e) { return null; }
    }
    static int integer(Map<String, Object> m, String k, int def) {
        BigDecimal d = dec(m, k);
        return d == null ? def : d.intValue();
    }
}
