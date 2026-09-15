package com.example.milklog.measure

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import com.example.milklog.model.BottleProfile

/** 实时测量引擎：把相机画面变成"稳定的奶量读数"。 */
class MeasurementEngine(val camera: CameraController) {

    var detection by mutableStateOf<LiquidDetection?>(null)
        private set

    var frameSize by mutableStateOf(IntSize(1080, 1920))
        private set

    var isStable by mutableStateOf(false)
        private set

    var settledVolume by mutableStateOf<Double?>(null)
        private set

    var latestImage by mutableStateOf<Bitmap?>(null)
        private set

    var torchOn by mutableStateOf(false)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    @Volatile
    private var profileStorage: BottleProfile? = null

    @Volatile
    private var bandStorage: Pair<Double, Double>? = null

    private val history = ArrayList<Double>()
    private var stableValue: Double? = null
    private var lastValidTime = 0L
    private var lastImageTime = 0L

    init {
        camera.onFrame = { proxy -> process(proxy) }
    }

    // MARK: - 输入

    var profile: BottleProfile?
        get() = profileStorage
        set(value) {
            profileStorage = value
            reset()
        }

    /** 清除临时取样区域，改回使用奶瓶自己的设置 */
    fun clearBand() {
        bandStorage = null
    }

    fun setBand(left: Double, right: Double) {
        bandStorage = Pair(left, right)
        reset()
    }

    fun setTorch(on: Boolean) {
        torchOn = on
        camera.setTorch(on)
    }

    fun toggleTorch() {
        setTorch(!torchOn)
    }

    fun reset() {
        mainHandler.post {
            history.clear()
            stableValue = null
            settledVolume = null
            isStable = false
            detection = null
        }
    }

    // MARK: - 采集

    fun start() {
        camera.permissionDenied = false
    }

    private fun currentBand(): Pair<Double, Double> {
        val band = bandStorage
        if (band != null) return band
        val p = profileStorage
        return Pair(p?.bandLeft ?: 0.32, p?.bandRight ?: 0.68)
    }

    private fun process(proxy: ImageProxy) {
        val profile = profileStorage
        val band = currentBand()
        val result = try {
            LiquidDetector.detect(proxy, profile, band.first, band.second)
        } catch (t: Throwable) {
            null
        }

        var image: Bitmap? = null
        val now = System.currentTimeMillis()
        if (now - lastImageTime > 1000) {
            lastImageTime = now
            image = try {
                Yuv.toBitmap(proxy, 720)
            } catch (t: Throwable) {
                null
            }
        }

        var rotation = proxy.imageInfo.rotationDegrees % 360
        if (rotation < 0) rotation += 360
        val swapped = rotation == 90 || rotation == 270
        val size = if (swapped) {
            IntSize(proxy.height, proxy.width)
        } else {
            IntSize(proxy.width, proxy.height)
        }

        mainHandler.post {
            if (frameSize != size) frameSize = size
            if (image != null) latestImage = image
            if (result != null) apply(result) else applyMiss()
        }
    }

    // MARK: - 平滑读数

    private fun apply(result: LiquidDetection) {
        if (!result.found) {
            applyMiss()
            return
        }
        synchronized(lock) {
            history.add(result.surfaceY)
            while (history.size > 6) history.removeAt(0)
            val sorted = history.sorted()
            val median = sorted[sorted.size / 2]
            val spread = sorted[sorted.size - 1] - sorted[0]
            val volume = profileStorage?.volumeFor(median)

            isStable = history.size >= 4 && spread < 0.015 && result.confidence > 0.2

            if (isStable && volume != null) {
                lastValidTime = System.currentTimeMillis()
                val previous = stableValue
                stableValue = if (previous == null) volume else previous * 0.6 + volume * 0.4
                settledVolume = stableValue
            }
            detection = result.copy(surfaceY = median, volumeML = volume)
        }
    }

    private fun applyMiss() {
        synchronized(lock) {
            isStable = false
            if (System.currentTimeMillis() - lastValidTime > 2000) {
                stableValue = null
                settledVolume = null
                history.clear()
                detection = LiquidDetection(found = false)
            } else {
                val current = detection
                detection = if (current != null) current.copy(found = false) else LiquidDetection(found = false)
            }
        }
    }
}
