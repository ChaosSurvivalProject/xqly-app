import 'dart:convert';
import 'dart:typed_data';

/// 纯 Dart SHA-256（FIPS 180-4）。
///
/// 之所以自带实现而不引 `package:crypto`：共享核心要求零第三方依赖、断网可构建，
/// 且同步包 / 内容清单 / 更新链的哈希必须在 Windows 与 Android 上逐字节一致。
/// 已知向量在 test/run_tests.dart 中校验（空串与 "abc"）。
const List<int> _k = <int>[
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
  0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
  0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
  0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
  0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
  0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
  0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
];

const int _mask32 = 0xffffffff;

int _rotr(int x, int n) => ((x >>> n) | (x << (32 - n))) & _mask32;

/// 计算字节序列的 SHA-256，返回小写 64 位 hex。
String sha256Hex(List<int> input) {
  final Uint32List h = Uint32List.fromList(<int>[
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
  ]);

  final int bitLen = input.length * 8;
  final List<int> data = <int>[...input, 0x80];
  while (data.length % 64 != 56) {
    data.add(0);
  }
  // 64 位大端长度。文档长度受 Json.maxChars 约束，bitLen 远小于 2^53。
  for (int i = 7; i >= 0; i--) {
    data.add((bitLen ~/ (1 << (8 * i))) & 0xff);
  }

  final Uint32List w = Uint32List(64);
  for (int off = 0; off < data.length; off += 64) {
    for (int i = 0; i < 16; i++) {
      final int j = off + i * 4;
      w[i] = ((data[j] << 24) |
              (data[j + 1] << 16) |
              (data[j + 2] << 8) |
              data[j + 3]) &
          _mask32;
    }
    for (int i = 16; i < 64; i++) {
      final int x = w[i - 15];
      final int y = w[i - 2];
      final int s0 = _rotr(x, 7) ^ _rotr(x, 18) ^ (x >>> 3);
      final int s1 = _rotr(y, 17) ^ _rotr(y, 19) ^ (y >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) & _mask32;
    }

    int a = h[0], b = h[1], c = h[2], d = h[3];
    int e = h[4], f = h[5], g = h[6], hh = h[7];

    for (int i = 0; i < 64; i++) {
      final int s1 = _rotr(e, 6) ^ _rotr(e, 11) ^ _rotr(e, 25);
      final int ch = (e & f) ^ ((~e & _mask32) & g);
      final int t1 = (hh + s1 + ch + _k[i] + w[i]) & _mask32;
      final int s0 = _rotr(a, 2) ^ _rotr(a, 13) ^ _rotr(a, 22);
      final int maj = (a & b) ^ (a & c) ^ (b & c);
      final int t2 = (s0 + maj) & _mask32;

      hh = g;
      g = f;
      f = e;
      e = (d + t1) & _mask32;
      d = c;
      c = b;
      b = a;
      a = (t1 + t2) & _mask32;
    }

    h[0] = (h[0] + a) & _mask32;
    h[1] = (h[1] + b) & _mask32;
    h[2] = (h[2] + c) & _mask32;
    h[3] = (h[3] + d) & _mask32;
    h[4] = (h[4] + e) & _mask32;
    h[5] = (h[5] + f) & _mask32;
    h[6] = (h[6] + g) & _mask32;
    h[7] = (h[7] + hh) & _mask32;
  }

  return h.map((int x) => x.toRadixString(16).padLeft(8, '0')).join();
}

/// UTF-8 字符串的 SHA-256。
String sha256OfString(String s) => sha256Hex(utf8.encode(s));

/// 归一化用户粘贴的指纹：去 `sha256:` 前缀、冒号、空白，转小写。
String? normalizeSha256(String? raw) {
  if (raw == null) return null;
  String s = raw.trim().toLowerCase();
  if (s.startsWith('sha256:')) s = s.substring(7);
  s = s.replaceAll(':', '').replaceAll(RegExp(r'\s'), '');
  if (s.length != 64 || !RegExp(r'^[0-9a-f]{64}$').hasMatch(s)) return null;
  return s;
}
