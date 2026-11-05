import 'foundation.dart';

class Doc {
  final String id;
  final String title;
  final String body;
  final List<String> tags;
  final bool favorite;

  const Doc(this.id, this.title, this.body, this.tags, this.favorite);
}

class Hit {
  final Doc doc;
  final double score;
  final String preview;

  const Hit(this.doc, this.score, this.preview);
}

class Index {
  final List<Doc> docs;
  final Map<String, List<int>> postings;
  final List<String> recent;

  Index(this.docs, this.postings, this.recent);
  int get docCount => docs.length;
  int recentCount() => recent.length;
}

/// 本地全文检索（纯 Dart，离线可用，不需要联网）。
///
/// 中文按单字 + bigram 切分，ASCII 按词切分；标题/标签加权；
/// 含多字词的查询必须命中至少一个多字词，避免「的」之类的常见单字误命中。
/// 索引可序列化，避免每次启动在主线程全量重建。
class SearchIndex {
  SearchIndex._();

  static List<String> tokenize(String s) {
    final List<String> out = <String>[];
    final String low = s.toLowerCase();
    final StringBuffer ascii = StringBuffer();
    final List<String> cjk = <String>[];

    void flushAscii() {
      if (ascii.isNotEmpty) {
        out.add(ascii.toString());
        ascii.clear();
      }
    }

    for (int i = 0; i < low.length; i++) {
      final int c = low.codeUnitAt(i);
      final bool isCjk = c >= 0x4e00 && c <= 0x9fff;
      final bool isWord = (c >= 0x61 && c <= 0x7a) ||
          (c >= 0x30 && c <= 0x39) ||
          (c >= 0x410 && c <= 0x44f);
      if (isCjk) {
        flushAscii();
        cjk.add(low[i]);
      } else if (isWord) {
        ascii.write(low[i]);
      } else {
        flushAscii();
        cjk.clear();
      }
    }
    flushAscii();

    for (int i = 0; i < cjk.length; i++) {
      out.add(cjk[i]);
      if (i + 1 < cjk.length) out.add(cjk[i] + cjk[i + 1]);
    }
    return out;
  }

  static Index build(List<Doc> docs) {
    final Map<String, List<int>> postings = <String, List<int>>{};
    for (int i = 0; i < docs.length; i++) {
      final Doc d = docs[i];
      final Set<String> terms = <String>{
        ...tokenize(d.title),
        ...tokenize(d.tags.join(' ')),
        ...tokenize(d.body),
      };
      for (final String t in terms) {
        postings.putIfAbsent(t, () => <int>[]).add(i);
      }
    }
    return Index(List<Doc>.unmodifiable(docs), postings, <String>[]);
  }

  static List<Hit> search(Index idx, String query,
      {String tag = '', bool favOnly = false, int limit = 30}) {
    final List<String> terms = tokenize(query.trim());
    final Set<String> distinct = terms.toSet();
    if (distinct.isEmpty) return <Hit>[];

    final bool hasMulti = distinct.any((String t) => t.length >= 2);
    final int minHits = (distinct.length + 2) ~/ 3 < 1
        ? 1
        : (distinct.length + 2) ~/ 3;

    final Map<int, int> counts = <int, int>{};
    final Map<int, Set<String>> hits = <int, Set<String>>{};
    for (final String t in distinct) {
      final List<int>? posting = idx.postings[t];
      if (posting == null) continue;
      for (final int i in posting) {
        counts[i] = (counts[i] ?? 0) + 1;
        hits.putIfAbsent(i, () => <String>{}).add(t);
      }
    }

    final List<Hit> out = <Hit>[];
    counts.forEach((int i, int n) {
      final Doc d = idx.docs[i];
      if (favOnly && !d.favorite) return;
      if (tag.isNotEmpty && !d.tags.contains(tag)) return;
      if (n < minHits) return;
      final Set<String> hs = hits[i] ?? <String>{};
      if (hasMulti && !hs.any((String t) => t.length >= 2)) return;

      double score = n * 1.0;
      for (final String t in distinct) {
        if (tokenize(d.title).contains(t)) score += 3;
        if (d.tags.any((String g) => tokenize(g).contains(t))) score += 2;
      }
      if (d.favorite) score += 0.5;
      out.add(Hit(d, score, preview(d.body, distinct)));
    });

    out.sort((Hit a, Hit b) => b.score.compareTo(a.score));
    return out.take(limit < 0 ? 30 : limit).toList();
  }

  static String preview(String body, Set<String> terms) {
    int pos = -1;
    final String low = body.toLowerCase();
    for (final String t in terms) {
      final int p = low.indexOf(t);
      if (p >= 0 && (pos < 0 || p < pos)) pos = p;
    }
    final int start = pos < 0 ? 0 : (pos - 30 < 0 ? 0 : pos - 30);
    final int end = (start + 160) > body.length ? body.length : start + 160;
    String slice = body.substring(start, end);
    for (final String t in terms) {
      if (t.isEmpty) continue;
      slice = slice.replaceAllMapped(
        RegExp(RegExp.escape(t), caseSensitive: false),
        (Match m) => '<mark>${m.group(0)}</mark>',
      );
    }
    return (start > 0 ? '…' : '') + escapeHtml(slice) +
        (end < body.length ? '…' : '');
  }

  static String escapeHtml(String s) => s
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');

  static String serialize(List<Doc> docs) => Json.encode(<Object?>[
        for (final Doc d in docs)
          <String, Object?>{
            'id': d.id,
            'title': d.title,
            'body': d.body,
            'tags': <Object?>[for (final String t in d.tags) t],
            'favorite': d.favorite,
          },
      ]);

  static List<Doc> deserialize(String json) {
    final Object? raw = Json.decode(json);
    if (raw is! List) return <Doc>[];
    final List<Doc> out = <Doc>[];
    for (final Object? item in raw) {
      if (item is! Map) continue;
      final Map<String, Object?> m =
          item.map((k, v) => MapEntry<String, Object?>(k.toString(), v));
      out.add(Doc(
        Json.str(m, 'id', ''),
        Json.str(m, 'title', ''),
        Json.str(m, 'body', ''),
        <String>[for (final Object? t in Json.list(m, 'tags')) if (t is String) t],
        Json.boolean(m, 'favorite', false),
      ));
    }
    return out;
  }
}
