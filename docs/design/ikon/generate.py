"""Generator ikon HabitFlow.

Semua file ikon dibuat dari geometri di script ini. Jangan mengedit hasilnya
langsung: ubah script, lalu jalankan ulang dari root repo:

    python docs/design/ikon/generate.py

Hasil:
- app/src/main/res/drawable/ic_launcher_foreground.xml
- app/src/main/res/drawable/ic_launcher_monochrome.xml
- app/src/main/res/values/ic_launcher_background.xml
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml dan ic_launcher_round.xml
- docs/design/ikon/ikon-512.png (Play Store dan README)
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
RES = ROOT / "app/src/main/res"
OUT = Path(__file__).resolve().parent

# Kanvas adaptive icon 108dp. Grid 3x3 berukuran 49dp di tengah. Berkat sudut
# 3dp, titik terjauhnya 32,9dp dari pusat, masih di dalam safe zone 66dp.
CANVAS = 108
CELL = 15
GAP = 2
RADIUS = 3  # rasio sudut sama dengan sel heatmap (2dp untuk 9dp)
GRID = 3 * CELL + 2 * GAP
ORIGIN = (CANVAS - GRID) / 2

BACKGROUND = "#FAF9F7"  # background, docs/design/README.md
HEAT = ["#E3E2E0", "#CFE8DB", "#B3CCBF", "#8FA79B", "#4D6359"]  # level 0..4, terang

# Level per sel, baris atas ke bawah. Makin pekat ke kanan atas: hari-hari
# yang terus membaik.
LEVELS = [
    [2, 3, 4],
    [1, 2, 3],
    [0, 1, 2],
]

# Monochrome (themed icon Android 13+) hanya memakai alpha.
MONO_ALPHA = [0.18, 0.35, 0.55, 0.78, 1.0]


def cells():
    for row, levels in enumerate(LEVELS):
        for col, level in enumerate(levels):
            x = ORIGIN + col * (CELL + GAP)
            y = ORIGIN + row * (CELL + GAP)
            yield x, y, level


def rounded_rect_path(x, y, w, h, r):
    def f(v):
        return f"{v:g}"

    return (
        f"M{f(x + r)},{f(y)}h{f(w - 2 * r)}a{f(r)},{f(r)} 0,0 1,{f(r)},{f(r)}"
        f"v{f(h - 2 * r)}a{f(r)},{f(r)} 0,0 1,{f(-r)},{f(r)}"
        f"h{f(-(w - 2 * r))}a{f(r)},{f(r)} 0,0 1,{f(-r)},{f(-r)}"
        f"v{f(-(h - 2 * r))}a{f(r)},{f(r)} 0,0 1,{f(r)},{f(-r)}z"
    )


HEADER = "<!-- Dibuat oleh docs/design/ikon/generate.py. Jangan diedit langsung. -->\n"


def vector(paths):
    body = "\n".join(paths)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        + HEADER
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{CANVAS}dp"\n'
        f'    android:height="{CANVAS}dp"\n'
        f'    android:viewportWidth="{CANVAS}"\n'
        f'    android:viewportHeight="{CANVAS}">\n'
        f"{body}\n"
        "</vector>\n"
    )


def path_xml(d, color, alpha=None):
    alpha_attr = f'\n        android:fillAlpha="{alpha:g}"' if alpha is not None else ""
    return (
        "    <path\n"
        f'        android:fillColor="{color}"{alpha_attr}\n'
        f'        android:pathData="{d}" />'
    )


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")
    print(f"tulis {path.relative_to(ROOT)}")


def main():
    foreground = [
        path_xml(rounded_rect_path(x, y, CELL, CELL, RADIUS), HEAT[level])
        for x, y, level in cells()
    ]
    monochrome = [
        path_xml(rounded_rect_path(x, y, CELL, CELL, RADIUS), "#FFFFFFFF", MONO_ALPHA[level])
        for x, y, level in cells()
    ]
    write(RES / "drawable/ic_launcher_foreground.xml", vector(foreground))
    write(RES / "drawable/ic_launcher_monochrome.xml", vector(monochrome))
    write(
        RES / "values/ic_launcher_background.xml",
        '<?xml version="1.0" encoding="utf-8"?>\n'
        + HEADER
        + f'<resources>\n    <color name="ic_launcher_background">{BACKGROUND}</color>\n</resources>\n',
    )
    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        + HEADER
        + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    )
    write(RES / "mipmap-anydpi-v26/ic_launcher.xml", adaptive)
    write(RES / "mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)

    # PNG 512 untuk Play Store: kanvas penuh tanpa masker (Play Store memberi sudut sendiri).
    size = 512
    scale = size / CANVAS
    ss = 4  # supersampling supaya tepi halus
    img = Image.new("RGB", (size * ss, size * ss), BACKGROUND)
    draw = ImageDraw.Draw(img)
    for x, y, level in cells():
        box = [v * scale * ss for v in (x, y, x + CELL, y + CELL)]
        draw.rounded_rectangle(box, radius=RADIUS * scale * ss, fill=HEAT[level])
    img = img.resize((size, size), Image.LANCZOS)
    png = OUT / "ikon-512.png"
    img.save(png)
    print(f"tulis {png.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
