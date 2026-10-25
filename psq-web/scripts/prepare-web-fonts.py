"""Prepare web fonts from the APP's original Source Han Sans / Saira hybrid.

Run manually after changing the APP font; the generated files are distributed
with the web source. Requires fonttools[woff] (FontTools and Brotli).
"""

from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / "app/src/main/res/font/source_han_sans_saira_hybrid.ttf"
OUTPUT = ROOT / "public/fonts"


def original_font():
    font = TTFont(SOURCE, recalcTimestamp=False)
    if "fvar" in font:
        raise ValueError("The web CSS expects the APP's static Regular font.")
    # The merged font has inconsistent localized family names. Give both web
    # faces a consistent identity while retaining the original license names.
    names = font["name"]
    for name_id in (1, 2, 3, 4, 6, 16, 17, 18):
        names.removeNames(nameID=name_id)
    for name_id, text in {
        1: "PSQ Hybrid Web",
        2: "Regular",
        3: "PSQHybridWeb-Regular",
        4: "PSQ Hybrid Web Regular",
        6: "PSQHybridWeb-Regular",
        16: "PSQ Hybrid Web",
        17: "Regular",
    }.items():
        names.setName(text, name_id, 3, 1, 0x409)
        names.setName(text, name_id, 1, 0, 0)
    # An original signature does not describe a derived web container.
    if "DSIG" in font:
        del font["DSIG"]
    font.flavor = "woff2"
    return font


OUTPUT.mkdir(parents=True, exist_ok=True)
full = original_font()
full.save(OUTPUT / "psq-hybrid.woff2")
full.close()

latin = original_font()
options = subset.Options()
options.name_IDs = ["*"]
options.name_languages = ["*"]
options.name_legacy = True
options.layout_features = ["*"]
options.notdef_outline = True
subsetter = subset.Subsetter(options=options)
subsetter.populate(unicodes=range(0x100))
subsetter.subset(latin)
latin.save(OUTPUT / "psq-hybrid-latin.woff2")
latin.close()

for path in OUTPUT.glob("*.woff2"):
    print(f"{path.name}: {path.stat().st_size:,} bytes")
