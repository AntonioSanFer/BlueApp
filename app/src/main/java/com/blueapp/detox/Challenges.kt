package com.blueapp.detox

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.random.Random

/**
 * The pause is never the same twice in a row: a different kind of challenge each time,
 * with random parameters, so the thumb can't learn a fixed "tap-tap-dismiss" pattern.
 *
 * [level] 0..3 makes things longer/harder (more opens today, or asking for extra time).
 */
abstract class Challenge(protected val ctx: Context, protected val level: Int) {
    abstract val kind: String
    abstract val title: String
    abstract val subtitle: String
    protected lateinit var onSolved: () -> Unit
    protected val main = Handler(Looper.getMainLooper())

    fun mount(host: FrameLayout, solved: () -> Unit) {
        onSolved = solved
        host.removeAllViews()
        host.addView(build(), FrameLayout.LayoutParams(MATCH, WRAP))
    }

    protected abstract fun build(): View

    open fun dispose() {
        main.removeCallbacksAndMessages(null)
    }

    protected fun dp(v: Int) = ctx.dp(v)
    protected fun str(id: Int, vararg args: Any) = ctx.getString(id, *args)

    protected fun column() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    protected fun big(text: String, sizeSp: Float = 44f) = TextView(ctx).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(ctx.attrColor(R.attr.textMain))
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    protected fun input(numeric: Boolean, hint: String = "") = EditText(ctx).apply {
        background = ctx.getDrawable(R.drawable.input)
        setTextColor(ctx.attrColor(R.attr.textMain))
        setHintTextColor(ctx.attrColor(R.attr.textDim))
        this.hint = hint
        textSize = 22f
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isSingleLine = true
        // No paste: the answer has to come from you, not the clipboard.
        isLongClickable = false
        val noMenu = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(m: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onPrepareActionMode(m: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onActionItemClicked(m: android.view.ActionMode?, item: android.view.MenuItem?) = false
            override fun onDestroyActionMode(m: android.view.ActionMode?) {}
        }
        customSelectionActionModeCallback = noMenu
        customInsertionActionModeCallback = noMenu
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD // disables autocorrect/predictions
        imeOptions = EditorInfo.IME_ACTION_DONE
    }

    protected fun button(text: String) = Button(ctx, null, 0, R.style.Primary).apply {
        this.text = text
    }

    /** Text field + check button. Calls [check] on submit; returns the field. */
    protected fun answerRow(parent: LinearLayout, numeric: Boolean, check: (String) -> Boolean): EditText {
        val field = input(numeric)
        val ok = button(str(R.string.check))
        fun submit() {
            if (check(field.text.toString().trim())) {
                hideKeyboard(field); onSolved()
            } else {
                field.setText(""); shake(field)
            }
        }
        ok.setOnClickListener { submit() }
        field.setOnEditorActionListener { _, _, _ -> submit(); true }
        parent.addView(field, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(20) })
        parent.addView(ok, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
        main.postDelayed({ showKeyboard(field) }, 250)
        return field
    }

    protected fun shake(v: View) {
        ObjectAnimator.ofFloat(v, "translationX", 0f, 24f, -24f, 16f, -16f, 6f, 0f)
            .setDuration(380).start()
    }

    protected fun showKeyboard(v: View) {
        v.requestFocus()
        ctx.getSystemService(InputMethodManager::class.java).showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    protected fun hideKeyboard(v: View) {
        ctx.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(v.windowToken, 0)
    }

    companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        private val FACTORIES: List<Pair<String, (Context, Int) -> Challenge>> = listOf(
            "countdown" to ::Countdown,
            "math" to ::MathChallenge,
            "phrase" to ::Phrase,
            "reverse" to ::Reverse,
            "taps" to ::TapOrder,
            "hold" to ::Hold,
            "count" to ::CountDots,
            "memory" to ::Memory,
        )

        /** Random challenge, never the same kind as last time. Countdown is a bit more common: it's the gentlest. */
        fun random(ctx: Context, level: Int, lastKind: String?): Challenge {
            val pool = FACTORIES.filter { it.first != lastKind }
            val weighted = pool.flatMap { if (it.first == "countdown") listOf(it, it) else listOf(it) }
            return weighted.random().second(ctx, level)
        }
    }
}

/** Breathe with the circle for a random number of seconds. */
class Countdown(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "countdown"
    private val seconds = when (level) {
        0 -> Random.nextInt(5, 13)
        1 -> Random.nextInt(10, 21)
        2 -> Random.nextInt(15, 31)
        else -> Random.nextInt(25, 46)
    }
    private val prompts = ctx.resources.getStringArray(R.array.reflections).toList().shuffled()
    override val title = prompts[0]
    override val subtitle = str(R.string.countdown_sub)
    private var breath: ValueAnimator? = null

    override fun build(): View {
        val size = dp(220)
        val frame = FrameLayout(ctx)
        val glow = ctx.attrColor(R.attr.accent2Color)
        val circle = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = size / 2f
                colors = intArrayOf((glow and 0xFFFFFF) or 0x66000000, glow and 0xFFFFFF)
            }
        }
        val ring = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), ctx.attrColor(R.attr.accentColor))
                setColor(Color.TRANSPARENT)
            }
        }
        val number = big(seconds.toString(), 56f)
        frame.addView(circle, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        frame.addView(ring, FrameLayout.LayoutParams(size * 6 / 10, size * 6 / 10, Gravity.CENTER))
        frame.addView(number, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))

        // 4s in, 4s out. Only runs while this screen is visible.
        breath = ValueAnimator.ofFloat(0.75f, 1.15f).apply {
            duration = 4000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val s = it.animatedValue as Float
                ring.scaleX = s; ring.scaleY = s
                circle.scaleX = s; circle.scaleY = s
            }
            start()
        }

        var left = seconds
        val tick = object : Runnable {
            override fun run() {
                left--
                if (left <= 0) {
                    number.text = "✓"
                    breath?.cancel()
                    onSolved()
                } else {
                    number.text = left.toString()
                    main.postDelayed(this, 1000)
                }
            }
        }
        main.postDelayed(tick, 1000)
        return FrameLayout(ctx).apply {
            addView(frame, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            minimumHeight = size
        }
    }

    override fun dispose() {
        breath?.cancel()
        super.dispose()
    }
}

class MathChallenge(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "math"
    override val title = str(R.string.math_title)
    override val subtitle = str(R.string.math_sub)
    private lateinit var question: TextView
    private var answer = 0

    private fun next() {
        val r = Random
        val (q, a) = when (if (level == 0) r.nextInt(2) else r.nextInt(2, 4 + minOf(level, 1))) {
            0 -> { val a = r.nextInt(12, 60); val b = r.nextInt(12, 40); "$a + $b" to a + b }
            1 -> { val a = r.nextInt(40, 99); val b = r.nextInt(11, a - 5); "$a − $b" to a - b }
            2 -> { val a = r.nextInt(6, 13); val b = r.nextInt(3, 10); "$a × $b" to a * b }
            3 -> { val a = r.nextInt(11, 20); val b = r.nextInt(3, 8); "$a × $b" to a * b }
            else -> { val a = r.nextInt(4, 10); val b = r.nextInt(3, 9); val c = r.nextInt(5, 30); "$a × $b + $c" to a * b + c }
        }
        question.text = "$q = ?"
        answer = a
    }

    override fun build(): View = column().apply {
        question = big("", 40f)
        addView(question)
        next()
        answerRow(this, numeric = true) { s ->
            (s.toIntOrNull() == answer).also { if (!it) next() }
        }
    }
}

class Phrase(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "phrase"
    override val title = str(R.string.phrase_title)
    override val subtitle = str(R.string.phrase_sub)
    private val phrase = ctx.resources.getStringArray(R.array.phrases).random()

    override fun build(): View = column().apply {
        addView(big("“$phrase”", 22f).apply { setPadding(dp(8), 0, dp(8), 0) })
        answerRow(this, numeric = false) { s -> norm(s) == norm(phrase) }
    }

    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "") // "Sé a qué" == "Se a que"
        .replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()
}

class Reverse(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "reverse"
    override val title = str(R.string.reverse_title)
    override val subtitle = str(R.string.reverse_sub)
    private val word: String = ctx.resources.getStringArray(R.array.words)
        .filter { if (level >= 2) it.length >= 7 else it.length in 4..7 }
        .ifEmpty { listOf("pause") }.random().uppercase()

    override fun build(): View = column().apply {
        addView(big(word, 38f).apply { letterSpacing = 0.15f })
        answerRow(this, numeric = false) { s -> s.uppercase() == word.reversed() }
    }
}

/** Tap numbered dots in order. They land in different spots every time. */
class TapOrder(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "taps"
    private val count = Random.nextInt(4, 7) + level
    override val title = str(R.string.taps_title, count)
    override val subtitle = str(R.string.taps_sub)

    override fun build(): View {
        val area = FrameLayout(ctx)
        area.layoutParams = FrameLayout.LayoutParams(MATCH, dp(300))
        area.minimumHeight = dp(300)
        area.post { place(area) }
        return area
    }

    private fun place(area: FrameLayout) {
        area.removeAllViews()
        val d = dp(56)
        val spots = scatter(area.width, area.height, d, count)
        var expected = 1
        var locked = false
        spots.forEachIndexed { i, (x, y) ->
            val n = i + 1
            val dot = TextView(ctx).apply {
                text = n.toString()
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(ctx.attrColor(R.attr.onAccent2Color))
                background = ctx.getDrawable(R.drawable.dot)
                setOnClickListener {
                    if (locked) return@setOnClickListener
                    if (n == expected) {
                        animate().alpha(0f).scaleX(0.4f).scaleY(0.4f).setDuration(150).start()
                        isEnabled = false
                        expected++
                        if (expected > count) onSolved()
                    } else {
                        locked = true // wrong: freeze the board, then reshuffle
                        shake(area); main.postDelayed({ place(area) }, 400)
                    }
                }
            }
            area.addView(dot, FrameLayout.LayoutParams(d, d).apply { leftMargin = x; topMargin = y })
        }
    }
}

/** Press and hold. Letting go early resets. */
class Hold(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "hold"
    private val seconds = Random.nextInt(3, 7) + level * 2
    override val title = str(R.string.hold_title, seconds)
    override val subtitle = str(R.string.hold_sub)
    private var anim: ValueAnimator? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun build(): View = column().apply {
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(ctx.attrColor(R.attr.accentColor))
        }
        val pad = TextView(ctx).apply {
            text = str(R.string.hold_button)
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(ctx.attrColor(R.attr.onAccent2Color))
            background = ctx.getDrawable(R.drawable.dot)
        }
        val size = dp(150)
        // The button sits at a random horizontal offset each time.
        val wrap = FrameLayout(ctx)
        wrap.addView(pad, FrameLayout.LayoutParams(size, size, Gravity.CENTER_VERTICAL).apply {
            leftMargin = 0
        })
        wrap.post {
            val room = (wrap.width - size).coerceAtLeast(0)
            (pad.layoutParams as FrameLayout.LayoutParams).leftMargin = Random.nextInt(room + 1)
            pad.requestLayout()
        }
        pad.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).start()
                    anim = ValueAnimator.ofInt(0, 1000).apply {
                        duration = seconds * 1000L
                        addUpdateListener { bar.progress = it.animatedValue as Int }
                        start()
                    }
                    main.postDelayed({ anim?.cancel(); onSolved() }, seconds * 1000L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (bar.progress < 1000) {
                        main.removeCallbacksAndMessages(null)
                        anim?.cancel(); bar.progress = 0
                    }
                }
            }
            true
        }
        addView(wrap, LinearLayout.LayoutParams(MATCH, size + dp(20)))
        addView(bar, LinearLayout.LayoutParams(MATCH, dp(10)).apply { topMargin = dp(24) })
    }

    override fun dispose() {
        anim?.cancel()
        super.dispose()
    }
}

class CountDots(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "count"
    override val title = str(R.string.count_title)
    override val subtitle = str(R.string.count_sub)
    private var n = 0

    override fun build(): View = column().apply {
        val area = FrameLayout(ctx)
        addView(area, LinearLayout.LayoutParams(MATCH, dp(220)))
        fun fill() {
            area.removeAllViews()
            n = Random.nextInt(7, 13) + level * 3
            val d = dp(22)
            scatter(area.width, area.height, d, n).forEach { (x, y) ->
                area.addView(View(ctx).apply { background = ctx.getDrawable(R.drawable.dot) },
                    FrameLayout.LayoutParams(d, d).apply { leftMargin = x; topMargin = y })
            }
        }
        area.post { fill() }
        answerRow(this, numeric = true) { s -> (s.toIntOrNull() == n).also { if (!it) area.post { fill() } } }
    }
}

/** A code flashes for a moment; type it from memory. */
class Memory(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "memory"
    private val len = 4 + level
    override val title = str(R.string.memory_title)
    override val subtitle = str(R.string.memory_sub, len)
    private var code = ""

    override fun build(): View = column().apply {
        val shown = big("", 44f).apply { letterSpacing = 0.3f }
        addView(shown)
        lateinit var field: EditText
        fun flash() {
            code = (1..len).map { Random.nextInt(10) }.joinToString("")
            shown.text = code
            shown.alpha = 1f
            field.isEnabled = false
            main.postDelayed({
                shown.animate().alpha(0f).setDuration(250).withEndAction {
                    shown.text = "• ".repeat(len).trim(); shown.alpha = 1f
                }.start()
                field.isEnabled = true
                showKeyboard(field)
            }, 1800L + len * 250L)
        }
        field = answerRow(this, numeric = true) { s -> (s == code).also { if (!it) main.post { flash() } } }
        flash()
    }
}

/** Random non-overlapping top-left positions for [n] squares of size [d] in a w×h box. */
fun scatter(w: Int, h: Int, d: Int, n: Int): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    if (w <= d || h <= d) return List(n) { 0 to 0 }
    val gap = d / 4
    var tries = 0
    while (out.size < n && tries < 4000) {
        tries++
        val x = Random.nextInt(w - d)
        val y = Random.nextInt(h - d)
        if (out.none { (ox, oy) -> kotlin.math.abs(ox - x) < d + gap && kotlin.math.abs(oy - y) < d + gap }) {
            out += x to y
        }
    }
    while (out.size < n) out += Random.nextInt(w - d) to Random.nextInt(h - d)
    return out
}
