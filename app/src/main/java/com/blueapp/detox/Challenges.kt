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
    /** Centered in the free space (the countdown) instead of sitting under the title. */
    open val centered = false
    protected lateinit var onSolved: () -> Unit
    protected val main = Handler(Looper.getMainLooper())

    fun mount(host: FrameLayout, solved: () -> Unit) {
        onSolved = solved
        host.removeAllViews()
        host.addView(build(), FrameLayout.LayoutParams(MATCH, WRAP, if (centered) Gravity.CENTER else Gravity.TOP))
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

    protected fun big(text: String, sizeSp: Float = 36f) = TextView(ctx).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(ctx.attrColor(R.attr.textMain))
        gravity = Gravity.CENTER
        fontFeatureSettings = "tnum"
    }

    protected fun dim(text: String, sizeSp: Float = 13f) = TextView(ctx).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(ctx.attrColor(R.attr.textDim))
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

        /** One exercise: its id, its name and one-line description for Settings, and how to build it. */
        class Kind(val id: String, val name: Int, val desc: Int, val make: (Context, Int) -> Challenge)

        val KINDS: Map<String, Kind> = listOf(
            Kind("countdown", R.string.ex_countdown, R.string.ex_countdown_desc, ::Countdown),
            Kind("box", R.string.ex_box, R.string.ex_box_desc, ::BoxBreath),
            Kind("senses", R.string.ex_senses, R.string.ex_senses_desc, ::Senses),
            Kind("hold", R.string.ex_hold, R.string.ex_hold_desc, ::Hold),
            Kind("phrase", R.string.ex_phrase, R.string.ex_phrase_desc, ::Phrase),
            Kind("math", R.string.ex_math, R.string.ex_math_desc, ::MathChallenge),
            Kind("colors", R.string.ex_colors, R.string.ex_colors_desc, ::ColorWords),
            Kind("taps", R.string.ex_taps, R.string.ex_taps_desc, ::TapOrder),
            Kind("count", R.string.ex_count, R.string.ex_count_desc, ::CountDots),
            Kind("digits", R.string.ex_digits, R.string.ex_digits_desc, ::Memory),
            Kind("sequence", R.string.ex_sequence, R.string.ex_sequence_desc, ::Sequence),
            Kind("words", R.string.ex_words, R.string.ex_words_desc, ::Words),
            Kind("reverse", R.string.ex_reverse, R.string.ex_reverse_desc, ::Reverse),
        ).associateBy { it.id }

        fun blockName(block: String) = when (block) {
            Exercises.CALM -> R.string.block_calm
            Exercises.AGILITY -> R.string.block_agility
            else -> R.string.block_memory
        }

        fun blockDesc(block: String) = when (block) {
            Exercises.CALM -> R.string.block_calm_desc
            Exercises.AGILITY -> R.string.block_agility_desc
            else -> R.string.block_memory_desc
        }

        fun of(ctx: Context, kind: String, level: Int): Challenge =
            (KINDS[kind] ?: KINDS.getValue("countdown")).make(ctx, level)

        /** A random exercise from the ones you have on, never the same kind as last time. */
        fun random(ctx: Context, level: Int, lastKind: String?, enabled: Set<String>): Challenge =
            of(ctx, Exercises.pick(enabled, lastKind), level)

        /** Lowercase, no accents, no punctuation: "Sé a qué" == "se a que". */
        fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()
    }
}

/** Breathe with the circle for a random number of seconds. The question sits under it. */
class Countdown(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "countdown"
    override val centered = true
    private val seconds = when (level) {
        0 -> Random.nextInt(5, 13)
        1 -> Random.nextInt(10, 21)
        2 -> Random.nextInt(15, 31)
        else -> Random.nextInt(25, 46)
    }
    private val question = ctx.resources.getStringArray(R.array.reflections).random()
    override val title = ""
    override val subtitle = ""
    private var breath: ValueAnimator? = null

    override fun build(): View {
        val size = dp(220)
        val ring = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1) + dp(1) / 2, ctx.attrColor(R.attr.textDim) and 0x00FFFFFF or 0x80000000.toInt())
                setColor(Color.TRANSPARENT)
            }
        }
        val number = big(seconds.toString(), 44f)
        val phase = dim(str(R.string.breathe_in))
        val center = column().apply {
            addView(number)
            addView(phase, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) })
        }
        // The ring scales inside a box of its full size, so nothing ever clips it.
        val circle = FrameLayout(ctx).apply {
            addView(ring, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            addView(center, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }

        // 4s in, 4s out, gently. Only runs while this screen is visible.
        breath = ValueAnimator.ofFloat(0.86f, 1f).apply {
            duration = 4000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                ring.scaleX = v; ring.scaleY = v
            }
            var inhale = true
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: android.animation.Animator) {
                    inhale = !inhale
                    phase.text = str(if (inhale) R.string.breathe_in else R.string.breathe_out)
                }
            })
            start()
        }

        var left = seconds
        val tick = object : Runnable {
            override fun run() {
                left--
                if (left <= 0) {
                    breath?.cancel()
                    onSolved()
                } else {
                    number.text = left.toString()
                    main.postDelayed(this, 1000)
                }
            }
        }
        main.postDelayed(tick, 1000)

        return column().apply {
            addView(circle, LinearLayout.LayoutParams(size, size))
            addView(TextView(ctx).apply {
                text = question
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(ctx.attrColor(R.attr.textMain))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setLineSpacing(0f, 1.2f)
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(36) })
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
        question = big("", 34f)
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
        addView(big("“$phrase”", 20f).apply { setPadding(dp(8), 0, dp(8), 0) })
        answerRow(this, numeric = false) { s -> norm(s) == norm(phrase) }
    }
}

class Reverse(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "reverse"
    override val title = str(R.string.reverse_title)
    override val subtitle = str(R.string.reverse_sub)
    private val word: String = ctx.resources.getStringArray(R.array.words)
        .filter { if (level >= 2) it.length >= 7 else it.length in 4..7 }
        .ifEmpty { listOf("pause") }.random().uppercase()

    override fun build(): View = column().apply {
        addView(big(word, 32f).apply { letterSpacing = 0.15f })
        answerRow(this, numeric = false) { s -> s.uppercase() == word.reversed() }
    }
}

/** Tap numbered dots in order. They land in different spots every time. */
class TapOrder(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "taps"
    private val count = Random.nextInt(4, 7) + level
    override val title = str(R.string.taps_title, count)
    override val subtitle = str(R.string.taps_sub)

    private lateinit var progress: TextView

    override fun build(): View {
        val area = FrameLayout(ctx).apply {
            background = ctx.getDrawable(R.drawable.input)
            minimumHeight = dp(320)
        }
        progress = dim(str(R.string.taps_progress, 0, count))
        area.post { place(area) }
        return column().apply {
            gravity = Gravity.START
            addView(area, LinearLayout.LayoutParams(MATCH, dp(320)))
            addView(progress, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(10) })
        }
    }

    private fun place(area: FrameLayout) {
        area.removeAllViews()
        progress.text = str(R.string.taps_progress, 0, count)
        val d = dp(52)
        val pad = dp(8)
        val spots = scatter(area.width - 2 * pad, area.height - 2 * pad, d, count).map { (x, y) -> (x + pad) to (y + pad) }
        var expected = 1
        var locked = false
        spots.forEachIndexed { i, (x, y) ->
            val n = i + 1
            val dot = TextView(ctx).apply {
                text = n.toString()
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(ctx.attrColor(R.attr.textMain))
                background = ctx.getDrawable(R.drawable.dot)
                setOnClickListener {
                    if (locked) return@setOnClickListener
                    if (n == expected) {
                        animate().alpha(0.3f).setDuration(150).start()
                        isEnabled = false
                        progress.text = str(R.string.taps_progress, n, count)
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
            progressTintList = android.content.res.ColorStateList.valueOf(ctx.attrColor(R.attr.textMain))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(ctx.attrColor(R.attr.outlineColor))
        }
        val pad = TextView(ctx).apply {
            text = str(R.string.hold_button)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(ctx.attrColor(R.attr.textMain))
            background = ctx.getDrawable(R.drawable.dot)
        }
        val size = dp(132)
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
        addView(bar, LinearLayout.LayoutParams(MATCH, dp(4)).apply { topMargin = dp(24) })
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
        val area = FrameLayout(ctx).apply { background = ctx.getDrawable(R.drawable.input) }
        addView(area, LinearLayout.LayoutParams(MATCH, dp(220)))
        fun fill() {
            area.removeAllViews()
            n = Random.nextInt(7, 13) + level * 3
            val d = dp(18)
            val pad = dp(10)
            val fill = ctx.attrColor(R.attr.textDim)
            scatter(area.width - 2 * pad, area.height - 2 * pad, d, n).forEach { (x, y) ->
                area.addView(View(ctx).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }
                }, FrameLayout.LayoutParams(d, d).apply { leftMargin = x + pad; topMargin = y + pad })
            }
        }
        area.post { fill() }
        answerRow(this, numeric = true) { s -> (s.toIntOrNull() == n).also { if (!it) area.post { fill() } } }
    }
}

/** A code flashes for a moment; type it from memory. */
class Memory(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "digits"
    private val len = 4 + level
    override val title = str(R.string.memory_title)
    override val subtitle = str(R.string.memory_sub, len)
    private var code = ""

    override fun build(): View = column().apply {
        val shown = big("", 36f).apply { letterSpacing = 0.3f }
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

// ---------------------------------------------------------------- Calm

/** Box breathing: a dot walks round a square, four seconds a side: in, hold, out, hold. */
class BoxBreath(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "box"
    override val centered = true
    private val rounds = intArrayOf(1, 2, 2, 3)[level.coerceIn(0, 3)]
    override val title = str(R.string.box_title)
    override val subtitle = str(R.string.box_sub)

    override fun build(): View {
        val side = dp(200)
        val d = dp(14)
        val square = View(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1) + dp(1) / 2, ctx.attrColor(R.attr.outlineColor))
                setColor(Color.TRANSPARENT)
            }
        }
        val dot = View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ctx.attrColor(R.attr.textMain)) }
        }
        val phase = big(str(R.string.breathe_in), 22f)
        val round = dim(str(R.string.taps_progress, 1, rounds))
        val center = column().apply {
            addView(phase)
            addView(round, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) })
        }
        val frame = FrameLayout(ctx).apply {
            addView(square, FrameLayout.LayoutParams(side, side).apply { leftMargin = d / 2; topMargin = d / 2 })
            addView(center, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            addView(dot, FrameLayout.LayoutParams(d, d))
        }
        val labels = intArrayOf(R.string.breathe_in, R.string.breath_hold, R.string.breathe_out, R.string.breath_hold)
        val sideMs = 4000L
        val total = sideMs * 4 * rounds
        val start = android.os.SystemClock.uptimeMillis()
        var shownSide = -1
        // Timed off the clock, not an animator, so "remove animations" can't skip it.
        val frameTick = object : Runnable {
            override fun run() {
                val t = android.os.SystemClock.uptimeMillis() - start
                if (t >= total) { onSolved(); return }
                val step = (t / sideMs).toInt()
                val s = step % 4
                val f = (t % sideMs).toFloat() / sideMs
                val (x, y) = when (s) {
                    0 -> f * side to 0f
                    1 -> side.toFloat() to f * side
                    2 -> (1 - f) * side to side.toFloat()
                    else -> 0f to (1 - f) * side
                }
                dot.translationX = x; dot.translationY = y
                if (step != shownSide) {
                    shownSide = step
                    phase.text = str(labels[s])
                    round.text = str(R.string.taps_progress, step / 4 + 1, rounds)
                }
                main.postDelayed(this, 16)
            }
        }
        main.post(frameTick)
        return column().apply { addView(frame, LinearLayout.LayoutParams(side + d, side + d)) }
    }
}

/** Grounding: name a few things you can see, hear or feel right now. Anything real counts. */
class Senses(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "senses"
    private val n = 3 + level / 2
    override val title = str(intArrayOf(R.string.senses_see, R.string.senses_hear, R.string.senses_feel).random(), n)
    override val subtitle = str(R.string.senses_sub)

    override fun build(): View = column().apply {
        val fields = (1..n).map { i ->
            input(numeric = false, hint = i.toString()).apply {
                textSize = 17f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                if (i < n) imeOptions = EditorInfo.IME_ACTION_NEXT
            }
        }
        fun submit() {
            val seen = HashSet<String>()
            val bad = fields.firstOrNull { f ->
                val v = norm(f.text.toString())
                v.count { it.isLetter() } < 2 || !seen.add(v)
            }
            if (bad == null) {
                hideKeyboard(fields.last()); onSolved()
            } else {
                shake(bad); bad.requestFocus()
            }
        }
        fields.forEachIndexed { i, f ->
            addView(f, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(if (i == 0) 4 else 10) })
        }
        fields.last().setOnEditorActionListener { _, _, _ -> submit(); true }
        addView(button(str(R.string.done)).apply { setOnClickListener { submit() } },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
        main.postDelayed({ showKeyboard(fields.first()) }, 250)
    }
}

// ---------------------------------------------------------------- Agility

/** Stroop test: a colour name printed in another colour. Tap the ink, not the word. */
class ColorWords(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "colors"
    private val goal = 3 + level
    override val title = str(R.string.colors_title)
    override val subtitle = str(R.string.colors_sub, goal)
    private val names = ctx.resources.getStringArray(R.array.color_names) // red, blue, green, purple

    /** Same four hues, darker on light themes so they stay readable. */
    private val inks: IntArray =
        if (Color.luminance(ctx.attrColor(R.attr.bgColor)) > 0.5f)
            intArrayOf(0xFFC62828.toInt(), 0xFF1E5FC2.toInt(), 0xFF1F7A45.toInt(), 0xFF6E3FB5.toInt())
        else
            intArrayOf(0xFFF0716F.toInt(), 0xFF6CA6F2.toInt(), 0xFF5DC08A.toInt(), 0xFFB792F0.toInt())

    override fun build(): View = column().apply {
        val word = big("", 40f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val progress = dim(str(R.string.taps_progress, 0, goal))
        val buttons = List(4) { Button(ctx, null, 0, R.style.Secondary) }
        var streak = 0
        fun round() {
            val w = Random.nextInt(4)
            // Mostly mismatched; sometimes it matches, so "never the word" isn't a shortcut.
            val ink = if (Random.nextInt(4) == 0) w else (w + 1 + Random.nextInt(3)) % 4
            word.text = names[w].uppercase()
            word.setTextColor(inks[ink])
            val order = (0..3).shuffled()
            buttons.forEachIndexed { i, b ->
                val c = order[i]
                b.text = names[c]
                b.setOnClickListener {
                    if (c == ink) streak++ else { streak = 0; shake(word) }
                    progress.text = str(R.string.taps_progress, streak, goal)
                    if (streak >= goal) onSolved() else round()
                }
            }
        }
        addView(word, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
        for (r in 0..1) {
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                for (c in 0..1) addView(buttons[r * 2 + c], LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    if (c == 1) marginStart = dp(10)
                })
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(if (r == 0) 32 else 10) })
        }
        addView(progress, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })
        round()
    }
}

// ---------------------------------------------------------------- Memory

/** Squares in a 3×3 grid light up one by one; tap them back in the same order. */
class Sequence(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "sequence"
    private val len = 3 + level
    override val title = str(R.string.sequence_title)
    override val subtitle = str(R.string.sequence_sub, len)

    private val cells = ArrayList<View>()
    private lateinit var grid: LinearLayout
    private lateinit var status: TextView
    private var seq = IntArray(0)
    private var pos = 0
    private var accepting = false

    private fun cellBg(lit: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        if (lit) setColor(ctx.attrColor(R.attr.textMain))
        else { setColor(ctx.attrColor(R.attr.surfaceColor)); setStroke(dp(1), ctx.attrColor(R.attr.outlineColor)) }
    }

    private fun light(i: Int, ms: Long) {
        cells[i].background = cellBg(true)
        main.postDelayed({ cells[i].background = cellBg(false) }, ms)
    }

    private fun play() {
        accepting = false
        pos = 0
        status.text = str(R.string.sequence_watch)
        val out = IntArray(len)
        for (k in 0 until len) {
            var c: Int
            do c = Random.nextInt(9) while (k > 0 && c == out[k - 1]) // no immediate repeats: they'd look like one flash
            out[k] = c
        }
        seq = out
        seq.forEachIndexed { k, c -> main.postDelayed({ light(c, 450) }, 700L + k * 700L) }
        main.postDelayed({
            accepting = true
            status.text = str(R.string.sequence_turn, 0, len)
        }, 700L + len * 700L)
    }

    private fun tap(i: Int) {
        if (!accepting) return
        light(i, 180)
        if (i == seq[pos]) {
            pos++
            status.text = str(R.string.sequence_turn, pos, len)
            if (pos == len) { accepting = false; main.postDelayed({ onSolved() }, 250) }
        } else {
            accepting = false
            shake(grid)
            main.postDelayed({ play() }, 800)
        }
    }

    override fun build(): View = column().apply {
        val size = dp(76)
        val gap = dp(10)
        grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0..2) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0..2) {
                val i = r * 3 + c
                val cell = View(ctx).apply {
                    background = cellBg(false)
                    setOnClickListener { tap(i) }
                    contentDescription = (i + 1).toString()
                }
                cells += cell
                row.addView(cell, LinearLayout.LayoutParams(size, size).apply { if (c > 0) marginStart = gap })
            }
            grid.addView(row, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (r > 0) topMargin = gap })
        }
        status = dim("")
        addView(grid, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) })
        addView(status, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(16) })
        play()
    }
}

/** A few words for a moment, then pick them out of a list twice as long. */
class Words(ctx: Context, level: Int) : Challenge(ctx, level) {
    override val kind = "words"
    private val n = 3 + level / 2
    override val title = str(R.string.words_title, n)
    override val subtitle = str(R.string.words_sub)
    private val pool = ctx.resources.getStringArray(R.array.words).distinct()

    override fun build(): View = column().apply {
        val shown = big("", 24f).apply { setLineSpacing(dp(6).toFloat(), 1f) }
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val status = dim("")
        addView(shown, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
        addView(list, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(status, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })

        lateinit var show: () -> Unit
        fun ask(seen: List<String>, decoys: List<String>) {
            shown.visibility = View.GONE
            list.visibility = View.VISIBLE
            list.removeAllViews()
            status.text = str(R.string.words_found, 0, n)
            var found = 0
            var locked = false
            (seen + decoys).shuffled().chunked(2).forEachIndexed { r, pair ->
                list.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    pair.forEachIndexed { c, w ->
                        addView(Button(ctx, null, 0, R.style.Secondary).apply {
                            text = w
                            setOnClickListener {
                                if (locked) return@setOnClickListener
                                if (w in seen) {
                                    isEnabled = false
                                    animate().alpha(0.35f).setDuration(150).start()
                                    found++
                                    status.text = str(R.string.words_found, found, n)
                                    if (found == n) { locked = true; main.postDelayed({ onSolved() }, 250) }
                                } else {
                                    locked = true // wrong word: new set
                                    shake(list); main.postDelayed({ show() }, 700)
                                }
                            }
                        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (c == 1) marginStart = dp(10) })
                    }
                }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(if (r == 0) 4 else 10) })
            }
        }
        show = {
            val picked = pool.shuffled().take(2 * n)
            val seen = picked.take(n)
            shown.text = seen.joinToString("\n")
            shown.visibility = View.VISIBLE
            list.visibility = View.GONE
            status.text = str(R.string.words_memorise)
            main.postDelayed({ ask(seen, picked.drop(n)) }, 1500L + n * 900L)
        }
        show()
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
