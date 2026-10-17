package com.vikash.mp3player.data

import android.content.Context
import android.net.Uri
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult

/**
 * Album art loading for every image in the app (lists, the spinning CD, Now Playing): the real
 * embedded/MediaStore art always wins; only when an album has none does this fall back to a
 * cover the user fetched with "Find album art" (filesDir/albumart/<albumId>.jpg).
 */
class SavedAlbumArtInterceptor(context: Context) : Interceptor {

    private val app = context.applicationContext

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        if (result is SuccessResult) return result
        val file = AlbumArtFinder.fallbackFor(app, chain.request.data) ?: return result
        val request = chain.request.newBuilder().data(Uri.fromFile(file)).build()
        return chain.withRequest(request).proceed()
    }
}
