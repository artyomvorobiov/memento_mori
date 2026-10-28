#!/usr/bin/env python3
"""Собирает границы стран для поиска стран по геометкам фото («Найти страны в фото»).

Берёт контуры Natural Earth 1:10m (общественное достояние) из npm-пакета world-atlas
(лицензия ISC), слегка упрощает и сохраняет в MementoMori/Resources/country-lookup.json.
Карта на экране рисуется по более грубым данным (scripts/build-world-map.py), а здесь
нужна точность: фото в Женеве не должно оказаться во Франции.

Подготовка:
    pip install pycountry
    npm pack world-atlas@2 && tar -xzf world-atlas-*.tgz      # появится package/countries-10m.json

Запуск:
    python3 scripts/build-country-lookup.py package/countries-10m.json Shared/Core/Countries.swift MementoMori/Resources/country-lookup.json

Формат: {"scale": 1000, "countries": {"FR": [кольцо, …]}, "circles": {"VA": [широта, долгота, км]}}.
Кольцо — плоский список
[долгота0, широта0, Δдолготы1, Δшироты1, …] в тысячных долях градуса: первая точка целиком,
дальше разницы с предыдущей. Кольца — и внешние границы, и дырки (Лесото внутри ЮАР):
точка внутри страны, если она внутри нечётного числа её колец. Круги — места, которых
нет в данных отдельной страной (Ватикан, Реюньон): они важнее контуров вокруг них.
"""
import json, math, re, sys
import pycountry

topo_path, countries_swift, out_path = sys.argv[1:4]
SCALE = 1000            # тысячные доли градуса ≈ 110 м
SIMPLIFY = 0.004        # допуск Дугласа — Пекера в градусах ≈ 450 м

topo = json.load(open(topo_path, encoding="utf-8"))
sx, sy = topo["transform"]["scale"]
tx, ty = topo["transform"]["translate"]

def decode_arc(arc):
    x = y = 0
    pts = []
    for dx, dy in arc:
        x += dx; y += dy
        pts.append((x * sx + tx, y * sy + ty))
    return pts

arcs = [decode_arc(a) for a in topo["arcs"]]

def ring_points(indices):
    pts = []
    for i in indices:
        arc = arcs[i] if i >= 0 else list(reversed(arcs[~i]))
        if pts:
            arc = arc[1:]
        pts.extend(arc)
    return pts

def dp(points, tol):
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (x1, y1), (x2, y2) = points[a], points[b]
        dx, dy = x2 - x1, y2 - y1
        norm = math.hypot(dx, dy)
        best, idx = -1.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * px - dx * py + x2 * y1 - y2 * x1) / norm if norm > 0 else math.hypot(px - x1, py - y1)
            if d > best:
                best, idx = d, i
        if best > tol and idx > 0:
            keep[idx] = True
            stack.append((a, idx)); stack.append((idx, b))
    return [p for p, k in zip(points, keep) if k]

def clip_half(ring, limit, keep_less):
    def inside(p):
        return p[0] <= limit if keep_less else p[0] >= limit
    out = []
    for i in range(len(ring)):
        cur, prev = ring[i], ring[i - 1]
        if inside(cur):
            if not inside(prev):
                t = (limit - prev[0]) / (cur[0] - prev[0])
                out.append((limit, prev[1] + t * (cur[1] - prev[1])))
            out.append(cur)
        elif inside(prev):
            t = (limit - prev[0]) / (cur[0] - prev[0])
            out.append((limit, prev[1] + t * (cur[1] - prev[1])))
    return out

def split_antimeridian(ring):
    """Разворачивает долготы без скачков на 360° и режет кольцо по ±180°."""
    unwrapped = [ring[0]]
    for lon, lat in ring[1:]:
        prev = unwrapped[-1][0]
        while lon - prev > 180:
            lon -= 360
        while lon - prev < -180:
            lon += 360
        unwrapped.append((lon, lat))
    lons = [p[0] for p in unwrapped]
    if min(lons) >= -180 and max(lons) <= 180:
        return [unwrapped]
    pieces = []
    for shift in (-360, 0, 360):
        moved = [(lon + shift, lat) for lon, lat in unwrapped]
        part = clip_half(clip_half(moved, 180, True), -180, False)
        if len(part) >= 3:
            pieces.append(part)
    return pieces

def encode(ring):
    q = []
    for lon, lat in ring:
        p = (round(lon * SCALE), round(lat * SCALE))
        if not q or q[-1] != p:
            q.append(p)
    if len(q) > 1 and q[0] == q[-1]:
        q.pop()
    if len(q) < 3:
        return None
    flat = [q[0][0], q[0][1]]
    for (x0, y0), (x1, y1) in zip(q, q[1:]):
        flat += [x1 - x0, y1 - y0]
    return flat

catalog = list(dict.fromkeys(re.findall(r'"([A-Z]{2})"', open(countries_swift, encoding="utf-8").read())))
# Части, которых нет в каталоге, считаем страной, в которой они находятся для путешественника.
MERGE_BY_NAME = {
    "N. Cyprus": "CY", "Somaliland": "SO", "Cyprus U.N. Buffer Zone": "CY", "Akrotiri": "CY", "Dhekelia": "CY",
    "Åland": "FI", "Baikonur": "KZ", "USNB Guantanamo Bay": "CU",
    "Jersey": "GB", "Guernsey": "GB", "Isle of Man": "GB",
}
# Нет в данных отдельной страной: круг вокруг центра (широта, долгота, радиус в км).
CIRCLES = {"VA": (41.9029, 12.4534, 0.5), "RE": (-21.11, 55.53, 45), "GI": (36.14, -5.35, 3), "TV": (-8.52, 179.20, 40)}

countries = {}
unmapped = set()
points = 0
for geom in topo["objects"]["countries"]["geometries"]:
    gid, name = geom.get("id"), geom.get("properties", {}).get("name")
    code = MERGE_BY_NAME.get(name)
    if code is None and gid and str(gid).isdigit():
        c = pycountry.countries.get(numeric=str(gid).zfill(3))
        code = c.alpha_2 if c else None
    if code not in catalog:
        unmapped.add(f"{name} ({gid})")
        continue
    if geom["type"] == "Polygon":
        polys = [geom["arcs"]]
    elif geom["type"] == "MultiPolygon":
        polys = geom["arcs"]
    else:
        continue
    for poly in polys:
        for ring in poly:
            for piece in split_antimeridian(ring_points(ring)):
                simplified = dp(piece + [piece[0]], SIMPLIFY)[:-1]
                flat = encode(simplified) or encode(piece)
                if flat:
                    countries.setdefault(code, []).append(flat)
                    points += len(flat) // 2

circles = {code: list(value) for code, value in CIRCLES.items() if code in catalog and code not in countries}

missing = [c for c in catalog if c not in countries and c not in circles]
json.dump({"scale": SCALE, "countries": dict(sorted(countries.items())), "circles": circles},
          open(out_path, "w", encoding="utf-8"), separators=(",", ":"))
print(f"countries {len(countries)}, circles {sorted(circles)}, points {points}")
print("missing:", missing)
print("not in catalog:", sorted(unmapped))
