// FocusOn 화면 관측 도우미 (Desktop 결정 024, screen-observation-PLAN.md).
// Main이 한 번 띄워 두고 표준 입출력으로 JSON 한 줄씩 주고받는다. 요청: {"id":1,"cmd":"frontmost"|"apps"|"ocr"|"camera"|"cameraStop","windowId":123}
// 창 제목은 화면 기록 권한이 없으면 null이다.
// [주의] ocr은 지정한 창 하나만 캡처해 글자만 돌려준다. 이미지는 이 프로세스 밖으로 내보내지 않고 OCR 직후 버린다(결정 024).
import AppKit
import AVFoundation
import CoreGraphics
import ScreenCaptureKit
import Vision

func reply(_ object: [String: Any]) {
  guard let data = try? JSONSerialization.data(withJSONObject: object), let line = String(data: data, encoding: .utf8) else { return }
  FileHandle.standardOutput.write((line + "\n").data(using: .utf8)!)
}

func frontmost(_ id: Int) {
  guard let app = NSWorkspace.shared.frontmostApplication else { reply(["id": id, "ok": true, "app": NSNull()]); return }
  let pid = app.processIdentifier
  // 창 목록은 앞에서 뒤 순서다. 그 앱의 첫 일반 창(layer 0) 중 보이는 창이 맨 앞 창이다.
  // [주의] Electron 앱(VS Code 등)은 맨 앞에 투명(alpha 0)·납작한 보조 창을 둔다. 그 창을 고르면 제목·OCR 글자가 비어 나온다.
  let windows = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as? [[String: Any]] ?? []
  let window = windows.first { info in
    let bounds = info[kCGWindowBounds as String] as? [String: Any]
    let width = bounds?["Width"] as? Double ?? 0
    let height = bounds?["Height"] as? Double ?? 0
    return (info[kCGWindowOwnerPID as String] as? Int32) == pid && (info[kCGWindowLayer as String] as? Int) == 0
      && (info[kCGWindowAlpha as String] as? Double ?? 1) > 0 && width >= 100 && height >= 100
  }
  let title = (window?[kCGWindowName as String] as? String).flatMap { $0.isEmpty ? nil : $0 }
  let windowId = window?[kCGWindowNumber as String] as? Int
  // 맨 앞 창이 화면 너비를 꽉 채우고 아래 끝이 화면 맨 아래(Dock 자리)까지 내려오면 전체 화면으로 본다(Dock이 숨겨짐).
  // [주의] 노치 있는 MacBook의 전체 화면 창은 위쪽 메뉴 막대 높이만큼 짧아서(1512×949) 화면 크기와 같지 않다.
  // 최대화한 일반 창은 아래 끝이 Dock 위에서 멈춘다. 포키 창을 화면 맨 아래에 붙일지 정하는 데만 쓴다(#225).
  let frame = (window?[kCGWindowBounds as String] as? [String: Any]).map { CGRect(x: $0["X"] as? Double ?? 0, y: $0["Y"] as? Double ?? 0, width: $0["Width"] as? Double ?? 0, height: $0["Height"] as? Double ?? 0) }
  let primaryHeight = NSScreen.screens.first?.frame.height ?? 0
  let fullScreen = frame.map { f in NSScreen.screens.contains { screen in
    // CG 창 좌표는 주 화면 왼쪽 위가 0이고 아래로 커진다. AppKit 화면 좌표(왼쪽 아래 0)를 맞춰 비교한다.
    let top = primaryHeight - screen.frame.maxY
    return abs(f.minX - screen.frame.minX) < 1 && abs(f.width - screen.frame.width) < 1 && abs(f.maxY - (top + screen.frame.height)) < 1
  } } ?? false
  reply(["id": id, "ok": true, "app": [
    "windowId": windowId ?? NSNull(),
    "bundleId": app.bundleIdentifier ?? NSNull(),
    "appName": app.localizedName ?? NSNull(),
    "pid": Int(pid),
    "windowTitle": title ?? NSNull(),
    "fullScreen": fullScreen,
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

// 창 하나만 캡처해 Vision으로 글자를 읽는다(macOS 14+). 위→아래 순서로 이어 최대 4,000자를 돌려준다.
let MAX_CAPTURE_WIDTH = 2560.0
let MAX_TEXT = 4000

func ocr(_ id: Int, windowId: Int) {
  Task {
    do {
      let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
      guard let window = content.windows.first(where: { Int($0.windowID) == windowId }) else { reply(["id": id, "ok": false, "error": "WINDOW_NOT_FOUND"]); return }
      let filter = SCContentFilter(desktopIndependentWindow: window)
      let config = SCStreamConfiguration()
      let scale = min(Double(filter.pointPixelScale), MAX_CAPTURE_WIDTH / max(1, Double(window.frame.width)))
      config.width = max(1, Int(Double(window.frame.width) * scale))
      config.height = max(1, Int(Double(window.frame.height) * scale))
      config.showsCursor = false
      let image = try await SCScreenshotManager.captureImage(contentFilter: filter, configuration: config)
      let request = VNRecognizeTextRequest()
      request.recognitionLevel = .accurate
      request.recognitionLanguages = ["ko-KR", "en-US"]
      request.usesLanguageCorrection = true
      try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
      // Vision 좌표는 아래가 0이라 y가 큰 것이 위쪽이다.
      let lines = (request.results ?? [])
        .sorted { $0.boundingBox.minY > $1.boundingBox.minY }
        .compactMap { $0.topCandidates(1).first?.string }
      reply(["id": id, "ok": true, "text": String(lines.joined(separator: "\n").prefix(MAX_TEXT))])
    } catch {
      // 화면 기록 권한이 없거나 캡처가 막히면 실패로만 알린다. 오류 문장은 넘기지 않는다.
      reply(["id": id, "ok": false, "error": "CAPTURE_FAILED"])
    }
  }
}

// 카메라 분석 (Notion 카메라 분석, #222). 기본 카메라 프레임을 이 프로세스 안에서만 Vision으로 보고 추상 신호만 돌려준다.
// [주의] 프레임·얼굴 좌표·랜드마크는 밖으로 내보내지 않고 저장하지 않는다. 가장 최근 프레임 하나만 메모리에 두고 다음 프레임이 오면 버린다.
// 머리가 이 각도(라디안, 약 25도)보다 돌아가면 화면을 보지 않는 것으로 본다. 실제 사용자 테스트로 조정한다.
let HEAD_AWAY_RAD = 0.44
// 얼굴 너비가 프레임의 이 비율보다 작으면 너무 멀어서 판단하지 않는다.
let MIN_FACE_WIDTH = 0.08
// 밝기 평균(0~255)이 이보다 낮으면 어두워서 얼굴이 없다고 단정하지 않는다.
let MIN_LUMA = 40.0

final class CameraSampler: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
  private var session: AVCaptureSession?
  private let queue = DispatchQueue(label: "focuson.camera")
  private var latest: CVPixelBuffer?
  private let lock = NSLock()

  func sample(_ id: Int) {
    // 실패 사유는 설정 화면에 보여 줄 수 있게 ok로 돌려준다(#228).
    guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else { reply(["id": id, "ok": true, "state": "UNAVAILABLE", "reason": "DENIED"]); return }
    if session == nil {
      guard let device = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: device) else { reply(["id": id, "ok": true, "state": "UNAVAILABLE", "reason": "NO_DEVICE"]); return }
      let next = AVCaptureSession()
      next.sessionPreset = .vga640x480
      let output = AVCaptureVideoDataOutput()
      output.alwaysDiscardsLateVideoFrames = true
      output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange]
      output.setSampleBufferDelegate(self, queue: queue)
      guard next.canAddInput(input), next.canAddOutput(output) else { reply(["id": id, "ok": true, "state": "UNAVAILABLE", "reason": "NO_DEVICE"]); return }
      next.addInput(input)
      next.addOutput(output)
      session = next
      queue.async { next.startRunning() }
    }
    lock.lock(); let frame = latest; lock.unlock()
    // 켜는 중이라 아직 프레임이 없으면 알림 근거로 쓰지 않는다.
    guard let frame else { reply(["id": id, "ok": true, "state": "STARTING"]); return }
    queue.async { reply(["id": id, "ok": true, "state": "SAMPLE"].merging(Self.analyze(frame)) { $1 }) }
  }

  func stop(_ id: Int) {
    let current = session
    session = nil
    lock.lock(); latest = nil; lock.unlock()
    queue.async { current?.stopRunning() }
    reply(["id": id, "ok": true])
  }

  func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
    guard let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
    lock.lock(); latest = buffer; lock.unlock()
  }

  private static func analyze(_ frame: CVPixelBuffer) -> [String: Any] {
    if averageLuma(frame) < MIN_LUMA { return ["faceCount": 0, "eyesVisible": false, "gaze": "UNKNOWN", "quality": "UNUSABLE"] }
    let request = VNDetectFaceLandmarksRequest()
    try? VNImageRequestHandler(cvPixelBuffer: frame, options: [:]).perform([request])
    let faces = request.results ?? []
    // 밝은데 얼굴이 없으면 자리를 비운 것으로 본다(설계서 5.2 얼굴 부재).
    guard let face = faces.first else { return ["faceCount": 0, "eyesVisible": false, "gaze": "AWAY", "quality": "GOOD"] }
    // 여러 얼굴이면 누구를 볼지 알 수 없어 판단하지 않는다(설계서 7.2).
    if faces.count > 1 { return ["faceCount": faces.count, "eyesVisible": false, "gaze": "UNKNOWN", "quality": "UNUSABLE"] }
    let eyesVisible = face.landmarks?.leftEye != nil && face.landmarks?.rightEye != nil
    if face.boundingBox.width < MIN_FACE_WIDTH || face.confidence < 0.5 { return ["faceCount": 1, "eyesVisible": eyesVisible, "gaze": "UNKNOWN", "quality": "LOW"] }
    let yaw = abs(face.yaw?.doubleValue ?? 0)
    let pitch = abs(face.pitch?.doubleValue ?? 0)
    let away = yaw > HEAD_AWAY_RAD || pitch > HEAD_AWAY_RAD
    // 정면인데 눈이 안 보이면(가림·반사) 시선을 모른다. 고개를 크게 돌린 것은 눈이 없어도 AWAY다.
    let gaze = away ? "AWAY" : eyesVisible ? "SCREEN" : "UNKNOWN"
    return ["faceCount": 1, "eyesVisible": eyesVisible, "gaze": gaze, "quality": gaze == "UNKNOWN" ? "LOW" : "GOOD"]
  }

  // 밝기 평면(Y)을 16칸마다 하나씩만 읽어 평균을 낸다.
  private static func averageLuma(_ frame: CVPixelBuffer) -> Double {
    CVPixelBufferLockBaseAddress(frame, .readOnly)
    defer { CVPixelBufferUnlockBaseAddress(frame, .readOnly) }
    guard let base = CVPixelBufferGetBaseAddressOfPlane(frame, 0) else { return 0 }
    let width = CVPixelBufferGetWidthOfPlane(frame, 0), height = CVPixelBufferGetHeightOfPlane(frame, 0), stride = CVPixelBufferGetBytesPerRowOfPlane(frame, 0)
    let bytes = base.assumingMemoryBound(to: UInt8.self)
    var sum = 0, count = 0
    for y in Swift.stride(from: 0, to: height, by: 16) { for x in Swift.stride(from: 0, to: width, by: 16) { sum += Int(bytes[y * stride + x]); count += 1 } }
    return count == 0 ? 0 : Double(sum) / Double(count)
  }
}
let camera = CameraSampler()

// [주의] 창 캡처(ScreenCaptureKit)는 화면 서버 연결이 초기화된 프로세스에서만 동작한다. 없으면 CGS_REQUIRE_INIT로 죽는다.
// NSApplication으로 연결만 만들고 Dock·메뉴에는 나타나지 않게 한다.
NSApplication.shared.setActivationPolicy(.prohibited)

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
      case "ocr":
        if let windowId = request["windowId"] as? Int { ocr(id, windowId: windowId) } else { reply(["id": id, "ok": false, "error": "INVALID_REQUEST"]) }
      case "apps": installedApps(id)
      case "camera": camera.sample(id)
      case "cameraStop": camera.stop(id)
      default: reply(["id": id, "ok": false, "error": "UNKNOWN_COMMAND"])
      }
    }
  }
}
RunLoop.main.run()
