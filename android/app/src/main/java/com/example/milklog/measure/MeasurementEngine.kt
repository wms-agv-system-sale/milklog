package com.example.milklog.measure

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.milklog.model.roundToTen
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 识别引擎：按一下快门 -> 拍一张照片 -> 只对这一张照片做识别。
 *
 * 流程：读奶瓶上印的刻度数字 -> 求出"照片高度 <-> 奶量"的关系 -> 找液面
 * -> 换算成奶量（取整到 10 的整数倍）。
 */
class MeasurementEngine(val camera: CameraController) {

    /** 是否正在拍摄 / 识别 */
    var analyzing by mutableStateOf(false)
        private set

    /** 这一张照片，保存记录时也会用它 */
    var photo by mutableStateOf<Bitmap?>(null)
        private set

    /** 识别到的液面（照片高度方向的位置） */
    var detection by mutableStateOf<LiquidDetection?>(null)
        private set

    /** 识别出的奶量；没识别出来时为 null */
    var volumeML by mutableStateOf<Double?>(null)
        private set

    /** 照片里读到的刻度数字 */
    var marks by mutableStateOf<List<ScaleMark>>(emptyList())
        private set

    /** 真正参与换算的刻度数字 */
    var fittedMarks by mutableStateOf<List<ScaleMark>>(emptyList())
        private set

    /** 识别不出来时的提示 */
    var message by mutableStateOf<String?>(null)
        private set

    var torchOn by mutableStateOf(false)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var workExecutor: ExecutorService? = null
    private var fit: ScaleFit? = null

    // MARK: - 开关

    fun setTorch(on: Boolean) {
        torchOn = on
        camera.setTorch(on)
    }

    fun toggleTorch() {
        setTorch(!torchOn)
    }

    /** 回到取景状态，准备拍下一张 */
    fun reset() {
        analyzing = false
        photo = null
        detection = null
        volumeML = null
        marks = emptyList()
        fittedMarks = emptyList()
        message = null
        fit = null
    }

    // MARK: - 拍一张 + 识别这一张

    fun capture() {
        if (analyzing) return
        analyzing = true
        message = null
        volumeML = null
        detection = null
        marks = emptyList()
        fittedMarks = emptyList()
        fit = null
        camera.capture(
            1800,
            { bitmap -> mainHandler.post { analyze(bitmap) } },
            { text ->
                mainHandler.post {
                    analyzing = false
                    message = "拍照失败：" + text
                }
            }
        )
    }

    private fun analyze(bitmap: Bitmap) {
        photo = bitmap
        ScaleOcr.read(bitmap) { list ->
            marks = list
            val built = ScaleFitBuilder.build(list)
            fit = built
            fittedMarks = if (built != null) built.marks else emptyList()
            val range = searchRange(list)
            val executor = workExecutor
                ?: Executors.newSingleThreadExecutor().also { workExecutor = it }
            executor.execute {
                val surface = try {
                    LiquidDetector.detect(bitmap, range.first, range.second)
                } catch (t: Throwable) {
                    null
                }
                mainHandler.post { finish(built, surface) }
            }
        }
    }

    private fun finish(built: ScaleFit?, surface: LiquidDetection?) {
        detection = surface
        val value = if (built != null && surface != null && surface.found) {
            roundToTen(clampVolume(built, surface.surfaceY))
        } else {
            null
        }
        volumeML = value
        analyzing = false
        message = when {
            value != null -> null
            built == null && marks.isEmpty() ->
                "这张照片里没读到刻度数字。凑近一点，让瓶身上的数字正对镜头再拍一张。"
            built == null ->
                "只读到一个刻度数字。退后一点，把整排刻度都拍进画面再拍一张。"
            surface == null || !surface.found ->
                "没找到牛奶的液面。把瓶子放正、光线均匀一点再拍一张。"
            else -> "识别不出来，可以点「手动输入」。"
        }
    }

    /** 液面只可能出现在刻度数字的范围里，这样能避开背景里的杂物 */
    private fun searchRange(list: List<ScaleMark>): Pair<Double, Double> {
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

    private fun clampVolume(current: ScaleFit, y: Double): Double {
        val upper = current.maxMarkML + 20.0
        var value = current.volumeAt(y)
        if (value > upper) value = upper
        if (value < 0.0) value = 0.0
        return value
    }
}
