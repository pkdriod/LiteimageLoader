package com.pk.liteimageloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors

class UrlImageLoader private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: UrlImageLoader? = null

        fun getInstance(context: Context): UrlImageLoader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UrlImageLoader(context).also { INSTANCE = it }
            }
        }
    }

    private val cache: LruCache<String, Bitmap>
    private val executor = Executors.newFixedThreadPool(4)
    private val client = OkHttpClient()

    init {
        val cacheSize = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        cache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    fun loadImage(url: String, imageView: ImageView) {
        imageView.tag = url
        cache.get(url)?.let {
            imageView.setImageBitmap(it)
            return
        }

        executor.submit {
            val bitmap = downloadImage(url) ?: return@submit
            cache.put(url, bitmap)
            if (imageView.tag == url) {
                imageView.post {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun downloadImage(url: String): Bitmap? {
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val inputStream: InputStream = response.body?.byteStream() ?: return null
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}