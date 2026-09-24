package com.nusantarafighter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** Ground line (feet contact) shared with GameView's arena/dock layout. */
const val GROUND_Y = 108f

class Fighter(
    val data: FighterData,
    var x: Float,
    var y: Float,
    startFacingRight: Boolean
) {
    var hp = data.hp
    var velocityX = 0f
    var velocityY = 0f
    var grounded = true
    var attackTimer = 0
    var hurtTimer = 0
    var slowTimer = 0
    var freezeTimer = 0
    var blindTimer = 0
    var invulnerableTimer = 0
    var silenceTimer = 0
    var buffTimer = 0
    var skill1Cooldown = 0
    var skill2Cooldown = 0
    var ultimateCooldown = 0

    /** Fighters always turn to face their opponent, like a real fighting game. */
    var facingRight: Boolean = startFacingRight

    /** Frame count of whatever move is currently playing, used to time the
     *  lunge/telegraph so short jabs and long ultimates both read clearly. */
    var lastAttackFrames = 10

    private var animFrame = 0

    val width = 26f
    val height = 48f

    fun reset(nx: Float, ny: Float) {
        x = nx
        y = ny
        hp = data.hp
        velocityX = 0f
        velocityY = 0f
        grounded = true
        attackTimer = 0
        hurtTimer = 0
        slowTimer = 0
        freezeTimer = 0
        blindTimer = 0
        invulnerableTimer = 0
        silenceTimer = 0
        buffTimer = 0
        skill1Cooldown = 0
        skill2Cooldown = 0
        ultimateCooldown = 0
    }

    fun tick() {
        animFrame++
        if (attackTimer > 0) attackTimer--
        if (hurtTimer > 0) hurtTimer--
        if (slowTimer > 0) slowTimer--
        if (freezeTimer > 0) freezeTimer--
        if (blindTimer > 0) blindTimer--
        if (invulnerableTimer > 0) invulnerableTimer--
        if (silenceTimer > 0) silenceTimer--
        if (buffTimer > 0) buffTimer--
        if (skill1Cooldown > 0) skill1Cooldown--
        if (skill2Cooldown > 0) skill2Cooldown--
        if (ultimateCooldown > 0) ultimateCooldown--

        velocityY += 0.65f
        y += velocityY
        if (y >= GROUND_Y) {
            y = GROUND_Y
            velocityY = 0f
            grounded = true
        } else {
            grounded = false
        }

        val slow = if (slowTimer > 0) 0.45f else 1f
        x += velocityX * slow
        velocityX *= 0.78f
        x = x.coerceIn(18f, 302f)
    }

    /** Turn to keep facing the opponent, the way SF2-style fighters do. Locked
     *  mid-attack so you can't spin around inside your own swing. */
    fun faceToward(opponentX: Float) {
        if (attackTimer > 0 || freezeTimer > 0) return
        facingRight = opponentX >= x
    }

    fun jump() {
        if (freezeTimer > 0 || !grounded) return
        velocityY = -8.8f
        grounded = false
    }

    fun move(dir: Float) {
        if (freezeTimer > 0) return
        velocityX += dir * data.speed * 0.30f
        velocityX = velocityX.coerceIn(-2.8f, 2.8f)
    }

    /** Puts the fighter into its attack pose for the given number of frames.
     *  Basic attacks use a short, snappy window; skills/ultimates use a
     *  longer one so they visibly read as a bigger move. */
    fun beginAttackPose(frames: Int) {
        attackTimer = frames
        lastAttackFrames = frames
    }

    fun takeDamage(amount: Int, knockback: Float = 0f) {
        if (invulnerableTimer > 0) return
        hp = max(0, hp - amount)
        hurtTimer = 10
        velocityX += knockback
    }

    fun draw(canvas: Canvas, p: Paint, context: Context) {
        val state = when {
            hurtTimer > 0 -> "hit_reaction"
            attackTimer > 0 -> "basic_attack"
            !grounded -> "walk"
            abs(velocityX) > 0.35f -> "walk"
            else -> "idle"
        }

        var frames = SpriteBank.frames(context, data.id, state)
        if (frames.isEmpty()) frames = SpriteBank.frames(context, data.id, "idle")

        // Small forward lunge on attack + idle breathing bob + air squash/stretch:
        // cheap juice that reads as "alive" instead of a static cardboard cutout.
        val lunge = if (attackTimer > 0) {
            val windUp = (attackTimer.toFloat() / max(1, lastAttackFrames)).coerceIn(0f, 1f)
            6f * (1f - windUp) * (if (facingRight) 1f else -1f)
        } else 0f
        val bob = if (state == "idle" && grounded) sin(animFrame / 14f) * 1.3f else 0f
        val stretch = when {
            !grounded && velocityY < 0f -> 1.06f
            !grounded && velocityY > 0f -> 0.95f
            else -> 1f
        }

        val sx = x + lunge
        val sy = y - bob

        if (frames.isNotEmpty()) {
            val speedDivisor = if (state == "basic_attack") max(2, lastAttackFrames / 5) else 6
            val bmp = frames[(animFrame / speedDivisor) % frames.size]
            p.isFilterBitmap = false
            // Bigger sprite, feet locked to the ground seam (GROUND_Y) instead of
            // floating 48px below it — that mismatch was the "sinking" look.
            val halfW = 46f
            val baseBottom = sy + 4f
            val baseTop = baseBottom - 118f
            val mid = (baseTop + baseBottom) / 2f
            val halfH = (baseBottom - baseTop) / 2f * stretch
            val destRect = RectF(sx - halfW, mid - halfH, sx + halfW, mid + halfH)

            canvas.save()
            if (!facingRight) canvas.scale(-1f, 1f, sx, 0f)
            canvas.drawBitmap(bmp, null, destRect, p)
            canvas.restore()

            // shadow, squashed a bit when the fighter is airborne+high
            p.color = 0x55000000
            val shadowScale = if (!grounded) (1f - (GROUND_Y - y).coerceIn(0f, 40f) / 60f) else 1f
            canvas.drawRect(x - 14 * shadowScale, GROUND_Y + 2, x + 14 * shadowScale, GROUND_Y + 6, p)

            if (hurtTimer > 0) {
                p.color = 0x66FFFFFF
                canvas.drawRect(sx - halfW, baseTop, sx + halfW, baseBottom, p)
            }

            if (attackTimer > 0) drawAttackTelegraph(canvas, p, sx, sy)
        } else {
            drawProcedural(canvas, p)
        }
    }

    /** Motion lines + a bright arc in front of the fighter so every attack —
     *  basic or skill — has a clear, readable "something happened here" beat,
     *  even though the sprite sheet has only one generic attack pose. */
    private fun drawAttackTelegraph(canvas: Canvas, p: Paint, sx: Float, sy: Float) {
        val progress = 1f - (attackTimer.toFloat() / max(1, lastAttackFrames))
        if (progress < 0.15f || progress > 0.75f) return // only flash during the "active" window

        val dir = if (facingRight) 1f else -1f
        val reach = 20f + 10f * progress
        val fx = sx + dir * reach
        val fy = sy + 6f

        p.isAntiAlias = true
        val alpha = (255 * (1f - progress)).toInt().coerceIn(60, 220)
        p.strokeWidth = 2.2f
        p.color = (alpha shl 24) or 0xFFF6D6
        for (i in 0..2) {
            val spread = (i - 1) * 7f
            canvas.drawLine(sx + dir * 10f, sy + spread, fx, fy + spread * 0.4f, p)
        }
        p.color = (alpha shl 24) or 0xFFFFFF
        canvas.drawCircle(fx, fy, 3.5f, p)
        p.isAntiAlias = false
    }

    private fun drawProcedural(canvas: Canvas, p: Paint) {
        val sx = x
        val sy = y

        // Scale the whole procedural rig up and re-anchor its feet (originally
        // drawn around sy+46..+50) onto the ground seam near sy, instead of
        // floating ~48px below it.
        val rigScale = 1.9f
        val oldFootLine = sy + 48f
        val newFootLine = sy + 4f
        canvas.save()
        canvas.translate(sx, newFootLine)
        canvas.scale(rigScale, rigScale)
        canvas.translate(-sx, -oldFootLine)

        p.isAntiAlias = false
        val body = GameView.colorFor(data.id)
        val trim = GameView.trimFor(data.id)
        val outline = 0xFF0A0A0A.toInt()
        val dark = 0xFF151515.toInt()
        val skin = 0xFFE7B98B.toInt()
        val cheek = 0xFFE79A85.toInt()
        val accent = 0xFFE53935.toInt()

        // shadow
        p.color = 0x55000000
        canvas.drawRect(sx - 14, sy + 46, sx + 14, sy + 50, p)

        // legs, outlined
        p.color = outline
        canvas.drawRect(sx - 11, sy + 26, sx - 2, sy + 46, p)
        canvas.drawRect(sx + 2, sy + 26, sx + 11, sy + 46, p)
        p.color = dark
        canvas.drawRect(sx - 10, sy + 27, sx - 3, sy + 45, p)
        canvas.drawRect(sx + 3, sy + 27, sx + 10, sy + 45, p)

        // body, outlined with stepped shoulders (chibi silhouette)
        p.color = outline
        canvas.drawRect(sx - 14, sy + 11, sx + 14, sy + 32, p)
        p.color = body
        canvas.drawRect(sx - 13, sy + 13, sx + 13, sy + 31, p)
        canvas.drawRect(sx - 15, sy + 15, sx - 13, sy + 22, p)
        canvas.drawRect(sx + 13, sy + 15, sx + 15, sy + 22, p)

        // head: big chibi head, stepped rows to fake a round outline
        p.color = outline
        canvas.drawRect(sx - 12, sy - 6, sx + 12, sy + 16, p)
        p.color = skin
        canvas.drawRect(sx - 7, sy - 5, sx + 7, sy - 3, p)
        canvas.drawRect(sx - 10, sy - 3, sx + 10, sy - 1, p)
        canvas.drawRect(sx - 11, sy - 1, sx + 11, sy + 12, p)
        canvas.drawRect(sx - 9, sy + 12, sx + 9, sy + 14, p)
        canvas.drawRect(sx - 6, sy + 14, sx + 6, sy + 15, p)

        // cheeks (caricature warmth)
        p.color = cheek
        canvas.drawRect(sx - 10, sy + 7, sx - 7, sy + 9, p)
        canvas.drawRect(sx + 7, sy + 7, sx + 10, sy + 9, p)

        // hair / headwear in a per-fighter trim color
        p.color = trim
        canvas.drawRect(sx - 12, sy - 8, sx + 12, sy - 1, p)

        // eyes
        p.color = outline
        canvas.drawRect(sx - 7, sy + 3, sx - 3, sy + 7, p)
        canvas.drawRect(sx + 3, sy + 3, sx + 7, sy + 7, p)
        p.color = 0xFFFFFFFF.toInt()
        canvas.drawRect(sx - 6, sy + 3, sx - 4, sy + 5, p)
        canvas.drawRect(sx + 4, sy + 3, sx + 6, sy + 5, p)

        // simple mouth
        p.color = 0xFF6D2E22.toInt()
        canvas.drawRect(sx - 3, sy + 10, sx + 3, sy + 11, p)

        // red scarf/tie accent
        p.color = accent
        canvas.drawRect(sx - 2, sy + 14, sx + 2, sy + 25, p)

        if (attackTimer > 0) {
            p.color = 0xFFFFD54F.toInt()
            val fx = if (facingRight) sx + 15 else sx - 25
            canvas.drawRect(fx, sy + 10, fx + 10, sy + 15, p)
            canvas.drawRect(fx + 5, sy + 5, fx + 15, sy + 10, p)
        }

        if (hurtTimer > 0) {
            p.color = 0x66FFFFFF
            canvas.drawRect(sx - 16, sy - 8, sx + 16, sy + 50, p)
        }

        canvas.restore()
    }
}
