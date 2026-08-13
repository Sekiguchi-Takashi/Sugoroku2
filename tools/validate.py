import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "app/src/main/kotlin/com/appathy/sugoroku/human/MainActivity.kt")
ASSETS = os.path.join(ROOT, "app/src/main/assets")
DRAWABLE = os.path.join(ROOT, "app/src/main/res/drawable")

ASSET_LIST = os.path.join(ROOT, "tools/asset_list.txt")

errors = []
warns = []


def drawable_names():
    """res/drawable と tools/asset_list.txt の和集合。
    納品ZIPは既存画像を含まない（新規追加ぶんだけ入る）ので、両方を見ないと
    「リポジトリにはあるがZIPには無い画像」を欠落と誤判定してしまう。"""
    listed = {os.path.splitext(l.strip())[0] for l in open(ASSET_LIST, encoding="utf-8") if l.strip()}
    if os.path.isdir(DRAWABLE):
        onDisk = {os.path.splitext(f)[0] for f in os.listdir(DRAWABLE)}
        missing = sorted(n for n in onDisk if n not in listed)
        if missing:
            errors.append("tools/asset_list.txt に未登録の画像: " + ", ".join(missing))
        return listed | onDisk
    return listed

# 1. JSON parse
charas = json.load(open(os.path.join(ASSETS, "charas_human.json"), encoding="utf-8"))
events = json.load(open(os.path.join(ASSETS, "events_human.json"), encoding="utf-8"))

# 2. drawable references
imgs = drawable_names()
for setname, st in charas["sets"].items():
    for c in st["charas"]:
        if c["img"] not in imgs:
            errors.append("missing drawable: " + c["img"])
        for k, v in c.get("images", {}).items():
            if v not in imgs:
                errors.append("missing stage drawable: %s (%s)" % (v, k))

# 3. cell index continuity
cells = events["cells"]
for n, c in enumerate(cells):
    if c["i"] != n:
        errors.append("cell index mismatch at %d (i=%s)" % (n, c["i"]))
if cells[0]["type"] != "START":
    errors.append("cell 0 must be START")
if cells[-1]["type"] != "GOAL":
    errors.append("last cell must be GOAL")

stages = events["stages"]
for n, st in enumerate(stages[:-1]):
    if cells[st["to"]]["type"] != "STAGEGOAL":
        errors.append("stage %s must end with STAGEGOAL at %d" % (st["key"], st["to"]))
for n, st in enumerate(stages):
    if n > 0 and st["from"] != stages[n - 1]["to"] + 1:
        errors.append("stage range gap before %s" % st["key"])
bgs = imgs
for c in cells:
    if c.get("bg") and c["bg"] not in bgs:
        errors.append("missing bg drawable at %d: %s" % (c["i"], c["bg"]))

# 4. type-specific required fields
VALID = {"START", "GOAL", "NORMAL", "GOOD", "BAD", "WARP", "REST", "CHOICE", "CHALLENGE", "CRUSH", "AGAIN", "RANDOM", "STAGEGOAL", "CLUBEVENT", "DATE", "LOVE"}
for c in cells:
    if c["type"] not in VALID:
        errors.append("unknown type at %d: %s" % (c["i"], c["type"]))
    if c["type"] in ("CHOICE", "RANDOM") and len(c.get("choices", [])) < 2:
        errors.append("%s needs 2 choices at %d" % (c["type"], c["i"]))
    if c.get("goal") and c["goal"] not in ("exam", "sports", "love"):
        errors.append("unknown goal key at %d: %s" % (c["i"], c["goal"]))
    if c.get("goal") and c["type"] not in ("CHALLENGE",):
        errors.append("goal must be on a CHALLENGE cell at %d" % c["i"])
    if c["type"] == "CHALLENGE":
        for k in ("stat", "need", "ok", "ng"):
            if k not in c:
                errors.append("CHALLENGE missing %s at %d" % (k, c["i"]))
        if c.get("stat") not in ("st", "sp", "pp", "mn"):
            errors.append("CHALLENGE bad stat at %d" % c["i"])
    if c["type"] == "WARP" and c.get("move", 0) == 0:
        errors.append("WARP needs move at %d" % c["i"])
    mv = c.get("move", 0)
    if not (0 <= c["i"] + mv <= len(cells) - 1):
        errors.append("move goes out of board at %d" % c["i"])

# 5. challenge feasibility (rough): accumulated stat before the cell
st = sp = pp = 5
mn = 1000
for c in cells:
    ch = c if c["type"] == "CHALLENGE" else None
    if ch:
        cur = {"st": st, "sp": sp, "pp": pp, "mn": mn}[ch["stat"]]
        if ch["need"] > cur * 1.6:
            warns.append("challenge at %d may be too hard (need=%s, typical=%s)" % (c["i"], ch["need"], cur))
    st += c.get("st", 0)
    sp += c.get("sp", 0)
    pp += c.get("pp", 0)
    mn += c.get("mn", 0)

# 6. Kotlin traps
src = open(KT, encoding="utf-8").read()
if src.count("{") != src.count("}"):
    errors.append("brace imbalance: %d open vs %d close" % (src.count("{"), src.count("}")))
if src.count("(") != src.count(")"):
    errors.append("paren imbalance: %d open vs %d close" % (src.count("("), src.count(")")))
for m in re.finditer(r"\$[A-Za-z_][A-Za-z0-9_]*", src):
    tail = src[m.end():m.end() + 1]
    if tail and ord(tail) > 0x2000:
        errors.append("string template trap: %s followed by non-ascii" % m.group(0))
if re.search(r"\becho\b", src):
    warns.append("echo found in source")

# 6.5 盤面のかたち（195マス構成の規約）
EXPECT = [("baby", 20), ("kinder", 25), ("elem", 40), ("jhs", 40), ("high", 40), ("univ", 40)]
if len(stages) != len(EXPECT):
    errors.append("stage count must be %d" % len(EXPECT))
else:
    for st, (key, n) in zip(stages, EXPECT):
        if st["key"] != key:
            errors.append("stage key mismatch: %s != %s" % (st["key"], key))
        if st["to"] - st["from"] + 1 != n:
            errors.append("stage %s must have %d cells (has %d)" % (key, n, st["to"] - st["from"] + 1))
if len(cells) != sum(n for _, n in EXPECT):
    errors.append("board must be %d cells (has %d)" % (sum(n for _, n in EXPECT), len(cells)))

DELTA = ("st", "sp", "pp", "mn", "move", "rest")
empty = [c for c in cells if c["type"] == "NORMAL" and not any(k in c for k in DELTA)]
ratio = len(empty) / float(len(cells))
if not (0.20 <= ratio <= 0.30):
    warns.append("通過マスが %d マス（%.1f%%）。ねらいは約25%%" % (len(empty), ratio * 100))
for c in empty:
    if c.get("bg"):
        errors.append("通過マスに bg があるが表示されない at %d" % c["i"])

bgcells = [c for c in cells if c.get("bg")]
if len(bgcells) > 160:
    warns.append("bg つきのマスが %d。ダイアログを開くマスは全部つける方針" % len(bgcells))
NODIALOG = ("CRUSH", "START", "CLUBEVENT")
# ダイアログを ひらく マスには かならず 背景を つける（v4.0の方針）
for c in cells:
    if c["type"] in NODIALOG or c in empty:
        continue
    if not c.get("bg"):
        errors.append("背景が ないマス at %d %s" % (c["i"], c.get("title")))
for c in bgcells:
    if c["type"] in NODIALOG:
        errors.append("%s は message() を開かないので bg が出ない at %d" % (c["type"], c["i"]))

# 6.6 ぶかつ
clubs = events.get("clubs", [])
clubStages = events.get("clubStages", [])
if clubs:
    if len(clubs) < 2:
        errors.append("clubs は 2つ以上ひつよう")
    keys = set()
    for c in clubs:
        for k in ("key", "name", "bg", "joinText", "eventText", "deaiText", "d", "join"):
            if k not in c:
                errors.append("club %s に %s が ない" % (c.get("key"), k))
        if c.get("key") in keys:
            errors.append("club key の じゅうふく: " + str(c.get("key")))
        keys.add(c.get("key"))
        if c.get("bg") and c["bg"] not in imgs:
            errors.append("missing club bg: " + c["bg"])
    stagekeys = {st["key"] for st in stages}
    for k in clubStages:
        if k not in stagekeys:
            errors.append("clubStages に しらない ステージ: " + k)
    # ぶかつマスは ぶかつを えらんだ あとの ステージにしか 置けない
    first = None
    for n, st in enumerate(stages):
        if st["key"] in clubStages:
            first = st["from"]
            break
    for c in cells:
        if c["type"] == "CLUBEVENT" and first is not None and c["i"] < first:
            errors.append("CLUBEVENT が ぶかつ選択の まえにある at %d" % c["i"])
elif any(c["type"] == "CLUBEVENT" for c in cells):
    errors.append("CLUBEVENT が あるのに clubs が ない")

# 6.7 タイプと いれかえスロット
pools = events.get("pools", {})
types = events.get("types", [])
if pools:
    tkeys = {t["key"] for t in types}
    for need in ("study", "sport", "love", "balance"):
        if need not in tkeys:
            errors.append("types に %s が ない" % need)
    stagemap = {st["key"]: st for st in stages}
    for key, po in pools.items():
        if key not in stagemap:
            errors.append("pools に しらない ステージ: " + key)
            continue
        st = stagemap[key]
        n = st["to"] - st["from"] + 1
        slots = po.get("slots", [])
        pcells = po.get("cells", [])
        if len(pcells) < len(slots):
            errors.append("%s: プールが スロットより すくない" % key)
        for j in slots:
            if not (0 < j < n - 1):
                errors.append("%s: スロット %d が ステージの はしにある" % (key, j))
            elif not cells[st["from"] + j].get("swap"):
                errors.append("%s: スロット %d に swap がない" % (key, j))
        tags = {c.get("tag") for c in pcells}
        for need in ("study", "sport", "love"):
            if need not in tags:
                errors.append("%s: プールに %s の マスが ない" % (key, need))
        for c in pcells:
            if c.get("bg") and c["bg"] not in imgs:
                errors.append("missing pool bg: " + c["bg"])
            if c["type"] in NODIALOG and c.get("bg"):
                errors.append("%s: %s は 背景が でない" % (key, c["type"]))
            if not c.get("bg") and c["type"] not in NODIALOG:
                errors.append("%s: プールの %s に 背景が ない" % (key, c["title"]))
    # こいのマスは いれかえに よらず かならず のこる
    for key, st in stagemap.items():
        if key not in pools:
            continue
        inner = cells[st["from"]:st["to"] + 1]
        if not any(c["type"] == "DATE" for c in inner):
            errors.append("%s に DATE が ない" % key)
        if not any(c["type"] == "LOVE" for c in inner):
            errors.append("%s に LOVE が ない" % key)
        for c in inner:
            if c["type"] in ("DATE", "LOVE") and c.get("swap"):
                errors.append("%s: こいのマスが いれかえ たいしょうに なっている at %d" % (key, c["i"]))

# 7. endings keys
for e in events["endings"]:
    if e["key"] not in ("st", "sp", "pp", "mn"):
        errors.append("bad ending key: " + e["key"])

for w in warns:
    print("WARN " + w)
for e in errors:
    print("ERROR " + e)
print("cells=%d players=%d partners=%d drawables=%d" % (len(cells), len(charas["sets"]["human"]["charas"]), len(charas["sets"].get("partner", {}).get("charas", [])), len(imgs)))
sys.exit(1 if errors else 0)
