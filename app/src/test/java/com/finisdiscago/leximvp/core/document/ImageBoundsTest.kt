package com.finisdiscago.leximvp.core.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageBoundsTest {
    @Test fun normalImagesKeepTheirOriginalResolution() {
        assertEquals(1, imageSampleSize(1600, 2000))
        assertEquals(1800 to 2400, ocrBitmapSize(600, 800))
    }

    @Test fun hugePortraitAndLandscapePagesCannotAllocateUnboundedBitmaps() {
        listOf(10_000 to 200_000, 200_000 to 10_000).forEach { (width, height) ->
            val sample = imageSampleSize(width, height)
            assertTrue(maxOf(width, height) / sample <= 2400)
            val (w, h) = ocrBitmapSize(width, height)
            assertTrue(w > 0 && h > 0 && maxOf(w, h) <= 2400)
        }
    }
}
