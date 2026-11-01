"""Merge build/stories/*.json into the Stories catalogue the app reads.

Writes docs/stories/catalog.json (served at https://ampliapps.in/stories/catalog.json)
and app/src/main/assets/stories_catalog.json (bundled so the tab works offline and
before the website updates)."""
import json, time
from pathlib import Path

ORDER = ["ramayan", "mahabharat", "haveli-13", "chandrakanta", "vikram-betal", "bible-stories"]
series = [json.loads(p.read_text(encoding="utf-8")) for p in Path("build/stories").glob("*.json")]
series.sort(key=lambda s: (ORDER.index(s["id"]) if s["id"] in ORDER else 99, s["id"]))
catalog = {"version": int(time.time()), "series": series}
text = json.dumps(catalog, ensure_ascii=False, indent=1)
for out in [Path("docs/stories/catalog.json"), Path("app/src/main/assets/stories_catalog.json")]:
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text + "\n", encoding="utf-8")
print(f"{len(series)} series, {sum(len(s['episodes']) for s in series)} episodes")
