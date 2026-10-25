import csv
import json
import re
from pathlib import Path
from urllib.request import urlopen

root = Path(__file__).resolve().parents[2]
output = root / "psq-web/public/data"
output.mkdir(parents=True, exist_ok=True)
metadata = {}
with (root / "app/src/main/assets/song_details.csv").open(encoding="utf-8-sig") as source:
    for row in csv.DictReader(source):
        metadata[row["id"]] = {
            "chapter": row["chapter"],
            "charts": {
                difficulty: {
                    "charter": row.get(difficulty + "_charter", ""),
                    "notes": int(row[difficulty + "_notes"]) if row.get(difficulty + "_notes", "").isdigit() else None,
                }
                for difficulty in ["EZ", "HD", "IN", "AT"]
            },
        }
(output / "song-metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
source = (root / "app/src/main/java/xyz/plcliangpicup/phigrosscore/data/CheckinModels.kt").read_text(encoding="utf-8")
block = source.split("private val checkinPoems = listOf(")[1].split("\n)")[0]
poems = re.findall(r'"([^"]+)"', block)
(output / "checkin-poems.json").write_text(json.dumps(poems, ensure_ascii=False), encoding="utf-8")
with urlopen("https://api.plc-liangpi-cup.xyz/api/v2/songs/catalog", timeout=20) as response:
    catalog = json.loads(response.read())
(output / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print("Exported", len(metadata), "song metadata rows,", len(poems), "poems,", len(catalog["items"]), "catalog songs.")
