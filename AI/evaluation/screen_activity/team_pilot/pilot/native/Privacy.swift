import Foundation

enum Privacy {
    static let chrome: Set<String> = [
        "com.google.chrome", "com.google.chrome.beta", "com.google.chrome.dev", "com.google.chrome.canary"
    ]
    private static let privateApps: Set<String> = [
        "com.apple.keychainaccess", "com.apple.passwords", "com.1password.1password",
        "com.agilebits.onepassword7", "com.bitwarden.desktop", "com.apple.mobilesms",
        "com.kakao.kakaotalkmac", "com.tinyspeck.slackmacgap", "com.hnc.discord",
        "ru.keepcoder.telegram", "net.whatsapp.whatsapp", "jp.naver.line.mac", "com.apple.mail",
        "com.microsoft.outlook", "com.apple.systempreferences", "com.apple.loginwindow",
        "com.apple.securityagent", "com.apple.screensaver.engine"
    ]
    private static let excludedApps: Set<String> = [
        "com.apple.safari", "com.apple.safaritechnologypreview", "company.thebrowser.browser",
        "com.microsoft.edgemac", "org.mozilla.firefox", "com.naver.whale", "com.brave.browser",
        "com.operasoftware.opera", "com.vivaldi.vivaldi", "org.chromium.chromium",
        "com.duckduckgo.macos.browser"
    ]
    private static let protectedTitle = try! NSRegularExpression(
        pattern: #"비밀번호|password|passcode|\botp\b|인증\s?번호|인증\s?코드|verification code|결제|payment|계좌|api\s?key|access token|secret"#,
        options: .caseInsensitive)
    private static let protectedText = try! NSRegularExpression(
        pattern: #"비밀번호|password|passcode|인증\s?번호|인증\s?코드|verification code|\botp\b|카드\s?번호|계좌\s?번호|cvc|cvv|api\s?key|secret key|access token|\b(?:\d[ -]?){13,19}\b|\bbearer\s+\S+"#,
        options: .caseInsensitive)
    private static let redactions = [
        #"\bhttps?://\S+"#, #"[\w.+-]+@[\w-]+(?:\.[\w-]+)+"#,
        #"(?:\+?\d{1,3}[ -]?)?\(?\d{2,4}\)?[ -]?\d{3,4}[ -]?\d{4}"#,
        #"\b(?=[A-Za-z0-9_-]*\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{24,}\b"#,
        #"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"#
    ].map { try! NSRegularExpression(pattern: $0, options: .caseInsensitive) }
    private static func matches(_ regex: NSRegularExpression, _ value: String) -> Bool {
        regex.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)) != nil
    }
    static func appGuard(_ bundle: String, excluding: Set<String>) -> String? {
        if excluding.contains(bundle) || excludedApps.contains(bundle) ||
            ["org.mozilla.firefox", "com.microsoft.edgemac.", "com.brave.browser.", "com.operasoftware.opera",
             "com.vivaldi.vivaldi.", "com.apple.safari.", "com.google.chrome.app."].contains(where: bundle.hasPrefix) {
            return "EXCLUDED"
        }
        if privateApps.contains(bundle) || bundle.hasPrefix("com.hnc.discord") { return "PRIVACY_BLOCKED" }
        return nil
    }
    static func titleGuard(_ title: String) -> String? {
        let value = title.lowercased()
        if value.contains("focuson-local-pilot") || value.contains("focuson local pilot") { return "EXCLUDED" }
        return matches(protectedTitle, title) || matches(protectedText, title) ? "PRIVACY_BLOCKED" : nil
    }
    static func sanitized(_ input: String, limit: Int) -> String? {
        guard !matches(protectedText, input) else { return nil }
        let stripped = redactions.reduce(input) { text, regex in
            regex.stringByReplacingMatches(in: text, range: NSRange(text.startIndex..., in: text), withTemplate: " ")
        }
        let result = stripped.split(whereSeparator: { $0.isWhitespace || $0.isNewline }).joined(separator: " ")
        guard !matches(protectedText, result) else { return nil }
        return String(result.prefix(limit))
    }
    static func titleForModel(_ title: String) -> String? {
        guard titleGuard(title) == nil else { return nil }
        return sanitized(title, limit: 200)
    }
}
