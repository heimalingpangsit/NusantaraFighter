package com.nusantarafighter

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    companion object {
        const val W = 320f
        const val H = 180f
        const val SCALE = 3f

        fun colorFor(id: FighterId): Int = when (id) {
            FighterId.PRORORO -> 0xFF3E4A59.toInt()
            FighterId.MEGACHAN -> 0xFF8E2430.toInt()
            FighterId.MR_ETANOL -> 0xFF1976A8.toInt()
            FighterId.MR_YOUTUBE -> 0xFFC62828.toInt()
            FighterId.FUFU -> 0xFF1565C0.toInt()
            FighterId.RAJA_SOLO -> 0xFF212121.toInt()
            FighterId.PURBANKYA -> 0xFF6D4C41.toInt()
        }

        // Accent/headwear color, distinct per fighter for readability at a glance.
        fun trimFor(id: FighterId): Int = when (id) {
            FighterId.PRORORO -> 0xFF2B2B2B.toInt()
            FighterId.MEGACHAN -> 0xFFD32F2F.toInt()
            FighterId.MR_ETANOL -> 0xFF212121.toInt()
            FighterId.MR_YOUTUBE -> 0xFF1B1B1B.toInt()
            FighterId.FUFU -> 0xFFECEFF1.toInt()
            FighterId.RAJA_SOLO -> 0xFF1A1A2E.toInt()
            FighterId.PURBANKYA -> 0xFF8D6E63.toInt()
        }
    }

    private enum class Screen { LOADING, TITLE, SELECT, BATTLE, RESULT, ABOUT }
    private var screen = Screen.LOADING
    private var selected = 0
    private var enemySelected = 1
    private var player: Fighter? = null
    private var enemy: Fighter? = null
    private var timer = 60
    private var frame = 0
    private var message = "READY"
    private var messageTimer = 0
    private var winner = ""
    private var lastAttackFrame = -99
    private var attackCooldown = 0
    // CPU re-decides "where to walk" only every so often, instead of chasing
    // the player's x every single frame — that instant 1:1 tracking read as
    // the enemy "mirroring" the player's movement.
    private var aiMoveDir = 0f
    private var aiDecisionTimer = 0
    private var globalFrame = 0
    private var loadingTimer = 140

    // Pause / juice state
    private var paused = false
    private var shakeTimer = 0
    private var shakeMag = 0f
    private var hitstopTimer = 0
    private var flashTimer = 0

    private data class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Int, val maxLife: Int, val color: Int, val size: Float
    )

    private data class FloatText(
        var x: Float, var y: Float, var life: Int, val maxLife: Int,
        val text: String, val color: Int
    )

    private val sparks = mutableListOf<Spark>()
    private val floatTexts = mutableListOf<FloatText>()

    private val paint = Paint().apply { isAntiAlias = false }
    private val text = Paint().apply {
        isAntiAlias = false
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var scale = 1f
    private var ox = 0f
    private var oy = 0f

    init {
        isFocusable = true
        setBackgroundColor(Color.BLACK)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scale = min(width / W, height / H)
        ox = (width - W * scale) / 2f
        oy = (height - H * scale) / 2f

        val shx = if (shakeTimer > 0) (Random.nextFloat() * 2f - 1f) * shakeMag * scale else 0f
        val shy = if (shakeTimer > 0) (Random.nextFloat() * 2f - 1f) * shakeMag * scale else 0f

        canvas.save()
        canvas.translate(ox + shx, oy + shy)
        canvas.scale(scale, scale)

        globalFrame++

        when (screen) {
            Screen.LOADING -> drawLoading(canvas)
            Screen.TITLE -> drawTitle(canvas)
            Screen.SELECT -> drawSelect(canvas)
            Screen.BATTLE -> drawBattle(canvas)
            Screen.RESULT -> drawResult(canvas)
            Screen.ABOUT -> drawAbout(canvas)
        }

        canvas.restore()

        if (screen == Screen.BATTLE && !paused) {
            updateGame()
        }
        postInvalidateOnAnimation()
    }

    private fun drawLoading(c: Canvas) {
        val bg = SpriteBank.background(context, "istana.png")
        if (bg != null) {
            paint.isFilterBitmap = true
            c.drawBitmap(bg, null, RectF(0f, 0f, W, H), paint)
            paint.isFilterBitmap = false
        } else {
            paint.color = 0xFF0A0E16.toInt()
            c.drawRect(0f, 0f, W, H, paint)
        }

        paint.color = 0xD9050505.toInt()
        c.drawRect(0f, 0f, W, H, paint)

        // slow ambient glow line behind the crest area
        paint.isAntiAlias = true
        paint.color = 0x33FFC107
        c.drawCircle(W / 2f, 60f, 46f + 4f * kotlin.math.sin(globalFrame / 20f), paint)
        paint.isAntiAlias = false

        centerText(c, "WELCOME TO", 58f, 0xFFBBBBBB.toInt(), 7f)
        centerText(c, "NUSANTARA", 79f, 0x77000000, 15f)
        centerText(c, "NUSANTARA", 78f, 0xFFFFC107.toInt(), 15f)
        centerText(c, "FIGHTER", 97f, 0x77000000, 20f)
        centerText(c, "FIGHTER", 96f, Color.WHITE, 20f)

        val progress = (1f - (loadingTimer.toFloat() / 140f)).coerceIn(0f, 1f)
        paint.isAntiAlias = true
        paint.color = 0xFF14171C.toInt()
        c.drawRoundRect(RectF(70f, 122f, 250f, 132f), 4f, 4f, paint)
        paint.color = 0xFFFFC107.toInt()
        c.drawRoundRect(RectF(72f, 124f, 72f + 176f * progress, 130f), 3f, 3f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        c.drawRoundRect(RectF(70f, 122f, 250f, 132f), 4f, 4f, paint)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false

        if (loadingTimer > 0) {
            centerText(c, "LOADING ${(progress * 100).toInt()}%", 145f, 0xFF9AA0A6.toInt(), 6f)
            loadingTimer--
        } else if (globalFrame % 40 < 26) {
            centerText(c, "TAP TO CONTINUE", 145f, 0xFFFFFFFF.toInt(), 6f)
        }

        centerText(c, "by JEPIN666X", 165f, 0xFFFFC107.toInt(), 6f)
    }

    private fun drawTitle(c: Canvas) {
        val bg = SpriteBank.background(context, "monas.png")
        if (bg != null) {
            paint.isFilterBitmap = true
            c.drawBitmap(bg, null, RectF(0f, 0f, W, H), paint)
            paint.isFilterBitmap = false
        } else {
            paint.color = 0xFF090909.toInt()
            c.drawRect(0f, 0f, W, H, paint)
            paint.color = 0xFF172033.toInt()
            c.drawRect(0f, 0f, W, 92f, paint)
            paint.color = 0xFF4A1820.toInt()
            c.drawRect(0f, 65f, W, 120f, paint)
            paint.color = 0xFFB84A24.toInt()
            c.drawRect(0f, 105f, W, H, paint)
            drawCity(c)
        }

        // darkening vignette so text/buttons stay legible over the art
        paint.color = 0x99050505.toInt()
        c.drawRect(0f, 0f, W, H, paint)
        paint.color = 0xCC050505.toInt()
        c.drawRect(0f, 88f, W, H, paint)

        // title with a soft drop shadow for depth
        centerText(c, "NUSANTARA", 39f, 0x66000000, 14f)
        centerText(c, "NUSANTARA", 38f, 0xFFFFC107.toInt(), 14f)
        centerText(c, "FIGHTER", 55f, 0x66000000, 20f)
        centerText(c, "FIGHTER", 54f, Color.WHITE, 20f)
        centerText(c, "PIXEL PARODY ARCADE", 70f, 0xFFFF6B6B.toInt(), 6f)

        elegantButton(c, 88f, 88f, 232f, 109f, "START GAME", true)
        elegantButton(c, 88f, 114f, 232f, 135f, "CHARACTERS", false)
        elegantButton(c, 88f, 140f, 232f, 161f, "ABOUT US", false)

        centerText(c, "Created by JEPIN666X", 173f, 0xFF9AA0A6.toInt(), 5f)
    }

    private fun drawAbout(c: Canvas) {
        val bg = SpriteBank.background(context, "istana.png")
        if (bg != null) {
            paint.isFilterBitmap = true
            c.drawBitmap(bg, null, RectF(0f, 0f, W, H), paint)
            paint.isFilterBitmap = false
        } else {
            paint.color = 0xFF090909.toInt()
            c.drawRect(0f, 0f, W, H, paint)
        }

        paint.color = 0xCC050505.toInt()
        c.drawRect(0f, 0f, W, H, paint)

        paint.isAntiAlias = true
        paint.color = 0xE6151A22.toInt()
        c.drawRoundRect(RectF(40f, 28f, 280f, 152f), 8f, 8f, paint)
        paint.color = 0xFFFFC107.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        c.drawRoundRect(RectF(40f, 28f, 280f, 152f), 8f, 8f, paint)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false

        centerText(c, "ABOUT", 46f, 0xFFFFC107.toInt(), 12f)
        centerText(c, "NUSANTARA FIGHTER", 58f, Color.WHITE, 9f)

        paint.color = 0xFF2A2F3A.toInt()
        c.drawRect(70f, 65f, 250f, 66f, paint)

        centerText(c, "Pixel-parody arcade fighter starring", 80f, 0xFFCFCFCF.toInt(), 5.5f)
        centerText(c, "original caricature characters.", 89f, 0xFFCFCFCF.toInt(), 5.5f)
        centerText(c, "Fictional combat, for laughs only.", 98f, 0xFFCFCFCF.toInt(), 5.5f)

        centerText(c, "CREATED BY", 116f, 0xFF888888.toInt(), 5f)
        centerText(c, "JEPIN666X", 130f, 0xFFFFC107.toInt(), 11f)

        elegantButton(c, 130f, 160f, 190f, 178f, "BACK", true)
    }

    private fun elegantButton(c: Canvas, l: Float, t: Float, r: Float, b: Float, label: String, primary: Boolean) {
        val rr = 6f
        paint.isAntiAlias = true

        // shadow
        paint.color = 0x66000000
        c.drawRoundRect(RectF(l, t + 2f, r, b + 2f), rr, rr, paint)

        // panel fill
        paint.color = if (primary) 0xE6C1272D.toInt() else 0xE6151A22.toInt()
        c.drawRoundRect(RectF(l, t, r, b), rr, rr, paint)

        // gold hairline border
        paint.color = 0xFFFFC107.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        c.drawRoundRect(RectF(l, t, r, b), rr, rr, paint)
        paint.style = Paint.Style.FILL

        // subtle top highlight
        paint.color = 0x33FFFFFF
        c.drawRoundRect(RectF(l + 2f, t + 1.5f, r - 2f, t + (b - t) / 2.2f), rr - 2f, rr - 2f, paint)

        paint.isAntiAlias = false
        centerText(c, label, (t + b) / 2f + 3f, Color.WHITE, 8f, l, r)
    }

    private fun drawSelect(c: Canvas) {
        paint.color = 0xFF0A0E16.toInt()
        c.drawRect(0f, 0f, W, H, paint)

        val data = Fighters.all[selected]
        val enemyData = Fighters.all[enemySelected]

        // side panels, red for P1 / blue for CPU, like the reference layout
        paint.color = 0xFF3A1414.toInt()
        c.drawRect(0f, 0f, 96f, H, paint)
        paint.color = 0xFF10202E.toInt()
        c.drawRect(224f, 0f, W, H, paint)

        smallText(c, "PLAYER 1", 6f, 10f, 0xFFFF5252.toInt(), 7f)
        smallText(c, "CPU", 292f, 10f, 0xFF42A5F5.toInt(), 7f)

        drawLargeFighter(c, data, 48f, 66f, true)
        drawLargeFighter(c, enemyData, 272f, 66f, false)

        centerText(c, data.name, 118f, Color.WHITE, 9f, 0f, 96f)
        centerText(c, enemyData.name, 118f, Color.WHITE, 9f, 224f, W)

        centerText(c, "NUSANTARA FIGHTER", 12f, 0xFFFFC107.toInt(), 10f, 96f, 224f)
        centerText(c, "CHOOSE YOUR FIGHTER", 22f, 0xFFBBBBBB.toInt(), 6f, 96f, 224f)

        // roster grid in the middle
        val cols = 4
        val cellW = 30f
        val cellH = 38f
        val gridLeft = 98f
        val gridTop = 28f
        for (i in Fighters.all.indices) {
            val col = i % cols
            val row = i / cols
            val cx = gridLeft + col * cellW
            val cy = gridTop + row * cellH
            val f = Fighters.all[i]

            paint.color = 0xFF161B24.toInt()
            c.drawRect(cx + 1f, cy + 1f, cx + cellW - 1f, cy + cellH - 3f, paint)

            val isP1 = i == selected
            val isCpu = i == enemySelected
            if (isP1 || isCpu) {
                paint.color = if (isP1) 0xFFFF5252.toInt() else 0xFF42A5F5.toInt()
                c.drawRect(cx, cy, cx + cellW, cy + 2f, paint)
                c.drawRect(cx, cy + cellH - 5f, cx + cellW, cy + cellH - 3f, paint)
                c.drawRect(cx, cy, cx + 2f, cy + cellH - 3f, paint)
                c.drawRect(cx + cellW - 2f, cy, cx + cellW, cy + cellH - 3f, paint)
            }

            drawIcon(c, f, cx + cellW / 2f, cy + 18f)
            smallText(c, f.name.take(7), cx + 2f, cy + cellH - 6f, 0xFFDDDDDD.toInt(), 4f)
        }

        statBar(c, 6f, 128f, "POWER", data.attack, 20)
        statBar(c, 6f, 138f, "SPEED", (data.speed * 10).toInt(), 32)
        statBar(c, 6f, 148f, "DEFENSE", data.hp / 3, 47)

        statBar(c, 228f, 128f, "POWER", enemyData.attack, 20)
        statBar(c, 228f, 138f, "SPEED", (enemyData.speed * 10).toInt(), 32)
        statBar(c, 228f, 148f, "DEFENSE", enemyData.hp / 3, 47)

        smallText(c, "SKILL 1: ${data.skill1}", 4f, 172f, 0xFFFFD54F.toInt(), 5f)
        centerText(c, "TAP grid pilih P1 • TAP panel biru = CPU", 154f, 0xFF888888.toInt(), 4.5f, 96f, 224f)

        elegantButton(c, 130f, 158f, 190f, 176f, "FIGHT!", true)
    }

    private fun statBar(c: Canvas, x: Float, y: Float, label: String, value: Int, max: Int) {
        smallText(c, label, x, y + 4f, 0xFFAAAAAA.toInt(), 5f)
        paint.color = 0xFF1B1B1B.toInt()
        c.drawRect(x + 30f, y - 3f, x + 88f, y + 3f, paint)
        paint.color = 0xFFE53935.toInt()
        val ratio = (value.toFloat() / max).coerceIn(0f, 1f)
        c.drawRect(x + 31f, y - 2f, x + 31f + 56f * ratio, y + 2f, paint)
    }

    private fun drawIcon(c: Canvas, d: FighterData, cx: Float, cy: Float) {
        val bmp = SpriteBank.frames(context, d.id, "idle").firstOrNull()
        if (bmp != null) {
            val half = 12f
            val r = android.graphics.RectF(cx - half, cy - half - 2f, cx + half, cy + half - 2f)
            paint.isFilterBitmap = false
            c.drawBitmap(bmp, null, r, paint)
        } else {
            paint.color = colorFor(d.id)
            c.drawRect(cx - 8f, cy - 5f, cx + 8f, cy + 11f, paint)
            paint.color = 0xFFE7B98B.toInt()
            c.drawRect(cx - 6f, cy - 13f, cx + 6f, cy - 3f, paint)
        }
    }

    /** Everything below this line is a dedicated HUD dock, visually separated
     *  from the fight itself, so the fighters never render "inside" a button. */
    private val dockTop = 120f

    private fun drawBattle(c: Canvas) {
        drawArena(c)

        val p = player ?: return
        val e = enemy ?: return

        elegantHealthBar(c, 10f, 6f, 132f, p.hp, p.data.hp, false)
        elegantHealthBar(c, 188f, 6f, 310f, e.hp, e.data.hp, true)

        timerBadge(c, timer)

        nameplate(c, p.data.name, p.data.title, 10f, 19f, false)
        nameplate(c, e.data.name, e.data.title, 310f, 19f, true)

        p.draw(c, paint, context)
        e.draw(c, paint, context)

        drawEffects(c)

        if (messageTimer > 0) {
            messageBanner(c, message)
        }

        drawControlDock(c, p)

        if (flashTimer > 0) {
            paint.isAntiAlias = false
            paint.color = ((flashTimer * 32).coerceAtMost(190) shl 24) or 0xFFFFFF
            c.drawRect(0f, 0f, W, H, paint)
        }

        if (paused) drawPauseOverlay(c)
    }

    private fun drawControlDock(c: Canvas, p: Fighter) {
        paint.isAntiAlias = true
        paint.color = 0xF20B0E14.toInt()
        c.drawRect(0f, dockTop, W, H, paint)
        paint.color = 0xFFFFC107.toInt()
        c.drawRect(0f, dockTop, W, dockTop + 1.4f, paint)
        paint.isAntiAlias = false

        // movement, left cluster
        control(c, 10f, 146f, 42f, 176f, "◀")
        control(c, 46f, 146f, 78f, 176f, "▶")
        control(c, 82f, 146f, 114f, 176f, "J")

        // attacks, right cluster
        control(c, 206f, 146f, 244f, 176f, "ATK", accent = 0xFFE53935.toInt())
        control(
            c, 248f, 146f, 276f, 176f, "S1",
            accent = 0xFF4FC3F7.toInt(), dimmed = p.skill1Cooldown > 0
        )
        control(
            c, 280f, 146f, 308f, 176f, "S2",
            accent = 0xFF4FC3F7.toInt(), dimmed = p.skill2Cooldown > 0
        )

        // ultimate meter bar, above the attack cluster
        control(
            c, 210f, 124f, 310f, 140f, "ULT",
            accent = 0xFFFFC107.toInt(), dimmed = p.ultimateCooldown > 0
        )

        // pause / menu, dead-center of the dock where nothing else lives
        control(c, 146f, 124f, 174f, 140f, "❚❚", accent = 0xFFCFCFCF.toInt())
    }

    private fun drawPauseOverlay(c: Canvas) {
        paint.isAntiAlias = true
        paint.color = 0xCC050608.toInt()
        c.drawRect(0f, 0f, W, H, paint)

        val l = 92f; val t = 44f; val r = 228f; val b = 138f
        paint.color = 0xF20F131A.toInt()
        c.drawRoundRect(RectF(l, t, r, b), 8f, 8f, paint)
        paint.color = 0xFFFFC107.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f
        c.drawRoundRect(RectF(l, t, r, b), 8f, 8f, paint)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false

        centerText(c, "PAUSED", 62f, 0xFFFFC107.toInt(), 11f)
        elegantButton(c, 106f, 78f, 214f, 98f, "RESUME", true)
        elegantButton(c, 106f, 104f, 214f, 124f, "BACK TO LOBBY", false)
    }

    private fun elegantHealthBar(c: Canvas, left: Float, top: Float, right: Float, hp: Int, maxHp: Int, flip: Boolean) {
        val rr = 4f
        paint.isAntiAlias = true

        paint.color = 0x88000000.toInt()
        c.drawRoundRect(RectF(left, top + 1.5f, right, top + 11.5f), rr, rr, paint)
        paint.color = 0xFF14171C.toInt()
        c.drawRoundRect(RectF(left, top, right, top + 10f), rr, rr, paint)

        val ratio = (hp.toFloat() / max(1, maxHp)).coerceIn(0f, 1f)
        val barColor = when {
            ratio > 0.5f -> 0xFF43A047.toInt()
            ratio > 0.22f -> 0xFFFFB300.toInt()
            else -> 0xFFE53935.toInt()
        }
        paint.color = barColor
        val innerW = right - left - 4f
        if (!flip) {
            c.drawRoundRect(RectF(left + 2f, top + 2f, left + 2f + innerW * ratio, top + 8f), 2.5f, 2.5f, paint)
        } else {
            c.drawRoundRect(RectF(right - 2f - innerW * ratio, top + 2f, right - 2f, top + 8f), 2.5f, 2.5f, paint)
        }

        paint.color = 0xFFFFC107.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        c.drawRoundRect(RectF(left, top, right, top + 10f), rr, rr, paint)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false
    }

    private fun timerBadge(c: Canvas, t: Int) {
        paint.isAntiAlias = true
        paint.color = 0xE6151A22.toInt()
        c.drawCircle(W / 2f, 15f, 13f, paint)
        paint.color = 0xFFFFC107.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f
        c.drawCircle(W / 2f, 15f, 13f, paint)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false
        centerText(c, t.toString(), 19f, Color.WHITE, 12f)
    }

    private fun nameplate(c: Canvas, name: String, title: String, x: Float, y: Float, flip: Boolean) {
        val w = 118f
        val h = 20f
        val l = if (!flip) x else x - w
        paint.isAntiAlias = true
        paint.color = 0xCC0C0F14.toInt()
        c.drawRoundRect(RectF(l, y, l + w, y + h), 4f, 4f, paint)
        paint.color = if (!flip) 0xFFE53935.toInt() else 0xFF2E9BF5.toInt()
        if (!flip) c.drawRect(l, y, l + 3f, y + h, paint) else c.drawRect(l + w - 3f, y, l + w, y + h, paint)
        paint.isAntiAlias = false

        if (!flip) {
            smallText(c, name, l + 7f, y + 10f, Color.WHITE, 6f)
            smallText(c, title, l + 7f, y + 18f, 0xFFAAAAAA.toInt(), 4f)
        } else {
            text.textAlign = Paint.Align.RIGHT
            text.color = Color.WHITE
            text.textSize = 6f
            c.drawText(name, l + w - 7f, y + 10f, text)
            text.color = 0xFFAAAAAA.toInt()
            text.textSize = 4f
            c.drawText(title, l + w - 7f, y + 18f, text)
            text.textAlign = Paint.Align.LEFT
        }
    }

    private fun messageBanner(c: Canvas, msg: String) {
        paint.isAntiAlias = true
        val w = (msg.length * 6f + 24f).coerceAtMost(280f)
        val l = W / 2f - w / 2f
        paint.color = 0xCC0C0F14.toInt()
        c.drawRoundRect(RectF(l, 52f, l + w, 70f), 5f, 5f, paint)
        paint.color = 0xFFFFC107.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        c.drawRoundRect(RectF(l, 52f, l + w, 70f), 5f, 5f, paint)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false
        centerText(c, msg, 65f, 0xFFFFD54F.toInt(), 8f)
    }

    // ---- Hit VFX: particle bursts + floating damage numbers + screen shake ----

    private fun spawnBurst(x: Float, y: Float, color: Int, count: Int, speed: Float) {
        repeat(count) {
            val ang = Random.nextFloat() * (Math.PI * 2).toFloat()
            val spd = speed * (0.5f + Random.nextFloat() * 0.6f)
            sparks.add(
                Spark(
                    x, y,
                    kotlin.math.cos(ang) * spd,
                    kotlin.math.sin(ang) * spd - 1f,
                    18, 18, color, 1.6f + Random.nextFloat() * 1.6f
                )
            )
        }
    }

    private fun spawnFloatText(x: Float, y: Float, txt: String, color: Int) {
        floatTexts.add(FloatText(x, y, 38, 38, txt, color))
    }

    private fun triggerShake(mag: Float, frames: Int) {
        shakeMag = max(shakeMag, mag)
        shakeTimer = max(shakeTimer, frames)
    }

    /** Compares HP before/after a move to decide what VFX to spawn, so every
     *  attack (basic, skill, ultimate, or the CPU's) gets consistent hit
     *  feedback from one place instead of being wired into every branch.
     *  Generalized over attacker/defender so it works for either side. */
    private fun afterAction(attacker: Fighter, defender: Fighter, defenderHpBefore: Int, attackerHpBefore: Int, tier: Int) {
        if (defender.hp < defenderHpBefore) {
            val dmg = defenderHpBefore - defender.hp
            val color = when (tier) {
                1 -> 0xFFFFFFFF.toInt()
                2 -> 0xFF4FC3F7.toInt()
                else -> 0xFFFFC107.toInt()
            }
            spawnBurst(defender.x, defender.y + 4f, color, 5 + tier * 4, 1.6f + tier * 0.6f)
            spawnFloatText(defender.x, defender.y - 30f, "-$dmg", 0xFFFF5252.toInt())
            when (tier) {
                1 -> triggerShake(1.6f, 5)
                2 -> { triggerShake(3f, 9); hitstopTimer = max(hitstopTimer, 4) }
                else -> { triggerShake(5.5f, 16); hitstopTimer = max(hitstopTimer, 8); flashTimer = 6 }
            }
        }
        if (attacker.hp > attackerHpBefore) {
            spawnBurst(attacker.x, attacker.y + 4f, 0xFF66BB6A.toInt(), 8, 1.4f)
            spawnFloatText(attacker.x, attacker.y - 30f, "+${attacker.hp - attackerHpBefore}", 0xFF66BB6A.toInt())
        }
    }

    private fun updateEffects() {
        val si = sparks.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.x += s.vx; s.y += s.vy; s.vy += 0.18f; s.vx *= 0.94f
            s.life--
            if (s.life <= 0) si.remove()
        }
        val fi = floatTexts.iterator()
        while (fi.hasNext()) {
            val f = fi.next()
            f.y -= 0.6f
            f.life--
            if (f.life <= 0) fi.remove()
        }
        if (shakeTimer > 0) shakeTimer-- else shakeMag = 0f
        if (flashTimer > 0) flashTimer--
    }

    private fun drawEffects(c: Canvas) {
        paint.isAntiAlias = true
        for (s in sparks) {
            val a = (255f * (s.life.toFloat() / s.maxLife)).toInt().coerceIn(0, 255)
            paint.color = (a shl 24) or (s.color and 0x00FFFFFF)
            c.drawCircle(s.x, s.y, s.size, paint)
        }
        paint.isAntiAlias = false
        for (f in floatTexts) {
            val a = (255f * (f.life.toFloat() / f.maxLife)).toInt().coerceIn(0, 255)
            text.textAlign = Paint.Align.CENTER
            text.color = (a shl 24) or (f.color and 0x00FFFFFF)
            text.textSize = 9f
            c.drawText(f.text, f.x, f.y, text)
            text.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawResult(c: Canvas) {
        paint.color = 0xFF090909.toInt()
        c.drawRect(0f, 0f, W, H, paint)

        centerText(c, "ROUND OVER", 50f, 0xFFFFC107.toInt(), 12f)
        centerText(c, winner, 78f, Color.WHITE, 15f)
        centerText(c, "TAP TO PLAY AGAIN", 118f, 0xFFFF5252.toInt(), 7f)
    }

    private var arenaChoice = 0

    private fun drawArena(c: Canvas) {
        val bgFile = when (arenaChoice % 4) {
            0 -> "istana.png"
            1 -> "monumen.png"
            2 -> "monas.png"
            else -> "kopdes.png"
        }
        val bg = SpriteBank.background(context, bgFile)

        if (bg != null) {
            paint.isFilterBitmap = true
            c.drawBitmap(bg, null, RectF(0f, 0f, W, 108f), paint)
            paint.isFilterBitmap = false
        } else {
            paint.color = 0xFF5DB7E8.toInt()
            c.drawRect(0f, 0f, W, 108f, paint)
            paint.color = 0xFFBFE9FF.toInt()
            c.drawRect(0f, 32f, W, 38f, paint)
            drawCity(c)
        }

        // ground
        paint.color = 0xFFB56A35.toInt()
        c.drawRect(0f, 92f, W, H, paint)

        // grass pixels, kept above the fighters' feet line (GROUND_Y)
        paint.color = 0xFF3D6B32.toInt()
        for (i in 0 until 30) {
            val x = ((i * 37) % 310 + 5).toFloat()
            val y = (94 + (i * 7) % 12).toFloat()
            c.drawRect(x, y, x + 3, y + 2, paint)
        }

        // ground seam right at the fighters' feet, so they visibly stand ON it
        paint.color = 0xFF70452B.toInt()
        c.drawRect(0f, GROUND_Y + 1, W, GROUND_Y + 3, paint)
    }

    private fun drawCity(c: Canvas) {
        paint.color = 0xFF263238.toInt()
        for (i in 0 until 14) {
            val x = (i * 25).toFloat()
            val h = (15 + (i * 9) % 35).toFloat()
            c.drawRect(x, 103f - h, x + 16f, 103f, paint)
        }
    }

    private fun drawLargeFighter(c: Canvas, d: FighterData, x: Float, y: Float, right: Boolean) {
        // portrait plate behind the sprite, like a proper character-select card
        paint.color = 0xFF13161C.toInt()
        c.drawRect(x - 26f, y - 34f, x + 26f, y + 54f, paint)
        paint.color = if (right) 0xFF2A4A73.toInt() else 0xFF732A2A.toInt()
        c.drawRect(x - 26f, y - 34f, x - 23f, y + 54f, paint)
        c.drawRect(x + 23f, y - 34f, x + 26f, y + 54f, paint)

        val idleFrames = SpriteBank.frames(context, d.id, "idle")
        val bmp = idleFrames.getOrNull((globalFrame / 8) % max(1, idleFrames.size))

        if (bmp != null) {
            paint.isFilterBitmap = false
            val half = 40f
            val r = android.graphics.RectF(x - half, y - 40f, x + half, y + 56f)
            c.save()
            if (!right) c.scale(-1f, 1f, x, 0f)
            c.drawBitmap(bmp, null, r, paint)
            c.restore()
        } else {
            drawLargeFighterProcedural(c, d, x, y)
        }
    }

    private fun drawLargeFighterProcedural(c: Canvas, d: FighterData, x: Float, y: Float) {
        val body = colorFor(d.id)
        val trim = trimFor(d.id)
        val outline = 0xFF0A0A0A.toInt()
        val skin = 0xFFE7B98B.toInt()

        // legs
        paint.color = outline
        c.drawRect(x - 11f, y + 29f, x - 2f, y + 50f, paint)
        c.drawRect(x + 2f, y + 29f, x + 11f, y + 50f, paint)
        paint.color = 0xFF151515.toInt()
        c.drawRect(x - 10f, y + 30f, x - 3f, y + 49f, paint)
        c.drawRect(x + 3f, y + 30f, x + 10f, y + 49f, paint)

        // body
        paint.color = outline
        c.drawRect(x - 17f, y - 2f, x + 17f, y + 32f, paint)
        paint.color = body
        c.drawRect(x - 15f, y, x + 15f, y + 30f, paint)
        c.drawRect(x - 19f, y + 2f, x - 17f, y + 12f, paint)
        c.drawRect(x + 17f, y + 2f, x + 19f, y + 12f, paint)

        // big chibi head, stepped-round
        paint.color = outline
        c.drawRect(x - 14f, y - 24f, x + 14f, y + 1f, paint)
        paint.color = skin
        c.drawRect(x - 8f, y - 23f, x + 8f, y - 20f, paint)
        c.drawRect(x - 12f, y - 20f, x + 12f, y - 17f, paint)
        c.drawRect(x - 13f, y - 17f, x + 13f, y - 4f, paint)
        c.drawRect(x - 10f, y - 4f, x + 10f, y - 1f, paint)

        // headwear / hair
        paint.color = trim
        c.drawRect(x - 14f, y - 26f, x + 14f, y - 19f, paint)

        // eyes + mouth
        paint.color = outline
        c.drawRect(x - 8f, y - 15f, x - 3f, y - 10f, paint)
        c.drawRect(x + 3f, y - 15f, x + 8f, y - 10f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        c.drawRect(x - 7f, y - 15f, x - 4f, y - 12f, paint)
        c.drawRect(x + 4f, y - 15f, x + 7f, y - 12f, paint)
        paint.color = 0xFF6D2E22.toInt()
        c.drawRect(x - 3f, y - 6f, x + 3f, y - 4f, paint)

        // tie accent
        paint.color = 0xFFE53935.toInt()
        c.drawRect(x - 2f, y + 2f, x + 2f, y + 22f, paint)
    }

    private fun control(c: Canvas, l: Float, t: Float, r: Float, b: Float, label: String, accent: Int = 0xFF888888.toInt(), dimmed: Boolean = false) {
        val rr = 5f
        paint.isAntiAlias = true

        paint.color = if (dimmed) 0x552A2A2A.toInt() else 0xB3141822.toInt()
        c.drawRoundRect(RectF(l, t, r, b), rr, rr, paint)

        paint.color = if (dimmed) 0x33888888 else (0x33000000 or (accent and 0x00FFFFFF))
        c.drawRoundRect(RectF(l, t, r, t + (b - t) * 0.4f), rr, rr, paint)

        paint.color = if (dimmed) 0x44888888 else accent
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        c.drawRoundRect(RectF(l, t, r, b), rr, rr, paint)
        paint.style = Paint.Style.FILL

        paint.isAntiAlias = false
        centerText(c, label, (t + b) / 2f + 3f, if (dimmed) 0xFF777777.toInt() else Color.WHITE, 6f, l, r)
    }

    private fun centerText(c: Canvas, s: String, y: Float, color: Int, size: Float, l: Float = 0f, r: Float = W) {
        text.color = color
        text.textSize = size
        text.textAlign = Paint.Align.CENTER
        c.drawText(s, (l + r) / 2f, y, text)
    }

    private fun smallText(c: Canvas, s: String, x: Float, y: Float, color: Int, size: Float) {
        text.color = color
        text.textSize = size
        text.textAlign = Paint.Align.LEFT
        c.drawText(s, x, y, text)
    }

    private fun updateGame() {
        val p = player ?: return
        val e = enemy ?: return

        // A brief freeze-frame on meaty hits reads as "impact" instead of
        // damage numbers just silently ticking down.
        if (hitstopTimer > 0) {
            hitstopTimer--
            updateEffects()
            return
        }

        frame++

        // Apply held movement controls continuously (fixes controls only firing on release).
        if (activePointers.containsValue("LEFT")) p.move(-1f)
        if (activePointers.containsValue("RIGHT")) p.move(1f)

        p.tick()
        e.tick()

        // Always turn to face each other, like a real fighting game.
        p.faceToward(e.x)
        e.faceToward(p.x)

        // CPU: picks a move every ~0.65s, weighted toward normals with
        // occasional specials/ultimate when they're actually off cooldown —
        // so the player has an opponent that fights back, not a punching bag.
        if (e.freezeTimer <= 0 && e.silenceTimer <= 0) {
            val dx = p.x - e.x

            // Reaction-delayed movement: only re-reads the player's position
            // every ~18 frames, and sometimes chooses to hold ground or back
            // off instead of always beelining in — reads as an opponent with
            // its own will, not a mirror of the player's stick.
            if (aiDecisionTimer <= 0) {
                aiDecisionTimer = 18
                aiMoveDir = when {
                    abs(dx) > 100f -> if (dx > 0) 1f else -1f // too far: close in
                    abs(dx) < 40f -> if (Random.nextInt(100) < 25) (if (dx > 0) -1f else 1f) else 0f // too close: sometimes back off
                    else -> when (Random.nextInt(100)) {
                        in 0..59 -> if (dx > 0) 1f else -1f
                        in 60..74 -> if (dx > 0) -1f else 1f
                        else -> 0f
                    }
                }
            }
            aiDecisionTimer--
            if (aiMoveDir != 0f) e.move(aiMoveDir)

            if (frame % 40 == 0) {
                val roll = Random.nextInt(100)
                val pB = p.hp; val eB = e.hp
                when {
                    roll < 8 && e.ultimateCooldown <= 0 -> { useUltimate(e, p); afterAction(e, p, pB, eB, 3) }
                    roll < 30 && e.skill2Cooldown <= 0 -> { useSkill2(e, p); afterAction(e, p, pB, eB, 2) }
                    roll < 62 && e.skill1Cooldown <= 0 -> { useSkill1(e, p); afterAction(e, p, pB, eB, 2) }
                    else -> cpuBasicAttack()
                }
            }
            if (frame % 90 == 0 && Random.nextBoolean()) e.jump()
        }

        if (attackCooldown > 0) attackCooldown--

        if (messageTimer > 0) messageTimer--
        if (frame % 60 == 0 && timer > 0) timer--

        updateEffects()

        if (timer <= 0 || p.hp <= 0 || e.hp <= 0) {
            winner = when {
                p.hp > e.hp -> "${p.data.name} WINS"
                e.hp > p.hp -> "${e.data.name} WINS"
                else -> "DRAW"
            }
            screen = Screen.RESULT
        }
    }

    private fun cpuBasicAttack() {
        val p = player ?: return
        val e = enemy ?: return
        val dx = p.x - e.x
        if (abs(dx) < 55f) {
            e.beginAttackPose(10)
            val pBefore = p.hp
            p.takeDamage(e.data.attack, if (dx > 0) 2f else -2f)
            showMessage("HIT -${e.data.attack}")
            if (p.hp < pBefore) {
                spawnBurst(p.x, p.y + 4f, 0xFFFF8A65.toInt(), 8, 1.8f)
                spawnFloatText(p.x, p.y - 30f, "-${pBefore - p.hp}", 0xFFFF5252.toInt())
                triggerShake(2.2f, 7)
            }
        }
    }

    private fun playerAttack() {
        val p = player ?: return
        val e = enemy ?: return
        if (p.freezeTimer > 0 || p.silenceTimer > 0 || attackCooldown > 0) return

        attackCooldown = 14
        p.beginAttackPose(10)
        val dx = e.x - p.x
        if (abs(dx) < 50f) {
            val damage = if (p.buffTimer > 0) p.data.attack + 6 else p.data.attack
            e.takeDamage(damage, if (dx > 0) 3.2f else -3.2f)
            showMessage("HIT -$damage")
        }
        lastAttackFrame = frame
    }

    /** Special move: hits noticeably harder than a jab but needs real
     *  positioning and a ~1.3s cooldown, so it can't be tapped as fast as a
     *  finger moves the way it used to. attacker/defender are generic so
     *  both the player and the CPU share the exact same move logic. */
    private fun useSkill1(attacker: Fighter, defender: Fighter) {
        if (attacker.silenceTimer > 0 || attacker.freezeTimer > 0 || attacker.skill1Cooldown > 0) return
        attacker.skill1Cooldown = 80
        attacker.beginAttackPose(16)
        val p = attacker
        val e = defender

        when (p.data.id) {
            FighterId.PRORORO -> {
                if (abs(e.x - p.x) < 70f) e.takeDamage(16, if (e.x > p.x) 5f else -5f)
                showMessage("DOR! DOR! DOR!")
            }
            FighterId.MEGACHAN -> {
                if (abs(e.x - p.x) < 70f) {
                    e.takeDamage(12)
                    e.silenceTimer = 35
                }
                showMessage("MONOLOG PANJANG")
            }
            FighterId.MR_ETANOL -> {
                if (abs(e.x - p.x) < 65f) e.takeDamage(16, if (e.x > p.x) 6f else -6f)
                showMessage("TENDANGAN BOTOL")
            }
            FighterId.MR_YOUTUBE -> {
                if (abs(e.x - p.x) < 120f) e.takeDamage(10)
                showMessage("REC & UPLOAD")
            }
            FighterId.FUFU -> {
                p.velocityX += if (e.x > p.x) 6f else -6f
                if (abs(e.x - p.x) < 75f) e.takeDamage(15, if (e.x > p.x) 5f else -5f)
                showMessage("COMBO ANAK MUDA")
            }
            FighterId.RAJA_SOLO -> {
                if (abs(e.x - p.x) < 65f) e.takeDamage(17, if (e.x > p.x) 6f else -6f)
                showMessage("SABETAN KERATON")
            }
            FighterId.PURBANKYA -> {
                if (abs(e.x - p.x) < 65f) e.takeDamage(16, if (e.x > p.x) 6f else -6f)
                showMessage("PUKULAN BATU")
            }
        }
    }

    /** Bigger special: more damage/utility than skill 1, longer ~2.3s cooldown. */
    private fun useSkill2(attacker: Fighter, defender: Fighter) {
        if (attacker.silenceTimer > 0 || attacker.freezeTimer > 0 || attacker.skill2Cooldown > 0) return
        attacker.skill2Cooldown = 140
        attacker.beginAttackPose(18)
        val p = attacker
        val e = defender

        when (p.data.id) {
            FighterId.PRORORO -> {
                if (abs(e.x - p.x) < 70f) {
                    p.hp = min(p.data.hp, p.hp + 8)
                    e.takeDamage(14)
                }
                showMessage("KOPI PAGI")
            }
            FighterId.MEGACHAN -> {
                if (abs(e.x - p.x) < 70f) {
                    e.takeDamage(16)
                    e.slowTimer = 90
                }
                showMessage("KONGRES BESAR")
            }
            FighterId.MR_ETANOL -> {
                if (abs(e.x - p.x) < 70f) {
                    e.takeDamage(16, if (e.x > p.x) 7f else -7f)
                    e.slowTimer = 70
                }
                showMessage("OPLOSAN COMBO")
            }
            FighterId.MR_YOUTUBE -> {
                if (abs(e.x - p.x) < 110f) {
                    e.takeDamage(8)
                    e.blindTimer = 80
                }
                showMessage("CLICKBAIT SLAM")
            }
            FighterId.FUFU -> {
                if (abs(e.x - p.x) < 70f) e.takeDamage(16, if (e.x > p.x) 8f else -8f)
                showMessage("SERANGAN CEPAT")
            }
            FighterId.RAJA_SOLO -> {
                p.invulnerableTimer = 70
                p.hp = min(p.data.hp, p.hp + 22)
                showMessage("MODE TENANG")
            }
            FighterId.PURBANKYA -> {
                if (abs(e.x - p.x) < 70f) {
                    p.hp = min(p.data.hp, p.hp + 10)
                    e.takeDamage(14)
                }
                showMessage("TARIAN PURBA")
            }
        }
    }

    /** The big finisher: a real chunk of HP and a guaranteed hit (that's what
     *  makes it read as an "ultimate"), but a long ~12s cooldown means it's a
     *  rare comeback tool, not something you open every exchange with. */
    private fun useUltimate(attacker: Fighter, defender: Fighter) {
        if (attacker.silenceTimer > 0 || attacker.freezeTimer > 0 || attacker.ultimateCooldown > 0) return

        attacker.ultimateCooldown = 720
        attacker.beginAttackPose(26)
        val p = attacker
        val e = defender

        when (p.data.id) {
            FighterId.PRORORO -> {
                e.takeDamage(30, if (e.x > p.x) 10f else -10f)
                p.invulnerableTimer = 35
                showMessage("PERINTAH RAHASIA")
            }
            FighterId.MEGACHAN -> {
                e.takeDamage(26)
                e.silenceTimer = 100
                showMessage("PETUGAS PARTAI")
            }
            FighterId.MR_ETANOL -> {
                e.takeDamage(28, if (e.x > p.x) 11f else -11f)
                showMessage("PESTA MALAM")
            }
            FighterId.MR_YOUTUBE -> {
                e.takeDamage(24)
                e.blindTimer = 110
                showMessage("VIRAL 1 MILIAR VIEW")
            }
            FighterId.FUFU -> {
                e.takeDamage(28, if (e.x > p.x) 12f else -12f)
                p.velocityX += if (e.x > p.x) 8f else -8f
                showMessage("SEMANGAT GENERASI")
            }
            FighterId.RAJA_SOLO -> {
                e.takeDamage(22)
                e.silenceTimer = 120
                showMessage("TITAH RAJA")
            }
            FighterId.PURBANKYA -> {
                p.invulnerableTimer = 80
                e.takeDamage(22, if (e.x > p.x) 9f else -9f)
                showMessage("PERISAI PURBA")
            }
        }
    }

    private fun showMessage(s: String) {
        message = s
        messageTimer = 65
    }

    // pointerId -> control zone currently held down by that finger
    private val activePointers = mutableMapOf<Int, String>()

    private fun zoneAt(x: Float, y: Float): String? = when {
        x in 10f..42f && y in 146f..176f -> "LEFT"
        x in 46f..78f && y in 146f..176f -> "RIGHT"
        x in 82f..114f && y in 146f..176f -> "JUMP"
        x in 206f..244f && y in 146f..176f -> "ATK"
        x in 248f..276f && y in 146f..176f -> "S1"
        x in 280f..308f && y in 146f..176f -> "S2"
        x in 210f..310f && y in 124f..140f -> "ULT"
        x in 146f..174f && y in 124f..140f -> "PAUSE"
        else -> null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pointerId = event.getPointerId(idx)
                val gx = ((event.getX(idx) - ox) / scale).coerceIn(0f, W)
                val gy = ((event.getY(idx) - oy) / scale).coerceIn(0f, H)
                handleTouchDown(gx, gy, pointerId)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val pointerId = event.getPointerId(idx)
                val gx = ((event.getX(idx) - ox) / scale).coerceIn(0f, W)
                val gy = ((event.getY(idx) - oy) / scale).coerceIn(0f, H)
                handleTouchUp(gx, gy, pointerId)
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointers.clear()
            }
        }
        return true
    }

    private fun handleTouchDown(gx: Float, gy: Float, pointerId: Int) {
        if (screen != Screen.BATTLE || paused) return
        val zone = zoneAt(gx, gy) ?: return
        activePointers[pointerId] = zone
        // Movement is applied continuously each frame in updateGame() while held.
        // One-shot actions fire immediately on press for responsive feel.
        when (zone) {
            "JUMP" -> player?.jump()
            "ATK" -> {
                val p = player ?: return; val e = enemy ?: return
                val pB = p.hp; val eB = e.hp
                playerAttack(); afterAction(p, e, eB, pB, 1)
            }
            "S1" -> {
                val p = player ?: return; val e = enemy ?: return
                val pB = p.hp; val eB = e.hp
                useSkill1(p, e); afterAction(p, e, eB, pB, 2)
            }
            "S2" -> {
                val p = player ?: return; val e = enemy ?: return
                val pB = p.hp; val eB = e.hp
                useSkill2(p, e); afterAction(p, e, eB, pB, 2)
            }
            "ULT" -> {
                val p = player ?: return; val e = enemy ?: return
                val pB = p.hp; val eB = e.hp
                useUltimate(p, e); afterAction(p, e, eB, pB, 3)
            }
            "PAUSE" -> paused = true
        }
    }

    private fun handleTouchUp(gx: Float, gy: Float, pointerId: Int) {
        activePointers.remove(pointerId)
        when (screen) {
            Screen.LOADING -> {
                screen = Screen.TITLE
            }
            Screen.TITLE -> {
                when {
                    gy in 88f..109f -> screen = Screen.SELECT
                    gy in 114f..135f -> screen = Screen.SELECT
                    gy in 140f..161f -> screen = Screen.ABOUT
                }
            }
            Screen.ABOUT -> {
                screen = Screen.TITLE
            }
            Screen.SELECT -> {
                val col = ((gx - 98f) / 30f).toInt()
                val row = ((gy - 28f) / 38f).toInt()
                val idx = row * 4 + col
                when {
                    gx in 98f..218f && gy in 28f..104f && idx in Fighters.all.indices -> selected = idx
                    gx in 130f..190f && gy in 158f..176f -> startBattle()
                    gx < 96f -> selected = (selected + 1) % Fighters.all.size
                    gx > 224f -> enemySelected = (enemySelected + 1) % Fighters.all.size
                }
            }
            Screen.RESULT -> startBattle()
            Screen.BATTLE -> {
                if (paused) {
                    when {
                        gx in 106f..214f && gy in 78f..98f -> paused = false
                        gx in 106f..214f && gy in 104f..124f -> {
                            paused = false
                            screen = Screen.TITLE
                        }
                    }
                }
                // else: zone actions already handled on press
            }
        }
    }

    private fun startBattle() {
        val pData = Fighters.all[selected]
        val eData = Fighters.all[enemySelected]
        player = Fighter(pData, 82f, GROUND_Y, true)
        enemy = Fighter(eData, 238f, GROUND_Y, false)
        timer = 99
        frame = 0
        winner = ""
        message = "FIGHT!"
        messageTimer = 60
        arenaChoice = Random.nextInt(4)
        aiMoveDir = 0f
        aiDecisionTimer = 0
        paused = false
        activePointers.clear()
        sparks.clear()
        floatTexts.clear()
        shakeTimer = 0
        shakeMag = 0f
        flashTimer = 0
        hitstopTimer = 0
        screen = Screen.BATTLE
    }
}
