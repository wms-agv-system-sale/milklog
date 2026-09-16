package com.example.milklog.measure

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize

/**
 * 实时识别引擎：把相机画面变成"稳定的奶量读数"。
 *
 * 流程：读奶瓶上印的刻度数字 -> 求出"画面高度 <-> 奶量"的关系
 * -> 找液面 -> 换算成奶量（取整到 10 的整数倍）。
 */
class MeasurementEngine(val camera: CameraController) {

    var detection by mutableStateOf<LiquidDetection?>(null)
        private set

    var frameSize by mutableStateOf(IntSize(1080, 1920))
        private set

    var isStable by mutableStateOf(false)
        private set

    /** 当前读数（毫升，已经取整到 10 的整数倍）；识别不出来时为 null */
    var volumeML by mutableStateOf<Double?>(null)
        private set

    /** 最新一帧读到的刻度数字 */
    var marks by mutableStateOf<List<ScaleMark>>(emptyList())
        private set

    /** 真正参与换算的刻度数字 */
    var fittedMarks by mutableStateOf<List<ScaleMark>>(emptyList())
        private set

    var latestImage by mutableStateOf<Bitmap?>(null)
        private set

    var torchOn by mutableStateOf(false)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var fit: ScaleFit? = null
    private var fitTime = 0L
    private val history = ArrayList<Double>()
    private var lastValidTime = 0L
    private var lastImageTime = 0L

    /** 供相机线程读取的刻度快照 */
    @Volatile
    private var markSnapshot: List<ScaleMark> = emptyList()

    init {
        camera.onFrame = { proxy -> process(proxy) }
    }

    // MARK: - 开关

    fun setTorch(on: Boolean) {
        torchOn = on
        camera.setTorch(on)
    }

    fun toggleTorch() {
        setTorch(!torchOn)
    }

    fun reset() {
        mainHandler.post {
            synchronized(lock) {
                history.clear()
                isStable = false
                detection = null
                volumeML = null
                marks = emptyList()
                fittedMarks = emptyList()
                markSnapshot = emptyList()
                fit = null
                fitTime = 0L
                lastValidTime = 0L
            }
            LiquidDetector.reset()
        }
    }

    fun start() {
        camera.permissionDenied = false
    }

    // MARK: - 采集

    private fun process(proxy: ImageProxy) {
        val range = currentSearchRange()
        val result = try {
            LiquidDetector.detect(proxy, range.first, range.second)
        } catch (t: Throwable) {
            null
        }

        var rotation = proxy.imageInfo.rotationDegrees % 360
        if (rotation < 0) rotation += 360
        val swapped = rotation == 90 || rotation == 270
        val size = if (swapped) {
            IntSize(proxy.height, proxy.width)
        } else {
            IntSize(proxy.width, proxy.height)
        }

        val now = System.currentTimeMillis()
        var image: Bitmap? = null
        if (now - lastImageTime > 900) {
            lastImageTime = now
            image = try {
                Yuv.toBitmap(proxy, 1080)
            } catch (t: Throwable) {
                null
            }
        }

        mainHandler.post {
            if (frameSize != size) frameSize = size
            if (result != null) apply(result) else applyMiss()
        }

        val picture = image
        if (picture != null) {
            mainHandler.post { latestImage = picture }
            ScaleOcr.read(picture) { list -> handleMarks(list) }
        }
    }

    /** 液面只可能出现在刻度数字的范围里，这样可以避开背景里的杂物 */
    private fun currentSearchRange(): Pair<Double, Double> {
        val list = markSnapshot
        if (list.isEmpty()) return Pair(0.08, 0.95)
        var top = 1.0
        var bottom = 0.0
        for (mark in list) {
            if (mark.y < top) top = mark.y
            if (mark.y > bottom) bottom = mark.y
        }
        val from = Math.max(0.04, top - 0.26)
        val to = Math.min(0.97, bottom + 0.14)
        if (to - from < 0.12) return Pair(0.08, 0.95)
        return Pair(from, to)
    }

    // MARK: - 刻度数字

    private fun handleMarks(list: List<ScaleMark>) {
        marks = list
        markSnapshot = list

        val built = ScaleFitBuilder.build(list)
        if (built != null) {
            fit = built
            fitTime = System.currentTimeMillis()
            fittedMarks = built.marks
        } else {
            val current = fit
            val tooOld = current == null || System.currentTimeMillis() - fitTime > 3000L
            val contradicted = current != null && contradicts(current, list)
            if (tooOld || contradicted) {
                fit = null
                fittedMarks = emptyList()
            }
        }
        updateReading()
    }

    /** 新读到的数字和旧的关系对不上，说明手机或者奶瓶动过了，旧关系作废 */
    private fun contradicts(current: ScaleFit, list: List<ScaleMark>): Boolean {
        if (list.isEmpty()) return false
        for (mark in list) {
            if (Math.abs(current.volumeAt(mark.y) - mark.valueML) > 20.0) return true
        }
        return false
    }

    // MARK: - 读数

    private fun apply(result: LiquidDetection) {
        if (!result.found) {
            applyMiss()
            return
        }
        synchronized(lock) {
            history.add(result.surfaceY)
            while (history.size > 8) history.removeAt(0)
            val sorted = history.sorted()
            val median = sorted[sorted.size / 2]
            val spread = sorted[sorted.size - 1] - sorted[0]
            isStable = history.size >= 5 && spread < 0.025
            lastValidTime = System.currentTimeMillis()
            detection = LiquidDetection(
                surfaceY = median,
                confidence = result.confidence,
                found = true
            )
        }
        updateReading()
    }

    private fun applyMiss() {
        synchronized(lock) {
            isStable = false
            if (System.currentTimeMillis() - lastValidTime > 2500L) {
                history.clear()
                detection = null
            }
        }
        updateReading()
    }

    private fun updateReading() {
        synchronized(lock) {
            val current = fit
            val surface = detection
            if (current == null || surface == null || !surface.found) {
                if (current == null || System.currentTimeMillis() - lastValidTime > 2500L) {
                    volumeML = null
                }
                return
            }
            val upper = current.maxMarkML + 20.0
            var value = current.volumeAt(surface.surfaceY)
            if (value > upper) value = upper
            if (value < 0.0) value = 0.0
            volumeML = Math.round(value / 10.0) * 10.0
        }
    }
}
