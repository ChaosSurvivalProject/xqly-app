package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 本地全文检索（纯 Java，可持久化索引，不在主线程全量扫描）。
 * 中文按「单字 + 相邻双字」切分，英文/数字按词切分；支持标题/标签加权、
 * 上下文预览、命中高亮标记、筛选与最近搜索。零网络、零外部依赖。
 */
public final class SearchIndex {
    private SearchIndex() {}

    public static final class Doc {
        public final String id, title, body;
        public final List<String> tags;
        public final boolean favorite;
        public Doc(String id, String title, String body, List<String> tags, boolean favorite) {
            this.id = id; this.title = title; this.body = body;
            this.tags = tags == null ? Collections.<String>emptyList() : tags;
            this.favorite = favorite;
        }
    }

    public static final class Hit {
        public final String id, title, snippet;
        public final double score;
        Hit(String id, String title, String snippet, double score) {
            this.id = id; this.title = title; this.snippet = snippet; this.score = score;
        }
    }

    public static final class Index {
        final List<Doc> docs = new ArrayList<Doc>();
        final Map<String, Map<String, Integer>> postings = new HashMap<String, Map<String, Integer>>();
        final List<String> recent = new ArrayList<String>();
        public int docCount() { return docs.size(); }
        public int termCount() { return postings.size(); }
        public int recentCount() { return recent.size(); }
    }

    public static Index build(List<Doc> input) {
        Index idx = new Index();
        if (input == null) return idx;
        for (Doc d : input) {
            if (d == null || d.id == null || d.title == null) continue;
            idx.docs.add(d);
            addField(idx, d.id, d.title, 3);
            addField(idx, d.id, d.body == null ? "" : d.body, 1);
            for (String t : d.tags) addField(idx, d.id, t, 2);
        }
        return idx;
    }

    private static void addField(Index idx, String id, String text, int weight) {
        for (String term : tokenize(text)) {
            Map<String, Integer> m = idx.postings.get(term);
            if (m == null) { m = new HashMap<String, Integer>(); idx.postings.put(term, m); }
            Integer old = m.get(id);
            m.put(id, (old == null ? 0 : old) + weight);
        }
    }

    /** 切词：CJK 单字 + bigram，ASCII 词（≥2 字符），数字保留。 */
    public static List<String> tokenize(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null || s.isEmpty()) return out;
        String low = s.toLowerCase(Locale.US);
        StringBuilder ascii = new StringBuilder();
        List<Character> cjk = new ArrayList<Character>();
        for (int i = 0; i < low.length(); i++) {
            char c = low.charAt(i);
            boolean isCjk = c >= 0x3400 && c <= 0x9FFF;
            boolean isWord = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (isWord) {
                ascii.append(c);
                flushCjk(out, cjk); cjk.clear();
            } else if (isCjk) {
                if (ascii.length() > 0) { out.add(ascii.toString()); ascii.setLength(0); }
                cjk.add(c);
            } else {
                if (ascii.length() > 0) { out.add(ascii.toString()); ascii.setLength(0); }
                flushCjk(out, cjk); cjk.clear();
            }
        }
        if (ascii.length() > 0) out.add(ascii.toString());
        flushCjk(out, cjk);
        return out;
    }

    private static void flushCjk(List<String> out, List<Character> cjk) {
        for (int i = 0; i < cjk.size(); i++) {
            out.add(String.valueOf(cjk.get(i)));
            if (i + 1 < cjk.size()) out.add("" + cjk.get(i) + cjk.get(i + 1));
        }
    }

    /** 检索：多词 AND 计分，标题/收藏加权，返回按分数排序的命中与预览。 */
    public static List<Hit> search(Index idx, String query, String filterTag, boolean onlyFav, int limit) {
        List<Hit> out = new ArrayList<Hit>();
        if (idx == null || query == null || query.trim().isEmpty()) return out;
        String q = query.trim().toLowerCase(Locale.US);
        idx.recent.remove(q);
        idx.recent.add(0, q);
        while (idx.recent.size() > 8) idx.recent.remove(idx.recent.size() - 1);

        List<String> terms = tokenize(q);
        if (terms.isEmpty()) return out;

        // distinct 查询词，并判断是否存在「非单字」词（bigram / ASCII 词）
        Set<String> distinct = new HashSet<String>(terms);
        boolean hasMulti = false;
        for (String t : distinct) if (t.length() >= 2) { hasMulti = true; break; }

        Map<String, Double> score = new HashMap<String, Double>();
        Map<String, Set<String>> hitTerms = new HashMap<String, Set<String>>();
        for (String t : distinct) {
            Map<String, Integer> p = idx.postings.get(t);
            if (p == null) continue;
            for (Map.Entry<String, Integer> e : p.entrySet()) {
                Double cur = score.get(e.getKey());
                score.put(e.getKey(), (cur == null ? 0 : cur) + e.getValue() * idf(idx, t, p.size()));
                Set<String> hs = hitTerms.get(e.getKey());
                if (hs == null) { hs = new HashSet<String>(); hitTerms.put(e.getKey(), hs); }
                hs.add(t);
            }
        }
        Map<String, Doc> byId = new HashMap<String, Doc>();
        for (Doc d : idx.docs) byId.put(d.id, d);
        // 至少命中 ceil(distinct/3) 个词，避免长查询只靠一个常见字误命中
        int minHits = Math.max(1, (distinct.size() + 2) / 3);

        for (Map.Entry<String, Double> e : score.entrySet()) {
            Doc d = byId.get(e.getKey());
            if (d == null) continue;
            if (onlyFav && !d.favorite) continue;
            if (filterTag != null && !filterTag.isEmpty() && !d.tags.contains(filterTag)) continue;
            Set<String> hs = hitTerms.get(e.getKey());
            if (hs == null || hs.size() < minHits) continue;
            // 含非单字词的查询，必须命中至少一个非单字词（否则视为噪声）
            if (hasMulti) {
                boolean hitMulti = false;
                for (String t : hs) if (t.length() >= 2) { hitMulti = true; break; }
                if (!hitMulti) continue;
            }
            double s = e.getValue();
            if (d.title.toLowerCase(Locale.US).contains(q)) s += 8;
            if (d.favorite) s += 0.5;
            out.add(new Hit(d.id, d.title, snippet(d.body, q), s));
        }
        Collections.sort(out, new Comparator<Hit>() {
            public int compare(Hit a, Hit b) { return Double.compare(b.score, a.score); }
        });
        if (limit > 0 && out.size() > limit) return new ArrayList<Hit>(out.subList(0, limit));
        return out;
    }

    private static double idf(Index idx, String term, int df) {
        int n = Math.max(1, idx.docs.size());
        return 1.0 + Math.log((double) n / (double) (1 + df));
    }

    /** 生成带 <mark> 的上下文预览，并做 HTML 转义（防注入）。 */
    public static String snippet(String body, String query) {
        if (body == null) return "";
        String low = body.toLowerCase(Locale.US);
        String q = query.toLowerCase(Locale.US);
        int at = low.indexOf(q);
        if (at < 0) at = 0;
        int start = Math.max(0, at - 28);
        int end = Math.min(body.length(), at + q.length() + 52);
        String pre = (start > 0 ? "…" : "") + esc(body.substring(start, end)) + (end < body.length() ? "…" : "");
        if (q.isEmpty()) return pre;
        return pre.replaceAll("(?i)(" + java.util.regex.Pattern.quote(esc(q)) + ")", "<mark>$1</mark>");
    }

    public static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** 索引持久化（构建期生成、运行期直接加载，避免主线程全量扫描）。 */
    public static String serialize(List<Doc> docs) {
        List<Object> arr = new ArrayList<Object>();
        for (Doc d : docs) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", d.id); m.put("title", d.title); m.put("body", d.body);
            m.put("tags", new ArrayList<Object>(d.tags)); m.put("favorite", d.favorite);
            arr.add(m);
        }
        return MiniJson.stringify(arr);
    }

    public static List<Doc> deserialize(String json) {
        List<Doc> out = new ArrayList<Doc>();
        try {
            Object o = MiniJson.parse(json);
            if (!(o instanceof List)) return out;
            for (Object e : (List<?>) o) {
                if (!(e instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) e;
                List<String> tags = new ArrayList<String>();
                List<Object> ta = MiniJson.arr(m, "tags");
                if (ta != null) for (Object t : ta) if (t instanceof String) tags.add((String) t);
                out.add(new Doc(MiniJson.str(m, "id", ""), MiniJson.str(m, "title", ""),
                        MiniJson.str(m, "body", ""), tags, MiniJson.bool(m, "favorite", false)));
            }
        } catch (Exception ignored) { }
        return out;
    }
}
