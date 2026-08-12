# -*- coding: utf-8 -*-
import json
import collections
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from board_a import BABY, KINDER, ELEM
from board_b import JHS, HIGH, UNIV, POOL

STAGES = [
    ("baby", "あかちゃん", BABY),
    ("kinder", "ほいくえん", KINDER),
    ("elem", "しょうがっこう", ELEM),
    ("jhs", "ちゅうがっこう", JHS),
    ("high", "こうこう", HIGH),
    ("univ", "だいがく・しんしゃかいじん", UNIV),
]

TARGET_BG = 90
DELTA = ("st", "sp", "pp", "mn", "move", "rest")


def is_empty(c):
    return c["type"] == "NORMAL" and not any(k in c for k in DELTA)


cells = []
stages = []
i = 0
for key, name, arr in STAGES:
    stages.append({"key": key, "name": name, "from": i, "to": i + len(arr) - 1})
    for c in arr:
        c = dict(c)
        c["i"] = i
        cells.append(c)
        i += 1

# ---- bg を約80マスに絞る ----
# 背景は message() を開くマスでしか表示されない（CHOICE / CRUSH / START / 空マスは出ない）
NODIALOG = ("CHOICE", "CRUSH", "START", "CLUBEVENT")
TOP = ("STAGEGOAL", "GOAL", "CHALLENGE", "RANDOM", "AGAIN")


def shows_dialog(c):
    return c["type"] not in NODIALOG and not is_empty(c)


def prio(c):
    return 0 if c["type"] in TOP else 1


# 表示されないマスに書かれた背景は、近くの表示マスへ移す（使えない指定を捨てない）
for c in cells:
    if not c.get("bg") or shows_dialog(c):
        continue
    bg = c.pop("bg")
    for d in (1, -1, 2, -2, 3, -3):
        j = c["i"] + d
        if 0 <= j < len(cells) and shows_dialog(cells[j]) and not cells[j].get("bg"):
            cells[j]["bg"] = bg
            break

bgc = [c for c in cells if c.get("bg")]
# 1周目: その背景ファイルが初登場なら残す（種類のカバー率を優先）
keep = set()
seen = set()
for c in sorted(bgc, key=lambda c: (prio(c), c["i"])):
    if len(keep) >= TARGET_BG:
        break
    if c["bg"] in seen:
        continue
    seen.add(c["bg"])
    keep.add(c["i"])
# 2周目: 残り枠を優先度順で埋める
for c in sorted(bgc, key=lambda c: (prio(c), c["i"])):
    if len(keep) >= TARGET_BG:
        break
    keep.add(c["i"])
for c in bgc:
    if c["i"] not in keep:
        del c["bg"]

# ---- 出力 ----
out = collections.OrderedDict()
out["schemaVersion"] = 1
out["statNames"] = {"st": "べんきょう", "sp": "うんどう", "pp": "にんき", "mn": "おこづかい"}
out["stages"] = stages
# ---- タイプで いれかわる スロットと プール ----
# ステージに はいるたび、えらばれた タイプの おおい ものが スロットに ならぶ。
SWAP_N = 10
# DATE / LOVE は「かならず ある こいの マス」なので いれかえない
STRUCT = ("START", "GOAL", "STAGEGOAL", "CHALLENGE", "CLUBEVENT", "WARP", "REST",
          "CRUSH", "AGAIN", "DATE", "LOVE")


def swappable(c):
    if c["type"] in STRUCT:
        return False
    if is_empty(c):
        # 通過マスは テンポの ためのもの。いれかえ たいしょうに しない
        return False
    if any(k in c for k in ("goal", "love", "deai", "move", "rest", "choices")):
        return False
    return True


def autotag(c):
    st = c.get("st", 0)
    sp = c.get("sp", 0)
    if c["type"] in ("DATE", "LOVE"):
        return "love"
    if st > 0 and st >= sp:
        return "study"
    if sp > 0:
        return "sport"
    return "free"


pools = collections.OrderedDict()
for st in stages:
    key = st["key"]
    if key not in POOL:
        continue
    inner = [j for j in range(st["from"] + 1, st["to"]) if swappable(cells[j])]
    if len(inner) < SWAP_N:
        raise SystemExit("stage %s: いれかえ できる マスが たりない (%d)" % (key, len(inner)))
    # ステージ全体に ちらばるように ひろう
    slots = [inner[int(round(k * (len(inner) - 1) / float(SWAP_N - 1)))] for k in range(SWAP_N)]
    slots = sorted(set(slots))
    while len(slots) < SWAP_N:
        for j in inner:
            if j not in slots:
                slots.append(j)
                break
        slots = sorted(set(slots))
    pool = []
    for j in slots:
        cells[j]["swap"] = True
        cells[j].setdefault("tag", autotag(cells[j]))
        p = dict(cells[j])
        p.pop("i", None)
        p.pop("swap", None)
        pool.append(p)
    for c in POOL[key]:
        p = dict(c)
        p.pop("swap", None)
        pool.append(p)
    pools[key] = {"slots": [j - st["from"] for j in slots], "cells": pool}
out["pools"] = pools

out["types"] = [
    {"key": "study", "name": "べんきょうタイプ", "icon": "📘",
     "text": "べんきょうの マスが ふえる。"},
    {"key": "sport", "name": "うんどうタイプ", "icon": "🏃",
     "text": "うんどうの マスが ふえる。"},
    {"key": "love", "name": "こいタイプ", "icon": "💗",
     "text": "デートと こいの マスが ふえる。"},
    {"key": "balance", "name": "バランスタイプ", "icon": "⚖",
     "text": "どれも まんべんなく ふえる。"},
]

ordered = []
for c in cells:
    o = collections.OrderedDict()
    o["i"] = c["i"]
    o["type"] = c["type"]
    for k in ("title", "text", "stat", "need", "okText", "ngText", "ok", "ng",
              "choices", "goal", "love", "deai", "tag", "swap",
              "st", "sp", "pp", "mn", "move", "rest", "bg"):
        if k in c:
            o[k] = c[k]
    ordered.append(o)
out["cells"] = ordered

out["endings"] = [
    {"key": "st", "title": "けんきゅうしゃルート", "text": "しらべること、かんがえることが たからものに なった。"},
    {"key": "sp", "title": "アスリートルート", "text": "からだを うごかす よろこびが みちに なった。"},
    {"key": "pp", "title": "なかまルート", "text": "きみの まわりには いつも 人が いた。"},
    {"key": "mn", "title": "じりつルート", "text": "じぶんの ちからで たつ じゅんびが できた。"},
]
# ---- ぶかつ ----
# ちゅうがっこう いこう、ステージに はいるたびに ぜんいんが 1つ えらぶ。
# d = ぶかつマス1回ぶんの のび / join = にゅうぶ時の のび（d の およそ半分）
def CLUB(key, name, icon, bg, joinText, eventText, deaiText, **d):
    join = {}
    for k, v in d.items():
        if k == "mn":
            join[k] = int(v * 0.5)
        elif v > 0:
            join[k] = (v + 1) // 2
        else:
            join[k] = v // 2
    return {"key": key, "name": name, "icon": icon, "bg": bg,
            "joinText": joinText, "eventText": eventText, "deaiText": deaiText,
            "d": d, "join": join}


out["clubs"] = [
    CLUB("baseball", "やきゅうぶ", "⚾", "bg_schoolyard",
         "しろい ボールを おいかける ひびが はじまった。",
         "ノックを うけて どろだらけに なった。",
         "おなじ ポジションの人と キャッチボールを した。", sp=5, st=1),
    CLUB("soccer", "サッカーぶ", "⚽", "bg_field_sunset",
         "くらくなるまで ボールを けった。",
         "ハーフコートを なんども はしった。",
         "パスの あいてと はなすように なった。", sp=5, pp=1),
    CLUB("tennis", "テニスぶ", "🎾", "bg_highschool_day",
         "ラケットの にぎりかたから おそわった。",
         "かべうちを 100かい つづけた。",
         "ダブルスの ペアと なかよくなった。", sp=3, pp=3),
    CLUB("basket", "バスケットぶ", "🏀", "bg_gym",
         "たいいくかんに シューズの おとが ひびく。",
         "ドリブルで ぬく れんしゅうを くりかえした。",
         "しあいの あと ジュースを おごってもらった。", sp=4, pp=2),
    CLUB("volley", "バレーボールぶ", "🏐", "bg_gym",
         "こえを だすことから おぼえた。",
         "レシーブを つなげて ラリーが つづいた。",
         "セッターの人と いきが あった。", sp=3, pp=4),
    CLUB("golf", "ゴルフぶ", "⛳", "bg_plains_path",
         "しばの うえで スイングを ならった。",
         "しずかに いっぽん、まっすぐ とばせた。",
         "コースを まわりながら ずっと はなしていた。", sp=2, st=2, mn=-500),
    CLUB("shogi", "しょうぎぶ", "♟", "bg_school_library",
         "こまの うごきから おしえてもらった。",
         "つめしょうぎを といて さきを よむ ちからが ついた。",
         "たいきょくの あと かんそうせんで もりあがった。", st=5),
    CLUB("kagaku", "かがくぶ", "🧪", "bg_science_room",
         "はくいを きて じっけんだいの まえに たった。",
         "しやくの いろが かわる しゅんかんを きろくした。",
         "じっけんの パートナーと きが あった。", st=4, pp=1),
    CLUB("kateika", "かていかぶ", "🍳", "bg_cooking_room",
         "エプロンを して ほうちょうを もった。",
         "みんなに ふるまった おかしが 大こうひょう。",
         "レシピを おしえあう なかに なった。", st=2, pp=3),
    CLUB("kitaku", "きたくぶ", "🏠", "bg_mall",
         "まっすぐ かえって じぶんの じかんを つくった。",
         "よりみちの まちで じぶんの すきなことを した。",
         "おなじ みせに かよう人と はなすように なった。", st=2, mn=1200),
]
# ぶかつを えらびなおす ステージ
out["clubStages"] = ["jhs", "high", "univ"]

out["goals"] = [
    {"key": "exam", "label": "じゅけん せいこう"},
    {"key": "sports", "label": "たいかい ゆうしょう"},
    {"key": "love", "label": "こいびとが できる"},
]

path = os.path.join(ROOT, "app/src/main/assets/events_human.json")
json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

# ---- レポート ----
print("cells=%d stages=%d" % (len(cells), len(stages)))
print("empty=%d (%.1f%%)" % (sum(1 for c in cells if is_empty(c)),
                             100.0 * sum(1 for c in cells if is_empty(c)) / len(cells)))
bgs = [c["bg"] for c in cells if c.get("bg")]
print("bg cells=%d distinct=%d" % (len(bgs), len(set(bgs))))
avail = set(l.strip()[:-4] for l in open(os.path.join(ROOT, "tools/asset_list.txt"), encoding="utf-8")
            if l.startswith("bg_"))
print("unused bg:", sorted(avail - set(bgs)))
print("missing bg:", sorted(set(bgs) - avail))
for s in stages:
    seg = cells[s["from"]:s["to"] + 1]
    print("%-6s %3d-%3d n=%2d empty=%2d bg=%2d" % (
        s["key"], s["from"], s["to"], len(seg),
        sum(1 for c in seg if is_empty(c)), sum(1 for c in seg if c.get("bg"))))

st = sp = pp = 5
mn = 1000
for c in cells:
    if c["type"] == "CHALLENGE":
        cur = {"st": st, "sp": sp, "pp": pp, "mn": mn}[c["stat"]]
        print("CH %3d %-12s stat=%s need=%3d base=%3d ratio=%.2f" % (
            c["i"], c["title"], c["stat"], c["need"], cur, c["need"] / float(cur)))
    st += c.get("st", 0)
    sp += c.get("sp", 0)
    pp += c.get("pp", 0)
    mn += c.get("mn", 0)
print("final base st=%d sp=%d pp=%d mn=%d" % (st, sp, pp, mn))
