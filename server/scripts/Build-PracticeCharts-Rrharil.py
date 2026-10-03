"""Package converted Rrhar'il charts without changing the supplied source PEZs.

Extract each source JSON and use 官谱转RPE谱 V2.4.exe to produce
<converted-root>/IN/output.json and <converted-root>/AT/output.json first.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import shutil
import zipfile


def beat(value):
    return value[0] + value[1] / value[2]


def validate_notes(source, converted):
    # Compare every note's type, side, lane, start and end in seconds, independent
    # of line splitting performed by the converter for constant-speed holds.
    expected = Counter()
    for line in source["judgeLineList"]:
        seconds = 1.875 / line["bpm"]
        for side, key in ((1, "notesAbove"), (2, "notesBelow")):
            for note in line[key]:
                expected[({1: 1, 2: 4, 3: 2, 4: 3}[note["type"]], side,
                          round(note["positionX"] * 75, 2),
                          round(note["time"] * seconds, 4),
                          round((note["time"] + note["holdTime"]) * seconds, 4))] += 1
    assert len(converted["BPMList"]) == 1, "Unexpected converted BPM layout"
    seconds = 60 / converted["BPMList"][0]["bpm"]
    actual = Counter()
    for line in converted["judgeLineList"]:
        for note in line.get("notes", []):
            actual[(note["type"], note["above"], round(note["positionX"], 2),
                    round(beat(note["startTime"]) * seconds, 4),
                    round(beat(note["endTime"]) * seconds, 4))] += 1
    assert actual == expected, f"Note conversion mismatch: {list((expected - actual).items())[:3]}"
    return sum(actual.values())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--converted-root", type=Path, default=Path("D:/PhigrosAppBuild/rrharil-v86"))
    parser.add_argument("--output-root", type=Path, default=Path("D:/PhigrosAppBuild"))
    args = parser.parse_args()
    name = "server-only-upgrade-Pre-0.9.7.11-v86-Rrharil-20260929"
    stage = args.output_root / name
    archive_path = stage.with_name(name + ".zip")
    if stage.exists() or archive_path.exists():
        raise FileExistsError(f"Output already exists: {stage}")
    inputs = []
    for difficulty, constant in (("IN", "16.1"), ("AT", "17.6")):
        original = Path(f"D:/Rrharil_{difficulty}_{constant}.pez")
        converted = json.loads((args.converted_root / difficulty / "output.json").read_text(encoding="utf-8"))
        with zipfile.ZipFile(original) as src:
            source = json.loads(src.read("Rrharil.TeamGrimoire.0.json"))
            count = validate_notes(source, converted)
            info = dict(line.split(": ", 1) for line in src.read("info.txt").decode("utf-8-sig").splitlines() if ": " in line)
            converted["META"].update(name="Rrhar'il", level=f"{difficulty} Lv.{constant}",
                                     composer="Team Grimoire", charter=info["Charter"],
                                     background="illustration.jpg", song="music.mp3",
                                     offset=source["offset"] * 1000)
            inputs.append((original.name, converted, src.read(info["Song"]), src.read(info["Picture"]), info, count))
    (stage / "practice-charts").mkdir(parents=True)
    (stage / "scripts").mkdir()
    for filename, chart, music, illustration, info, count in inputs:
        with zipfile.ZipFile(stage / "practice-charts" / filename, "w", zipfile.ZIP_DEFLATED) as target:
            target.writestr("chart.json", json.dumps(chart, ensure_ascii=False, separators=(",", ":")))
            target.writestr("music.mp3", music)
            target.writestr("illustration.jpg", illustration)
            info.update(Chart="chart.json", Song="music.mp3", Picture="illustration.jpg", Level=chart["META"]["level"])
            target.writestr("info.txt", "#\n" + "\n".join(f"{key}: {value}" for key, value in info.items()))
        print(f"{filename}: {count} notes; types, sides, positions and times preserved")
    shutil.copy2(Path(__file__).with_name("Deploy-PracticeCharts.ps1"), stage / "scripts")
    manifest = dict(package=name, kind="server-static-resource-only", appVersionName="Pre-0.9.7.11",
                    appVersionCode=86, includesApk=False, publishesAppUpdate=False, modifiesLatestJson=False,
                    payload=[f"practice-charts/{item[0]}" for item in inputs],
                    destination="C:/Services/PhigrosScore/app-update/practice-charts/")
    (stage / "SERVER_RESOURCE_MANIFEST.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8-sig")
    deploy = f"""# Pre-0.9.7.11 谱面资源升级包

新增 Rrhar'il IN 16.1 / AT 17.6，已转换为播放器支持的 RPE 格式；原始 PEZ 保持不变。
此包仅部署两张谱面，不含 APK、不发布 APP 更新、不修改 latest.json、后端程序或 Caddy。
默认安装目录 C:\\Services\\PhigrosScore，需沿用已有的 app-update 静态文件服务配置。
现有谱面文件会先备份。APP 界面和播放更新已包含在单独提供的 v86 APK 中。

将 ZIP 上传服务器桌面，在管理员 PowerShell 执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
$desktop = [Environment]::GetFolderPath('Desktop')
$dest = Join-Path $desktop ('PSQ-v86-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Expand-Archive -LiteralPath (Join-Path $desktop '{archive_path.name}') -DestinationPath $dest
Set-Location $dest
& .\\scripts\\Deploy-PracticeCharts.ps1 -ValidateOnly
```

校验通过后，在同一窗口正式部署：

```powershell
& .\\scripts\\Deploy-PracticeCharts.ps1
```

本包仅在本地生成并校验，尚未部署至生产服务器。
"""
    (stage / "DEPLOY.md").write_text(deploy, encoding="utf-8-sig")
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in stage.rglob("*"):
            if path.is_file():
                assert path.suffix.lower() != ".apk" and path.name not in ("latest.json", "Publish-AppUpdate.ps1")
                bundle.write(path, path.relative_to(stage).as_posix())
    print(archive_path)


if __name__ == "__main__":
    main()
