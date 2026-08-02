package xyz.plcliangpicup.phigrosscore

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class PhigrosScoreApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("artwork_cache"))
                .maxSizeBytes(192L * 1024L * 1024L)
                .build()
        }
        .respectCacheHeaders(false)
        .allowRgb565(true)
        .crossfade(120)
        .components { add(SvgDecoder.Factory()) }
        .build()
}
