"""Generate Marketplace PNG icons for AIGenCommitMsg.

The geometry mirrors src/main/resources/META-INF/pluginIcon.svg:
a large four-point sparkle plus a small one, on a fully transparent canvas.

Usage:
    python tools/generate_icons.py

Outputs (relative to the repository root):
    assets/marketplace/icon-40.png   40x40 RGBA
    assets/marketplace/icon-80.png   80x80 RGBA

Requires Pillow (pip install pillow).
"""

from pathlib import Path

from PIL import Image, ImageDraw

REPO_ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = REPO_ROOT / "assets" / "marketplace"

SIZES = (40, 80)
SUPERSAMPLE = 8
CANVAS = 40  # pluginIcon.svg viewBox edge

PRIMARY = "#2563EB"    # large sparkle (same as pluginIcon.svg)
SECONDARY = "#60A5FA"  # small sparkle (same as pluginIcon.svg)

# Polygon coordinates copied from the path data in pluginIcon.svg.
LARGE_STAR = [
    (15.0, 3.75),
    (18.125, 11.875),
    (26.25, 15.0),
    (18.125, 18.125),
    (15.0, 26.25),
    (11.875, 18.125),
    (3.75, 15.0),
    (11.875, 11.875),
]
SMALL_STAR = [
    (29.375, 20.0),
    (31.25, 24.375),
    (35.625, 26.25),
    (31.25, 28.125),
    (29.375, 32.5),
    (27.5, 28.125),
    (23.125, 26.25),
    (27.5, 24.375),
]


def scaled(points, size, factor):
    scale = size / CANVAS * factor
    return [(x * scale, y * scale) for x, y in points]


def render(size):
    hi = size * SUPERSAMPLE
    image = Image.new("RGBA", (hi, hi), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon(scaled(LARGE_STAR, size, SUPERSAMPLE), fill=PRIMARY)
    draw.polygon(scaled(SMALL_STAR, size, SUPERSAMPLE), fill=SECONDARY)
    return image.resize((size, size), Image.LANCZOS)


def verify(path, expected):
    with Image.open(path) as image:
        image.load()
        width, height = image.size
        if (width, height) != (expected, expected):
            raise SystemExit(f"{path} is {width}x{height}, expected {expected}x{expected}")
        if image.mode != "RGBA":
            raise SystemExit(f"{path} mode is {image.mode}, expected RGBA")
        alpha = image.getchannel("A")
        low, high = alpha.getextrema()
        if low != 0:
            raise SystemExit(f"{path} has no fully transparent pixels (alpha min {low})")
        if high != 255:
            raise SystemExit(f"{path} has no fully opaque pixels (alpha max {high})")
        bbox = alpha.getbbox()
        print(
            f"OK {path.relative_to(REPO_ROOT)}: {width}x{height} {image.mode} "
            f"alpha={low}..{high} content_bbox={bbox}"
        )


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for size in SIZES:
        target = OUTPUT_DIR / f"icon-{size}.png"
        render(size).save(target, "PNG")
        verify(target, size)


if __name__ == "__main__":
    main()
