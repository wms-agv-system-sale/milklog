package com.example.milklog.measure

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * 把相机的一帧（YUV_420_888）转成 Bitmap。
 * 自己算而不依赖系统接口，避免不同机型上的兼容问题。
 * rotationDegrees 会把画面转正，缩放由 maxDimension 控制。
 */
object Yuv {

    fun toBitmap(image: ImageProxy, maxDimension: Int = 720): Bitmap? {
        val planes = image.planes
        if (planes.size < 3) return null
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val rawW = image.width
        val rawH = image.height
        if (rawW <= 0 || rawH <= 0) return null

        var rotation = image.imageInfo.rotationDegrees % 360
        if (rotation < 0) rotation += 360
        val swapped = rotation == 90 || rotation == 270
        val outW = if (swapped) rawH else rawW
        val outH = if (swapped) rawW else rawH

        val step = Math.max(1, Math.max(outW, outH) / maxDimension)
        val dstW = Math.max(1, outW / step)
        val dstH = Math.max(1, outH / step)

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val yLimit = yBuf.limit()
        val uLimit = uBuf.limit()
        val vLimit = vBuf.limit()

        val pixels = IntArray(dstW * dstH)

        var oy = 0
        while (oy < dstH) {
            var ox = 0
            while (ox < dstW) {
                val dx = ox * step
                val dy = oy * step
                var rx: Int
                var ry: Int
                when (rotation) {
                    90 -> { rx = dy; ry = rawH - 1 - dx }
                    180 -> { rx = rawW - 1 - dx; ry = rawH - 1 - dy }
                    270 -> { rx = rawW - 1 - dy; ry = dx }
                    else -> { rx = dx; ry = dy }
                }
                var color = 0xFF000000.toInt()
                if (rx in 0 until rawW && ry in 0 until rawH) {
                    val yIndex = ry * yRowStride + rx * yPixelStride
                    if (yIndex in 0 until yLimit) {
                        val yv = yBuf.get(yIndex).toInt() and 0xFF
                        val cx = rx / 2
                        val cy = ry / 2
                        val uIndex = cy * uRowStride + cx * uPixelStride
                        val vIndex = cy * vRowStride + cx * vPixelStride
                        if (uIndex in 0 until uLimit && vIndex in 0 until vLimit) {
                            val uv = (uBuf.get(uIndex).toInt() and 0xFF) - 128
                            val vv = (vBuf.get(vIndex).toInt() and 0xFF) - 128
                            val r = yv + 1.402 * vv
                            val g = yv - 0.344136 * uv - 0.714136 * vv
                            val b = yv + 1.772 * uv
                            color = 0xFF000000.toInt() or
                                (clamp(r) shl 16) or
                                (clamp(g) shl 8) or
                                clamp(b)
                        }
                    }
                }
                pixels[oy * dstW + ox] = color
                ox++
            }
            oy++
        }

        return try {
            Bitmap.createBitmap(pixels, dstW, dstH, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
    }

    private fun clamp(value: Double): Int {
        val v = value.toInt()
        return if (v < 0) 0 else if (v > 255) 255 else v
    }
}
