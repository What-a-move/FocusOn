#import <AppKit/AppKit.h>
#import <ApplicationServices/ApplicationServices.h>
#import <CommonCrypto/CommonDigest.h>
#import <ScreenCaptureKit/ScreenCaptureKit.h>
#import <Vision/Vision.h>

static void Fail(NSString *code) {
    @throw [NSException exceptionWithName:@"CaptureFailure" reason:code userInfo:nil];
}
static id Attribute(AXUIElementRef element, CFStringRef name) {
    CFTypeRef value = NULL;
    if (AXUIElementCopyAttributeValue(element, name, &value) != kAXErrorSuccess) return nil;
    return CFBridgingRelease(value);
}
static BOOL Matches(NSString *value, NSString *pattern) {
    return [value rangeOfString:pattern options:NSRegularExpressionSearch | NSCaseInsensitiveSearch].location != NSNotFound;
}
static BOOL Sensitive(NSString *value) {
    return Matches(value, @"[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|\\b(sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9_]{16,}|eyJ[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+)|\\bBearer\\s+\\S+|(api[_ -]?key|password|비밀번호|인증\\s*코드|OTP)\\s*[:=]\\s*\\S+|\\b(\\d[ -]?){13,19}\\b|\\b01[016789][ -]?\\d{3,4}[ -]?\\d{4}\\b");
}
static NSDictionary *Selected(NSDictionary *request) {
    NSRunningApplication *app = NSWorkspace.sharedWorkspace.frontmostApplication;
    NSString *bundle = app.bundleIdentifier;
    if (!app || !bundle) Fail(@"TARGET_UNAVAILABLE");
    NSRunningApplication *parent = [NSRunningApplication runningApplicationWithProcessIdentifier:getppid()];
    if ([bundle isEqual:parent.bundleIdentifier]) Fail(@"FOCUSON_UI_EXCLUDED");
    if ([request[@"ownBundleIds"] containsObject:bundle]) Fail(@"FOCUSON_UI_EXCLUDED");
    if ([request[@"excludedBundleIds"] containsObject:bundle]) Fail(@"EXCLUDED");
    if ([bundle isEqualToString:@"com.google.Chrome"]) Fail(@"CHROME_HANDLED_BY_EXTENSION");
    if ([@[@"com.apple.Safari", @"com.microsoft.edgemac", @"org.mozilla.firefox", @"com.brave.Browser", @"com.operasoftware.Opera"] containsObject:bundle] || [bundle hasPrefix:@"com.google.Chrome."]) Fail(@"UNSUPPORTED_BROWSER");
    if ([@[@"com.apple.mail", @"com.apple.MobileSMS", @"com.apple.Passwords", @"com.apple.keychainaccess", @"com.hnc.Discord", @"com.tinyspeck.slackmacgap", @"com.kakao.KakaoTalkMac", @"ru.keepcoder.Telegram", @"net.whatsapp.WhatsApp", @"org.whispersystems.signal-desktop", @"com.microsoft.Outlook"] containsObject:bundle]) Fail(@"PRIVACY_BLOCKED");
    if (!AXIsProcessTrusted()) Fail(@"ACCESSIBILITY_PERMISSION_REQUIRED");
    AXUIElementRef application = AXUIElementCreateApplication(app.processIdentifier);
    @try {
        id window = Attribute(application, kAXFocusedWindowAttribute);
        if (!window || CFGetTypeID((__bridge CFTypeRef)window) != AXUIElementGetTypeID()) Fail(@"TARGET_UNAVAILABLE");
        AXUIElementRef windowRef = (__bridge AXUIElementRef)window;
        if ([Attribute(windowRef, kAXMinimizedAttribute) boolValue]) Fail(@"INACTIVE_TARGET");
        NSString *title = Attribute(windowRef, kAXTitleAttribute) ?: @"";
        if (![title isKindOfClass:NSString.class]) Fail(@"TARGET_UNAVAILABLE");
        if (Sensitive(title) || Matches(title, @"\\b(passwords?|sign.?in|checkout|payment|banking|API keys?|access tokens?|OTP)\\b|비밀번호|계정\\s*복구|인터넷\\s*뱅킹|인증\\s*코드|개인\\s*메시지")) Fail(@"PRIVACY_BLOCKED");
        id focused = Attribute(application, kAXFocusedUIElementAttribute);
        if (focused && CFGetTypeID((__bridge CFTypeRef)focused) == AXUIElementGetTypeID() &&
            [Attribute((__bridge AXUIElementRef)focused, kAXSubroleAttribute) isEqualToString:(__bridge NSString *)kAXSecureTextFieldSubrole]) Fail(@"PRIVACY_BLOCKED");
        id rawPosition = Attribute(windowRef, kAXPositionAttribute), rawSize = Attribute(windowRef, kAXSizeAttribute);
        if (!rawPosition || !rawSize || CFGetTypeID((__bridge CFTypeRef)rawPosition) != AXValueGetTypeID() || CFGetTypeID((__bridge CFTypeRef)rawSize) != AXValueGetTypeID()) Fail(@"TARGET_UNAVAILABLE");
        CGPoint position = CGPointZero; CGSize size = CGSizeZero;
        if (!AXValueGetValue((__bridge AXValueRef)rawPosition, kAXValueCGPointType, &position) ||
            !AXValueGetValue((__bridge AXValueRef)rawSize, kAXValueCGSizeType, &size) ||
            !isfinite(position.x) || !isfinite(position.y) || !isfinite(size.width) || !isfinite(size.height) || size.width <= 0 || size.height <= 0) Fail(@"TARGET_UNAVAILABLE");
        return @{@"pid": @(app.processIdentifier), @"bundleId": bundle, @"appName": app.localizedName ?: bundle,
                 @"windowTitle": [title substringToIndex:MIN(800, title.length)], @"x": @(position.x), @"y": @(position.y), @"width": @(size.width), @"height": @(size.height)};
    } @finally { CFRelease(application); }
}
static void Wait(BOOL (^completed)(void)) {
    NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:25];
    while (!completed()) {
        if ([deadline timeIntervalSinceNow] <= 0) Fail(@"CAPTURE_TIMEOUT");
        [NSRunLoop.currentRunLoop runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.01]];
    }
}
static BOOL BlackImage(CGImageRef image) {
    uint8_t pixels[32 * 32 * 4] = {0};
    CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = CGBitmapContextCreate(pixels, 32, 32, 8, 32 * 4, space, kCGImageAlphaPremultipliedLast);
    CGColorSpaceRelease(space);
    if (!context) return YES;
    CGContextDrawImage(context, CGRectMake(0, 0, 32, 32), image); CGContextRelease(context);
    for (NSUInteger i = 0; i < sizeof(pixels); i += 4) if ((int)pixels[i] + pixels[i + 1] + pixels[i + 2] > 24) return NO;
    return YES;
}
static NSDictionary *Capture(NSDictionary *request) {
    NSDictionary *target = Selected(request);
    if (![request[@"expected"] isEqual:target]) Fail(@"STALE_EVENT");
    if (!CGPreflightScreenCaptureAccess()) Fail(@"SCREEN_PERMISSION_REQUIRED");
    __block SCShareableContent *content; __block BOOL complete = NO;
    [SCShareableContent getShareableContentExcludingDesktopWindows:YES onScreenWindowsOnly:YES completionHandler:^(SCShareableContent *value, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{ content = value; complete = YES; });
    }];
    Wait(^BOOL { return complete; });
    if (!content) Fail(@"CAPTURE_UNAVAILABLE");
    if (![Selected(request) isEqual:target]) Fail(@"STALE_EVENT");
    CGRect expectedFrame = CGRectMake([target[@"x"] doubleValue], [target[@"y"] doubleValue], [target[@"width"] doubleValue], [target[@"height"] doubleValue]);
    NSMutableArray<SCWindow *> *candidates = [NSMutableArray array];
    for (SCWindow *window in content.windows) {
        CGRect frame = window.frame;
        NSString *title = window.title;
        BOOL titleMatches = !title || [[title substringToIndex:MIN(800, title.length)] isEqualToString:target[@"windowTitle"]];
        if (window.owningApplication.processID == [target[@"pid"] intValue] && window.windowLayer == 0 && titleMatches &&
            fabs(frame.origin.x - expectedFrame.origin.x) < 2 && fabs(frame.origin.y - expectedFrame.origin.y) < 2 &&
            fabs(frame.size.width - expectedFrame.size.width) < 2 && fabs(frame.size.height - expectedFrame.size.height) < 2) [candidates addObject:window];
    }
    if (candidates.count != 1) Fail(@"TARGET_UNAVAILABLE");
    SCWindow *window = candidates.firstObject;
    BOOL fullyVisible = NO;
    for (SCDisplay *display in content.displays) if (CGRectContainsRect(display.frame, window.frame)) fullyVisible = YES;
    if (!fullyVisible) Fail(@"WINDOW_NOT_FULLY_VISIBLE");
    SCStreamConfiguration *config = [SCStreamConfiguration new];
    config.width = (size_t)(window.frame.size.width * 2); config.height = (size_t)(window.frame.size.height * 2); config.showsCursor = NO;
    if (!config.width || !config.height || config.width * config.height > 32000000) Fail(@"CAPTURE_TOO_LARGE");
    // The independent window filter excludes all other windows, including Floating Poki.
    SCContentFilter *filter = [[SCContentFilter alloc] initWithDesktopIndependentWindow:window];
    __block CGImageRef image = NULL; complete = NO;
    [SCScreenshotManager captureImageWithFilter:filter configuration:config completionHandler:^(CGImageRef value, NSError *error) {
        CGImageRef retained = value ? CGImageRetain(value) : NULL;
        dispatch_async(dispatch_get_main_queue(), ^{ image = retained; complete = YES; });
    }];
    Wait(^BOOL { return complete; });
    if (!image) Fail(@"EMPTY_CAPTURE");
    @try {
        if (![Selected(request) isEqual:target]) Fail(@"STALE_EVENT");
        if (BlackImage(image)) Fail(@"BLACK_SCREEN");
        NSData *pixels = CFBridgingRelease(CGDataProviderCopyData(CGImageGetDataProvider(image)));
        if (!pixels || pixels.length > UINT32_MAX) Fail(@"EMPTY_CAPTURE");
        unsigned char hashBytes[CC_SHA256_DIGEST_LENGTH]; CC_SHA256(pixels.bytes, (CC_LONG)pixels.length, hashBytes);
        NSMutableString *hash = [NSMutableString string];
        for (NSUInteger i = 0; i < CC_SHA256_DIGEST_LENGTH; i++) [hash appendFormat:@"%02x", hashBytes[i]];
        if ([hash isEqual:request[@"previousImageHash"]]) Fail(@"DUPLICATE");
        VNRecognizeTextRequest *recognition = [VNRecognizeTextRequest new];
        recognition.recognitionLevel = VNRequestTextRecognitionLevelAccurate;
        recognition.recognitionLanguages = @[@"ko-KR", @"en-US"]; recognition.usesLanguageCorrection = NO;
        VNImageRequestHandler *handler = [[VNImageRequestHandler alloc] initWithCGImage:image options:@{}];
        if (![handler performRequests:@[recognition] error:nil]) Fail(@"OCR_FAILED");
        if (![Selected(request) isEqual:target]) Fail(@"STALE_EVENT");
        NSArray<VNRecognizedTextObservation *> *lines = [recognition.results sortedArrayUsingComparator:^NSComparisonResult(VNRecognizedTextObservation *a, VNRecognizedTextObservation *b) {
            CGFloat ay = CGRectGetMidY(a.boundingBox), by = CGRectGetMidY(b.boundingBox);
            if (fabs(ay - by) > 0.01) return ay > by ? NSOrderedAscending : NSOrderedDescending;
            return a.boundingBox.origin.x < b.boundingBox.origin.x ? NSOrderedAscending : NSOrderedDescending;
        }];
        NSMutableArray<NSString *> *parts = [NSMutableArray array]; double weighted = 0; NSUInteger total = 0;
        for (VNRecognizedTextObservation *line in lines) {
            VNRecognizedText *item = [line topCandidates:1].firstObject;
            if (!item) continue;
            [parts addObject:item.string]; weighted += item.confidence * item.string.length; total += item.string.length;
        }
        NSString *text = [parts componentsJoinedByString:@"\n"];
        if (!text.length) Fail(@"EMPTY_CAPTURE");
        if (Sensitive(text)) Fail(@"PRIVACY_BLOCKED");
        if (text.length > 6000) Fail(@"INCOMPLETE_REPRESENTATION");
        return @{@"target": target, @"text": text, @"confidence": @(weighted / MAX(1, total)), @"imageHash": hash};
    } @finally { CGImageRelease(image); }
}
int main(void) {
    @autoreleasepool {
        NSDictionary *response;
        @try {
            NSData *input = [NSFileHandle.fileHandleWithStandardInput readDataToEndOfFile];
            if (input.length > 32000) Fail(@"INVALID_REQUEST");
            NSDictionary *request = [NSJSONSerialization JSONObjectWithData:input options:0 error:nil];
            if (![request isKindOfClass:NSDictionary.class]) Fail(@"INVALID_REQUEST");
            NSString *operation = request[@"operation"]; NSDictionary *data;
            if ([operation isEqual:@"permissions"]) data = @{@"screen": @(CGPreflightScreenCaptureAccess()), @"accessibility": @(AXIsProcessTrusted())};
            else if ([operation isEqual:@"metadata"]) data = @{@"target": Selected(request)};
            else if ([operation isEqual:@"ocr"]) data = Capture(request);
            else Fail(@"INVALID_REQUEST");
            response = @{@"success": @YES, @"data": data};
        } @catch (NSException *exception) {
            NSString *code = [exception.name isEqual:@"CaptureFailure"] ? exception.reason : @"CAPTURE_UNAVAILABLE";
            response = @{@"success": @NO, @"error": @{@"code": code ?: @"CAPTURE_UNAVAILABLE"}};
        }
        NSData *output = [NSJSONSerialization dataWithJSONObject:response options:0 error:nil];
        [NSFileHandle.fileHandleWithStandardOutput writeData:output];
    }
    return 0;
}
