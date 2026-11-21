import AppKit
import CoreGraphics
import CryptoKit
import ScreenCaptureKit
import Vision

struct Window {
    let pid: pid_t
    let id: CGWindowID
    let context: String
    let app: String
    let title: String

    var result: [String: Any] {
        ["state": "desktop", "context": context, "metadata": ["appName": app, "windowTitle": title]]
    }
}

enum Observation {
    case window(Window)
    case other([String: Any])

    var json: [String: Any] {
        switch self {
        case .window(let window): return window.result
        case .other(let result): return result
        }
    }
}

func state(_ value: String, _ reason: String) -> [String: Any] { ["state": value, "reason": reason] }

func fingerprint(_ value: String) -> String {
    SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
}

@MainActor
func activeWindow() -> Observation {
    guard let session = CGSessionCopyCurrentDictionary() as? [String: Any],
          session["kCGSSessionOnConsoleKey"] as? Bool == true,
          session["kCGSessionLoginDoneKey"] as? Bool == true,
          session["CGSSessionScreenIsLocked"] as? Bool != true,
          CGDisplayIsAsleep(CGMainDisplayID()) == 0 else {
        return .other(state("guard", "SESSION_INACTIVE"))
    }
    guard let app = NSWorkspace.shared.frontmostApplication, let identifier = app.bundleIdentifier,
          let name = app.localizedName, !name.isEmpty, app.activationPolicy == .regular else {
        return .other(state("unavailable", "NO_FRONT_APP"))
    }
    let bundle = identifier.lowercased()
    let extra = ProcessInfo.processInfo.environment["FOCUSON_PILOT_EXCLUDED_APPS"] ?? ""
    let excluded = Set(extra.split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() })
    if let reason = Privacy.appGuard(bundle, excluding: excluded) {
        return .other(state("guard", reason))
    }
    if Privacy.chrome.contains(bundle) {
        return .other(["state": "chrome", "context": fingerprint("\(bundle):\(app.processIdentifier)")])
    }
    // Also recognize installed browsers beyond the product list. Browser windows must use the extension.
    if let appURL = app.bundleURL, let info = Bundle(url: appURL),
       let types = info.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]],
       types.contains(where: {
           let schemes = ($0["CFBundleURLSchemes"] as? [String] ?? []).map { $0.lowercased() }
           return schemes.contains("http") && schemes.contains("https")
       }) {
        return .other(state("guard", "EXCLUDED"))
    }
    guard CGPreflightScreenCaptureAccess() else {
        return .other(state("unavailable", "SCREEN_PERMISSION_REQUIRED"))
    }
    let pid = app.processIdentifier
    let flags: CGWindowListOption = [.optionOnScreenOnly, .excludeDesktopElements]
    let windows = CGWindowListCopyWindowInfo(flags, kCGNullWindowID) as? [[String: Any]] ?? []
    // Electron can have a transparent helper above its visible editor. Match its first visible normal window.
    let first = windows.first { info in
        let bounds = info[kCGWindowBounds as String] as? [String: Any]
        return info[kCGWindowOwnerPID as String] as? Int32 == pid
            && info[kCGWindowLayer as String] as? Int == 0
            && (info[kCGWindowAlpha as String] as? Double ?? 1) > 0
            && (bounds?["Width"] as? Double ?? 0) >= 100 && (bounds?["Height"] as? Double ?? 0) >= 100
    }
    guard let info = first, let id = info[kCGWindowNumber as String] as? CGWindowID,
          let rawTitle = info[kCGWindowName as String] as? String else {
        return .other(state("unavailable", "NO_VISIBLE_WINDOW"))
    }
    if let reason = Privacy.titleGuard(rawTitle) { return .other(state("guard", reason)) }
    // Ensure names that invoke the trusted editor fast path actually come from its native bundle.
    let aliases = ["code", "visual studio code", "code - insiders", "visual studio code - insiders"]
    if aliases.contains(name.lowercased()) && !["com.microsoft.vscode", "com.microsoft.vscodeinsiders"].contains(bundle) {
        return .other(state("guard", "UNVERIFIED_EDITOR"))
    }
    guard let title = Privacy.titleForModel(rawTitle), let appName = Privacy.sanitized(name, limit: 100),
          !appName.isEmpty else { return .other(state("guard", "PRIVACY_BLOCKED")) }
    let context = fingerprint("\(bundle):\(pid):\(id):\(rawTitle)")
    return .window(Window(pid: pid, id: id, context: context, app: appName, title: title))
}

@MainActor
func readWindow(_ context: String) async -> [String: Any] {
    let current = activeWindow()
    guard case .window(let expected) = current else {
        if case .other(let outcome) = current, outcome["state"] as? String == "guard" { return outcome }
        return state("unavailable", "STALE_OR_UNAVAILABLE")
    }
    guard expected.context == context else { return state("unavailable", "STALE_CONTEXT") }
    func stillCurrent() -> Bool {
        guard case .window(let now) = activeWindow() else { return false }
        return now.context == expected.context
    }
    do {
        let content = try await SCShareableContent.excludingDesktopWindows(true, onScreenWindowsOnly: true)
        guard let window = content.windows.first(where: {
            $0.windowID == expected.id && $0.owningApplication?.processID == expected.pid
        }), stillCurrent() else { return state("unavailable", "STALE_CONTEXT") }
        let filter = SCContentFilter(desktopIndependentWindow: window)
        let size = window.frame.size
        let scale = min(Double(filter.pointPixelScale), 2560 / max(1, size.width, size.height))
        let config = SCStreamConfiguration()
        config.width = max(1, Int(size.width * scale))
        config.height = max(1, Int(size.height * scale))
        config.showsCursor = false
        let image = try await SCScreenshotManager.captureImage(contentFilter: filter, configuration: config)
        guard stillCurrent() else { return state("unavailable", "STALE_CONTEXT") }
        // Vision runs away from the main actor so AppKit processes foreground changes during OCR.
        let raw = try await Task.detached {
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.recognitionLanguages = ["ko-KR", "en-US"]
            request.usesLanguageCorrection = true
            try VNImageRequestHandler(cgImage: image).perform([request])
            return (request.results ?? []).sorted { $0.boundingBox.minY > $1.boundingBox.minY }
                .compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")
        }.value
        guard stillCurrent() else { return state("unavailable", "STALE_CONTEXT") }
        guard let text = Privacy.sanitized(raw, limit: 2000) else { return state("guard", "PRIVACY_BLOCKED") }
        guard !text.isEmpty else { return state("unavailable", "EMPTY_OCR") }
        var result = expected.result
        result["text"] = text
        return result
    } catch { return state("unavailable", "CAPTURE_FAILED") }
}

@main
struct Observer {
    @MainActor static func main() async {
        let args = Array(CommandLine.arguments.dropFirst())
        let result: [String: Any]
        switch args.first {
        case "status":
            result = ["screenRecordingGranted": CGPreflightScreenCaptureAccess()]
        case "request-permission":
            _ = NSApplication.shared.setActivationPolicy(.prohibited)
            result = ["screenRecordingGranted": CGRequestScreenCaptureAccess()]
        case "meta" where args.count == 1:
            _ = NSApplication.shared.setActivationPolicy(.prohibited)
            result = activeWindow().json
        case "ocr" where args.count == 2 && args[1].count == 64:
            _ = NSApplication.shared.setActivationPolicy(.prohibited)
            result = await readWindow(args[1])
        default:
            result = state("unavailable", "INVALID_REQUEST")
        }
        if var data = try? JSONSerialization.data(withJSONObject: result) {
            data.append(10)
            FileHandle.standardOutput.write(data)
        }
    }
}
