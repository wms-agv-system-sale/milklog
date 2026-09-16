package com.example.milklog.measure

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * 奶瓶上读到的一个刻度数字。
 * valueML 是数字本身（毫升），y 是它在画面里的竖直位置（0 = 画面顶部，1 = 画面底部）。
 */
data class ScaleMark(val valueML: Double, val y: Double, val raw: String)

/**
 * 读奶瓶上印的刻度数字（例如 100、50、10）。
 *
 * 识别模型直接打包在 App 里，全部在本机运行，不需要联网。
 */
object ScaleOcr {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val digitRun = Regex("[0-9]+")

    @Volatile
    private var busy = false

    /**
     * 识别一张照片，结果回调在主线程。
     * 上一张还没识别完时会跳过这一张，避免任务堆积。
     */
    fun read(bitmap: Bitmap, onResult: (List<ScaleMark>) -> Unit) {
        if (busy) return
        val input: InputImage = try {
            InputImage.fromBitmap(bitmap, 0)
        } catch (t: Throwable) {
            return
        }
        busy = true
        recognizer.process(input)
            .addOnSuccessListener { text ->
                busy = false
                onResult(collect(text, bitmap.width.toDouble(), bitmap.height.toDouble()))
            }
            .addOnFailureListener {
                busy = false
                onResult(emptyList())
            }
    }

    /** 把一次识别出来的文字块整理成一组刻度数字 */
    private fun collect(text: Text, width: Double, height: Double): List<ScaleMark> {
        if (width <= 0.0 || height <= 0.0) return emptyList()
        val grouped = HashMap<Int, ArrayList<ScaleMark>>()

        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    if (box.height() < 5) continue
                    val value = parseValue(element.text) ?: continue
                    val centerY = (box.top + box.bottom) / 2.0 / height
                    if (centerY < 0.02 || centerY > 0.98) continue
                    val key = Math.round(value).toInt()
                    val mark = ScaleMark(value, centerY, element.text.trim())
                    val list = grouped[key]
                    if (list == null) {
                        grouped[key] = arrayListOf(mark)
                    } else {
                        list.add(mark)
                    }
                }
            }
        }

        val marks = ArrayList<ScaleMark>()
        for (entry in grouped.entries) {
            val list = entry.value
            val ys = ArrayList<Double>()
            for (item in list) ys.add(item.y)
            ys.sort()
            marks.add(ScaleMark(entry.key.toDouble(), ys[ys.size / 2], list[0].raw))
        }
        marks.sortBy { it.y }
        return marks
    }

    /**
     * 从一段文字里取出"像刻度"的数字，不像刻度就忽略。
     * 奶瓶刻度都是 5 的倍数，而且不会小于 5ml、大于 600ml。
     */
    private fun parseValue(raw: String): Double? {
        val normalized = normalize(raw)
        var best = ""
        for (match in digitRun.findAll(normalized)) {
            val token = match.value
            if (token.length <= 3 && token.length > best.length) best = token
        }
        if (best.isEmpty()) return null
        val value = best.toIntOrNull() ?: return null
        if (value < 5 || value > 600) return null
        if (value % 5 != 0) return null
        return value.toDouble()
    }

    /** 把容易被认错的字母还原成数字，例如把 1OO 还原成 100 */
    private fun normalize(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (ch in raw) {
            val fixed = when (ch) {
                'O', 'o', 'Q', 'D' -> '0'
                'l', 'I', 'i', '|', '!' -> '1'
                'Z', 'z' -> '2'
                'A', 'a' -> '4'
                'S', 's' -> '5'
                'G', 'g', 'b' -> '6'
                'B' -> '8'
                else -> ch
            }
            builder.append(fixed)
        }
        return builder.toString()
    }
}
