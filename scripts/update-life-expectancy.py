#!/usr/bin/env python3
"""Обновляет таблицу средней продолжительности жизни по странам.

Берёт свежие данные Всемирного банка (показатель SP.DYN.LE00.IN — ожидаемая
продолжительность жизни при рождении, оба пола) и перезаписывает файл
Shared/Core/LifeExpectancyData.swift. Сторонние библиотеки не нужны.

Использование:
    python3 scripts/update-life-expectancy.py
    python3 scripts/update-life-expectancy.py --input wb.json   # без интернета, из сохранённого ответа API
"""

import argparse
import json
import pathlib
import re
import sys
import urllib.request

API_URL = (
    "https://api.worldbank.org/v2/country/all/indicator/SP.DYN.LE00.IN"
    "?format=json&per_page=20000&mrnev=1"
)
WORLD_ID = "1W"
ROOT = pathlib.Path(__file__).resolve().parent.parent
COUNTRIES_SWIFT = ROOT / "MementoMori" / "Content" / "Countries.swift"
OUTPUT_SWIFT = ROOT / "Shared" / "Core" / "LifeExpectancyData.swift"


def load_rows(input_path):
    if input_path:
        payload = json.loads(pathlib.Path(input_path).read_text(encoding="utf-8"))
    else:
        with urllib.request.urlopen(API_URL, timeout=60) as response:
            payload = json.load(response)
    if not isinstance(payload, list) or len(payload) < 2:
        sys.exit(f"Неожиданный ответ API: {str(payload)[:200]}")
    return payload[1] or []


def latest_values(rows):
    """Последнее непустое значение для каждого кода страны: {код: (год, значение)}."""
    latest = {}
    for row in rows:
        value = row.get("value")
        code = (row.get("country") or {}).get("id")
        if value is None or not code:
            continue
        year = int(row.get("date", 0))
        if code not in latest or year > latest[code][0]:
            latest[code] = (year, float(value))
    return latest


def catalog_codes():
    source = COUNTRIES_SWIFT.read_text(encoding="utf-8")
    return list(dict.fromkeys(re.findall(r'"([A-Z]{2})"', source)))


def render(entries, world, year, missing):
    lines = "\n".join(f'        "{code}": {value:.1f},' for code, value in sorted(entries.items()))
    missing_note = ", ".join(missing) if missing else "нет"
    return f'''import Foundation

/// Ожидаемая продолжительность жизни при рождении (оба пола), лет.
///
/// Источник: Всемирный банк, World Development Indicators, показатель SP.DYN.LE00.IN,
/// данные за {year} год. Лицензия CC BY 4.0: https://datacatalog.worldbank.org/public-licenses
/// Файл сгенерирован скриптом scripts/update-life-expectancy.py.
/// Стран из каталога без данных: {missing_note}.
enum LifeExpectancyData {{
    static let year = {year}
    static let sourceName = "Всемирный банк"
    static let attribution = "Данные: Всемирный банк (World Development Indicators), {year} год, CC BY 4.0."

    /// Код «весь мир» (UN M49) — среднее по планете.
    static let worldCode = "001"
    static let world: Double = {world:.1f}

    static let byCountry: [String: Double] = [
{lines}
    ]

    /// Средняя продолжительность жизни для страны (или для всего мира по коду 001).
    static func value(for code: String) -> Double? {{
        code == worldCode ? world : byCountry[code]
    }}

    static func hasData(for code: String) -> Bool {{
        value(for: code) != nil
    }}
}}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", help="JSON-ответ API Всемирного банка, сохранённый заранее")
    args = parser.parse_args()

    latest = latest_values(load_rows(args.input))
    if WORLD_ID not in latest:
        sys.exit("В ответе нет среднего по миру (код 1W)")

    entries, missing, years = {}, [], set()
    for code in catalog_codes():
        if code in latest:
            year, value = latest[code]
            entries[code] = round(value, 1)
            years.add(year)
        else:
            missing.append(code)

    year = max(years | {latest[WORLD_ID][0]})
    OUTPUT_SWIFT.write_text(render(entries, round(latest[WORLD_ID][1], 1), year, missing), encoding="utf-8")
    print(f"Готово: {len(entries)} стран, данные за {year} год, весь мир — {latest[WORLD_ID][1]:.1f}")
    if missing:
        print("Нет данных:", ", ".join(missing))


if __name__ == "__main__":
    main()
