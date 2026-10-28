#!/usr/bin/env python3
"""Собирает карту мира для вкладки «Мир» и виджета «Мой мир».

Берёт контуры стран Natural Earth 1:50m (общественное достояние) из npm-пакета
world-atlas (лицензия ISC), проецирует их в проекцию Natural Earth I, упрощает
и сохраняет в Shared/Resources/world-map.json.

Подготовка:
    pip install pycountry
    npm pack world-atlas@2 && tar -xzf world-atlas-*.tgz      # появится package/countries-50m.json

Запуск:
    python3 scripts/build-world-map.py package/countries-50m.json Shared/Core/Countries.swift Shared/Resources/world-map.json
"""
import json, math, re, sys
import pycountry

topo_path, countries_swift, out_path = sys.argv[1:4]
WIDTH = 2000
SIMPLIFY = 0.7          # допуск Дугласа — Пекера в пикселях карты
MIN_RING_AREA = 3.0     # кольца меньше этой площади выбрасываем (кроме крупнейшего у страны)
DOT_AREA = 40.0         # страны меньше этой площади дополнительно рисуем точкой
MIN_LAT = -58.0

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

def natural_earth(lon, lat):
    lam, phi = math.radians(lon), math.radians(lat)
    phi2 = phi * phi
    phi4 = phi2 * phi2
    x = lam * (0.8707 - 0.131979 * phi2 + phi4 * (-0.013791 + phi4 * (0.003971 * phi2 - 0.001529 * phi4)))
    y = phi * (1.007226 + phi2 * (0.015085 + phi4 * (-0.044475 + 0.028874 * phi2 - 0.005916 * phi4)))
    return x, y

def area(ring):
    s = 0.0
    for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1]):
        s += x1 * y2 - x2 * y1
    return abs(s) / 2

def centroid(ring):
    a = 0.0; cx = cy = 0.0
    for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1]):
        f = x1 * y2 - x2 * y1
        a += f; cx += (x1 + x2) * f; cy += (y1 + y2) * f
    if abs(a) < 1e-9:
        xs = [p[0] for p in ring]; ys = [p[1] for p in ring]
        return sum(xs) / len(xs), sum(ys) / len(ys)
    return cx / (3 * a), cy / (3 * a)

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
        norm = math.hypot(dx, dy) or 1e-12
        best, idx = -1.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * px - dx * py + x2 * y1 - y2 * x1) / norm if (dx or dy) else math.hypot(px - x1, py - y1)
            if d > best:
                best, idx = d, i
        if best > tol and idx > 0:
            keep[idx] = True
            stack.append((a, idx)); stack.append((idx, b))
    return [p for p, k in zip(points, keep) if k]

def clip_half(ring, limit, keep_less):
    """Сазерленд — Ходжман: оставляет часть кольца по одну сторону вертикали lon = limit."""
    def inside(p):
        return p[0] <= limit if keep_less else p[0] >= limit
    out = []
    n = len(ring)
    for i in range(n):
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

# 1) Проецируем все кольца
features = []
for geom in topo["objects"]["countries"]["geometries"]:
    gid = geom.get("id")
    if geom["type"] == "Polygon":
        polys = [geom["arcs"]]
    elif geom["type"] == "MultiPolygon":
        polys = geom["arcs"]
    else:
        continue
    rings = []
    for poly in polys:
        for k, ring in enumerate(poly):
            if k > 0:
                continue  # дырки (озёра/анклавы) не рисуем
            pts = ring_points(ring)
            if all(lat < MIN_LAT for _, lat in pts):
                continue
            for piece in split_antimeridian(pts):
                rings.append([natural_earth(lon, max(lat, MIN_LAT - 2)) for lon, lat in piece])
    features.append((gid, geom.get("properties", {}).get("name"), rings))

xs = [x for _, _, rings in features for r in rings for x, _ in r]
ys = [y for _, _, rings in features for r in rings for _, y in r]
minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
scale = WIDTH / (maxx - minx)
height = math.ceil((maxy - miny) * scale)

def to_px(p):
    return ((p[0] - minx) * scale, (maxy - p[1]) * scale)

catalog = list(dict.fromkeys(re.findall(r'"([A-Z]{2})"', open(countries_swift, encoding="utf-8").read())))

# Непризнанные части рисуем вместе с признанными странами (по границам ООН/ISO).
MERGE_BY_NAME = {"N. Cyprus": "CY", "Somaliland": "SO"}
# Нет в данных 1:50m — рисуем только точкой.
EXTRA_DOTS = {"TV": (-8.52, 179.20), "GI": (36.14, -5.35), "RE": (-21.11, 55.53)}

countries, other, dots, names = {}, [], {}, {}
areas = {}
first_rings = {}
total_points = 0
for gid, name, rings in features:
    code = MERGE_BY_NAME.get(name)
    if code is None and gid and gid.isdigit():
        c = pycountry.countries.get(numeric=gid.zfill(3))
        code = c.alpha_2 if c else None
    if code == "AQ":
        continue
    px_rings = [[to_px(p) for p in r] for r in rings]
    px_rings.sort(key=area, reverse=True)
    kept = []
    for i, r in enumerate(px_rings):
        if i > 0 and area(r) < MIN_RING_AREA:
            continue
        s = dp(r + [r[0]], SIMPLIFY)[:-1]
        if len(s) < 3:
            s = r if len(r) >= 3 else s
        if len(s) >= 3:
            kept.append(s)
    if not kept:
        continue
    flat = [[v for p in r for v in (round(p[0], 1), round(p[1], 1))] for r in kept]
    total_points += sum(len(r) for r in kept)
    total_area = sum(area(r) for r in px_rings)
    target = countries if code in catalog else None
    if target is None:
        other.extend(flat)
        continue
    countries.setdefault(code, []).extend(flat)
    names[code] = name
    areas[code] = areas.get(code, 0) + total_area
    if code not in first_rings or area(px_rings[0]) > area(first_rings[code]):
        first_rings[code] = px_rings[0]

for code, total in areas.items():
    if total < DOT_AREA:
        cx, cy = centroid(first_rings[code])
        dots[code] = [round(cx, 1), round(cy, 1)]
for code, (lat, lon) in EXTRA_DOTS.items():
    if code in catalog and code not in countries:
        x, y = to_px(natural_earth(lon, lat))
        dots[code] = [round(x, 1), round(y, 1)]

missing = [c for c in catalog if c not in countries and c not in dots]
json.dump({"width": WIDTH, "height": height, "countries": countries, "other": other, "dots": dots},
          open(out_path, "w", encoding="utf-8"), separators=(",", ":"))
print(f"size {WIDTH}x{height}, countries {len(countries)}, other rings {len(other)}, dots {len(dots)}, points {total_points}")
print("missing from map:", missing)
print("dots:", sorted(dots))
