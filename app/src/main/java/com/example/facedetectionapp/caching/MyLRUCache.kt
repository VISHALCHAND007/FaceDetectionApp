package com.example.facedetectionapp.caching

import android.graphics.Bitmap
import androidx.collection.LruCache

class MyLRUCache(maxSize: Int): LruCache<String, Bitmap>(maxSize) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return value.byteCount
    }

    // Add an item to the cache
    fun addBitmapToMemoryCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            put(key, bitmap)
        }
    }

    // Retrieve an item from the cache
    fun getBitmapFromMemoryCache(key: String): Bitmap? {
        return get(key)
    }
    // Remove an item from the cache
    fun removeBitmapFromMemoryCache(key: String) {
        remove(key)
    }
}