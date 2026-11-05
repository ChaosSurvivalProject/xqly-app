package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简严格 JSON 解析/序列化（纯 Java，零第三方依赖）。
 * 目的：让「桥协议 / 更新校验 / 内容清单 / 搜索索引」等安全逻辑与领域逻辑
 * 不依赖 Android 的 org.json，从而可以在标准 JVM 上编译并真实运行单元测试。
 *
 * 严格性：拒绝尾随内容、拒绝未转义控制字符、拒绝 NaN/Infinity、限制嵌套深度，
 * 避免把畸形输入当成合法对象（桥消息的常见攻击面）。
 */
public final class MiniJson {
    private MiniJson() {}

    public static final int MAX_DEPTH = 32;
    public static final int MAX_DOC_CHARS = 8 * 1024 * 1024;

    public static final class JsonException extends RuntimeException {
        public JsonException(String m) { super(m); }
    }

    public static Object parse(String s) {
        if (s == null) throw new JsonException("null input");
        if (s.length() > MAX_DOC_CHARS) throw new JsonException("document too large");
        Parser p = new Parser(s);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != s.length()) throw new JsonException("trailing content at " + p.i);
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String s) {
        Object v = parse(s);
        if (!(v instanceof Map)) throw new JsonException("expected object");
        return (Map<String, Object>) v;
    }

    /** 稳定序列化：键按字典序输出，便于对同一内容生成确定性摘要/清单。 */
    public static String stringify(Object v) {
        StringBuilder b = new StringBuilder();
        write(v, b);
        return b.toString();
    }

    private static void write(Object v, StringBuilder b) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof String) { quote((String) v, b); return; }
        if (v instanceof Boolean || v instanceof Number) { b.append(v.toString()); return; }
        if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            List<String> keys = new ArrayList<String>();
            for (Object k : m.keySet()) keys.add(String.valueOf(k));
            java.util.Collections.sort(keys);
            b.append('{');
            boolean first = true;
            for (String k : keys) {
                if (!first) b.append(',');
                first = false;
                quote(k, b); b.append(':');
                write(m.get(k), b);
            }
            b.append('}');
            return;
        }
        if (v instanceof Iterable) {
            b.append('[');
            boolean first = true;
            for (Object o : (Iterable<?>) v) {
                if (!first) b.append(',');
                first = false;
                write(o, b);
            }
            b.append(']');
            return;
        }
        quote(String.valueOf(v), b);
    }

    private static void quote(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    private static final class Parser {
        final String s;
        int i = 0;
        Parser(String s) { this.s = s; }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) throw new JsonException("nesting too deep");
            if (i >= s.length()) throw new JsonException("unexpected end");
            char c = s.charAt(i);
            switch (c) {
                case '{': return obj(depth);
                case '[': return arr(depth);
                case '"': return str();
                case 't': lit("true"); return Boolean.TRUE;
                case 'f': lit("false"); return Boolean.FALSE;
                case 'n': lit("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return num();
                    throw new JsonException("unexpected char '" + c + "' at " + i);
            }
        }

        void lit(String w) {
            if (!s.regionMatches(i, w, 0, w.length())) throw new JsonException("bad literal at " + i);
            i += w.length();
        }

        Map<String, Object> obj(int depth) {
            i++; // {
            LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
            ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') throw new JsonException("expected key at " + i);
                String k = str();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') throw new JsonException("expected ':' at " + i);
                i++;
                ws();
                m.put(k, value(depth + 1));
                ws();
                if (i >= s.length()) throw new JsonException("unterminated object");
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw new JsonException("expected ',' or '}' at " + (i - 1));
            }
        }

        List<Object> arr(int depth) {
            i++; // [
            ArrayList<Object> a = new ArrayList<Object>();
            ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return a; }
            while (true) {
                ws();
                a.add(value(depth + 1));
                ws();
                if (i >= s.length()) throw new JsonException("unterminated array");
                char c = s.charAt(i++);
                if (c == ']') return a;
                if (c != ',') throw new JsonException("expected ',' or ']' at " + (i - 1));
            }
        }

        String str() {
            i++; // opening quote
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw new JsonException("bad escape");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': b.append('"'); break;
                        case '\\': b.append('\\'); break;
                        case '/': b.append('/'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'n': b.append('\n'); break;
                        case 'r': b.append('\r'); break;
                        case 't': b.append('\t'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw new JsonException("bad unicode escape");
                            String h = s.substring(i, i + 4);
                            try { b.append((char) Integer.parseInt(h, 16)); }
                            catch (NumberFormatException ex) { throw new JsonException("bad unicode hex"); }
                            i += 4;
                            break;
                        default: throw new JsonException("bad escape \\" + e);
                    }
                } else if (c < 0x20) {
                    throw new JsonException("unescaped control char at " + (i - 1));
                } else {
                    b.append(c);
                }
            }
            throw new JsonException("unterminated string");
        }

        Number num() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            if (i >= s.length()) throw new JsonException("bad number");
            if (s.charAt(i) == '0') i++;
            else {
                if (s.charAt(i) < '1' || s.charAt(i) > '9') throw new JsonException("bad number");
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            boolean fp = false;
            if (i < s.length() && s.charAt(i) == '.') {
                fp = true; i++;
                if (i >= s.length() || !Character.isDigit(s.charAt(i))) throw new JsonException("bad fraction");
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                fp = true; i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                if (i >= s.length() || !Character.isDigit(s.charAt(i))) throw new JsonException("bad exponent");
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            String t = s.substring(start, i);
            try {
                if (fp) {
                    double d = Double.parseDouble(t);
                    if (Double.isNaN(d) || Double.isInfinite(d)) throw new JsonException("non-finite number");
                    return Double.valueOf(d);
                }
                return Long.valueOf(Long.parseLong(t));
            } catch (NumberFormatException e) {
                throw new JsonException("number overflow: " + t);
            }
        }
    }

    // ---- 便捷取值（避免到处 instanceof / 强转）----
    public static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v instanceof String ? (String) v : def;
    }
    public static long num(Map<String, Object> m, String k, long def) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).longValue() : def;
    }
    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        return v instanceof Boolean ? (Boolean) v : def;
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }
    @SuppressWarnings("unchecked")
    public static List<Object> arr(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof List ? (List<Object>) v : null;
    }
}
