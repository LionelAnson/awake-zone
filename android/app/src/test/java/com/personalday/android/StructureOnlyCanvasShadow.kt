package com.personalday.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLegacyCanvas

/**
 * For RemoteViews/settings/pinning structure tests only; never use for pixel assertions.
 *
 * Robolectric 4.16.1's LEGACY bitmap compositor does not apply the production Canvas
 * transform before copying pixels. The face's right column starts at design x=622;
 * when fitting a 320px bitmap, that shadow attempts an out-of-bounds array copy.
 * Omit this unsupported raster operation while retaining the normal Canvas and
 * RemoteViews objects needed to check payloads, dimensions, settings and callbacks.
 * Actual composition, transparent pixels and row opacity are covered on Android by
 * instrumentation, which does not include anything from src/test.
 */
@Implements(Canvas::class)
class StructureOnlyCanvasShadow : ShadowLegacyCanvas() {
    @Implementation
    protected override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        // Deliberately no pixel output. Production Canvas behavior is unchanged.
    }
}
