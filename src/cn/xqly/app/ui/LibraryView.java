package cn.xqly.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.xqly.app.StateOverlay;
import cn.xqly.app.core.AppState;
import cn.xqly.app.core.DesignTokens;
import cn.xqly.app.core.SearchIndex;
import cn.xqly.app.core.UserData;
import cn.xqly.app.core.UserPrefs;

/**
 * 原生「星穹玻璃」资料库界面：实时本地搜索、收藏筛选、标签筛选、结果卡片列表。
 * 直接复用 core 的 {@link SearchIndex} 与 {@link UserData}，不是 WebView 壳——
 * 这是「自己写一套 UI」对 library 页面的落地。
 *
 * 性能要点：索引只在构造时构建一次（O(文档·词) 的分词成本只付一次）。收藏切换
 * 只更新一个 {@code favSet} 覆盖层并在搜索时按集合过滤，不再重建整个倒排索引，
 * 因此收藏高频操作时保持 O(结果数) 而非 O(全量文档) 的成本。
 */
public class LibraryView extends FrameLayout {

    private final Context ctx;
    private final SearchIndex.Index index;
    private final UserPrefs prefs;
    private final OnOpenDoc openDoc;
    private final Set<String> favSet = new LinkedHashSet<String>();

    private GlassSearchBar searchBar;
    private GlassChip favChip;
    private LinearLayout tagRow;
    private LinearLayout list;
    private StateOverlay state;

    private String curTag = "";
    private boolean favOnly = false;

    public interface OnOpenDoc { void open(String docId, String title); }

    public LibraryView(Context ctx, SearchIndex.Index srcIndex, UserPrefs prefs, OnOpenDoc openDoc) {
        super(ctx);
        this.ctx = ctx;
        this.prefs = prefs;
        this.openDoc = openDoc;
        this.index = (srcIndex != null) ? srcIndex : SearchIndex.build(new ArrayList<SearchIndex.Doc>());
        for (String id : UserData.favorites(prefs)) favSet.add(id);
        build();
        runSearch("");
    }

    private void build() {
        DesignTokens.Palette pal = DesignTokens.palette(DesignTokens.Theme.DARK, DesignTokens.Mode.GLASS);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(16));

        searchBar = new GlassSearchBar(ctx, 0xFFFFCE86);
        searchBar.setOnQueryListener(this::onQuery);
        col.addView(searchBar, lp(-1, -2));

        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        tagRow = new LinearLayout(ctx);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setPadding(0, dp(12), 0, dp(4));
        favChip = new GlassChip(ctx, "★ 收藏");
        favChip.setOnClickListener(v -> {
            favOnly = !favOnly;
            favChip.setSelected(favOnly);
            runSearch(searchBar.getQuery());
        });
        tagRow.addView(favChip, chipLp());
        for (String t : allTags()) {
            final String tag = t;
            GlassChip c = new GlassChip(ctx, tag);
            c.setOnClickListener(v -> {
                curTag = curTag.equals(tag) ? "" : tag;
                markTag(tag);
                runSearch(searchBar.getQuery());
            });
            c.setTag(tag);
            tagRow.addView(c, chipLp());
        }
        hsv.addView(tagRow);
        col.addView(hsv, lp(-1, -2));

        ScrollView scroll = new ScrollView(ctx);
        list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(list, lp(-1, -1));
        col.addView(scroll, lp(-1, -2, 1f));

        addView(col, lp(-1, -1));

        state = new StateOverlay(ctx);
        state.setOnAction(ph -> runSearch(searchBar.getQuery()));
        addView(state, lp(-1, -1));
    }

    private void onQuery(String q) { runSearch(q); }

    private void markTag(String active) {
        for (int i = 0; i < tagRow.getChildCount(); i++) {
            View v = tagRow.getChildAt(i);
            if (v instanceof GlassChip) {
                Object t = v.getTag();
                ((GlassChip) v).setSelected(t != null && t.equals(active));
            }
        }
    }

    private List<String> allTags() {
        Map<String, Integer> freq = new java.util.LinkedHashMap<String, Integer>();
        for (SearchIndex.Doc d : index.docs()) {
            for (String t : d.tags) {
                Integer c = freq.get(t);
                freq.put(t, (c == null ? 0 : c) + 1);
            }
        }
        List<String> out = new ArrayList<String>(freq.keySet());
        Collections.sort(out, (a, b) -> Integer.compare(freq.get(b), freq.get(a)));
        return out;
    }

    private void runSearch(String q) {
        List<SearchIndex.Hit> hits;
        if (q == null || q.trim().isEmpty()) {
            hits = new ArrayList<SearchIndex.Hit>();
            for (SearchIndex.Doc d : index.docs()) {
                if (favOnly && !favSet.contains(d.id)) continue;
                if (!curTag.isEmpty() && !d.tags.contains(curTag)) continue;
                hits.add(new SearchIndex.Hit(d.id, d.title, "", favSet.contains(d.id) ? 1 : 0));
            }
        } else {
            List<SearchIndex.Hit> raw = SearchIndex.search(index, q, curTag, false, 50);
            hits = new ArrayList<SearchIndex.Hit>();
            for (SearchIndex.Hit h : raw) {
                if (favOnly && !favSet.contains(h.id)) continue;
                hits.add(h);
            }
        }
        list.removeAllViews();
        if (hits.isEmpty()) {
            boolean blank = q.trim().isEmpty() && !favOnly && curTag.isEmpty();
            state.show(AppState.Page.LIBRARY, blank ? AppState.Phase.EMPTY : AppState.Phase.NO_RESULT);
            return;
        }
        state.show(AppState.Page.LIBRARY, AppState.Phase.SUCCESS);
        for (SearchIndex.Hit h : hits) {
            list.addView(resultRow(h));
        }
    }

    private View resultRow(final SearchIndex.Hit h) {
        DesignTokens.Palette pal = DesignTokens.palette(DesignTokens.Theme.DARK, DesignTokens.Mode.GLASS);

        GlassCard card = new GlassCard(ctx, 0xFFA87CFF, 16f, false);
        card.setContentPadding(14);
        card.setClickable(true);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(ctx);
        title.setText(h.title);
        title.setTextColor(pal.get(DesignTokens.ColorKey.TEXT));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.T_TITLE.sp);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        row.addView(title, lp(-1, -2));

        if (h.snippet != null && !h.snippet.isEmpty()) {
            TextView snip = new TextView(ctx);
            snip.setText(stripMark(h.snippet));
            snip.setTextColor(pal.get(DesignTokens.ColorKey.TEXT_SECONDARY));
            snip.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.T_BODY.sp);
            snip.setMaxLines(2);
            snip.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams slp = lp(-1, -2);
            slp.topMargin = dp(4);
            row.addView(snip, slp);
        }

        card.addView(row, lp(-1, -2));

        final boolean fav = favSet.contains(h.id);
        GlassButton favBtn = new GlassButton(ctx, 0xFFFFCE86, fav ? "★" : "☆");
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.TOP);
        flp.topMargin = dp(6);
        flp.rightMargin = dp(6);
        card.addView(favBtn, flp);

        final String id = h.id;
        card.setOnClickListener(v -> { if (openDoc != null) openDoc.open(id, h.title); });
        favBtn.setOnClickListener(v -> toggleFavorite(id));

        return card;
    }

    private void toggleFavorite(String id) {
        if (id == null || id.isEmpty()) return;
        UserData.toggleFavorite(prefs, id);
        favSet.clear();
        favSet.addAll(UserData.favorites(prefs));
        runSearch(searchBar.getQuery());
    }

    private static String stripMark(String s) {
        return s.replace("<mark>", "").replace("</mark>", "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
    }

    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }
    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.rightMargin = dp(8); p.bottomMargin = dp(4);
        return p;
    }
    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
