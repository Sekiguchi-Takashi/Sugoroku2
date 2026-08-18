# -*- coding: utf-8 -*-
# 盤面データ（前半）: baby / kinder / elem

def C(t, title, text, bg=None, **kw):
    c = {"type": t, "title": title, "text": text}
    for k in ("st", "sp", "pp", "mn", "move", "rest"):
        if k in kw:
            c[k] = kw.pop(k)
    c.update(kw)
    if bg:
        c["bg"] = bg
    return c


def E(title, text, bg=None):
    return C("NORMAL", title, text, bg)


def CH(title, text, stat, need, okText, ngText, ok, ng, goal=None, bg=None, love=False):
    c = C("CHALLENGE", title, text, bg, stat=stat, need=need,
          okText=okText, ngText=ngText, ok=ok, ng=ng)
    if goal:
        c["goal"] = goal
    if love:
        c["love"] = True
    return c


def CHOICE(title, text, a, b, bg=None):
    return C("CHOICE", title, text, bg, choices=[a, b])


def RANDOM(title, text, a, b, bg=None):
    return C("RANDOM", title, text, bg, choices=[a, b])


def CL(title, text, deai=False, **kw):
    """ぶかつマス。上がるステータスと文章は はいっている ぶかつで きまるので、
    ここには ぶかつに よらない ぶん（おこづかいなど）だけを 書く。
    背景も ぶかつの ものを つかうので bg は 書かない。"""
    c = C("CLUBEVENT", title, text, None, **kw)
    if deai:
        c["deai"] = True
    return c


def DATE(title, text, bg=None, **kw):
    """デートマス。ランダムな3人が出て、えらんだ人の こうかんどが 上がる。"""
    return C("DATE", title, text, bg, tag="love", **kw)


def LOVE(title, text, bg=None, **kw):
    """こいのマス。こいびとが いなければ こくはく、いれば デートか けんか。"""
    return C("LOVE", title, text, bg, tag="love", **kw)


def SW(cell, tag):
    """タイプえらびで いれかわる スロット。tag は study / sport / love。"""
    cell["tag"] = tag
    cell["swap"] = True
    return cell


def op(label, text, **d):
    o = {"label": label, "text": text}
    o.update(d)
    return o


BABY = [
    C("START", "たんじょう", "この せかいに うまれた。ここから スタート。", "bg_delivery_room"),
    C("GOOD", "はじめての なきごえ", "げんきな こえが へやに ひびいた。", pp=2),
    E("すやすや", "きもちよさそうに ねている。とくに なにも おきない。"),
    C("GOOD", "ねがえり", "ころんと よこを むいた。はじめての ぼうけん。", "bg_baby_room", sp=2),
    C("GOOD", "はじめての おふろ", "ちいさな ベビーバスで あわあわ。あひるさんと いっしょ。",
      "bg_baby_bath", sp=2, pp=2),
    E("おひるね", "おひさまが あたたかい。ゆっくり すすむ。", "bg_park_day"),
    C("GOOD", "はじめての あんよ", "よろよろ たったまま 3ぽ あるけた！", sp=3),
    C("REST", "よぼうせっしゃ", "ちくっと して なきべそ。1かい やすみ。", "bg_hospital_room", rest=1),
    E("だっこ", "だっこされて まちを ながめた。"),
    C("GOOD", "えほん", "おなじ えほんを なんども よんでもらった。", "bg_baby_room", st=3),
    C("GOOD", "ベビーベッドの よる", "モビールが くるくる まわる。あさまで ぐっすり ねむれた。",
      "bg_baby_room", st=2, sp=1),
    CHOICE("はじめての おもちゃ", "どっちで あそぶ？",
           op("つみき", "たかく つんで こわして わらった。", st=3),
           op("ボール", "ころころ おいかけて はいはいが はやくなった。", sp=3),
           bg="bg_baby_room"),
    C("BAD", "よなき", "よるじゅう ないて みんな ねぶそく。", pp=-1),
    E("おさんぽ", "ベビーカーで こうえんを 1しゅう。", "bg_park_field"),
    RANDOM("ベビーようひんてん", "「ちいさな ともだち」で かいもの。なにを かって もらえた？",
           op("ベビーカー", "そとに でるのが たのしくなった。", sp=3, mn=-800),
           op("にぎにぎ おもちゃ", "ふると おとが して きゃっきゃ わらう。", st=2, pp=2, mn=-200),
           bg="bg_baby_shop"),
    C("GOOD", "はじめての ことば", "「まんま」と いえた。かぞくが おおよろこび。", "bg_cabin_living", st=2, pp=2),
    E("ゆびさし", "きに なる ものを ゆびで さした。"),
    RANDOM("はいはい きょうそう", "じどうかんの はいはい レース。どうなる？",
           op("ゴールした！", "みんなの こえに つられて まっすぐ すすんだ。", sp=3, pp=2),
           op("とちゅうで ストップ", "ゆかの もようが きに なって うごかなくなった。", pp=1),
           bg="bg_nursery_room"),
    C("GOOD", "1さいの たんじょうび", "ケーキの まえで しゃしんを とった。", "bg_cabin_living", pp=3, mn=300),
    C("STAGEGOAL", "たっち！", "よちよち あるけた。ほいくえんへ いく じゅんびが できた。",
      "bg_baby_room", pp=2),
]

KINDER = [
    E("ほいくえんの もん", "きいろい ぼうしを かぶって もんを くぐった。", "bg_nursery_gate"),
    C("GOOD", "ほいくえん にゅうえん", "しきに でて せんせいに なまえを よばれた。", "bg_ceremony_hall", pp=2),
    C("GOOD", "ほいくしつの じゆうあそび", "つみきで おおきな おしろを つくった。せんせいに ほめられた。",
      "bg_nursery_room", st=3, pp=2),
    CHOICE("じゆうじかん", "そとあそびの じかん、なにする？",
           op("そとで かけっこ", "ころんでも すぐ たちあがって はしった。", sp=4),
           op("おえかき", "クレヨンで かぞくの えを かいた。", st=4, pp=1),
           bg="bg_nursery_gate"),
    C("GOOD", "はじめての おともだち", "となりの子と てを つないで あるいた。", "bg_nursery_room", pp=3),
    E("ねんど あそび", "ねんどで へんな どうぶつを つくった。"),
    C("REST", "みずぼうそう", "おやすみして ゆっくり ねる。1かい やすみ。", "bg_hospital_room", rest=1),
    C("GOOD", "えんそく（どうぶつえん）", "キリンの くびの ながさに びっくり。おべんとうも おいしい。",
      "bg_zoo", st=3, pp=2),
    C("GOOD", "すいぞくかん", "おおきな すいそうの まえで ずっと うごかなかった。",
      "bg_aquarium", st=4, pp=1),
    C("GOOD", "ゆうえんち", "かんらんしゃの てっぺんから パークぜんたいを みおろした。",
      "bg_amusement_wide", pp=3, mn=-500),
    C("GOOD", "なわとび", "10かい とべるように なった。", "bg_playground", sp=3),
    C("BAD", "ころんだ", "すべりだいで ころんで ひざを すりむいた。", "bg_playground", sp=-1, pp=-1),
    C("AGAIN", "おかしを もらった", "せんせいに おかしを もらって げんき まんたん。",
      "bg_nursery_room", mn=200),
    E("とうえんの みち", "さくらの はなびらが ふる みちを あるいた。", "bg_sakura_school_road"),
    CHOICE("しゅうまつの おでかけ", "きゅうじつ、どこへ いく？",
           op("のりものに のる", "のりものを のりまくって くたくたに なった。", sp=3, pp=3, mn=-600),
           op("しばふで おべんとう", "レジャーシートを ひろげて ゆっくり すごした。", st=2, pp=3, mn=-400)),
    C("GOOD", "ぼくじょう たいけん", "おおきな うしを まぢかで みた。ミルクの あじが かわった。",
      "bg_farm", st=3, sp=1),
    CHOICE("おゆうぎかい", "はっぴょうかい、どっちに でる？",
           op("げき", "おおきな こえで セリフを いえた。", pp=4),
           op("がっそう", "たいこの リズムを おぼえた。", st=3, pp=1),
           bg="bg_gym"),
    C("GOOD", "プールあそび", "みずが こわくなくなった。", sp=3, bg="bg_pool"),
    C("GOOD", "サーカスを みた", "そらを とぶ ブランコに くぎづけ。ピエロに わらった。",
      "bg_circus", pp=4, st=1, mn=-400),
    C("WARP", "バスに のった", "えんの バスで さきまで すすむ。3マス すすむ。", "bg_bus", move=3),
    C("GOOD", "おつかい", "はじめての おつかいで やおやさんへ。", mn=300, pp=1, bg="bg_veggie_stand"),
    C("GOOD", "はじめての キャンプ", "テントで ねた よる、ほしが たくさん みえた。",
      "bg_campsite", sp=3, st=2, mn=-600),
    RANDOM("たからさがし", "こうていで たからさがし。どうなる？",
           op("みつけた！", "きんいろの カードを みつけた。", mn=500, pp=2),
           op("みつからない", "さいごまで みつからなくて ないた。", pp=-1),
           bg="bg_schoolyard"),
    C("GOOD", "メリーゴーラウンド", "ゆうぐれの こうえんで しろい うまに のった。",
      "bg_carousel", pp=3, mn=-200),
    C("STAGEGOAL", "そつえんしき", "きいろい ぼうしと おわかれ。ランドセルが まっている。", pp=2, sp=1, bg="bg_gym"),
]

ELEM = [
    E("こうもんの まえ", "ランドセルを せおって こうもんを くぐった。"),
    C("GOOD", "はじめての ともだち", "となりの せきの子と なかよくなった。", "bg_classroom_elem", pp=2),
    C("GOOD", "しょうがっこう にゅうがく", "たいいくかんの にゅうがくしきに でた。", "bg_ceremony_hall", pp=2),
    C("AGAIN", "きゅうしょく", "おかわりを もらって げんき まんたん。", "bg_classroom_elem", sp=1),
    C("GOOD", "けいさんドリル", "ドリルを さいごまで やりきった。", "bg_study_corner", st=3),
    E("そうじとうばん", "ほうきで きょうしつを はいた。", "bg_school_corridor"),
    C("BAD", "ねぼう", "ねぼうして ちこく。せんせいに おこられた。", pp=-2),
    CHOICE("ほうかご", "ほうかご、どうする？",
           op("こうえんで あそぶ", "みんなで おにごっこ。あせだくで たのしかった。", sp=3, pp=2),
           op("としょしつで よむ", "ずかんを よみふけった。", st=4),
           bg="bg_school_library"),
    C("GOOD", "おこづかい", "おてつだいで おこづかいを もらった。", "bg_veggie_stand", mn=500),
    E("したじきの らくがき", "じゅぎょうちゅうに こっそり らくがき。"),
    C("REST", "かぜをひいた", "ねつが でて おやすみ。1かい やすみ。", "bg_hospital_room", rest=1),
    C("GOOD", "りかの じっけん", "じしゃくの ふしぎに おどろいた。", st=4, bg="bg_science_room"),
    C("GOOD", "うんどうかい", "リレーで 1いに なった！", sp=4, pp=3, bg="bg_sports_festival"),
    E("しもつばこ", "うわばきに はきかえて きょうしつへ。", "bg_shoe_locker_wood"),
    C("BAD", "わすれもの", "しゅくだいを わすれた。ろうかで はんせい。", st=-2),
    C("GOOD", "おんがくの じかん", "リコーダーが ふけるように なった。", st=2, pp=2, bg="bg_music_room"),
    C("WARP", "ちかみち", "うらの ほそみちを みつけた。3マス すすむ。", "bg_school_route", move=3),
    CHOICE("クラブかつどう", "クラブを えらぼう。",
           op("スポーツクラブ", "まいにち はしって からだが つよくなった。", sp=5),
           op("かがくクラブ", "じっけんが おもしろくて はまった。", st=5),
           bg="bg_science_room"),
    E("ろうかを あるく", "きょうしつを いどうした。とくに なにも おきない。", "bg_corridor"),
    C("GOOD", "たんじょうびかい", "みんなが きてくれた。", pp=4, mn=800),
    C("GOOD", "ずこうの じかん", "はんがを ほって てを まっくろに した。", st=3, bg="bg_art_room"),
    CH("がくげいかい", "げきの しゅやくに りっこうほ！", "pp", 16,
       "はくしゅ かっさい！ しゅやくに えらばれた。", "きんちょうして セリフを とばした…",
       {"pp": 5, "st": 1}, {"pp": -1}, bg="bg_gym"),
    E("あめの ひ", "そとで あそべず まどを ながめた。", "bg_park_rain"),
    C("GOOD", "かていかの じっしゅう", "はじめて たまごを やいた。", st=2, sp=1, bg="bg_cooking_room"),
    C("BAD", "ゲームの やりすぎ", "よふかしして あさが つらい。", st=-3, sp=-1),
    C("GOOD", "さかあがり", "なんどめかで くるんと まわれた。", sp=4, pp=1, bg="bg_playground"),
    E("としょしつの かえり", "かりた本を かかえて あるいた。", "bg_library"),
    C("NORMAL", "てんこうせい", "となりの まちから きた子と はなした。", pp=2, bg="bg_classroom_elem"),
    C("BAD", "けんか", "ろうかで ささいな ことから いいあいに なった。", pp=-3, bg="bg_corridor"),
    C("GOOD", "なかなおり", "あやまって なかなおり。まえより なかよく。", pp=5),
    C("REST", "ほけんしつ", "きゅうに おなかが いたくなった。1かい やすみ。", "bg_nurse_room", rest=1),
    CHOICE("しゅうがくりょこう", "じゆうじかん、なにを する？",
           op("やまを のぼる", "いきを きらして ちょうじょうへ。けしきが すごい。", sp=5),
           op("おみやげを えらぶ", "かぞくの ぶんまで かんがえて えらんだ。", pp=3, mn=-800),
           bg="bg_ryokan"),
    E("よるの ロッジ", "みんなで まくらを ならべて ねた。", "bg_lodge_night"),
    C("GOOD", "じゆうけんきゅう", "なつやすみ ずっと かんさつ にっきを つけた。", st=5),
    C("GOOD", "いいんかい", "としょいいんに なって 本の せいりを した。", st=2, pp=3, bg="bg_school_library"),
    E("かえりみち", "ゆうやけの みちを ゆっくり あるいた。", "bg_field_sunset"),
    C("WARP", "わすれものを とりに", "きょうしつまで もどる。2マス もどる。", move=-2),
    C("GOOD", "スキーきょうしつ", "ころびながら すべれるように なった。", sp=4, mn=-800, bg="bg_ski_slope"),
    C("GOOD", "さいごの きゅうしょく", "すきな メニューが でた。みんなで わらった。",
      "bg_classroom_elem", pp=3),
    C("STAGEGOAL", "しょうがっこう そつぎょう", "6ねんかんの おわり。せいふくの まちへ。", st=2, sp=2, pp=2, bg="bg_graduation"),
]
