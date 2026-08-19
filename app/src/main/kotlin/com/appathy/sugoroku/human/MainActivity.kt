package com.appathy.sugoroku.human

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 人生ゲーム（にんげんすごろく）
 * どうぶつすごろく（SugorokuApp）から切り出した人間版（B面）。
 * 45マスの学園すごろく。小学校 → 中学校 → 高校 の3ステージ。
 * 依存ゼロ / XMLレイアウト不使用 / 全てプログラマティックUI。
 */
class MainActivity : Activity() {

    // ---------------- データ定義 ----------------

    class Chara(val name: String, val img: String, val images: HashMap<String, String>)

    class Delta(val st: Int, val sp: Int, val pp: Int, val mn: Int)

    class Choice(val label: String, val text: String, val d: Delta)

    class Challenge(
        val stat: String, val need: Int,
        val okText: String, val ngText: String,
        val ok: Delta, val ng: Delta
    )

    class Cell(
        var i: Int, val type: String, val title: String, val text: String,
        val d: Delta, val move: Int, val rest: Int,
        val choices: List<Choice>, val ch: Challenge?, val love: Boolean,
        val goalKey: String, val bg: String, val deai: Boolean,
        val tag: String, val swap: Boolean, val cast: List<String>
    )

    // タイプ（べんきょう / うんどう / こい / バランス）
    class PlayType(val key: String, val name: String, val icon: String, val text: String)

    // ステージごとの いれかえスロットと その こうほ
    class Pool(val slots: List<Int>, val cells: List<Cell>)

    // ぶかつ。d = ぶかつマス1回ぶんの のび / join = にゅうぶ時の のび
    class Club(
        val key: String, val name: String, val icon: String, val bg: String,
        val joinText: String, val eventText: String, val deaiText: String,
        val d: Delta, val join: Delta
    )

    class Stage(val key: String, val name: String, val from: Int, val to: Int)

    class Ending(val key: String, val title: String, val text: String)

    class Player(val chara: Chara, val cpu: Boolean) {
        var pos = 0
        var st = 5
        var sp = 5
        var pp = 5
        var mn = 1000
        var rest = 0
        var done = false
        var goalOrder = 0
        var crush: Chara? = null
        var partner: Chara? = null
        val goals = HashSet<String>()
        var stageWins = 0
        var club: Club? = null
        var type = ""
        // ループステージ用。dir=+1 いき / -1 かえり、phase 0=いき 1=かえり 2=さいごの いき
        var dir = 1
        var phase = 2
        // こうかんど（あいての なまえ → 0..10）
        val aff = HashMap<String, Int>()
        var fightStreak = 0
        var exCount = 0
    }

    // ---------------- 状態 ----------------

    private val handler = Handler(Looper.getMainLooper())

    // ---- テンポ（A面 SugorokuApp と同じ値）----
    object Speed {
        var fast = false
        val spinMs get() = if (fast) 1100L else 2600L
        val stepMs get() = if (fast) 140L else 300L
        val resultMs get() = if (fast) 300L else 700L
        val cpuWaitMs get() = if (fast) 400L else 1000L
        val eventWaitMs get() = if (fast) 350L else 700L
    }

    // ---- 盤面ズーム（A面と同じ。画面に何マス見えるか）----
    object Zoom {
        val steps = floatArrayOf(3f, 5f, 8f)
        var index = 0
        val cells: Float get() = steps[index]
        val label: String get() = steps[index].toInt().toString() + "マス"
        val isMax: Boolean get() = index == steps.size - 1
        fun next() { index = (index + 1) % steps.size }
        // 縮小するほどマスが潰れるので、比率を少し大きめに補正する
        val cellRatio: Float get() = if (index == 0) 0.27f else if (index == 1) 0.30f else 0.33f
    }
    private var charas: List<Chara> = ArrayList()
    private var partners: List<Chara> = ArrayList()
    private var eventCharas: List<Chara> = ArrayList()
    private var cells: MutableList<Cell> = ArrayList()
    private var stages: List<Stage> = ArrayList()
    private var endings: List<Ending> = ArrayList()
    private var clubs: List<Club> = ArrayList()
    private var clubStages: List<String> = ArrayList()
    private var playTypes: List<PlayType> = ArrayList()
    private val pools = HashMap<String, Pool>()
    // だいがくの 盤面背景（すごしかたの とうひょうで きまる）
    private var univBoardBg = ""
    // ループステージの オン・オフ（ステージkey → true）。タイトル画面で きりかえる
    private val loopOn = HashMap<String, Boolean>()

    private var players: MutableList<Player> = ArrayList()
    private var turn = 0
    private var goalCount = 0

    private var totalCount = 3
    private var humanCount = 1
    private val picked = ArrayList<Int>()

    private var boardView: BoardView? = null
    private var roulette: RouletteView? = null
    private var statusText: TextView? = null
    private var logText: TextView? = null
    private var statsBar: TextView? = null
    private val logs = ArrayList<String>()

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun dpi(v: Float): Int = dp(v).toInt()

    private fun roundedBg(fill: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(10f)
        if (stroke != 0) g.setStroke(dpi(2f), stroke)
        return g
    }

    /** えらぶ画面の 1こ。label=ボタンの 文字 / hint=「？」で ひらく 小さな せつめい */
    class PickItem(val label: String, val hint: CharSequence)

    /**
     * ボタンだけ ならべた えらぶ画面。ヒントは たたんであるので 画面が こまない。
     * head に ビューを わたすと 上に つく（デートの 絵など）。
     */
    private fun showPicker(title: String, head: View?, items: List<PickItem>, onPick: (Int) -> Unit) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
        if (head != null) box.addView(head)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).also { it.addView(box) })
            .setCancelable(false)
            .create()

        var i = 0
        while (i < items.size) {
            val item = items[i]
            val index = i

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL

            val btn = styledButton(item.label, 17f, Color.parseColor("#4CAF50"))
            btn.setOnClickListener {
                dialog.dismiss()
                onPick(index)
            }
            row.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val detail = TextView(this)
            detail.text = item.hint
            detail.textSize = 14f
            detail.setTextColor(Color.parseColor("#37474F"))
            detail.background = roundedBg(Color.parseColor("#F1F8E9"))
            detail.setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f))
            detail.visibility = View.GONE

            val q = styledButton("？", 15f, Color.parseColor("#90A4AE"))
            q.setOnClickListener {
                detail.visibility = if (detail.visibility == View.GONE) View.VISIBLE else View.GONE
            }
            val qlp = LinearLayout.LayoutParams(dpi(52f), ViewGroup.LayoutParams.WRAP_CONTENT)
            qlp.leftMargin = dpi(8f)
            row.addView(q, qlp)

            val rlp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            rlp.topMargin = dpi(6f)
            box.addView(row, rlp)

            val dlp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            dlp.topMargin = dpi(4f)
            box.addView(detail, dlp)
            i++
        }
        dialog.show()
    }

    private fun styledButton(labelText: String, size: Float, fill: Int): Button {
        val b = Button(this)
        b.text = labelText
        b.textSize = size
        b.minHeight = 0
        b.minimumHeight = 0
        b.setTextColor(Color.WHITE)
        b.background = roundedBg(fill)
        b.setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f))
        return b
    }

    // ---------------- ライフステージ別の画像解決 ----------------
    // images に無いステージは近いステージへフォールバック。
    // suffix は "_s"(側面) / "_b"(背面) など。存在しなければ suffix なしへ落ちる。

    private val stageKeys = listOf("baby", "kinder", "elem", "jhs", "high", "univ", "work", "senior")
    private val resCache = HashMap<String, Int>()

    private var currentBg = ""

    private fun stageIndexAt(pos: Int): Int {
        var i = 0
        while (i < stages.size) {
            if (pos >= stages[i].from && pos <= stages[i].to) return i
            i++
        }
        return if (stages.isEmpty()) 0 else stages.size - 1
    }

    private fun stageKeyAt(pos: Int): String {
        var i = 0
        while (i < stages.size) {
            val s = stages[i]
            if (pos >= s.from && pos <= s.to) return s.key
            i++
        }
        if (stages.isEmpty()) return "elem"
        return stages[stages.size - 1].key
    }

    private fun imageBaseFor(c: Chara, stageKey: String): String {
        var idx = stageKeys.indexOf(stageKey)
        if (idx < 0) idx = stageKeys.size - 1
        var i = idx
        while (i >= 0) {
            val b = c.images[stageKeys[i]]
            if (b != null) return b
            i--
        }
        i = idx + 1
        while (i < stageKeys.size) {
            val b = c.images[stageKeys[i]]
            if (b != null) return b
            i++
        }
        return c.img
    }

    private fun resOf(base: String, suffix: String): Int {
        val key = base + suffix
        val hit = resCache[key]
        if (hit != null) return hit
        var id = resources.getIdentifier(key, "drawable", packageName)
        if (id == 0) id = resources.getIdentifier(base, "drawable", packageName)
        resCache[key] = id
        return id
    }

    private fun charaRes(c: Chara, stageKey: String, suffix: String): Int {
        return resOf(imageBaseFor(c, stageKey), suffix)
    }

    // タイトルとキャラ選択で見せる姿（盤面の先頭＝あかちゃんではなく高校生を出す）
    private fun previewStageKey(): String = "high"

    // ---------------- 起動 ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()
        loadLoopSettings()
        showTitle()
    }

    private fun readAsset(name: String): String {
        val ins = assets.open(name)
        val bytes = ins.readBytes()
        ins.close()
        return String(bytes, Charsets.UTF_8)
    }

    private fun readDelta(o: JSONObject?): Delta {
        if (o == null) return Delta(0, 0, 0, 0)
        return Delta(o.optInt("st", 0), o.optInt("sp", 0), o.optInt("pp", 0), o.optInt("mn", 0))
    }

    private fun readCell(o: JSONObject, fallbackIndex: Int): Cell {
        val chs = ArrayList<Choice>()
        val ca = o.optJSONArray("choices")
        if (ca != null) {
            var k = 0
            while (k < ca.length()) {
                val co = ca.getJSONObject(k)
                chs.add(Choice(co.getString("label"), co.getString("text"), readDelta(co)))
                k++
            }
        }
        var chal: Challenge? = null
        if (o.optString("type") == "CHALLENGE") {
            chal = Challenge(
                o.optString("stat", "st"),
                o.optInt("need", 10),
                o.optString("okText", "せいこう！"),
                o.optString("ngText", "しっぱい…"),
                readDelta(o.optJSONObject("ok")),
                readDelta(o.optJSONObject("ng"))
            )
        }
        return Cell(
            o.optInt("i", fallbackIndex),
            o.optString("type", "NORMAL"),
            o.optString("title", ""),
            o.optString("text", ""),
            readDelta(o),
            o.optInt("move", 0),
            o.optInt("rest", 0),
            chs,
            chal,
            o.optBoolean("love", false),
            o.optString("goal", ""),
            o.optString("bg", ""),
            o.optBoolean("deai", false),
            o.optString("tag", ""),
            o.optBoolean("swap", false),
            readCast(o)
        )
    }

    private fun readCast(o: JSONObject): List<String> {
        val l = ArrayList<String>()
        val a = o.optJSONArray("cast") ?: return l
        var i = 0
        while (i < a.length()) {
            l.add(a.getString(i))
            i++
        }
        return l
    }

    private fun readCharaSet(root: JSONObject, setName: String): List<Chara> {
        val cl = ArrayList<Chara>()
        val sets = root.getJSONObject("sets")
        val set = sets.optJSONObject(setName) ?: return cl
        val arr = set.optJSONArray("charas") ?: return cl
        var i = 0
        while (i < arr.length()) {
            val o = arr.getJSONObject(i)
            val img = o.getString("img")
            val map = HashMap<String, String>()
            val io = o.optJSONObject("images")
            if (io != null) {
                val ks = io.keys()
                while (ks.hasNext()) {
                    val k = ks.next()
                    map[k] = io.getString(k)
                }
            }
            if (resources.getIdentifier(img, "drawable", packageName) != 0) {
                cl.add(Chara(o.getString("name"), img, map))
            }
            i++
        }
        return cl
    }

    private fun loadData() {
        val cj = JSONObject(readAsset("charas_human.json"))
        charas = readCharaSet(cj, "human")
        partners = readCharaSet(cj, "partner")
        eventCharas = readCharaSet(cj, "event")

        val ej = JSONObject(readAsset("events_human.json"))
        var i = 0

        val sl = ArrayList<Stage>()
        val sarr = ej.getJSONArray("stages")
        i = 0
        while (i < sarr.length()) {
            val o = sarr.getJSONObject(i)
            sl.add(Stage(o.optString("key", "elem"), o.getString("name"), o.getInt("from"), o.getInt("to")))
            i++
        }
        stages = sl

        val list = ArrayList<Cell>()
        val carr = ej.getJSONArray("cells")
        i = 0
        while (i < carr.length()) {
            list.add(readCell(carr.getJSONObject(i), i))
            i++
        }
        cells = list

        pools.clear()
        val pj = ej.optJSONObject("pools")
        if (pj != null) {
            val pk = pj.keys()
            while (pk.hasNext()) {
                val key = pk.next()
                val po = pj.getJSONObject(key)
                val sl = ArrayList<Int>()
                val sa = po.getJSONArray("slots")
                i = 0
                while (i < sa.length()) {
                    sl.add(sa.getInt(i))
                    i++
                }
                val pc = ArrayList<Cell>()
                val pa = po.getJSONArray("cells")
                i = 0
                while (i < pa.length()) {
                    pc.add(readCell(pa.getJSONObject(i), 0))
                    i++
                }
                pools[key] = Pool(sl, pc)
            }
        }

        val tl = ArrayList<PlayType>()
        val tarr = ej.optJSONArray("types")
        if (tarr != null) {
            i = 0
            while (i < tarr.length()) {
                val o = tarr.getJSONObject(i)
                tl.add(
                    PlayType(
                        o.getString("key"), o.getString("name"),
                        o.optString("icon", ""), o.optString("text", "")
                    )
                )
                i++
            }
        }
        playTypes = tl

        val kl = ArrayList<Club>()
        val karr = ej.optJSONArray("clubs")
        if (karr != null) {
            i = 0
            while (i < karr.length()) {
                val o = karr.getJSONObject(i)
                kl.add(
                    Club(
                        o.getString("key"), o.getString("name"),
                        o.optString("icon", ""), o.optString("bg", ""),
                        o.optString("joinText", ""), o.optString("eventText", ""),
                        o.optString("deaiText", ""),
                        readDelta(o.optJSONObject("d")), readDelta(o.optJSONObject("join"))
                    )
                )
                i++
            }
        }
        clubs = kl

        val ksl = ArrayList<String>()
        val ksarr = ej.optJSONArray("clubStages")
        if (ksarr != null) {
            i = 0
            while (i < ksarr.length()) {
                ksl.add(ksarr.getString(i))
                i++
            }
        }
        clubStages = ksl

        val el = ArrayList<Ending>()
        val earr = ej.getJSONArray("endings")
        i = 0
        while (i < earr.length()) {
            val o = earr.getJSONObject(i)
            el.add(Ending(o.getString("key"), o.getString("title"), o.getString("text")))
            i++
        }
        endings = el
    }

    // ---------------- 共通UI部品 ----------------

    private fun bigButton(label: String, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 18f
        b.setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(10f))
        b.setOnClickListener { action() }
        val lp = LinearLayout.LayoutParams(dpi(240f), ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dpi(10f)
        b.layoutParams = lp
        return b
    }

    private fun label(text: String, size: Float, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.gravity = Gravity.CENTER
        return t
    }

    private fun column(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.gravity = Gravity.CENTER_HORIZONTAL
        l.setPadding(dpi(16f), dpi(24f), dpi(16f), dpi(24f))
        return l
    }

    // ---------------- タイトル ----------------

    // ---------------- ループステージ ----------------

    private fun loopPrefs() = getSharedPreferences("sugoroku_human", Context.MODE_PRIVATE)

    private fun loadLoopSettings() {
        val pr = loopPrefs()
        loopOn.clear()
        var i = 0
        while (i < stages.size) {
            val k = stages[i].key
            loopOn[k] = pr.getBoolean("loop_" + k, false)
            i++
        }
    }

    private fun isLoop(stageKey: String): Boolean = loopOn[stageKey] == true

    private fun setLoop(stageKey: String, on: Boolean) {
        loopOn[stageKey] = on
        loopPrefs().edit().putBoolean("loop_" + stageKey, on).apply()
    }

    /** 各世代ごとに オン・オフ。オンの ステージは ゴール→スタート→ゴール の 3回わたり。 */
    private fun showLoopSettings() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))

        val note = TextView(this)
        note.text = "オンに すると、ゴールに ついても おりかえして スタートまで もどり、" +
                "もういちど ゴールを めざします。イベントは たくさん おきますが、" +
                "その せだいだけで ルーレットの かいすうが 3ばいに なります。"
        note.textSize = 13f
        note.setTextColor(Color.parseColor("#52616B"))
        note.setPadding(0, 0, 0, dpi(8f))
        box.addView(note)

        var i = 0
        while (i < stages.size) {
            val st = stages[i]
            val b = styledButton("", 17f, Color.parseColor("#4CAF50"))
            val paint = { ->
                val on = isLoop(st.key)
                b.text = (if (on) "🔁 オン　" else "▶ オフ　") + st.name
                b.background = roundedBg(
                    if (on) Color.parseColor("#F57C00") else Color.parseColor("#9E9E9E"))
            }
            paint()
            b.setOnClickListener {
                setLoop(st.key, !isLoop(st.key))
                paint()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dpi(6f)
            box.addView(b, lp)
            i++
        }

        AlertDialog.Builder(this)
            .setTitle("ループステージ")
            .setView(ScrollView(this).also { it.addView(box) })
            .setPositiveButton("とじる", null)
            .show()
    }

    private fun loopLabel(p: Player): String {
        val key = stageKeyAt(p.pos)
        if (!isLoop(key)) return ""
        if (p.phase == 0) return "🔁 いき ▶"
        if (p.phase == 1) return "🔁 かえり ◀"
        return "🔁 さいごの いき ▶"
    }

    private fun resetLoopState(p: Player, stageKey: String) {
        p.dir = 1
        p.phase = if (isLoop(stageKey)) 0 else 2
    }

    private fun showTitle() {
        val root = column()
        root.setBackgroundColor(Color.parseColor("#FFF6E5"))
        root.gravity = Gravity.CENTER

        val t = label("すごろく人生ゲーム", 30f, Color.parseColor("#3A5A40"))
        t.setTypeface(Typeface.DEFAULT_BOLD)
        root.addView(t)
        root.addView(label("しょうがっこう から こうこうまで の すごろく", 15f, Color.parseColor("#6B705C")))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.setPadding(0, dpi(18f), 0, dpi(8f))
        var i = 0
        while (i < 4 && i < charas.size) {
            val iv = ImageView(this)
            iv.setImageResource(charaRes(charas[i], previewStageKey(), ""))
            val lp = LinearLayout.LayoutParams(dpi(64f), dpi(64f))
            lp.leftMargin = dpi(4f)
            lp.rightMargin = dpi(4f)
            iv.layoutParams = lp
            row.addView(iv)
            i++
        }
        root.addView(row)

        root.addView(bigButton("はじめる") { showModeSelect() })
        root.addView(bigButton("ループステージ") { showLoopSettings() })
        root.addView(bigButton("あそびかた") { showHelp() })
        setContentView(root)
    }

    private fun showHelp() {
        val msg = "ルーレットを タップして すすもう。\n\n" +
                "とまった マスで イベントが おこり、\n" +
                "べんきょう / うんどう / にんき / おこづかい が かわります。\n\n" +
                "あかい マスは ちょうせん。ステータスが たりないと しっぱいします。\n" +
                "ぜんいんが ゴールしたら けっかはっぴょう。"
        AlertDialog.Builder(this).setTitle("あそびかた").setMessage(msg)
            .setPositiveButton("とじる", null).show()
    }

    // ---------------- あそびかた選択（1画面）----------------

    private fun modeButton(labelText: String, total: Int, humans: Int): Button {
        return bigButton(labelText) {
            totalCount = total
            humanCount = humans
            picked.clear()
            players = ArrayList()
            showCharaSelect(0)
        }
    }

    private fun showModeSelect() {
        val sv = ScrollView(this)
        val root = column()
        root.setBackgroundColor(Color.parseColor("#FFF6E5"))
        root.addView(label("あそびかたを えらぼう", 24f, Color.parseColor("#3A5A40")))
        root.addView(label("えらぶと キャラせんたくに すすみます", 13f, Color.parseColor("#6B705C")))
        root.addView(modeButton("ひとり ＋ CPU 1にん", 2, 1))
        root.addView(modeButton("ひとり ＋ CPU 2にん", 3, 1))
        root.addView(modeButton("ひとり ＋ CPU 3にん", 4, 1))
        root.addView(modeButton("ふたりで あそぶ", 2, 2))
        root.addView(modeButton("ふたり ＋ CPU 2にん", 4, 2))
        root.addView(bigButton("タイトルへ") { showTitle() })
        sv.addView(root)
        setContentView(sv)
    }

    // ---------------- 紹介ムービー ----------------
    // res/raw/intro_<NN>.mp4 があればキャラ決定時に再生する。無ければ何もせず次へ進む。

    // ムービー名はキャラの基準画像名に紐づける（プレイヤーの並び順に依存させない）
    // 例: あかり(chara_kid01) → res/raw/intro_chara_kid01.mp4
    private fun introResFor(c: Chara): Int {
        return resources.getIdentifier("intro_" + c.img, "raw", packageName)
    }

    private fun playIntro(resId: Int, after: () -> Unit) {
        if (resId == 0) {
            after()
            return
        }
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val vv = VideoView(this)
        val vlp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        vlp.gravity = Gravity.CENTER
        vv.layoutParams = vlp
        root.addView(vv)

        val tip = label("タップで スキップ", 14f, Color.WHITE)
        val tlp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        tlp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        tlp.bottomMargin = dpi(28f)
        tip.layoutParams = tlp
        root.addView(tip)

        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                vv.stopPlayback()
                after()
            }
        }

        root.setOnClickListener { finish() }
        vv.setOnClickListener { finish() }
        tip.setOnClickListener { finish() }
        vv.setOnCompletionListener { finish() }
        vv.setOnErrorListener { _: MediaPlayer?, _: Int, _: Int ->
            finish()
            true
        }
        vv.setOnPreparedListener { mp: MediaPlayer ->
            mp.setVolume(0.7f, 0.7f)
        }
        vv.setVideoURI(Uri.parse("android.resource://" + packageName + "/" + resId))
        setContentView(root)
        vv.start()
        handler.postDelayed({ finish() }, 15000)
    }

    // ---------------- キャラ選択 ----------------

    private fun showCharaSelect(index: Int) {
        if (charas.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("キャラが よみこめません")
                .setMessage("charas_human.json と drawable を かくにんして ください。")
                .setPositiveButton("OK") { _, _ -> showTitle() }
                .show()
            return
        }
        if (humanCount > charas.size) humanCount = charas.size
        if (totalCount > charas.size) totalCount = charas.size
        if (index >= humanCount) {
            fillCpu()
            startGame()
            return
        }
        val sv = ScrollView(this)
        val root = column()
        root.setBackgroundColor(Color.parseColor("#FFF6E5"))
        root.addView(label((index + 1).toString() + "P の キャラを えらぼう", 22f, Color.parseColor("#3A5A40")))

        var row: LinearLayout? = null
        var i = 0
        while (i < charas.size) {
            if (i % 3 == 0) {
                row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER
                root.addView(row)
            }
            val c = charas[i]
            val idx = i
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(dpi(6f), dpi(8f), dpi(6f), dpi(8f))
            val iv = ImageView(this)
            iv.setImageResource(charaRes(c, previewStageKey(), ""))
            iv.layoutParams = LinearLayout.LayoutParams(dpi(84f), dpi(84f))
            item.addView(iv)
            item.addView(label(c.name, 14f, Color.parseColor("#3A3A3A")))
            if (picked.contains(idx)) {
                item.alpha = 0.25f
            } else {
                item.setOnClickListener {
                    picked.add(idx)
                    players.add(Player(c, false))
                    playIntro(introResFor(c)) { showCharaSelect(index + 1) }
                }
            }
            row!!.addView(item)
            i++
        }
        root.addView(bigButton("もどる") { showModeSelect() })
        sv.addView(root)
        setContentView(sv)
    }

    private fun fillCpu() {
        while (players.size < totalCount) {
            var idx = Random.nextInt(charas.size)
            var guard = 0
            while (picked.contains(idx) && guard < 100) {
                idx = Random.nextInt(charas.size)
                guard++
            }
            picked.add(idx)
            players.add(Player(charas[idx], true))
        }
    }

    // ---------------- ゲーム画面 ----------------

    private var statusText2: TextView? = null
    private var speedButton: Button? = null
    private var zoomButton: Button? = null
    private var startButton: Button? = null

    private fun updateSpeedLabel() {
        speedButton?.text = if (Speed.fast) "はやさ: はやい⚡" else "はやさ: ふつう"
        speedButton?.background = roundedBg(
            if (Speed.fast) Color.parseColor("#EF6C00") else Color.parseColor("#78909C")
        )
    }

    private fun startGame() {
        turn = 0
        goalCount = 0
        univBoardBg = ""
        logs.clear()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#E8F5E9"))

        // 盤面（A面と同じく画面の上側いっぱい。ミニマップは盤面内に描く）
        val bv = BoardView(this)
        boardView = bv
        root.addView(bv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // 情報行（ステージ名・凡例）＋ ズーム / はやさ
        val infoRow = LinearLayout(this)
        infoRow.orientation = LinearLayout.HORIZONTAL
        infoRow.gravity = Gravity.CENTER_VERTICAL
        infoRow.setPadding(dpi(12f), 0, dpi(12f), 0)

        val st = TextView(this)
        st.textSize = 11f
        st.setTextColor(Color.parseColor("#558B2F"))
        statusText = st
        infoRow.addView(st, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val btnCol = LinearLayout(this)
        btnCol.orientation = LinearLayout.VERTICAL
        btnCol.gravity = Gravity.END

        val zb = Button(this)
        zb.textSize = 12f
        zb.minHeight = 0
        zb.minimumHeight = 0
        zb.setTextColor(Color.WHITE)
        zb.background = roundedBg(Color.parseColor("#5E35B1"))
        zb.setPadding(dpi(10f), dpi(5f), dpi(10f), dpi(5f))
        zb.setOnClickListener {
            boardView?.cycleZoom(players.getOrNull(turn)?.pos ?: 0)
            updateZoomLabel()
        }
        zoomButton = zb
        btnCol.addView(zb)
        updateZoomLabel()

        val sb2 = Button(this)
        sb2.textSize = 12f
        sb2.minHeight = 0
        sb2.minimumHeight = 0
        sb2.setTextColor(Color.WHITE)
        sb2.setPadding(dpi(10f), dpi(5f), dpi(10f), dpi(5f))
        sb2.setOnClickListener {
            Speed.fast = !Speed.fast
            updateSpeedLabel()
        }
        speedButton = sb2
        updateSpeedLabel()
        val slp2 = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slp2.topMargin = dpi(4f)
        btnCol.addView(sb2, slp2)
        infoRow.addView(btnCol)
        root.addView(infoRow)

        // 手番の見出し
        val st2 = TextView(this)
        st2.textSize = 17f
        st2.gravity = Gravity.CENTER
        st2.setTextColor(Color.parseColor("#33691E"))
        st2.setTypeface(Typeface.DEFAULT_BOLD)
        st2.setPadding(dpi(8f), dpi(2f), dpi(8f), dpi(2f))
        statusText2 = st2
        root.addView(st2)

        val lg = TextView(this)
        lg.textSize = 12f
        lg.setTextColor(Color.parseColor("#52616B"))
        lg.setPadding(dpi(12f), dpi(2f), dpi(12f), dpi(2f))
        lg.minLines = 2
        logText = lg
        root.addView(lg)

        // ルーレットとスタートボタン
        val controlRow = LinearLayout(this)
        controlRow.orientation = LinearLayout.HORIZONTAL
        controlRow.setPadding(dpi(8f), 0, dpi(8f), dpi(4f))

        val rv = RouletteView(this)
        rv.onResult = { n -> onSpinResult(n) }
        roulette = rv
        controlRow.addView(rv, LinearLayout.LayoutParams(0, dpi(200f), 1.3f))

        val buttonCol = LinearLayout(this)
        buttonCol.orientation = LinearLayout.VERTICAL
        buttonCol.gravity = Gravity.CENTER
        buttonCol.setPadding(dpi(8f), 0, dpi(4f), 0)

        val sbtn = Button(this)
        sbtn.text = "スタート！"
        sbtn.textSize = 20f
        sbtn.setTypeface(Typeface.DEFAULT_BOLD)
        sbtn.setTextColor(Color.WHITE)
        sbtn.background = roundedBg(Color.parseColor("#FF9800"))
        sbtn.setPadding(dpi(8f), dpi(20f), dpi(8f), dpi(20f))
        sbtn.setOnClickListener { roulette?.pressStart() }
        startButton = sbtn
        buttonCol.addView(sbtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val stbtn = styledButton("ステータス", 15f, Color.parseColor("#4CAF50"))
        stbtn.setOnClickListener { showStatusDialog() }
        val slp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slp.topMargin = dpi(12f)
        buttonCol.addView(stbtn, slp)

        controlRow.addView(buttonCol, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controlRow)

        // 下部のステータスバー（A面と同じ1行表示。全員ぶんはステータスボタンで見る）
        val sb = TextView(this)
        sb.textSize = 13f
        sb.gravity = Gravity.CENTER
        sb.setTextColor(Color.WHITE)
        sb.setBackgroundColor(Color.parseColor("#33691E"))
        sb.setPadding(dpi(8f), dpi(5f), dpi(8f), dpi(5f))
        statsBar = sb
        root.addView(sb)

        setContentView(root)
        var i = 0
        while (i < players.size) {
            resetLoopState(players[i], if (stages.isEmpty()) "" else stages[0].key)
            i++
        }
        updateStats()
        log("ゲームスタート！")
        beginTurn()
    }

    private fun showStatusDialog() {
        val sb = StringBuilder()
        var i = 0
        while (i < players.size) {
            val p = players[i]
            val who = if (p.cpu) "（CPU）" else "（" + (i + 1) + "P）"
            sb.append(p.chara.name).append(who).append("\n")
            sb.append("  べんきょう").append(p.st).append("　うんどう").append(p.sp)
            sb.append("　にんき").append(p.pp).append("　おこづかい").append(p.mn).append("えん\n")
            val cb = p.club
            if (cb != null) sb.append("  ").append(cb.icon).append(cb.name).append("\n")
            val mt = p.partner
            if (mt != null) {
                sb.append("  ♥ ").append(mt.name)
                if (p.fightStreak > 0) sb.append("（けんか ").append(p.fightStreak).append("かい）")
                sb.append("\n")
            } else {
                val tp = topCandidate(p)
                if (tp != null) sb.append("  ").append(tp.name).append(" ").append(affBar(affOf(p, tp))).append("\n")
            }
            sb.append("  ").append(goalLine(p)).append("\n\n")
            i++
        }
        AlertDialog.Builder(this).setTitle("ステータス")
            .setMessage(colorizeStats(sb.toString().trim()))
            .setPositiveButton("とじる", null).show()
    }

    private fun updateStats() {
        val bar = statsBar ?: return
        val me = players.getOrNull(turn) ?: return
        val tag = if (me.cpu) "（CPU）" else ""
        var extra = ""
        val pt = me.partner
        val cr = me.crush
        if (pt != null) extra = extra + "　♥" + pt.name
        else if (cr != null) extra = extra + "　…" + cr.name
        if (me.goals.size > 0) extra = extra + "　★" + me.goals.size
        val cb = me.club
        if (cb != null) extra = "　" + cb.icon + cb.name + extra
        val line = me.chara.name + tag + "　べんきょう" + me.st + " うんどう" + me.sp +
                " にんき" + me.pp + " おこづかい" + me.mn + "えん" + extra
        bar.text = colorizeStats(line, true)
    }

    private fun log(s: String) {
        logs.add(s)
        while (logs.size > 2) logs.removeAt(0)
        val lt = logText ?: return
        lt.text = logs.joinToString("\n")
    }

    private fun stageName(pos: Int): String {
        var i = 0
        while (i < stages.size) {
            val s = stages[i]
            if (pos >= s.from && pos <= s.to) return s.name
            i++
        }
        return ""
    }

    private fun updateStatus() {
        val p = players[turn]
        val who = if (p.cpu) "CPU" else (turn + 1).toString() + "P"
        val si = stageIndexAt(p.pos)
        val lpTag = loopLabel(p)
        statusText?.text = "ステージ" + (si + 1) + "/" + stages.size + "「" + stageName(p.pos) + "」" +
                (if (lpTag.isEmpty()) "" else "　" + lpTag) + "\n" +
                "🟢いいこと 🟣わるいこと 🟠ワープ 🔴ちょうせん\n☕デート 💞こい 🟡ステージゴール 🔵ぶかつ"
        statusText2?.text = who + "・" + p.chara.name + " の ばん（" + (p.pos + 1) + " / " +
                cells.size + "マス）"
        startButton?.isEnabled = !p.cpu
        startButton?.alpha = if (p.cpu) 0.4f else 1f
        boardView?.turnIndex = turn
        updateStats()
    }

    private fun updateZoomLabel() {
        val zb = zoomButton ?: return
        zb.text = "🔍 " + Zoom.label + "ぶん" + (if (Zoom.isMax) "（さいだい）" else "")
    }

    // ---------------- ターン進行 ----------------

    private fun beginTurn() {
        if (goalCount >= players.size) {
            showResult()
            return
        }
        val p = players[turn]
        if (p.done) {
            nextTurn()
            return
        }
        updateStatus()
        boardView?.focus(p.pos)
        if (p.rest > 0) {
            p.rest--
            log(p.chara.name + " は おやすみ中")
            handler.postDelayed({ nextTurn() }, 600)
            return
        }
        if (p.cpu) {
            roulette?.lock()
            handler.postDelayed({ roulette?.autoSpin() }, Speed.cpuWaitMs)
        } else {
            roulette?.unlock()
        }
    }

    private fun nextTurn() {
        turn = (turn + 1) % players.size
        beginTurn()
    }

    private fun onSpinResult(n: Int) {
        val p = players[turn]
        log(p.chara.name + " は " + n + " すすむ")
        stepMove(p, n)
    }

    private fun stageLimit(pos: Int): Int {
        val si = stageIndexAt(pos)
        if (stages.isEmpty()) return cells.size - 1
        return stages[si].to
    }

    private fun stageStart(pos: Int): Int {
        val si = stageIndexAt(pos)
        if (stages.isEmpty()) return 0
        return stages[si].from
    }

    /** すすめる さきが あるか。かえり中は スタートの ほうが かべに なる。 */
    private fun canStep(p: Player): Boolean {
        if (p.dir < 0) return p.pos > stageStart(p.pos)
        return p.pos < cells.size - 1 && p.pos < stageLimit(p.pos)
    }

    private fun stepMove(p: Player, remain: Int) {
        if (remain <= 0 || !canStep(p)) {
            handler.postDelayed({ onLanded(p, true) }, 150)
            return
        }
        p.pos += p.dir
        boardView?.focus(p.pos)
        boardView?.invalidate()
        updateStatus()
        handler.postDelayed({ stepMove(p, remain - 1) }, Speed.stepMs)
    }

    private fun applyDelta(p: Player, d: Delta) {
        p.st += d.st
        p.sp += d.sp
        p.pp += d.pp
        p.mn += d.mn
        if (p.st < 0) p.st = 0
        if (p.sp < 0) p.sp = 0
        if (p.pp < 0) p.pp = 0
        updateStats()
    }

    // ステータスの いろ。べんきょう=みどり / うんどう=オレンジ / こい(にんき)=あか
    private val COL_ST = Color.parseColor("#2E7D32")
    private val COL_SP = Color.parseColor("#E65100")
    private val COL_PP = Color.parseColor("#C62828")
    private val COL_MN = Color.parseColor("#00695C")

    // くらい 背景（下の ステータスバー）用の あかるい いろ
    private val COL_ST_D = Color.parseColor("#A5D6A7")
    private val COL_SP_D = Color.parseColor("#FFCC80")
    private val COL_PP_D = Color.parseColor("#FF8A80")
    private val COL_MN_D = Color.parseColor("#80CBC4")

    private fun statColor(name: String, onDark: Boolean): Int {
        if (name == "べんきょう") return if (onDark) COL_ST_D else COL_ST
        if (name == "うんどう") return if (onDark) COL_SP_D else COL_SP
        if (name == "にんき") return if (onDark) COL_PP_D else COL_PP
        return if (onDark) COL_MN_D else COL_MN
    }

    /** 文章の なかの「べんきょう+3」などを いろ分けして ふとじに する。 */
    private fun colorizeStats(text: String): CharSequence = colorizeStats(text, false)

    private fun colorizeStats(text: String, onDark: Boolean): CharSequence {
        val sb = SpannableStringBuilder(text)
        val names = arrayOf("べんきょう", "うんどう", "にんき", "おこづかい")
        var n = 0
        while (n < names.size) {
            val name = names[n]
            var from = 0
            while (true) {
                val at = text.indexOf(name, from)
                if (at < 0) break
                var end = at + name.length
                // うしろに つづく 「+3」「-1200えん」「51」までを ふくめる
                if (end < text.length && (text[end] == '+' || text[end] == '-')) end++
                while (end < text.length && text[end] >= '0' && text[end] <= '9') end++
                if (end + 1 < text.length && text.substring(end, end + 2) == "えん") end += 2
                val col = statColor(name, onDark)
                sb.setSpan(ForegroundColorSpan(col), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                from = end
            }
            n++
        }
        return sb
    }

    private fun deltaText(d: Delta): String {
        val sb = StringBuilder()
        if (d.st != 0) sb.append("べんきょう" + signed(d.st) + " ")
        if (d.sp != 0) sb.append("うんどう" + signed(d.sp) + " ")
        if (d.pp != 0) sb.append("にんき" + signed(d.pp) + " ")
        if (d.mn != 0) sb.append("おこづかい" + signed(d.mn) + "えん")
        return sb.toString().trim()
    }

    private fun signed(v: Int): String {
        return if (v >= 0) "+" + v else v.toString()
    }

    // タップ不要でそのまま進む結果表示（トースト＋ログ）
    private fun flash(title: String, body: String, after: () -> Unit) {
        log("[" + title + "] " + body.replace("\n", " "))
        statusText2?.text = title
        if (!players[turn].cpu) {
            Toast.makeText(this, body, Toast.LENGTH_SHORT).show()
        }
        handler.postDelayed({ after() }, if (players[turn].cpu) Speed.cpuWaitMs else Speed.resultMs)
    }

    // イベントに でてくる 人を きめる。
    // friend = プレイヤーが つかっていない キャラを ゆうせん（いなければ ほかの プレイヤー）
    // love   = こいびと、いなければ いちばん こうかんどの たかい人
    private fun castOf(p: Player, cell: Cell): List<Chara> {
        val out = ArrayList<Chara>()
        var i = 0
        while (i < cell.cast.size) {
            val role = cell.cast[i]
            if (role == "love") {
                val c = p.partner ?: p.crush
                if (c != null && !out.contains(c)) out.add(c)
            } else {
                val c = friendChara(p, cell.i + i)
                if (c != null && !out.contains(c)) out.add(c)
            }
            i++
        }
        return out
    }

    private fun friendChara(p: Player, seed: Int): Chara? {
        // こうこう・だいがくは、イベント用に かいた キャラを つかう
        val key = stageKeyAt(p.pos)
        if ((key == "high" || key == "univ") && eventCharas.isNotEmpty()) {
            return eventCharas[Math.floorMod(seed, eventCharas.size)]
        }
        if (charas.isEmpty()) return null
        val used = ArrayList<Chara>()
        var i = 0
        while (i < players.size) {
            used.add(players[i].chara)
            i++
        }
        val free = ArrayList<Chara>()
        i = 0
        while (i < charas.size) {
            if (!used.contains(charas[i])) free.add(charas[i])
            i++
        }
        if (free.isNotEmpty()) return free[Math.floorMod(seed, free.size)]
        val others = ArrayList<Chara>()
        i = 0
        while (i < players.size) {
            if (players[i] !== p) others.add(players[i].chara)
            i++
        }
        if (others.isEmpty()) return null
        return others[Math.floorMod(seed, others.size)]
    }

    // 背景と 登場人物を のせた ダイアログの なかみ
    private fun eventView(p: Player, body: String, cast: List<Chara>): ScrollView {
        val sv = ScrollView(this)
        sv.addView(eventContent(p, body, cast))
        return sv
    }

    /** ScrollView で つつむ まえの なかみ。えらぶ画面の 上に つけるときは こちらを つかう。 */
    private fun eventContent(p: Player, body: String, cast: List<Chara>): LinearLayout {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dpi(12f), dpi(12f), dpi(12f), dpi(12f))

        val rid = if (currentBg.isEmpty()) 0 else resources.getIdentifier(currentBg, "drawable", packageName)
        if (rid != 0) {
            val frame = FrameLayout(this)
            val iv = ImageView(this)
            iv.setImageResource(rid)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.adjustViewBounds = true
            frame.addView(iv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

            val key = stageKeyAt(p.pos)
            val me = ImageView(this)
            me.setImageResource(charaRes(p.chara, key, ""))
            val mlp = LinearLayout.LayoutParams(dpi(104f), dpi(104f))
            mlp.bottomMargin = dpi(6f)
            mlp.leftMargin = dpi(2f)
            mlp.rightMargin = dpi(2f)
            row.addView(me, mlp)

            // でてくる 人。ひとが おおいときは すこし ちいさく して はみ出さないように
            val sz = if (cast.size >= 2) dpi(84f) else dpi(96f)
            var i = 0
            while (i < cast.size && i < 2) {
                val res = charaRes(cast[i], key, "")
                if (res != 0) {
                    val cv = ImageView(this)
                    cv.setImageResource(res)
                    val clp = LinearLayout.LayoutParams(sz, sz)
                    clp.bottomMargin = dpi(6f)
                    clp.leftMargin = dpi(2f)
                    clp.rightMargin = dpi(2f)
                    row.addView(cv, clp)
                }
                i++
            }

            frame.addView(row, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            content.addView(frame)
        }

        val tv = TextView(this)
        tv.text = colorizeStats(body)
        tv.textSize = 16f
        tv.setTextColor(Color.parseColor("#263238"))
        tv.setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(12f))
        tv.background = roundedBg(Color.WHITE, Color.parseColor("#33691E"))
        val tlp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tlp.topMargin = dpi(10f)
        content.addView(tv, tlp)

        return content
    }

    private var castNow: List<Chara> = ArrayList()

    private fun message(title: String, body: String, after: () -> Unit) {
        val p = players[turn]
        if (p.cpu) {
            log("[" + title + "] " + body.replace("\n", " "))
            statusText2?.text = p.chara.name + "：" + title
            castNow = ArrayList()
            handler.postDelayed({ after() }, Speed.cpuWaitMs)
            return
        }
        val cast = castNow
        castNow = ArrayList()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(eventView(p, body, cast))
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> after() }
            .show()
    }

    private fun doCrush(p: Player, cell: Cell, after: () -> Unit) {
        if (partners.isEmpty()) {
            after()
            return
        }
        // ぶかつで すでに であっている ときは そのまま
        val already = p.crush
        if (already != null) {
            flash(cell.title, already.name + " の ことが やっぱり きに なる") { after() }
            return
        }
        val idx = ArrayList<Int>()
        var i = 0
        while (i < partners.size) {
            idx.add(i)
            i++
        }
        idx.shuffle()
        val pool = ArrayList<Chara>()
        i = 0
        while (i < idx.size && pool.size < 3) {
            pool.add(partners[idx[i]])
            i++
        }
        if (p.cpu) {
            val c = pool[Random.nextInt(pool.size)]
            p.crush = c
            updateStats()
            flash(cell.title, c.name + " が きに なるみたい") { after() }
        } else {
            val names = Array<CharSequence>(pool.size) { k -> pool[k].name }
            val b = AlertDialog.Builder(this)
            b.setTitle(cell.title + "：きに なる人は？")
            b.setCancelable(false)
            b.setItems(names) { _, which ->
                val c = pool[which]
                p.crush = c
                updateStats()
                flash(cell.title, c.name + " が きに なりはじめた") { after() }
            }
            b.show()
        }
    }

    private fun advanceStage(fromStage: Int, after: () -> Unit) {
        val next = fromStage + 1
        if (next >= stages.size) {
            after()
            return
        }
        val to = stages[next].from
        val nextKey = stages[next].key
        var i = 0
        while (i < players.size) {
            players[i].pos = to
            players[i].rest = 0
            resetLoopState(players[i], nextKey)
            i++
        }
        boardView?.focus(to)
        boardView?.invalidate()
        updateStatus()
        // ちゅうがっこう いこうは、ステージに はいったら ぜんいんが ぶかつを えらぶ
        if (clubs.isEmpty() || !clubStages.contains(stages[next].key)) {
            after()
            return
        }
        askClub(0, stages[next].name) {
            askType(0, stages[next].name) {
                rearrange(stages[next])
                after()
            }
        }
    }

    // 1人ずつ タイプを えらぶ。えらんだ かずが おおい ものほど 盤面に ならぶ
    private fun askType(index: Int, stageName: String, after: () -> Unit) {
        if (playTypes.isEmpty()) {
            after()
            return
        }
        if (index >= players.size) {
            after()
            return
        }
        val p = players[index]
        if (p.cpu) {
            p.type = playTypes[Random.nextInt(playTypes.size)].key
            handler.postDelayed({ askType(index + 1, stageName, after) }, 120)
            return
        }
        val items = ArrayList<PickItem>()
        var n = 0
        while (n < playTypes.size) {
            val t = playTypes[n]
            items.add(PickItem(t.icon + " " + t.name, colorizeStats(t.text)))
            n++
        }
        showPicker(stageName + "：" + p.chara.name + " の すごしかた", null, items) { which ->
            p.type = playTypes[which].key
            askType(index + 1, stageName, after)
        }
    }

    // ステージの いれかえスロットを、みんなが えらんだ タイプに よせて はりかえる
    private fun rearrange(stage: Stage) {
        val pool = pools[stage.key] ?: return
        if (pool.slots.isEmpty() || pool.cells.isEmpty()) return
        val w = HashMap<String, Int>()
        w["study"] = 1
        w["sport"] = 1
        w["love"] = 1
        w["free"] = 2
        var i = 0
        while (i < players.size) {
            val t = players[i].type
            if (t == "balance") {
                w["study"] = (w["study"] ?: 1) + 1
                w["sport"] = (w["sport"] ?: 1) + 1
                w["love"] = (w["love"] ?: 1) + 1
            } else if (t.isNotEmpty()) {
                w[t] = (w[t] ?: 1) + 3
            }
            i++
        }
        if (stage.key == "univ") {
            val st = w["study"] ?: 0
            val sp = w["sport"] ?: 0
            val lv = w["love"] ?: 0
            univBoardBg = if (sp > st && sp >= lv) "bg_stadium"
            else if (lv > st && lv > sp) "bg_bay_night"
            else "bg_campus_wide"
        }
        val rest = ArrayList<Cell>(pool.cells)
        val picked = ArrayList<Cell>()
        while (picked.size < pool.slots.size && rest.isNotEmpty()) {
            var total = 0
            var k = 0
            while (k < rest.size) {
                total += w[rest[k].tag] ?: 1
                k++
            }
            var r = Random.nextInt(if (total > 0) total else 1)
            var hit = rest.size - 1
            k = 0
            while (k < rest.size) {
                r -= w[rest[k].tag] ?: 1
                if (r < 0) {
                    hit = k
                    break
                }
                k++
            }
            picked.add(rest.removeAt(hit))
        }
        i = 0
        while (i < picked.size) {
            val at = stage.from + pool.slots[i]
            val c = picked[i]
            c.i = at
            cells[at] = c
            i++
        }
        boardView?.invalidate()
    }

    // 1人ずつ じゅんばんに えらばせる（CPUは じどう）
    private fun askClub(index: Int, stageName: String, after: () -> Unit) {
        if (index >= players.size) {
            updateStats()
            after()
            return
        }
        val p = players[index]
        if (p.cpu) {
            val c = pickClubForCpu(p)
            joinClub(p, c)
            log(p.chara.name + " は " + c.icon + c.name + " に はいった")
            handler.postDelayed({ askClub(index + 1, stageName, after) }, Speed.eventWaitMs)
            return
        }
        // 「まえと おなじ」を いちばん うえに 出して、つづけるのを かんたんにする
        val order = ArrayList<Club>()
        val keep = p.club
        if (keep != null) order.add(keep)
        var k = 0
        while (k < clubs.size) {
            if (clubs[k] !== keep) order.add(clubs[k])
            k++
        }
        val items = ArrayList<PickItem>()
        var n = 0
        while (n < order.size) {
            val c = order[n]
            val head = if (c === keep) "つづける " else ""
            items.add(PickItem(head + c.icon + " " + c.name, colorizeStats(deltaText(c.d))))
            n++
        }
        showPicker(stageName + "：" + p.chara.name + " の ぶかつ", null, items) { which ->
            val c = order[which]
            joinClub(p, c)
            val d = AlertDialog.Builder(this)
            d.setTitle(c.icon + c.name)
            d.setMessage(colorizeStats(c.joinText + "\n" + deltaText(c.join)))
            d.setCancelable(false)
            d.setPositiveButton("OK") { _, _ -> askClub(index + 1, stageName, after) }
            d.show()
        }
    }

    private fun pickClubForCpu(p: Player): Club {
        // まえと おなじ ぶかつを つづけやすくする
        val keep = p.club
        if (keep != null && Random.nextInt(100) < 60) return keep
        return clubs[Random.nextInt(clubs.size)]
    }

    private fun joinClub(p: Player, c: Club) {
        p.club = c
        applyDelta(p, c.join)
        updateStats()
    }

    // ---------------- こい ----------------

    private val AFF_MAX = 10
    private val AFF_READY = 5

    private fun affOf(p: Player, c: Chara): Int {
        return p.aff[c.name] ?: 0
    }

    private fun addAff(p: Player, c: Chara, v: Int) {
        var n = affOf(p, c) + v
        if (n > AFF_MAX) n = AFF_MAX
        if (n < 0) n = 0
        p.aff[c.name] = n
        // いちばん こうかんどの たかい人を「きに なる人」として かおを だす
        if (p.partner == null) p.crush = topCandidate(p)
        updateStats()
    }

    private fun topCandidate(p: Player): Chara? {
        var best: Chara? = null
        var bv = 0
        var i = 0
        while (i < partners.size) {
            val v = affOf(p, partners[i])
            if (v > bv) {
                bv = v
                best = partners[i]
            }
            i++
        }
        return best
    }

    // デート: ランダムな3人だけ でてくる
    private fun doDate(p: Player, cell: Cell, after: () -> Unit) {
        if (partners.isEmpty()) {
            after()
            return
        }
        val mate = p.partner
        if (mate != null) {
            applyDelta(p, cell.d)
            val d = Delta(0, 0, 2, -300)
            applyDelta(p, d)
            p.fightStreak = 0
            castNow = listOf(mate)
            message(cell.title, cell.text + "\n\n" + mate.name + " と ふたりで でかけた。\n" + deltaText(d)) { after() }
            return
        }
        val idx = ArrayList<Int>()
        var i = 0
        while (i < partners.size) {
            idx.add(i)
            i++
        }
        idx.shuffle()
        val pool = ArrayList<Chara>()
        i = 0
        while (i < idx.size && pool.size < 3) {
            pool.add(partners[idx[i]])
            i++
        }
        applyDelta(p, cell.d)
        if (p.cpu) {
            val c = pool[Random.nextInt(pool.size)]
            addAff(p, c, 3)
            flash(cell.title, c.name + " と はなした") { after() }
            return
        }
        // でてくる 3人の かおを 背景の うえに ならべる
        castNow = ArrayList()
        val items = ArrayList<PickItem>()
        var k = 0
        while (k < pool.size) {
            val c = pool[k]
            items.add(PickItem(c.name, "なかよし " + affBar(affOf(p, c))))
            k++
        }
        showPicker(cell.title + "：だれを さそう？", eventContent(p, cell.text, pool), items) { which ->
            val c = pool[which]
            addAff(p, c, 3)
            val d = AlertDialog.Builder(this)
            d.setTitle(c.name)
            d.setView(eventView(p, "いっしょに すごした。\nなかよし " + affBar(affOf(p, c)), listOf(c)))
            d.setCancelable(false)
            d.setPositiveButton("OK") { _, _ -> after() }
            d.show()
        }
    }

    private fun affBar(v: Int): String {
        val sb = StringBuilder()
        var i = 0
        while (i < AFF_MAX) {
            sb.append(if (i < v) "♥" else "・")
            i++
        }
        return sb.toString()
    }

    // こいのマス
    private fun doLove(p: Player, cell: Cell, after: () -> Unit) {
        applyDelta(p, cell.d)
        val mate = p.partner
        if (mate != null) {
            coupleEvent(p, cell, mate, after)
            return
        }
        if (partners.isEmpty()) {
            after()
            return
        }
        val top = topCandidate(p)
        val v = if (top == null) 0 else affOf(p, top)
        if (top == null || v < AFF_READY) {
            // まだ きっかけが ない。だれかと すこし ちかづく
            val c = partners[Random.nextInt(partners.size)]
            addAff(p, c, 1)
            castNow = listOf(c)
            message(cell.title, cell.text + "\n\n" + c.name + " と すこし はなした。\nなかよし " +
                    affBar(affOf(p, c))) { after() }
            return
        }
        // あいてから こくはくされる ことも ある
        if (Random.nextInt(100) < 25 + v * 3) {
            becomeCouple(p, top, cell, top.name + " から こくはく された！", after)
            return
        }
        if (p.cpu) {
            if (Random.nextInt(100) < 60) tryConfess(p, top, cell, after) else {
                flash(cell.title, "こえを かけられなかった") { after() }
            }
            return
        }
        val one = ArrayList<Chara>()
        one.add(top)
        castNow = ArrayList()
        val b = AlertDialog.Builder(this)
        b.setTitle(cell.title)
        b.setView(eventView(p, cell.text + "\n\n" + top.name + "　" + affBar(v), one))
        b.setCancelable(false)
        b.setPositiveButton("こくはくする") { _, _ -> tryConfess(p, top, cell, after) }
        b.setNegativeButton("きょうは やめておく") { _, _ ->
            addAff(p, top, 1)
            flash(cell.title, "また こんど はなそう") { after() }
        }
        b.show()
    }

    private fun tryConfess(p: Player, c: Chara, cell: Cell, after: () -> Unit) {
        val v = affOf(p, c)
        val chance = 20 + v * 7 + p.pp / 3
        if (Random.nextInt(100) < chance) {
            becomeCouple(p, c, cell, "きもちが つうじた！", after)
            return
        }
        addAff(p, c, -2)
        val d = Delta(1, 0, -1, 0)
        applyDelta(p, d)
        castNow = listOf(c)
        message(cell.title, "「ともだちで いよう」と いわれた。\n" + deltaText(d)) { after() }
    }

    private fun becomeCouple(p: Player, c: Chara, cell: Cell, head: String, after: () -> Unit) {
        p.partner = c
        p.crush = c
        p.fightStreak = 0
        p.aff[c.name] = AFF_MAX
        val d = Delta(0, 0, 6, 0)
        applyDelta(p, d)
        var body = head + "\n" + c.name + " と こいびとに なった！\n" + deltaText(d)
        if (!p.goals.contains("love")) {
            p.goals.add("love")
            body = body + "\n\n★ もくひょう たっせい： " + goalLabel("love")
        }
        updateStats()
        castNow = listOf(c)
        message(cell.title, body) { after() }
    }

    // こいびとが いる ときの こいのマス
    private fun coupleEvent(p: Player, cell: Cell, mate: Chara, after: () -> Unit) {
        castNow = listOf(mate)
        if (Random.nextInt(100) < 30) {
            p.fightStreak++
            val d = Delta(0, 0, -2, 0)
            applyDelta(p, d)
            if (p.fightStreak >= 3) {
                val bd = Delta(1, 0, -4, 0)
                applyDelta(p, bd)
                p.partner = null
                p.fightStreak = 0
                p.exCount++
                p.aff[mate.name] = 2
                p.crush = topCandidate(p)
                updateStats()
                castNow = listOf(mate)
                message(cell.title, "また けんかを して しまった。3かい つづけて…\n\n" +
                        mate.name + " と わかれる ことに なった。\n" + deltaText(bd)) { after() }
                return
            }
            message(cell.title, cell.text + "\n\n" + mate.name + " と けんかを した。（" +
                    p.fightStreak + "かい つづけて）\n" + deltaText(d)) { after() }
            return
        }
        p.fightStreak = 0
        val kind = Random.nextInt(3)
        val d: Delta
        val txt: String
        if (kind == 0) {
            d = Delta(0, 1, 4, -2000)
            txt = mate.name + " と りょこうに でかけた。"
        } else if (kind == 1) {
            d = Delta(1, 0, 3, -800)
            txt = mate.name + " に プレゼントを えらんだ。"
        } else {
            d = Delta(0, 0, 3, 0)
            txt = mate.name + " と ながく はなした。"
        }
        applyDelta(p, d)
        message(cell.title, cell.text + "\n\n" + txt + "\n" + deltaText(d)) { after() }
    }

    private fun doClubEvent(p: Player, cell: Cell, after: () -> Unit) {
        val c = p.club
        // ぶかつマスは なかまが でる
        val fr = friendChara(p, cell.i)
        if (fr != null) castNow = listOf(fr)
        if (c == null) {
            applyDelta(p, cell.d)
            message(cell.title, cell.text + "\n" + deltaText(cell.d)) { after() }
            return
        }
        currentBg = c.bg
        applyDelta(p, c.d)
        applyDelta(p, cell.d)
        val gain = deltaText(c.d)
        val extra = deltaText(cell.d)
        var body = c.icon + c.name + "\n\n" + cell.text + "\n" + c.eventText + "\n" + gain
        if (extra.isNotEmpty()) body = body + "　" + extra
        // ぶかつでの であい。まだ きに なる人が いない ときだけ おきる
        if (cell.deai && p.partner == null && partners.size > 0) {
            val met = partners[Random.nextInt(partners.size)]
            addAff(p, met, 3)
            castNow = listOf(met)
            body = body + "\n\n" + c.deaiText + "\n" + met.name + "　" + affBar(affOf(p, met))
        }
        message(cell.title, body) { after() }
    }

    /** 2択のあとの けっかも、おなじ 絵と 人の ついた イベント枠で 見せる。 */
    private fun choiceResult(p: Player, cell: Cell, c: Choice, cast: List<Chara>, allowChain: Boolean) {
        applyDelta(p, c.d)
        currentBg = cell.bg
        castNow = cast
        message(cell.title, c.label + "\n" + c.text + "\n" + deltaText(c.d)) {
            afterCell(p, cell, allowChain)
        }
    }

    private fun onLanded(p: Player, allowChain: Boolean) {
        val cell = cells[p.pos]
        currentBg = cell.bg
        castNow = castOf(p, cell)
        boardView?.invalidate()

        // ループステージの おりかえし。ゴールに ついても すぐには おわらない
        val key = stageKeyAt(p.pos)
        if (isLoop(key)) {
            if (p.phase == 0 && p.pos >= stageLimit(p.pos)) {
                p.phase = 1
                p.dir = -1
                updateStatus()
                message("おりかえし！", stageName(p.pos) + " の ゴールに ついた。\n" +
                        "ここから スタートの ほうへ もどる。\n🔁 かえり ◀") {
                    afterCell(p, cell, false)
                }
                return
            }
            if (p.phase == 1 && p.pos <= stageStart(p.pos)) {
                p.phase = 2
                p.dir = 1
                updateStatus()
                message("スタートに もどった！", "もういちど ゴールを めざそう。\n" +
                        "つぎに ついたら ステージクリア。\n🔁 さいごの いき ▶") {
                    afterCell(p, cell, false)
                }
                return
            }
        }

        if (cell.type == "STAGEGOAL") {
            val si = stageIndexAt(p.pos)
            p.stageWins++
            applyDelta(p, cell.d)
            val nextName = if (si + 1 < stages.size) stages[si + 1].name else ""
            val body = cell.text + "\n\n" + p.chara.name + " が いちばんに " + stages[si].name +
                    " を ぬけた！\n\nみんなで " + nextName + " へ すすむ。"
            message(cell.title, body) {
                advanceStage(si) { handler.postDelayed({ nextTurn() }, 250) }
            }
            return
        }

        if (cell.type == "GOAL") {
            p.done = true
            goalCount++
            p.goalOrder = goalCount
            message(cell.title, cell.text + "\n\n" + goalCount + "ばんめの ゴール！") { nextTurn() }
            return
        }

        if (cell.type == "CHOICE" && cell.choices.size >= 2) {
            if (p.cpu) {
                val c = cell.choices[Random.nextInt(cell.choices.size)]
                applyDelta(p, c.d)
                flash(cell.title, c.label + "：" + c.text + "\n" + deltaText(c.d)) { afterCell(p, cell, allowChain) }
            } else {
                val cast = castNow
                castNow = ArrayList()
                val b = AlertDialog.Builder(this)
                b.setTitle(cell.title)
                b.setView(eventView(p, cell.text, cast))
                b.setCancelable(false)
                b.setPositiveButton(cell.choices[0].label) { _, _ ->
                    choiceResult(p, cell, cell.choices[0], cast, allowChain)
                }
                b.setNegativeButton(cell.choices[1].label) { _, _ ->
                    choiceResult(p, cell, cell.choices[1], cast, allowChain)
                }
                b.show()
            }
            return
        }

        if (cell.type == "AGAIN") {
            applyDelta(p, cell.d)
            val dt0 = deltaText(cell.d)
            val b0 = if (dt0.isEmpty()) cell.text else cell.text + "\n" + dt0
            message(cell.title, b0 + "\n\nもう いちど ルーレットを まわせる！") { afterCell(p, cell, allowChain) }
            return
        }

        if (cell.type == "RANDOM" && cell.choices.size >= 2) {
            val c = cell.choices[Random.nextInt(cell.choices.size)]
            applyDelta(p, c.d)
            message(cell.title, cell.text + "\n\n" + c.label + "\n" + c.text + "\n" + deltaText(c.d)) {
                afterCell(p, cell, allowChain)
            }
            return
        }

        if (cell.type == "DATE") {
            doDate(p, cell) { afterCell(p, cell, allowChain) }
            return
        }

        if (cell.type == "LOVE") {
            doLove(p, cell) { afterCell(p, cell, allowChain) }
            return
        }

        if (cell.type == "CLUBEVENT") {
            doClubEvent(p, cell) { afterCell(p, cell, allowChain) }
            return
        }

        if (cell.type == "CRUSH") {
            applyDelta(p, cell.d)
            doCrush(p, cell) { afterCell(p, cell, allowChain) }
            return
        }

        if (cell.type == "CHALLENGE" && cell.ch != null) {
            val ch = cell.ch
            if (cell.love && p.crush == null && partners.size > 0) {
                p.crush = partners[Random.nextInt(partners.size)]
            }
            val v = statOf(p, ch.stat)
            val ok = v >= ch.need
            val d = if (ok) ch.ok else ch.ng
            applyDelta(p, d)
            var who = ""
            val cr = p.crush
            if (cell.love && cr != null) {
                who = "あいて: " + cr.name + "\n\n"
                if (ok) {
                    p.partner = cr
                    updateStats()
                }
            }
            val head = who + cell.text + "\n\n" + statLabel(ch.stat) + " " + v + " / ひつよう " + ch.need
            var body = head + "\n\n" + (if (ok) ch.okText else ch.ngText) + "\n" + deltaText(d)
            if (cell.love && ok && cr != null) body = body + "\n\n" + cr.name + " と こいびとに なった！"
            if (ok && cell.goalKey.isNotEmpty() && !p.goals.contains(cell.goalKey)) {
                p.goals.add(cell.goalKey)
                updateStats()
                body = body + "\n\n★ もくひょう たっせい： " + goalLabel(cell.goalKey)
            }
            message(cell.title, body) { afterCell(p, cell, allowChain) }
            return
        }

        applyDelta(p, cell.d)
        if (cell.rest > 0) p.rest += cell.rest
        val dt = deltaText(cell.d)
        val body = if (dt.isEmpty()) cell.text else cell.text + "\n" + dt
        if (cell.type == "NORMAL" && dt.isEmpty()) {
            log("[" + cell.title + "] " + cell.text)
            handler.postDelayed({ afterCell(p, cell, allowChain) }, Speed.eventWaitMs)
        } else {
            message(cell.title, body) { afterCell(p, cell, allowChain) }
        }
    }

    private fun afterCell(p: Player, cell: Cell, allowChain: Boolean) {
        if (cell.type == "AGAIN" && allowChain && !p.done) {
            handler.postDelayed({ beginTurn() }, 250)
            return
        }
        if (cell.move != 0 && allowChain) {
            // かえり中の ワープは スタートの ほうへ すすむ
            val to = clampStage(p.pos, p.pos + cell.move * p.dir)
            handler.postDelayed({ slideTo(p, to) }, 250)
            return
        }
        handler.postDelayed({ nextTurn() }, 200)
    }

    private fun clampPos(v: Int): Int {
        if (v < 0) return 0
        if (v > cells.size - 1) return cells.size - 1
        return v
    }

    // ワープはステージをまたがない（またぐと STAGEGOAL を踏まずに先へ行けてしまう）
    private fun clampStage(from: Int, v: Int): Int {
        var t = clampPos(v)
        if (stages.isEmpty()) return t
        val s = stages[stageIndexAt(from)]
        if (t > s.to) t = s.to
        if (t < s.from) t = s.from
        return t
    }

    private fun slideTo(p: Player, to: Int) {
        if (p.pos == to) {
            onLanded(p, false)
            return
        }
        p.pos += if (to > p.pos) 1 else -1
        boardView?.focus(p.pos)
        boardView?.invalidate()
        updateStatus()
        handler.postDelayed({ slideTo(p, to) }, Speed.stepMs)
    }

    private fun statOf(p: Player, key: String): Int {
        if (key == "sp") return p.sp
        if (key == "pp") return p.pp
        if (key == "mn") return p.mn
        return p.st
    }

    private val goalKeys = listOf("exam", "sports", "love")

    private fun goalLabel(key: String): String {
        if (key == "exam") return "じゅけん せいこう"
        if (key == "sports") return "たいかい ゆうしょう"
        if (key == "love") return "こいびとが できる"
        return key
    }

    private fun goalLine(p: Player): String {
        val sb = StringBuilder()
        var i = 0
        while (i < goalKeys.size) {
            val k = goalKeys[i]
            sb.append(if (p.goals.contains(k)) "★" else "☆")
            sb.append(goalLabel(k))
            if (i < goalKeys.size - 1) sb.append("　")
            i++
        }
        return sb.toString()
    }

    private fun statLabel(key: String): String {
        if (key == "sp") return "うんどう"
        if (key == "pp") return "にんき"
        if (key == "mn") return "おこづかい"
        return "べんきょう"
    }

    // ---------------- けっか ----------------

    private fun score(p: Player): Int {
        var v = p.st * 3 + p.sp * 3 + p.pp * 3 + p.mn / 200
        if (p.partner != null) v += 15
        v += p.goals.size * 20
        v += p.stageWins * 10
        return v
    }

    private fun endingOf(p: Player): Ending {
        var key = "st"
        var best = p.st
        if (p.sp > best) {
            best = p.sp
            key = "sp"
        }
        if (p.pp > best) {
            best = p.pp
            key = "pp"
        }
        if (p.mn / 200 > best) {
            key = "mn"
        }
        var i = 0
        while (i < endings.size) {
            if (endings[i].key == key) return endings[i]
            i++
        }
        return Ending("st", "そつぎょう", "おつかれさま。")
    }

    private fun showResult() {
        val sv = ScrollView(this)
        val root = column()
        root.setBackgroundColor(Color.parseColor("#FFF6E5"))
        root.addView(label("けっかはっぴょう", 28f, Color.parseColor("#3A5A40")))

        val sorted = players.sortedByDescending { score(it) }
        var rank = 1
        for (p in sorted) {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.HORIZONTAL
            box.gravity = Gravity.CENTER_VERTICAL
            box.setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f))

            val iv = ImageView(this)
            iv.setImageResource(charaRes(p.chara, stageKeyAt(cells.size - 1), ""))
            iv.layoutParams = LinearLayout.LayoutParams(dpi(72f), dpi(72f))
            box.addView(iv)

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(dpi(10f), 0, 0, 0)
            val e = endingOf(p)
            val t1 = label(rank.toString() + "い　" + p.chara.name + "　" + score(p) + "てん", 17f, Color.parseColor("#2F3E46"))
            t1.gravity = Gravity.LEFT
            t1.setTypeface(Typeface.DEFAULT_BOLD)
            val t2 = label(
                "べんきょう" + p.st + " / うんどう" + p.sp + " / にんき" + p.pp + " / ¥" + p.mn,
                12f, Color.parseColor("#52616B")
            )
            t2.gravity = Gravity.LEFT
            val t3 = label("【" + e.title + "】" + e.text, 13f, Color.parseColor("#6B705C"))
            t3.gravity = Gravity.LEFT
            col.addView(t1)
            col.addView(t2)
            val tg = label(goalLine(p), 12f, Color.parseColor("#8A6D3B"))
            tg.gravity = Gravity.LEFT
            col.addView(tg)
            col.addView(t3)
            val pt = p.partner
            if (pt != null) {
                val t4 = label("♥ " + pt.name + " と いっしょに あるいていく", 13f, Color.parseColor("#B5838D"))
                t4.gravity = Gravity.LEFT
                col.addView(t4)
            }
            box.addView(col)
            root.addView(box)
            rank++
        }

        root.addView(bigButton("もういちど") { showModeSelect() })
        root.addView(bigButton("タイトルへ") { showTitle() })
        sv.addView(root)
        setContentView(sv)
    }

    // ---------------- 盤面ビュー（A面スタイル：写真背景＋ミニマップ＋フラットな円マス） ----------------

    inner class BoardView(ctx: Context) : View(ctx) {

        var turnIndex = 0

        private val bmps = HashMap<Int, Bitmap>()
        private var camX = 0f
        private var camAnim: ValueAnimator? = null
        private var spacing = 0f
        private var cellR = 0f
        private var laneY = 0f
        private var lastFocus = 0

        private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mapBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mapEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mapLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mapCellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arrowEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var bounce = 0f
        private val bounceAnim = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat())

        init {
            pathPaint.color = Color.parseColor("#C9A66B")
            pathPaint.strokeWidth = dp(9f)
            pathPaint.strokeCap = Paint.Cap.ROUND
            edgePaint.color = Color.parseColor("#8D6E63")
            edgePaint.style = Paint.Style.STROKE
            edgePaint.strokeWidth = dp(2f)
            textPaint.color = Color.parseColor("#5D4037")
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.setTypeface(Typeface.DEFAULT_BOLD)
            shadowPaint.color = Color.argb(70, 0, 0, 0)
            mapBgPaint.color = Color.argb(220, 255, 255, 255)
            mapEdgePaint.color = Color.parseColor("#8D6E63")
            mapEdgePaint.style = Paint.Style.STROKE
            mapEdgePaint.strokeWidth = dp(1.5f)
            mapLinePaint.color = Color.parseColor("#C9A66B")
            mapLinePaint.strokeWidth = dp(2f)
            mapLinePaint.strokeCap = Paint.Cap.ROUND
            arrowPaint.color = Color.parseColor("#F57C00")
            arrowPaint.textAlign = Paint.Align.CENTER
            arrowPaint.setTypeface(Typeface.DEFAULT_BOLD)
            arrowEdgePaint.color = Color.WHITE
            arrowEdgePaint.style = Paint.Style.STROKE
            arrowEdgePaint.textAlign = Paint.Align.CENTER
            arrowEdgePaint.setTypeface(Typeface.DEFAULT_BOLD)

            bounceAnim.duration = 900
            bounceAnim.repeatCount = ValueAnimator.INFINITE
            bounceAnim.interpolator = LinearInterpolator()
            bounceAnim.addUpdateListener { a ->
                bounce = a.animatedValue as Float
                invalidate()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            bounceAnim.start()
        }

        override fun onDetachedFromWindow() {
            bounceAnim.cancel()
            camAnim?.cancel()
            super.onDetachedFromWindow()
        }

        private fun worldX(i: Int): Float = spacing * i

        private fun applyMetrics() {
            if (width == 0) return
            spacing = width / Zoom.cells
            cellR = spacing * Zoom.cellRatio
            laneY = height * 0.72f
            // 縮小してもマスの記号が読めるよう、文字サイズに下限を設ける
            val minText = dp(11f)
            val t = cellR * 0.62f
            textPaint.textSize = if (t > minText) t else minText
        }

        // 表示中の位置を保ったまま倍率だけ変える
        fun cycleZoom(focusCell: Int) {
            Zoom.next()
            applyMetrics()
            camAnim?.cancel()
            camX = worldX(focusCell)
            invalidate()
        }

        fun focus(i: Int) {
            lastFocus = i
            if (spacing == 0f) {
                invalidate()
                return
            }
            val target = worldX(i)
            camAnim?.cancel()
            val an = ValueAnimator.ofFloat(camX, target)
            an.duration = 220
            an.interpolator = DecelerateInterpolator()
            an.addUpdateListener { a ->
                camX = a.animatedValue as Float
                invalidate()
            }
            camAnim = an
            an.start()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            applyMetrics()
            camX = worldX(lastFocus)
        }

        private fun bmp(resId: Int): Bitmap {
            var b = bmps[resId]
            if (b == null) {
                b = BitmapFactory.decodeResource(resources, resId)
                bmps[resId] = b
            }
            return b!!
        }

        // ステージごとの盤面背景（イベント用の bg_*.jpg を流用）
        private fun boardBgName(si: Int): String {
            if (si == 0) return "bg_park_day"
            if (si == 1) return "bg_nursery_gate"
            if (si == 2) return "bg_elem_road"
            if (si == 3) return "bg_jhs_ground"
            if (si == 4) return "bg_high_gate"
            // だいがくは みんなが えらんだ すごしかたで けしきが かわる
            if (univBoardBg.isNotEmpty()) return univBoardBg
            return "bg_campus_wide"
        }

        private fun cellColor(type: String): Int {
            if (type == "START") return Color.parseColor("#81C784")
            if (type == "GOAL") return Color.parseColor("#FFB74D")
            if (type == "GOOD") return Color.parseColor("#66BB6A")
            if (type == "BAD") return Color.parseColor("#9575CD")
            if (type == "WARP") return Color.parseColor("#FF9800")
            if (type == "REST") return Color.parseColor("#90A4AE")
            if (type == "CHOICE") return Color.parseColor("#F2A6B3")
            if (type == "CHALLENGE") return Color.parseColor("#EF5350")
            if (type == "STAGEGOAL") return Color.parseColor("#FFD166")
            if (type == "CRUSH") return Color.parseColor("#F48FB1")
            if (type == "CLUBEVENT") return Color.parseColor("#4FC3F7")
            if (type == "DATE") return Color.parseColor("#F8BBD0")
            if (type == "LOVE") return Color.parseColor("#EC407A")
            if (type == "AGAIN") return Color.parseColor("#4DB6AC")
            if (type == "RANDOM") return Color.parseColor("#CE93D8")
            return Color.parseColor("#FFF8E1")
        }

        private fun symbolOf(type: String): String {
            if (type == "START") return "S"
            if (type == "GOAL") return "G"
            if (type == "STAGEGOAL") return "🚩"
            if (type == "CHALLENGE") return "🌸"
            if (type == "CRUSH") return "💗"
            if (type == "CLUBEVENT") return "🏅"
            if (type == "DATE") return "☕"
            if (type == "LOVE") return "💞"
            if (type == "GOOD") return "⭐"
            if (type == "BAD") return "💧"
            if (type == "WARP") return "🌀"
            if (type == "REST") return "💤"
            if (type == "CHOICE") return "❓"
            if (type == "RANDOM") return "🎲"
            if (type == "AGAIN") return "🔁"
            return ""
        }

        override fun onDraw(canvas: Canvas) {
            if (spacing == 0f) applyMetrics()
            if (spacing == 0f || cells.isEmpty()) return
            var camCell = (camX / spacing + 0.5f).toInt()
            if (camCell < 0) camCell = 0
            if (camCell > cells.size - 1) camCell = cells.size - 1
            val si = stageIndexAt(camCell)
            drawPhoto(canvas, si)
            drawTrack(canvas)
            drawMiniMap(canvas, si)
        }

        // 写真背景。カメラの0.25倍でパララックス、左右反転しながら並べて継ぎ目なし（A面と同じ）
        private fun drawPhoto(canvas: Canvas, si: Int) {
            val rid = resOf(boardBgName(si), "")
            if (rid == 0) {
                canvas.drawColor(Color.parseColor("#DFF3FB"))
                return
            }
            val b = bmp(rid)
            val scale = height.toFloat() / b.height
            val tileW = b.width * scale
            if (tileW <= 0f) return
            val offset = -camX * 0.25f
            var i = floor((0f - offset - tileW) / tileW).toInt()
            val iMax = floor((width - offset) / tileW).toInt() + 1
            while (i <= iMax) {
                val left = offset + i * tileW
                val dst = RectF(left, 0f, left + tileW, height.toFloat())
                if (Math.floorMod(i, 2) == 0) {
                    canvas.drawBitmap(b, null, dst, null)
                } else {
                    canvas.save()
                    canvas.scale(-1f, 1f, dst.centerX(), dst.centerY())
                    canvas.drawBitmap(b, null, dst, null)
                    canvas.restore()
                }
                i++
            }
        }

        private fun drawTrack(canvas: Canvas) {
            val dx = width / 2f - camX
            canvas.save()
            canvas.translate(dx, 0f)

            var first = ((camX - width) / spacing).toInt() - 1
            var last = ((camX + width) / spacing).toInt() + 1
            if (first < 0) first = 0
            if (last > cells.size - 1) last = cells.size - 1

            canvas.drawLine(worldX(first), laneY, worldX(last), laneY, pathPaint)

            var i = first
            while (i <= last) {
                val x = worldX(i)
                val c = cells[i]
                fillPaint.color = cellColor(c.type)
                canvas.drawCircle(x, laneY, cellR, fillPaint)
                canvas.drawCircle(x, laneY, cellR, edgePaint)
                val sym = symbolOf(c.type)
                val lbl = if (sym == "") (i + 1).toString() else sym
                canvas.drawText(lbl, x, laneY + textPaint.textSize / 3, textPaint)
                i++
            }

            // コマ（手番のキャラは大きく・はねる。A面と同じ）
            val byCell = HashMap<Int, ArrayList<Int>>()
            var pi = 0
            while (pi < players.size) {
                var pos = players[pi].pos
                if (pos < 0) pos = 0
                if (pos > cells.size - 1) pos = cells.size - 1
                var l = byCell[pos]
                if (l == null) {
                    l = ArrayList()
                    byCell[pos] = l
                }
                l.add(pi)
                pi++
            }
            for ((cell, group) in byCell) {
                if (cell < first - 1 || cell > last + 1) continue
                val cx = worldX(cell)
                group.sortBy { if (it == turnIndex) 1 else 0 }
                var slot = 0
                while (slot < group.size) {
                    val idx = group[slot]
                    val pl = players[idx]
                    val isTurn = idx == turnIndex
                    val rid = charaRes(pl.chara, stageKeyAt(pl.pos), "")
                    if (rid != 0) {
                        val b = bmp(rid)
                        val sz = cellR * (if (isTurn) 2.5f else 1.8f)
                        val pieceDx = (slot - (group.size - 1) / 2f) * cellR * 0.8f
                        val lift = if (isTurn) (sin(bounce) * 0.5f + 0.5f) * cellR * 0.4f else 0f
                        val shadowScale = 1f - (lift / (cellR * 0.4f)) * 0.35f
                        val shadowW = sz * 0.40f * shadowScale
                        val shadowH = sz * 0.12f * shadowScale
                        val baseY = laneY - cellR * 0.55f
                        canvas.drawOval(RectF(
                            cx + pieceDx - shadowW, baseY - shadowH,
                            cx + pieceDx + shadowW, baseY + shadowH), shadowPaint)
                        val pt = pl.partner
                        if (pt != null) {
                            val prid = charaRes(pt, stageKeyAt(pl.pos), "")
                            if (prid != 0) {
                                val pb = bmp(prid)
                                val ps = sz * 0.78f
                                val px = cx + pieceDx + sz * 0.42f
                                canvas.drawOval(RectF(
                                    px - ps * 0.36f, baseY - shadowH * 0.85f,
                                    px + ps * 0.36f, baseY + shadowH * 0.85f), shadowPaint)
                                canvas.drawBitmap(pb, null, RectF(
                                    px - ps / 2, baseY - ps, px + ps / 2, baseY), null)
                            }
                        }
                        canvas.drawBitmap(b, null, RectF(
                            cx + pieceDx - sz / 2, baseY - sz - lift,
                            cx + pieceDx + sz / 2, baseY - lift), null)
                        // ループステージ中は、手番のコマの うえに すすむ むきを だす
                        if (isTurn && isLoop(stageKeyAt(pl.pos))) {
                            val ar = if (pl.dir < 0) "◀" else "▶"
                            val ax = cx + pieceDx
                            val ay = baseY - sz - lift - cellR * 0.45f
                            arrowPaint.textSize = cellR * 1.15f
                            arrowEdgePaint.textSize = cellR * 1.15f
                            arrowEdgePaint.strokeWidth = dp(4f)
                            canvas.drawText(ar, ax, ay, arrowEdgePaint)
                            canvas.drawText(ar, ax, ay, arrowPaint)
                        }
                    }
                    slot++
                }
            }
            canvas.restore()
        }

        // 上部: いまのステージ全体が見えるミニマップ（A面スタイル）
        private fun drawMiniMap(canvas: Canvas, si: Int) {
            if (stages.isEmpty()) return
            val stg = stages[si]
            val frameL = width * 0.03f
            val frameR = width * 0.97f
            val frameT = height * 0.04f
            val frameB = height * 0.30f
            val rect = RectF(frameL, frameT, frameR, frameB)
            canvas.drawRoundRect(rect, dp(8f), dp(8f), mapBgPaint)
            canvas.drawRoundRect(rect, dp(8f), dp(8f), mapEdgePaint)

            val padX = width * 0.045f
            val l = frameL + padX
            val r = frameR - padX
            val lineY = frameT + (frameB - frameT) * 0.68f
            canvas.drawLine(l, lineY, r, lineY, mapLinePaint)

            val n = stg.to - stg.from + 1
            val miniR = (frameB - frameT) * 0.085f
            var i = 0
            while (i < n) {
                val x = l + (r - l) * i / (n - 1).toFloat()
                mapCellPaint.color = cellColor(cells[stg.from + i].type)
                canvas.drawCircle(x, lineY, miniR, mapCellPaint)
                i++
            }

            var pi = 0
            while (pi < players.size) {
                val pl = players[pi]
                if (stageIndexAt(pl.pos) == si) {
                    var rel = pl.pos - stg.from
                    if (rel < 0) rel = 0
                    if (rel > n - 1) rel = n - 1
                    val x = l + (r - l) * rel / (n - 1).toFloat()
                    val rid = charaRes(pl.chara, stageKeyAt(pl.pos), "")
                    if (rid != 0) {
                        val isTurn = pi == turnIndex
                        val sz = (frameB - frameT) * (if (isTurn) 0.46f else 0.34f)
                        canvas.drawBitmap(bmp(rid), null, RectF(
                            x - sz / 2, lineY - miniR * 1.6f - sz,
                            x + sz / 2, lineY - miniR * 1.6f), null)
                    }
                }
                pi++
            }
        }
    }

    private fun playerColor(i: Int): Int {
        if (i == 0) return Color.parseColor("#E76F51")
        if (i == 1) return Color.parseColor("#2A9D8F")
        if (i == 2) return Color.parseColor("#457B9D")
        return Color.parseColor("#B5838D")
    }

    // ---------------- ルーレット（A面スタイル：パステル6色・TAP・結果ポップ） ----------------

    inner class RouletteView(ctx: Context) : View(ctx) {

        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var rot = 0f
        private var spinning = false
        private var locked = true
        private var resultNum = 0
        private var resultScale = 1f
        var onResult: (Int) -> Unit = {}

        private val colors = intArrayOf(
            Color.parseColor("#EF9A9A"), Color.parseColor("#FFCC80"),
            Color.parseColor("#FFF59D"), Color.parseColor("#A5D6A7"),
            Color.parseColor("#90CAF9"), Color.parseColor("#CE93D8")
        )

        // 「ふつう」用の回転カーブ（A面と同じ）。前半72%で回転量の88.5%を消化して一気に減速
        private val snapSpin = TimeInterpolator { t ->
            val k = 0.72f
            val a = 0.885f
            if (t < k) t / k * a
            else {
                val u = (t - k) / (1f - k)
                val inv = 1f - u
                a + (1f - a) * (1f - inv * inv * inv)
            }
        }

        fun lock() {
            locked = true
            invalidate()
        }

        fun unlock() {
            locked = false
            invalidate()
        }

        fun autoSpin() {
            locked = false
            spin()
        }

        fun pressStart() {
            if (locked || spinning) return
            spin()
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_DOWN && !locked && !spinning) {
                spin()
            }
            return true
        }

        private fun spin() {
            if (spinning) return
            spinning = true
            locked = true
            resultNum = 0
            val n = Random.nextInt(1, 7)
            val from = rot
            val turns = if (Speed.fast) 3 else 5 + Random.nextInt(3)
            val to = from - (from % 360f) + 360f * turns + (330f - (n - 1) * 60f)
            val an = ValueAnimator.ofFloat(0f, 1f)
            an.duration = Speed.spinMs
            // ふつう=直前まで速く回して急停止 / はやい=短いので従来どおりなめらかに減速
            an.interpolator = if (Speed.fast) DecelerateInterpolator(2.2f) else snapSpin
            an.addUpdateListener { a ->
                rot = from + (to - from) * (a.animatedValue as Float)
                invalidate()
            }
            an.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    spinning = false
                    showResultPop(n)
                }
            })
            an.start()
        }

        private fun showResultPop(n: Int) {
            resultNum = n
            val an = ValueAnimator.ofFloat(0.3f, 1.15f, 1f)
            an.duration = 350
            an.addUpdateListener { a ->
                resultScale = a.animatedValue as Float
                invalidate()
            }
            an.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    postDelayed({ onResult(n) }, Speed.resultMs)
                }
            })
            an.start()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = min(width, height) / 2f * 0.82f
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)

            canvas.save()
            canvas.rotate(rot, cx, cy)
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.setTypeface(Typeface.DEFAULT_BOLD)
            var i = 0
            while (i < 6) {
                p.color = colors[i]
                canvas.drawArc(rect, -90f + i * 60f, 60f, true, p)
                i++
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(2f)
            p.color = Color.WHITE
            i = 0
            while (i < 6) {
                canvas.drawArc(rect, -90f + i * 60f, 60f, true, p)
                i++
            }
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#37474F")
            p.textSize = r * 0.28f
            i = 0
            while (i < 6) {
                val ang = Math.toRadians((-90 + i * 60 + 30).toDouble())
                val tx = cx + (r * 0.62f) * cos(ang).toFloat()
                val ty = cy + (r * 0.62f) * sin(ang).toFloat() + p.textSize / 3
                canvas.drawText((i + 1).toString(), tx, ty, p)
                i++
            }
            canvas.restore()

            // 上部のポインタ（下向きの赤い三角）
            val pin = Path()
            pin.moveTo(cx, cy - r - dp(2f))
            pin.lineTo(cx - r * 0.1f, cy - r + r * 0.22f)
            pin.lineTo(cx + r * 0.1f, cy - r + r * 0.22f)
            pin.close()
            p.color = Color.parseColor("#D32F2F")
            canvas.drawPath(pin, p)

            if (resultNum != 0) {
                val br = r * 0.46f * resultScale
                p.color = Color.WHITE
                canvas.drawCircle(cx, cy, br, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(3f)
                p.color = Color.parseColor("#D32F2F")
                canvas.drawCircle(cx, cy, br, p)
                p.style = Paint.Style.FILL
                p.textSize = br * 1.2f
                canvas.drawText(resultNum.toString(), cx, cy + p.textSize * 0.36f, p)
            } else {
                p.color = Color.WHITE
                canvas.drawCircle(cx, cy, r * 0.22f, p)
                p.color = Color.parseColor("#37474F")
                p.textSize = r * 0.16f
                val ctr = if (spinning || locked) "..." else "TAP"
                canvas.drawText(ctr, cx, cy + p.textSize / 3, p)
            }
            p.textAlign = Paint.Align.LEFT
        }
    }
}
