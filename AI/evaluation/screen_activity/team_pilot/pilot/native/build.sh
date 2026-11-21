#!/bin/sh
# macOS 14+; also produces a distributable binary for both Apple Silicon and Intel.
set -eu
cd "$(dirname "$0")"
work=$(mktemp -d "${TMPDIR:-/tmp}/focuson-observer.XXXXXXXX")
trap 'rm -rf "$work"' EXIT
# Some upgraded Apple CLT installs leave two module maps with the same SwiftBridging
# declaration. Hide the obsolete map for this build without modifying developer tools.
maps="$(xcode-select -p)/usr/include/swift"
set --
if test -f "$maps/bridging.modulemap" && test -f "$maps/module.modulemap" &&
   grep -q 'module SwiftBridging' "$maps/bridging.modulemap" &&
   grep -q 'module SwiftBridging' "$maps/module.modulemap"; then
  : > "$work/empty.modulemap"
  cat > "$work/overlay.yaml" <<EOF
{"version":0,"roots":[{"type":"file","name":"$maps/module.modulemap","external-contents":"$work/empty.modulemap"}]}
EOF
  set -- -vfsoverlay "$work/overlay.yaml" -Xcc -ivfsoverlay -Xcc "$work/overlay.yaml"
fi
for arch in arm64 x86_64; do
  xcrun --sdk macosx swiftc "$@" -swift-version 5 -O -whole-module-optimization -target "$arch-apple-macosx14.0" Observer.swift Privacy.swift -o "$work/$arch"
done
xcrun lipo -create "$work/arm64" "$work/x86_64" -output "$work/observer"
codesign --force --sign - --identifier org.focuson.localpilot.observer "$work/observer"
install -m 755 "$work/observer" ./observer
