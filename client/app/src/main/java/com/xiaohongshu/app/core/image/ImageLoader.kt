package com.xiaohongshu.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 轻量图片加载器。
 *
 * 选型说明：本工程**不引入 Coil**。缓存中的 Coil 2.7 面向 OkHttp 4，而网络栈统一在
 * OkHttp 5（Retrofit 3 的前置要求），两大版本混用会在运行期出现方法不兼容；自行实现同时
 * 便于精确控制「缺失素材统一占位」与按目标尺寸下采样的策略。
 *
 * 能力：
 * - 两级缓存：内存 [LruCache]（1/8 堆，上限 48MB）+ OkHttp 磁盘缓存；
 * - 同 URL 并发去重（瀑布流里同一头像只发一次请求）；
 * - 先读头部尺寸再按目标尺寸选 `inSampleSize`，避免整图解码撑爆内存；
 * - 失败或空 URL 返回 null，由 UI 回落统一占位素材。
 */
class ImageLoader(context: Context) {

    private val appContext = context.applicationContext

    /** 图片专用客户端：不带 Authorization，避免把用户 token 发给图片主机。 */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(appContext.cacheDir, IMAGE_CACHE_DIR), DISK_CACHE_BYTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val memoryCache = object : LruCache<String, ImageBitmap>(memoryCacheSize()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 进行中的请求，用于并发去重。 */
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private val inFlightLock = Mutex()

    /**
     * 加载并解码图片。
     *
     * [targetWidthPx] / [targetHeightPx] 为期望的显示尺寸（像素）；传 0 表示该维度不限，
     * 仅按 [MAX_DECODE_DIMENSION] 封顶。返回 null 表示无图或加载失败。
     */
    suspend fun load(url: String, targetWidthPx: Int = 0, targetHeightPx: Int = 0): ImageBitmap? {
        if (url.isBlank()) return null
        val key = "$url#${targetWidthPx}x${targetHeightPx}"

        memoryCache.get(key)?.let { return it }

        val deferred = inFlightLock.withLock {
            inFlight[key] ?: scope.async { download(url, targetWidthPx, targetHeightPx) }
                .also { inFlight[key] = it }
        }

        val result = try {
            deferred.await()
        } finally {
            inFlightLock.withLock {
                if (inFlight[key] === deferred) inFlight.remove(key)
            }
        }
        result?.let { memoryCache.put(key, it) }
        return result
    }

    private suspend fun download(url: String, targetW: Int, targetH: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bytes = response.body?.bytes() ?: return@use null
                    decode(bytes, targetW, targetH)
                }
            }.getOrNull()
        }

    private fun decode(bytes: ByteArray, targetW: Int, targetH: Int): ImageBitmap? {
        // 第一遍只读头部，拿到原始尺寸（不分配像素内存）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    /**
     * 选择 2 的幂采样率，使解码结果不小于目标尺寸（避免糊），同时不超过目标尺寸的 2 倍（避免浪费）。
     * 目标为 0 的维度按 [MAX_DECODE_DIMENSION] 处理。
     */
    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        val safeTargetW = if (targetWidth > 0) targetWidth else MAX_DECODE_DIMENSION
        val safeTargetH = if (targetHeight > 0) targetHeight else MAX_DECODE_DIMENSION

        var sampleSize = 1
        while (true) {
            val next = sampleSize * 2
            val nextW = width / next
            val nextH = height / next
            // 再降一半就会小于目标尺寸时停止
            if (nextW < safeTargetW || nextH < safeTargetH) break
            sampleSize = next
        }
        return sampleSize
    }

    private fun memoryCacheSize(): Int {
        val maxHeap = Runtime.getRuntime().maxMemory()
        return (maxHeap / 8).coerceIn(8L * 1024 * 1024, 48L * 1024 * 1024).toInt()
    }

    /** 内存吃紧时（Activity onTrimMemory）可调用。 */
    fun clearMemory() {
        memoryCache.evictAll()
    }

    private companion object {
        const val IMAGE_CACHE_DIR = "xhs_image_cache"
        const val DISK_CACHE_BYTES = 60L * 1024 * 1024
        const val MAX_DECODE_DIMENSION = 1080
    }
}
