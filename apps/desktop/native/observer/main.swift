// FocusOn 화면 관측 도우미 (Desktop 결정 024, screen-observation-PLAN.md).
// Main이 한 번 띄워 두고 표준 입출력으로 JSON 한 줄씩 주고받는다. 요청: {"id":1,"cmd":"frontmost"|"apps"}
// [주의] 화면 이미지·창 내용은 이 단계에서 읽지 않는다. 창 제목은 화면 기록 권한이 없으면 null이다.
import AppKit
import CoreGraphics

func reply(_ object: [String: Any]) {
  guard let data = try? JSONSerialization.data(withJSONObject: object), let line = String(data: data, encoding: .utf8) else { return }
  FileHandle.standardOutput.write((line + "\n").data(using: .utf8)!)
}

func frontmost(_ id: Int) {
  guard let app = NSWorkspace.shared.frontmostApplication else { reply(["id": id, "ok": true, "app": NSNull()]); return }
  let pid = app.processIdentifier
  // 창 목록은 앞에서 뒤 순서다. 그 앱의 첫 일반 창(layer 0)이 맨 앞 창이다.
  let windows = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as? [[String: Any]] ?? []
  let window = windows.first { ($0[kCGWindowOwnerPID as String] as? Int32) == pid && ($0[kCGWindowLayer as String] as? Int) == 0 }
  let title = (window?[kCGWindowName as String] as? String).flatMap { $0.isEmpty ? nil : $0 }
  reply(["id": id, "ok": true, "app": [
    "bundleId": app.bundleIdentifier ?? NSNull(),
    "appName": app.localizedName ?? NSNull(),
    "pid": Int(pid),
    "windowTitle": title ?? NSNull(),
  ] as [String: Any]])
}

func installedApps(_ id: Int) {
  let fm = FileManager.default
  let roots = ["/Applications", "/Applications/Utilities", "/System/Applications", "/System/Applications/Utilities", NSHomeDirectory() + "/Applications"]
  var seen = Set<String>()
  var apps: [[String: Any]] = []
  for root in roots {
    for name in (try? fm.contentsOfDirectory(atPath: root)) ?? [] where name.hasSuffix(".app") {
      let path = root + "/" + name
      guard let bundle = Bundle(path: path), let bundleId = bundle.bundleIdentifier, !seen.contains(bundleId) else { continue }
      seen.insert(bundleId)
      let display = (bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") ?? bundle.object(forInfoDictionaryKey: "CFBundleName")) as? String
      apps.append(["bundleId": bundleId, "name": display ?? String(name.dropLast(4)), "path": path])
    }
  }
  let running = NSWorkspace.shared.runningApplications.filter { $0.activationPolicy == .regular }.compactMap { $0.bundleIdentifier }
  reply(["id": id, "ok": true, "apps": apps, "running": running])
}

// [주의] frontmostApplication은 메인 run loop가 돌아야 최신 값으로 바뀐다. 입력은 백그라운드에서 읽고 처리는 메인에서 한다.
var pending = Data()
FileHandle.standardInput.readabilityHandler = { handle in
  let data = handle.availableData
  // Main이 끝나 입력이 닫히면 도우미도 끝낸다.
  if data.isEmpty { exit(0) }
  pending.append(data)
  // 한 번에 읽은 조각이 줄 중간에서 끊길 수 있어 마지막 미완성 줄은 남겨 둔다.
  guard let last = pending.lastIndex(of: 0x0A) else { return }
  let complete = pending[..<last]
  pending = Data(pending[pending.index(after: last)...])
  for line in String(decoding: complete, as: UTF8.self).split(separator: "\n") {
    guard let request = try? JSONSerialization.jsonObject(with: Data(line.utf8)) as? [String: Any], let id = request["id"] as? Int else { continue }
    DispatchQueue.main.async {
      switch request["cmd"] as? String {
      case "frontmost": frontmost(id)
      case "apps": installedApps(id)
      default: reply(["id": id, "ok": false, "error": "UNKNOWN_COMMAND"])
      }
    }
  }
}
RunLoop.main.run()
