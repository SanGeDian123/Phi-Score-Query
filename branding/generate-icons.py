from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageChops


CANVAS_SIZE = 1024
CONTENT_MAX_WIDTH = 660
CONTENT_MAX_HEIGHT = 420
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def extract_dark_art(source_path: Path) -> Image.Image:
    source = Image.open(source_path).convert("RGB")
    grayscale = source.convert("L")
    ink = ImageChops.invert(grayscale)
    ink = ink.point(lambda value: 0 if value < 10 else value)
    bounds = ink.getbbox()
    if bounds is None:
        raise ValueError(f"No dark artwork found in {source_path}")

    artwork = ink.crop(bounds)
    scale = min(CONTENT_MAX_WIDTH / artwork.width, CONTENT_MAX_HEIGHT / artwork.height)
    target_size = (
        max(1, round(artwork.width * scale)),
        max(1, round(artwork.height * scale)),
    )
    artwork = artwork.resize(target_size, Image.Resampling.LANCZOS)

    foreground = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    glyphs = Image.new("RGBA", artwork.size, (0, 0, 0, 255))
    glyphs.putalpha(artwork)
    offset = (
        (CANVAS_SIZE - artwork.width) // 2,
        (CANVAS_SIZE - artwork.height) // 2,
    )
    foreground.alpha_composite(glyphs, offset)
    return foreground


def render_legacy_icon(foreground: Image.Image, size: int, round_icon: bool) -> Image.Image:
    background = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (255, 255, 255, 255))
    background.alpha_composite(foreground)
    if round_icon:
        mask = Image.new("L", (CANVAS_SIZE, CANVAS_SIZE), 0)
        from PIL import ImageDraw

        ImageDraw.Draw(mask).ellipse((0, 0, CANVAS_SIZE - 1, CANVAS_SIZE - 1), fill=255)
        background.putalpha(mask)
    return background.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python branding/generate-icons.py <source-image>")

    project_root = Path(__file__).resolve().parent.parent
    source_path = Path(sys.argv[1]).resolve()
    foreground = extract_dark_art(source_path)

    normalized = Image.new("RGB", (CANVAS_SIZE, CANVAS_SIZE), "white")
    normalized.paste(foreground, mask=foreground.getchannel("A"))
    normalized.save(project_root / "branding" / "icon-source.png", optimize=True)

    drawable = project_root / "app" / "src" / "main" / "res" / "drawable-nodpi"
    foreground.save(drawable / "ic_launcher_source.png", optimize=True)

    res = project_root / "app" / "src" / "main" / "res"
    for density, size in DENSITIES.items():
        output = res / f"mipmap-{density}"
        render_legacy_icon(foreground, size, round_icon=False).save(
            output / "ic_launcher.png",
            optimize=True,
        )
        render_legacy_icon(foreground, size, round_icon=True).save(
            output / "ic_launcher_round.png",
            optimize=True,
        )


if __name__ == "__main__":
    main()
