package com.nusantarafighter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Loads and caches character animation-frame bitmaps and arena background
 * bitmaps from assets/. Falls back gracefully (returns null / empty list)
 * when an asset is missing, so callers can fall back to procedural drawing.
 */
object SpriteBank {

    // key: "FIGHTERID/state" -> ordered list of frames
    private val animCache = mutableMapOf<String, List<Bitmap>>()
    private val missing = mutableSetOf<String>()
    private val bgCache = mutableMapOf<String, Bitmap>()

    fun frames(context: Context, id: FighterId, state: String): List<Bitmap> {
        val key = "${id.name}/$state"
        animCache[key]?.let { return it }
        if (key in missing) return emptyList()

        val dir = "characters/${id.name}/$state"
        val names = try {
            context.assets.list(dir)?.filter { it.endsWith(".png") }?.sorted()
        } catch (e: Exception) {
            null
        }

        if (names.isNullOrEmpty()) {
            missing.add(key)
            return emptyList()
        }

        val bitmaps = names.mapNotNull { name ->
            try {
                context.assets.open("$dir/$name").use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        }

        if (bitmaps.isEmpty()) {
            missing.add(key)
            return emptyList()
        }

        animCache[key] = bitmaps
        return bitmaps
    }

    /** True if this fighter has at least an idle animation available. */
    fun hasSprites(context: Context, id: FighterId): Boolean =
        frames(context, id, "idle").isNotEmpty()

    fun background(context: Context, fileName: String): Bitmap? {
        bgCache[fileName]?.let { return it }
        return try {
            context.assets.open("backgrounds/$fileName").use { BitmapFactory.decodeStream(it) }
                .also { bgCache[fileName] = it }
        } catch (e: Exception) {
            null
        }
    }
}
