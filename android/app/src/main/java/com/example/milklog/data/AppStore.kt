package com.example.milklog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.milklog.model.AppSettings
import com.example.milklog.model.DateText
import com.example.milklog.model.FeedRecord
import com.example.milklog.model.RecordSource
import com.example.milklog.model.newId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** 本地数据仓库：全部数据保存在手机的应用目录里，不联网、不上传。 */
class AppStore(context: Context) {

    private val rootDir: File = File(context.filesDir, "milklog").also { it.mkdirs() }
    private val photosDir: File = File(rootDir, "photos").also { it.mkdirs() }
    private val dataFile: File = File(rootDir, "data.json")

    var feeds by mutableStateOf<List<FeedRecord>>(emptyList())
        private set

    var settings by mutableStateOf(AppSettings())
        private set

    init {
        load()
    }

    // MARK: - 读写

    private fun load() {
        if (!dataFile.exists()) return
        try {
            val text = dataFile.readText(Charsets.UTF_8)
            val root = JSONObject(text)
            val feedsArr = root.optJSONArray("feeds") ?: JSONArray()
            val loadedFeeds = ArrayList<FeedRecord>()
            for (i in 0 until feedsArr.length()) {
                val o = feedsArr.optJSONObject(i) ?: continue
                loadedFeeds.add(
                    FeedRecord(
                        id = o.optString("id", newId()),
                        date = o.optLong("date", System.currentTimeMillis()),
                        volumeML = o.optDouble("volumeML", 0.0),
                        source = if (o.optString("source") == "CAMERA") RecordSource.CAMERA else RecordSource.MANUAL,
                        note = o.optString("note", ""),
                        photoName = if (o.isNull("photoName")) null else o.optString("photoName", "").ifEmpty { null },
                        confidence = if (o.isNull("confidence")) null else o.optDouble("confidence", 0.0)
                    )
                )
            }
            feeds = loadedFeeds.sortedBy { it.date }

            val s = root.optJSONObject("settings")
            if (s != null) {
                settings = AppSettings(
                    dailyTargetML = s.optDouble("dailyTargetML", 600.0),
                    keepPhotos = s.optBoolean("keepPhotos", true)
                )
            }
        } catch (e: Exception) {
            // 数据损坏时保持空状态，不崩溃
        }
    }

    fun save() {
        try {
            val root = JSONObject()
            val feedsArr = JSONArray()
            for (f in feeds) {
                val o = JSONObject()
                o.put("id", f.id)
                o.put("date", f.date)
                o.put("volumeML", f.volumeML)
                o.put("source", f.source.name)
                o.put("note", f.note)
                if (f.photoName != null) o.put("photoName", f.photoName) else o.put("photoName", JSONObject.NULL)
                if (f.confidence != null) o.put("confidence", f.confidence) else o.put("confidence", JSONObject.NULL)
                feedsArr.put(o)
            }
            root.put("feeds", feedsArr)

            val s = JSONObject()
            s.put("dailyTargetML", settings.dailyTargetML)
            s.put("keepPhotos", settings.keepPhotos)
            root.put("settings", s)

            dataFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            // 忽略写入失败
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        save()
    }

    // MARK: - 记录

    fun add(record: FeedRecord) {
        feeds = (feeds + record).sortedBy { it.date }
        save()
    }

    fun update(record: FeedRecord) {
        feeds = feeds.map { if (it.id == record.id) record else it }.sortedBy { it.date }
        save()
    }

    fun deleteRecord(recordId: String) {
        val target = feeds.firstOrNull { it.id == recordId }
        if (target?.photoName != null) deletePhoto(target.photoName)
        feeds = feeds.filter { it.id != recordId }
        save()
    }

    fun deleteAllRecords() {
        for (f in feeds) {
            if (f.photoName != null) deletePhoto(f.photoName)
        }
        feeds = emptyList()
        save()
    }

    val todayRecords: List<FeedRecord>
        get() = feeds.filter { DateText.isToday(it.date) }

    val todayTotalML: Double
        get() = todayRecords.fold(0.0) { acc, item -> acc + item.volumeML }

    /** 最近 7 天平均（不含今天） */
    val recentDailyAverage: Double
        get() {
            val dayMillis = 24L * 60L * 60L * 1000L
            val today = DateText.dayStart(System.currentTimeMillis())
            val values = ArrayList<Double>()
            for (offset in 1..7) {
                val start = today - offset * dayMillis
                val end = start + dayMillis
                val items = feeds.filter { it.date >= start && it.date < end }
                if (items.isNotEmpty()) {
                    values.add(items.fold(0.0) { acc, item -> acc + item.volumeML })
                }
            }
            if (values.isEmpty()) return 0.0
            return values.sum() / values.size.toDouble()
        }

    // MARK: - 照片

    fun savePhoto(bitmap: Bitmap): String? {
        return try {
            val name = newId() + ".jpg"
            val file = File(photosDir, name)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            name
        } catch (e: Exception) {
            null
        }
    }

    /** 列表里用的小图，先采样再解码，避免占用太多内存 */
    fun thumbnail(name: String?, maxDimension: Int = 96): Bitmap? {
        if (name == null) return null
        return try {
            val file = File(photosDir, name)
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxDimension * 2 && bounds.outHeight / sample > maxDimension * 2) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        }
    }

    fun photo(name: String): Bitmap? {
        return try {
            val file = File(photosDir, name)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    private fun deletePhoto(name: String?) {
        if (name == null) return
        try {
            File(photosDir, name).delete()
        } catch (e: Exception) {
            // 忽略
        }
    }

    // MARK: - 导出

    fun exportFile(): File? {
        return try {
            val arr = JSONArray()
            for (f in feeds) {
                val o = JSONObject()
                o.put("id", f.id)
                o.put("dateText", DateText.full(f.date))
                o.put("date", f.date)
                o.put("volumeML", f.volumeML)
                o.put("source", f.source.label)
                o.put("note", f.note)
                arr.put(o)
            }
            val file = File(rootDir, "milklog-export.json")
            file.writeText(arr.toString(2), Charsets.UTF_8)
            file
        } catch (e: Exception) {
            null
        }
    }
}
