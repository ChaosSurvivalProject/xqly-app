
/// 内置模型供应商目录（对标 harness 的「模型供应商」配置交互）。
/// 这里只保存**配置元数据**：id / 显示名 / 端点主机 / 是否需要凭据。
/// 绝不保存 API Key 明文、令牌或任何密钥；配置状态只有 bool。
class ProviderSpec {
  final String id;
  final String displayName;
  final String host;
  final bool needsCredential;

  const ProviderSpec(this.id, this.displayName, this.host, this.needsCredential);

  Map<String, Object?> toJson(bool configured) => <String, Object?>{
        'id': id,
        'displayName': displayName,
        'host': host,
        'needsCredential': needsCredential,
        'configured': configured,
      };
}

/// 供应商目录：内置模板 + 一个自建 OpenAI 兼容端点占位。
/// 与 Java `WorkbenchProvider.CATALOG` 逐项对齐。
class WorkbenchProvider {
  static const List<ProviderSpec> catalog = <ProviderSpec>[
    ProviderSpec('deepseek', 'DeepSeek', 'api.deepseek.com', true),
    ProviderSpec('openai', 'OpenAI', 'api.openai.com', true),
    ProviderSpec('anthropic', 'Anthropic', 'api.anthropic.com', true),
    ProviderSpec('gemini', 'Google Gemini', 'generativelanguage.googleapis.com', true),
    ProviderSpec('openrouter', 'OpenRouter', 'openrouter.ai', true),
    ProviderSpec('groq', 'Groq', 'api.groq.com', true),
    ProviderSpec('xai', 'xAI', 'api.x.ai', true),
    ProviderSpec('mistral', 'Mistral', 'api.mistral.ai', true),
    ProviderSpec('custom', '自建兼容端点', '', true),
  ];

  static final Set<String> _ids =
      catalog.map((ProviderSpec p) => p.id).toSet();

  static bool validId(String id) => _ids.contains(id);

  /// 自建端点主机必须是公网 HTTPS 主机；拒绝私网 / 环回 / 裸 IP。
  static bool customHostAllowed(String host) {
    if (host.isEmpty || host.length > 253) return false;
    if (host.contains('://') || host.contains('/') || host.contains('@')) return false;
    if (host == 'localhost' || host.endsWith('.local')) return false;
    final RegExp ip = RegExp(r'^\d{1,3}(\.\d{1,3}){3}$');
    if (ip.hasMatch(host)) return false;
    return RegExp(r'^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$').hasMatch(host);
  }

  final Set<String> _configured = <String>{};

  bool isConfigured(String id) => _configured.contains(id);

  /// 只登记「已配置」布尔状态；不接收、不保存任何密钥值。
  bool markConfigured(String id, {bool value = true}) {
    if (!validId(id)) return false;
    if (value) _configured.add(id);
    else _configured.remove(id);
    return true;
  }

  List<Map<String, Object?>> list() => List<Map<String, Object?>>.unmodifiable(
      catalog.map((ProviderSpec p) => p.toJson(isConfigured(p.id))).toList());
}
