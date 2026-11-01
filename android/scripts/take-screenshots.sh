#!/usr/bin/env bash
# Снимает экраны Android-версии на демо-данных в docs/screenshots/android.
# Нужен запущенный эмулятор или телефон с отладкой по USB и собранный отладочный APK:
#   (cd android && ./gradlew :app:assembleDebug) && bash android/scripts/take-screenshots.sh
set -euo pipefail

APK=android/app/build/outputs/apk/debug/app-debug.apk
PKG=$(grep -E '^mementoApplicationId=' android/gradle.properties | cut -d= -f2)
OUT=docs/screenshots/android
mkdir -p "$OUT"

adb install -r "$APK" > /dev/null
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell appwidget grantbind --package "$PKG" --user 0 2>/dev/null || true
# Часы 9:41 и полная батарея, как на скриншотах из магазинов.
adb shell settings put global sysui_demo_allowed 1 || true
adb shell am broadcast -a com.android.systemui.demo -e command enter > /dev/null || true
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 > /dev/null || true
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false > /dev/null || true
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false > /dev/null || true

shot() {
  local name=$1
  shift
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/app.mementomori.MainActivity" --ez demo true "$@" > /dev/null
  sleep 5
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "снят $name"
}

gallery() {
  local name=$1 page=$2
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/app.mementomori.debug.WidgetGalleryActivity" --ei page "$page" > /dev/null
  sleep 8
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "снят $name"
}

shot 01-today --ei tab 0
shot 02-today-more --ei tab 0 --ez scrollToBottom true
shot 03-goals --ei tab 1
shot 04-world --ei tab 2
shot 05-life --ei tab 3 --es lifeMode weeks
shot 06-letters --ei tab 3 --ez scrollToBottom true
shot 07-counter --ei tab 0 --es screen counter
shot 08-letter --ei tab 0 --es screen letter
shot 09-photo-import --ei tab 2 --es screen photoImport
shot 10-new-goal --ei tab 1 --es screen newGoal --es title 'Поехать\ в\ Японию'
shot 11-life-years --ei tab 3 --es lifeMode years
gallery 12-widgets 1
gallery 13-widgets-more 2
adb shell am force-stop "$PKG"
# Уменьшаем, чтобы README не был тяжёлым.
if command -v mogrify > /dev/null; then
  mogrify -resize 540x "$OUT"/*.png
fi
echo "Готово: $OUT"
