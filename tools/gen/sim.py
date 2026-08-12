# -*- coding: utf-8 -*-
import json
import os
import random
import collections

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
D = json.load(open(os.path.join(ROOT, "app/src/main/assets/events_human.json"), encoding="utf-8"))
CELLS = D["cells"]
STAGES = D["stages"]
CLUBS = D.get("clubs", [])
CLUB_STAGES = set(D.get("clubStages", []))

hits = collections.defaultdict(list)   # cell i -> stat value at landing
finals = []
turns_used = []


def stage_of(pos):
    for n, s in enumerate(STAGES):
        if s["from"] <= pos <= s["to"]:
            return n
    return len(STAGES) - 1


def apply(p, d):
    for k in ("st", "sp", "pp", "mn"):
        if k in d:
            p[k] += d[k]


def land(p, pos, chain=True, depth=0):
    c = CELLS[pos]
    t = c["type"]
    if t == "DATE":
        p["aff"] = min(10, p["aff"] + 2)
        apply(p, c)
        return "ok"
    if t == "LOVE":
        apply(p, c)
        if p["mate"]:
            if random.random() < 0.30:
                p["fight"] += 1
                p["pp"] -= 2
                if p["fight"] >= 3:
                    p["mate"] = False
                    p["fight"] = 0
                    p["pp"] -= 4
            else:
                p["fight"] = 0
                p["pp"] += 3
                p["mn"] -= 900
        elif p["aff"] >= 6:
            if random.random() < 0.55:
                p["mate"] = True
                p["pp"] += 6
            else:
                p["aff"] = max(0, p["aff"] - 2)
                p["pp"] -= 1
        else:
            p["aff"] = min(10, p["aff"] + 1)
        return "ok"
    if t == "CLUBEVENT":
        if p["club"]:
            apply(p, p["club"]["d"])
        apply(p, c)
        return "ok"
    if t == "STAGEGOAL":
        apply(p, c)
        return "stagegoal"
    if t == "GOAL":
        apply(p, c)
        return "goal"
    if t in ("CHOICE", "RANDOM"):
        apply(p, random.choice(c["choices"]))
        return "ok"
    if t == "CHALLENGE":
        hits[pos].append(p[c["stat"]])
        ok = p[c["stat"]] >= c["need"]
        p["res"].append((pos, ok))
        apply(p, c["ok"] if ok else c["ng"])
        return "ok"
    apply(p, c)
    if c.get("rest"):
        p["rest"] += c["rest"]
    if t == "AGAIN" and chain:
        return "again"
    if c.get("move") and chain and depth < 3:
        nxt = max(STAGES[stage_of(pos)]["from"], min(STAGES[stage_of(pos)]["to"], pos + c["move"]))
        p["pos"] = nxt
        return land(p, nxt, True, depth + 1)
    return "ok"


def run(nplayers=4):
    ps = [{"pos": 0, "st": 5, "sp": 5, "pp": 5, "mn": 1000, "rest": 0, "res": [], "done": False,
           "club": None, "aff": 0, "mate": False, "fight": 0}
          for _ in range(nplayers)]
    rolls = 0
    turn = 0
    guard = 0
    while guard < 20000:
        guard += 1
        p = ps[turn % nplayers]
        turn += 1
        if p["done"]:
            if all(q["done"] for q in ps):
                break
            continue
        if p["rest"] > 0:
            p["rest"] -= 1
            continue
        again = True
        while again:
            again = False
            rolls += 1
            end = STAGES[stage_of(p["pos"])]["to"]
            p["pos"] = min(end, p["pos"] + random.randint(1, 6))
            r = land(p, p["pos"])
            if r == "again":
                again = True
            elif r == "stagegoal":
                si = stage_of(p["pos"])
                if si + 1 < len(STAGES):
                    nxt = STAGES[si + 1]
                    for q in ps:
                        q["pos"] = nxt["from"]
                        q["rest"] = 0
                        # ぶかつは ステージに はいるたび えらびなおす
                        if CLUBS and nxt["key"] in CLUB_STAGES:
                            if q["club"] and random.random() < 0.6:
                                pass
                            else:
                                q["club"] = random.choice(CLUBS)
                            apply(q, q["club"]["join"])
            elif r == "goal":
                p["done"] = True
        if all(q["done"] for q in ps):
            break
    turns_used.append(rolls)
    for q in ps:
        finals.append(q)


random.seed(1)
for _ in range(400):
    run(4)

print("平均ルーレット回数(4人合計) = %.0f" % (sum(turns_used) / float(len(turns_used))))
for k in ("st", "sp", "pp", "mn"):
    v = sorted(q[k] for q in finals)
    print("final %s  median=%d  p10=%d  p90=%d" % (k, v[len(v) // 2], v[len(v) // 10], v[len(v) * 9 // 10]))

print()
for c in CELLS:
    if c["type"] != "CHALLENGE":
        continue
    v = sorted(hits.get(c["i"], []))
    n = len(v)
    if n == 0:
        print("CH %3d %-12s 未到達" % (c["i"], c["title"]))
        continue
    okrate = sum(1 for x in v if x >= c["need"]) / float(n)
    print("CH %3d %-12s stat=%s need=%3d  到達率=%.2f  実測 median=%3d p25=%3d p75=%3d  せいこう率=%.2f"
          % (c["i"], c["title"], c["stat"], c["need"], n / (400.0 * 4),
             v[n // 2], v[n // 4], v[n * 3 // 4], okrate))
