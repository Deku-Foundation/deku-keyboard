/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.deku.foundation.dekukeyboard.ime.clipboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Upper bound for a decoded clipboard image preview. The full-resolution clipboard item is kept
 * untouched and is still pasted in full; only the non-interactive grid preview is downsampled so a
 * large photo cannot exhaust the IME's heap.
 */
internal const val IMAGE_PREVIEW_MAX_DIMENSION = 720

/** Fixed, bounded video thumbnail size so a 4K clip never allocates a full-frame bitmap. */
internal const val VIDEO_THUMBNAIL_WIDTH = 512
internal const val VIDEO_THUMBNAIL_HEIGHT = 384

/**
 * Chooses a power-of-two [BitmapFactory.Options.inSampleSize] so neither dimension stays more
 * than roughly twice [maxDimension]. Pure math so it can be unit-tested without Android.
 */
internal fun calculateInSampleSize(outWidth: Int, outHeight: Int, maxDimension: Int): Int {
    require(maxDimension > 0) { "maxDimension must be positive, was $maxDimension" }
    if (outWidth <= 0 || outHeight <= 0) return 1
    var sampleSize = 1
    while (
        outWidth / (sampleSize * 2) >= maxDimension ||
        outHeight / (sampleSize * 2) >= maxDimension
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Decodes a downsampled bitmap. Reads only the bounds first so the full pixel buffer is never
 * allocated for oversized images. Callers must run this off the main thread.
 */
internal fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}
