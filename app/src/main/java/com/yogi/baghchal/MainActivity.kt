package com.yogi.baghchal

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var col: LinearLayout
    private var inGame = false

    // game state
    private var board = Board()
    private val history = ArrayList<Board>()
    private val seen = HashMap<String, Int>()
    /** who the human plays: GOAT, TIGER, or EMPTY = two players */
    private var human = Rules.GOAT
    private var level = 1
    private var thinking = false
    private var over = false
    private lateinit var view: BoardView
    private lateinit var status: TextView
    private lateinit var goatsLine: TextView
    private lateinit var capturedLine: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG
        val sv = ScrollView(this)
        sv.setBackgroundColor(C.BG)
        col = vbox()
        col.setPadding(0, dp(30), 0, dp(24))
        sv.addView(col)
        setContentView(sv)
        level = Store.int("level", 1)
        menu()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (inGame) menu() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ===================================================================== menu

    private fun menu() {
        inGame = false
        thinking = false
        col.removeAllViews()
        col.addView(label("बाघ चाल", 40f, C.ACCENT, true).apply { gravity = Gravity.CENTER })
        col.addView(label("Bagh-Chal · tigers and goats", 16f, C.SUB).apply { gravity = Gravity.CENTER })
        col.addView(label("🐯  ·  🐐", 34f).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) })

        fun mode(icon: String, title: String, sub: String, who: Int) {
            val c = card()
            val r = hbox()
            r.addView(label(icon, 34f).lin(WRAP, WRAP, r = dp(14)))
            val m = vbox()
            m.addView(label(title, 17f, C.TEXT, true))
            m.addView(label(sub, 13f, C.SUB))
            r.addView(m.lin(0, WRAP, 1f))
            r.addView(label("›", 24f, C.SUB))
            c.addView(r)
            c.setOnClickListener { start(who) }
            col.addView(c)
        }
        mode("🐐", "Play the goats", "Trap all four tigers before they eat five goats", Rules.GOAT)
        mode("🐯", "Play the tigers", "Capture five goats before you're trapped", Rules.TIGER)
        mode("👥", "Two players", "Pass the phone · goats move first", Rules.EMPTY)

        col.addView(label("COMPUTER", 12f, C.SUB, true).apply { setPadding(dp(22), dp(14), dp(22), dp(4)) })
        val chips = hbox()
        chips.setPadding(dp(18), 0, dp(18), 0)
        for ((i, n) in listOf("Easy", "Medium", "Hard").withIndex()) chips.addView(chip(n, i == level) { level = i; Store.setInt("level", i); menu() })
        col.addView(HorizontalScrollView(this).apply { addView(chips) })

        val st = card()
        st.addView(label("Your record", 13f, C.SUB, true))
        st.addView(label("🐐 As goats: ${Store.int("gw", 0)} won · ${Store.int("gl", 0)} lost\n🐯 As tigers: ${Store.int("tw", 0)} won · ${Store.int("tl", 0)} lost",
            15f, C.TEXT).lin(t = dp(4)))
        col.addView(st)
        col.addView(button("📜  How to play", C.CARD2, C.TEXT) { rules() }.lin(l = dp(14), r = dp(14), t = dp(8)))
    }

    private fun rules() {
        AlertDialog.Builder(this).setTitle("How to play Bagh-Chal").setMessage(
            "Bagh-Chal (“moving tigers”) has been played in Nepal for centuries.\n\n" +
                "• The board has 25 points. Pieces move along the lines to a neighbouring empty point.\n\n" +
                "• 4 tigers start in the corners. There are 20 goats; goats move first.\n\n" +
                "• First, the goats are placed one per turn on any empty point. Goats can only move once all 20 are on the board.\n\n" +
                "• A tiger captures by jumping over a goat in a straight line to the empty point just behind it. Goats can't capture.\n\n" +
                "• 🐯 The tigers win by capturing 5 goats.\n• 🐐 The goats win when no tiger can move.\n\n" +
                "Tip for goats: fill the edges and corners first and keep goats in pairs, so no tiger has an empty point behind a goat.")
            .setPositiveButton("Let's play", null).show()
    }

    // ===================================================================== game

    private fun start(who: Int) {
        human = who
        board = Board()
        history.clear()
        seen.clear()
        over = false
        thinking = false
        inGame = true
        col.removeAllViews()
        val head = hbox()
        head.setPadding(dp(8), 0, dp(16), dp(6))
        head.addView(label("‹", 30f, C.TEXT, true).apply { setPadding(dp(12), 0, dp(12), 0); setOnClickListener { menu() } })
        status = label("", 18f, C.TEXT, true)
        head.addView(status.lin(0, WRAP, 1f))
        col.addView(head)
        val counts = hbox()
        counts.setPadding(dp(20), 0, dp(20), dp(10))
        goatsLine = label("", 14f, C.GOAT)
        capturedLine = label("", 14f, C.TIGER).apply { gravity = Gravity.END }
        counts.addView(goatsLine.lin(0, WRAP, 1f))
        counts.addView(capturedLine.lin(0, WRAP, 1f))
        col.addView(counts)
        view = BoardView(this)
        view.board = board
        view.onPoint = { tap(it) }
        col.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(12), 0, dp(12), dp(14))
        })
        val tools = hbox()
        tools.setPadding(dp(14), 0, dp(14), 0)
        tools.addView(button("↶ Undo", C.CARD2, C.TEXT) { undo() }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) })
        tools.addView(button("💡 Hint", C.CARD2, C.TEXT) { hintMove() }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) })
        tools.addView(button("⟳ New") { start(human) }, LinearLayout.LayoutParams(0, WRAP, 1f))
        col.addView(tools)
        refresh()
        if (human == Rules.TIGER) aiTurn()
    }

    private fun isHumanTurn() = !over && !thinking && (human == Rules.EMPTY || board.turn == human)

    private fun refresh() {
        val placed = Rules.GOATS_TOTAL - board.goatsToPlace
        goatsLine.text = if (board.goatsToPlace > 0) "🐐 To place: ${board.goatsToPlace}" else "🐐 All $placed placed · ${placed - board.captured} on board"
        capturedLine.text = "🐯 Captured: ${board.captured} / ${Rules.TIGERS_WIN_AT}"
        status.text = when {
            over -> "Game over"
            thinking -> "🤔 Thinking…"
            board.turn == Rules.GOAT -> if (human == Rules.EMPTY) "🐐 Goats' turn" else if (human == Rules.GOAT) "🐐 Your turn" else "🐐 Goats are moving"
            else -> if (human == Rules.EMPTY) "🐯 Tigers' turn" else if (human == Rules.TIGER) "🐯 Your turn" else "🐯 Tigers are moving"
        }
        view.board = board
        view.invalidate()
    }

    private fun tap(p: Int) {
        if (!isHumanTurn()) return
        val cell = board.cells[p]
        if (board.turn == Rules.GOAT && board.goatsToPlace > 0) {
            if (cell == Rules.EMPTY) play(Move(-1, p))
            return
        }
        if (cell == board.turn) {
            view.selected = p
            view.hints = board.movesFrom(p)
            if (view.hints.isEmpty()) view.performHapticFeedback(rejectFx())
            view.invalidate()
            return
        }
        val m = view.hints.firstOrNull { it.to == p }
        if (m != null) play(m) else { view.selected = -1; view.hints = emptyList(); view.invalidate() }
    }

    private fun play(m: Move) {
        history.add(board.copy())
        board.play(m)
        view.selected = -1
        view.hints = emptyList()
        view.lastMove = m
        view.board = board
        view.animate(m)
        view.performHapticFeedback(if (m.over >= 0) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
        if (board.goatsToPlace == 0) {
            val k = board.key()
            seen[k] = (seen[k] ?: 0) + 1
        }
        refresh()
        if (checkEnd()) return
        if (human != Rules.EMPTY && board.turn != human) aiTurn()
    }

    private fun aiTurn() {
        thinking = true
        refresh()
        val snapshot = board.copy()
        val lv = level
        Thread {
            val m = Brain.best(snapshot, lv)
            h.postDelayed({
                if (!inGame) return@postDelayed
                thinking = false
                if (m != null && board.key() == snapshot.key()) play(m) else refresh()
            }, 380)
        }.start()
    }

    private fun checkEnd(): Boolean {
        val w = board.winner()
        val draw = board.moves >= 300 || seen.values.any { it >= 3 }
        if (w == Rules.EMPTY && !draw) return false
        over = true
        refresh()
        val msg = when {
            draw -> "🤝 A draw: the same position kept coming back."
            human == Rules.EMPTY -> if (w == Rules.TIGER) "🐯 The tigers win!" else "🐐 The goats win: every tiger is trapped!"
            w == human -> if (w == Rules.GOAT) "🎉 You trapped every tiger!" else "🎉 You captured five goats!"
            else -> if (human == Rules.GOAT) "🐯 The tigers got five goats. Try again!" else "🐐 Your tigers are trapped. Try again!"
        }
        if (human != Rules.EMPTY && !draw) {
            val key = (if (human == Rules.GOAT) "g" else "t") + (if (w == human) "w" else "l")
            Store.setInt(key, Store.int(key, 0) + 1)
        }
        h.postDelayed({
            if (!inGame) return@postDelayed
            AlertDialog.Builder(this).setTitle("Game over").setMessage(msg)
                .setPositiveButton("Play again") { _, _ -> start(human) }
                .setNegativeButton("Menu") { _, _ -> menu() }
                .setCancelable(false).show()
        }, 600)
        return true
    }

    private fun undo() {
        if (thinking || history.isEmpty()) return
        // against the computer, go back to your own turn
        if (human == Rules.EMPTY) board = history.removeAt(history.size - 1)
        else do { board = history.removeAt(history.size - 1) } while (history.isNotEmpty() && board.turn != human)
        over = false
        view.lastMove = null
        view.selected = -1
        view.hints = emptyList()
        refresh()
    }

    private fun hintMove() {
        if (!isHumanTurn()) return
        thinking = true
        status.text = "💡 Looking for a good move…"
        val snap = board.copy()
        Thread {
            val m = Brain.best(snap, 2)
            h.post {
                thinking = false
                refresh()
                if (m == null) return@post
                view.selected = m.from
                view.hints = listOf(m)
                view.invalidate()
                if (m.from < 0) toast("Place a goat on the green point")
            }
        }.start()
    }

    @Suppress("unused")
    private val unused = View.NO_ID + Color.BLACK
}

/** REJECT exists from Android 11; older phones get a simple tick. */
private fun rejectFx(): Int =
    if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.CLOCK_TICK
