#!/bin/sh
# Меняет bundle id приложения и App Group во всех нужных местах.
#
# Использование:
#   ./scripts/set-bundle-id.sh com.ivanpetrov.mementomori
#
# Приложение получит этот id, виджет — <id>.widget, App Group — group.<id>.

set -e

NEW_ID="$1"
if [ -z "$NEW_ID" ]; then
  echo "Укажи новый идентификатор, например: ./scripts/set-bundle-id.sh com.ivanpetrov.mementomori"
  exit 1
fi

case "$NEW_ID" in
  *[!A-Za-z0-9.-]* | .* | *. | *..*)
    echo "Идентификатор может содержать только латинские буквы, цифры, точки и дефисы: $NEW_ID"
    exit 1
    ;;
esac

cd "$(dirname "$0")/.."

sed -i.bak "s/^BASE_BUNDLE_ID *=.*/BASE_BUNDLE_ID = $NEW_ID/" Config.xcconfig
rm -f Config.xcconfig.bak

for FILE in MementoMori/MementoMori.entitlements MementoWidget/MementoWidget.entitlements; do
  sed -i.bak "s#<string>group\.[^<]*</string>#<string>group.$NEW_ID</string>#" "$FILE"
  rm -f "$FILE.bak"
done

echo "Готово:"
echo "  приложение — $NEW_ID"
echo "  виджет     — $NEW_ID.widget"
echo "  App Group  — group.$NEW_ID"
