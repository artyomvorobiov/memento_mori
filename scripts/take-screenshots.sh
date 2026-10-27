#!/bin/bash
# Снимает скриншоты приложения и виджетов в симуляторе на демонстрационных данных.
# Нужен Mac с Xcode. Пригодится для App Store и README.
#
# Использование:
#   ./scripts/take-screenshots.sh [папка]      (по умолчанию docs/screenshots)

set -euo pipefail
cd "$(dirname "$0")/.."

OUT="${1:-docs/screenshots}"
DERIVED="build/DerivedData"
APP="$DERIVED/Build/Products/Debug-iphonesimulator/MementoMori.app"
mkdir -p "$OUT"

if [ ! -d "$APP" ]; then
  echo "Собираю приложение…"
  xcodebuild build \
    -project MementoMori.xcodeproj \
    -scheme MementoMori \
    -configuration Debug \
    -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$DERIVED" \
    CODE_SIGNING_ALLOWED=NO \
    -quiet
fi

BUNDLE_ID=$(/usr/libexec/PlistBuddy -c "Print CFBundleIdentifier" "$APP/Info.plist")

# iPhone с самой новой iOS; предпочитаем модель Pro (не Max).
UDID=$(xcrun simctl list devices available --json | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
def version(runtime):
    tail = runtime.rsplit("iOS-", 1)[-1]
    return tuple(int(part) for part in tail.split("-") if part.isdigit())
best = None
for runtime, items in devices.items():
    if "iOS" not in runtime:
        continue
    for device in items:
        name = device["name"]
        if not name.startswith("iPhone"):
            continue
        score = (version(runtime), "Pro" in name and "Max" not in name, name)
        if best is None or score > best[0]:
            best = (score, device["udid"], name)
if best:
    print(best[1])
    print("Симулятор:", best[2], file=sys.stderr)
')
if [ -z "$UDID" ]; then
  echo "Не найден симулятор iPhone. Установи его в Xcode → Settings → Components."
  exit 1
fi

xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b > /dev/null
xcrun simctl status_bar "$UDID" override --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3 || true
xcrun simctl uninstall "$UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install "$UDID" "$APP"

shot() {
  local name="$1"
  shift
  xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
  xcrun simctl launch "$UDID" "$BUNDLE_ID" "$@" > /dev/null
  sleep "${DELAY:-6}"
  xcrun simctl io "$UDID" screenshot --type=png "$OUT/$name.png" > /dev/null 2>&1
  echo "  $name"
}

echo "Снимаю:"
DELAY=15 shot 01-onboarding
shot 02-onboarding-life -MementoOnboardingStep 1
shot 03-today -MementoDemo -selectedTab 0
shot 04-today-more -MementoDemo -selectedTab 0 -MementoScrollToBottom YES
shot 05-goals -MementoDemo -selectedTab 1
shot 06-life-weeks -MementoDemo -selectedTab 2 -lifeCalendarMode weeks
shot 07-life-years -MementoDemo -selectedTab 2 -lifeCalendarMode years
shot 08-widgets -MementoDemo -MementoWidgetGallery 1
shot 09-widgets-more -MementoDemo -MementoWidgetGallery 2
xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true

# Уменьшаем, чтобы README не был тяжёлым.
for file in "$OUT"/*.png; do
  sips --resampleWidth 600 "$file" > /dev/null
done

echo "Готово: $OUT"
