package xyz.plcliangpicup.phigrosscore.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
import kotlin.math.floor

/** Texture2D sampling serialized in the 4.0.1 APK: Point, without mipmaps. */
internal fun practiceNoiseTextureShader(bitmap: Bitmap, mode: Shader.TileMode) =
    BitmapShader(bitmap, mode, mode).apply {
        if (Build.VERSION.SDK_INT >= 33) setFilterMode(BitmapShader.FILTER_MODE_NEAREST)
    }

/** Nearest texel with Unity Mirror wrapping, including negative animated UVs. */
internal fun practiceNoiseMirrorTexel(uv: Float, size: Int): Int {
    val index = Math.floorMod(floor(uv * size).toInt(), size * 2)
    return if (index < size) index else size * 2 - 1 - index
}
