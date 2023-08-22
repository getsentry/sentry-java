package android.graphics

import android.graphics.PorterDuff.Mode
import android.graphics.fonts.Font
import android.graphics.text.MeasuredText
import android.util.Log
import io.sentry.samples.android.replay.Recorder

class CanvasDelegate(
    private val recorder: Recorder,
    private val original: Canvas
) : Canvas() {

    companion object {
        val TAG = "Delegate"
    }

    override fun isHardwareAccelerated(): Boolean {
        return false
    }

    override fun setBitmap(bitmap: Bitmap?) {
        Log.d(TAG, "TODO setBitmap: ")
    }

    override fun enableZ() {
        // no-op, called by every ViewGroup
    }

    override fun disableZ() {
        // no-op
    }

    override fun isOpaque(): Boolean = super.isOpaque()

    override fun getWidth(): Int = original.width

    override fun getHeight(): Int = original.height

    override fun getDensity(): Int = original.density

    override fun setDensity(density: Int) {
        // no-op
    }

    override fun getMaximumBitmapWidth(): Int {
        return 0
    }

    override fun getMaximumBitmapHeight(): Int {
        return 0
    }

    override fun save(): Int {
        recorder.save()
        return original.save()
    }

    // no override here, as it's marked as @removed
    fun save(saveFlags: Int): Int {
        return save()
    }

    override fun saveLayer(bounds: RectF?, paint: Paint?, saveFlags: Int): Int {
        Log.d(TAG, "TODO saveLayer: ")
        return super.saveLayer(bounds, paint, saveFlags)
    }

    override fun saveLayer(bounds: RectF?, paint: Paint?): Int {
        Log.d(TAG, "TODO saveLayer: ")
        return super.saveLayer(bounds, paint)
    }

    override fun saveLayer(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        paint: Paint?,
        saveFlags: Int
    ): Int {
        Log.d(TAG, "TODO saveLayer: ")
        return super.saveLayer(left, top, right, bottom, paint, saveFlags)
    }

    override fun saveLayer(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        paint: Paint?
    ): Int {
        Log.d(TAG, "TODO saveLayer: ")
        return super.saveLayer(left, top, right, bottom, paint)
    }

    override fun saveLayerAlpha(bounds: RectF?, alpha: Int, saveFlags: Int): Int {
        Log.d(TAG, "TODO saveLayer: ")
        return super.saveLayerAlpha(bounds, alpha, saveFlags)
    }

    override fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int {
        Log.d(TAG, "TODO saveLayer: ")
        return super.saveLayerAlpha(bounds, alpha)
    }

    override fun saveLayerAlpha(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Int,
        saveFlags: Int
    ): Int {
        Log.d(TAG, "TODO saveLayer: ")
        return super.saveLayerAlpha(left, top, right, bottom, alpha, saveFlags)
    }

    override fun saveLayerAlpha(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Int
    ): Int {
        Log.d(TAG, "TODO saveLayer: ")
        return super.saveLayerAlpha(left, top, right, bottom, alpha)
    }

    override fun restore() {
        // Log.d(TAG, "restore: ")
        recorder.restore()
        original.restore()
    }

    override fun getSaveCount(): Int = original.saveCount

    override fun restoreToCount(saveCount: Int) {
        // Log.d(TAG, "restoreToCount: $saveCount")
        recorder.restoreToCount(saveCount)
        original.restoreToCount(saveCount)
    }

    override fun translate(dx: Float, dy: Float) {
        // Log.d(TAG, "translate: dx: Float, dy: Float")
        recorder.translate(dx, dy)
        original.translate(dx, dy)
    }

    override fun scale(sx: Float, sy: Float) {
        // Log.d(TAG, "TODO scale: ")
        recorder.scale(sx, sy)
        original.scale(sx, sy)
    }

    override fun rotate(degrees: Float) {
        // Log.d(TAG, "TODO rotate: ")
        recorder.rotate(degrees)
        original.rotate(degrees)
    }

    override fun skew(sx: Float, sy: Float) {
        // Log.d(TAG, "TODO skew: ")
        recorder.skew(sx, sy)
        original.skew(sx, sy)
    }

    override fun concat(matrix: Matrix?) {
        // Log.d(TAG, "concat: ")
        recorder.concat(matrix!!)
        original.concat(matrix)
    }

    override fun setMatrix(matrix: Matrix?) {
        // Log.d(TAG, "TODO setMatrix: ")
        recorder.setMatrix(matrix)
        original.setMatrix(matrix)
    }

    override fun getMatrix(ctm: Matrix) {
        // Log.d(TAG, "TODO getMatrix: ")
        original.getMatrix(ctm)
    }

    override fun clipRect(rect: RectF, op: Region.Op): Boolean {
        Log.d(TAG, "TODO clipRect: rect: RectF, op: Region.Op")
        return original.clipRect(rect, op)
    }

    override fun clipRect(rect: Rect, op: Region.Op): Boolean {
        Log.d(TAG, "TODO clipRect: rect: Rect, op: Region.Op")
        return original.clipRect(rect, op)
    }

    override fun clipRect(rect: RectF): Boolean {
        // Log.d(TAG, "TODO clipRect: rect: Rect")
        recorder.clipRectF(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom
        )
        return original.clipRect(rect)
    }

    override fun clipRect(rect: Rect): Boolean {
        // Log.d(TAG, "TODO clipRect: rect: Rect")
        recorder.clipRectF(
            rect.left.toFloat(),
            rect.top.toFloat(),
            rect.right.toFloat(),
            rect.bottom.toFloat()
        )
        return original.clipRect(rect)
    }

    override fun clipRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        op: Region.Op
    ): Boolean {
        Log.d(
            TAG,
            "TODO clipRect: left: Float, top: Float, right: Float, bottom: Float, op: Region.Op"
        )
        return original.clipRect(left, top, right, bottom, op)
    }

    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        // Log.d(TAG, "clipRect: left: Float, top: Float, right: Float, bottom: Float")
        recorder.clipRectF(left, top, right, bottom)
        return original.clipRect(left, top, right, bottom)
    }

    override fun clipRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        // Log.d(TAG, "TODO clipRect: left: Int, top: Int, right: Int, bottom: Int")
        recorder.clipRectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        return original.clipRect(left, top, right, bottom)
    }

    override fun clipOutRect(rect: RectF): Boolean {
        Log.d(TAG, "TODO clipOutRect: rect: RectF")
        return original.clipOutRect(rect)
    }

    override fun clipOutRect(rect: Rect): Boolean {
        Log.d(TAG, "TODO clipOutRect: ")
        return original.clipOutRect(rect)
    }

    override fun clipOutRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        Log.d(TAG, "TODO clipOutRect: ")
        return original.clipOutRect(left, top, right, bottom)
    }

    override fun clipOutRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        Log.d(TAG, "TODO clipOutRect: ")
        return original.clipOutRect(left, top, right, bottom)
    }

    override fun clipPath(path: Path, op: Region.Op): Boolean {
        Log.d(TAG, "TODO clipPath: ")
        return original.clipPath(path, op)
    }

    override fun clipPath(path: Path): Boolean {
        Log.d(TAG, "TODO clipPath: ")
        return original.clipPath(path)
    }

    override fun clipOutPath(path: Path): Boolean {
        Log.d(TAG, "TODO clipOutPath: ")
        return original.clipOutPath(path)
    }

    override fun getDrawFilter(): DrawFilter? {
        Log.d(TAG, "TODO clipOutPath: ")
        return null
    }

    override fun setDrawFilter(filter: DrawFilter?) {
        Log.d(TAG, "TODO setDrawFilter: ")
    }

    override fun quickReject(rect: RectF, type: EdgeType): Boolean {
        Log.d(TAG, "TODO quickReject: ")
        return original.quickReject(rect, type)
    }

    override fun quickReject(rect: RectF): Boolean {
        // seems to not have any effect
        Log.d(TAG, "TODO quickReject: ")
        return original.quickReject(rect)
    }

    override fun quickReject(path: Path, type: EdgeType): Boolean {
        // seems to not have any effect
        Log.d(TAG, "TODO quickReject: ")
        return original.quickReject(path, type)
    }

    override fun quickReject(path: Path): Boolean {
        // seems to not have any effect
        Log.d(TAG, "TODO quickReject: ")
        return original.quickReject(path)
    }

    override fun quickReject(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        type: EdgeType
    ): Boolean {
        // seems to not have any effect
        Log.d(TAG, "TODO quickReject: ")
        return original.quickReject(left, top, right, bottom, type)
    }

    override fun quickReject(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        // seems to not have any effect
        Log.d(TAG, "TODO quickReject: ")
        return original.quickReject(left, top, right, bottom)
    }

    override fun getClipBounds(bounds: Rect): Boolean {
        return original.getClipBounds(bounds)
    }

    override fun drawPicture(picture: Picture) {
        Log.d(TAG, "TODO drawPicture: ")
        // original.drawPicture(picture)
    }

    override fun drawPicture(picture: Picture, dst: RectF) {
        Log.d(TAG, "TODO drawPicture: ")
        // original.drawPicture(picture, dst)
    }

    override fun drawPicture(picture: Picture, dst: Rect) {
        Log.d(TAG, "TODO drawPicture: ")
        // original.drawPicture(picture, dst)
    }

    override fun drawArc(
        oval: RectF,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        paint: Paint
    ) {
        Log.d(TAG, "TODO drawArc: ")
        // original.drawArc(oval, startAngle, sweepAngle, useCenter, paint)
    }

    override fun drawArc(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        paint: Paint
    ) {
        Log.d(TAG, "drawArc: ")
        // original.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint)
    }

    override fun drawARGB(a: Int, r: Int, g: Int, b: Int) {
        Log.d(TAG, "drawARGB: ")
        // original.drawARGB(a, r, g, b)
    }

    override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        Log.d(TAG, "drawBitmap: ")
        // original.drawBitmap(bitmap, left, top, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
        Log.d(TAG, "drawBitmap: ")
        // original.drawBitmap(bitmap, src, dst, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
        Log.d(TAG, "drawBitmap: ")
        // original.drawBitmap(bitmap, src, dst, paint)
    }

    override fun drawBitmap(
        colors: IntArray,
        offset: Int,
        stride: Int,
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        hasAlpha: Boolean,
        paint: Paint?
    ) {
        Log.d(TAG, "drawBitmap: ")
        // original.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint)
    }

    override fun drawBitmap(
        colors: IntArray,
        offset: Int,
        stride: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        hasAlpha: Boolean,
        paint: Paint?
    ) {
        Log.d(TAG, "drawBitmap: ")
        // original.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
        Log.d(TAG, "drawBitmap: ")
        // original.drawBitmap(bitmap, matrix, paint)
    }

    override fun drawBitmapMesh(
        bitmap: Bitmap,
        meshWidth: Int,
        meshHeight: Int,
        verts: FloatArray,
        vertOffset: Int,
        colors: IntArray?,
        colorOffset: Int,
        paint: Paint?
    ) {
        Log.d(TAG, "drawBitmapMesh: ")
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        // Log.d(TAG, "drawCircle: ")
        recorder.drawCircle(cx, cy, radius, paint)
        // original.drawCircle(cx, cy, radius, paint)
    }

    override fun drawColor(color: Int) {
        Log.d(TAG, "drawColor: ")
        // original.drawColor(color)
    }

    override fun drawColor(color: Long) {
        Log.d(TAG, "drawColor: ")
        // original.drawColor(color)
    }

    override fun drawColor(color: Int, mode: Mode) {
        Log.d(TAG, "drawColor: ")
        // original.drawColor(color, mode)
    }

    override fun drawColor(color: Int, mode: BlendMode) {
        Log.d(TAG, "drawColor: ")
        // original.drawColor(color, mode)
    }

    override fun drawColor(color: Long, mode: BlendMode) {
        Log.d(TAG, "drawColor: ")
        // original.drawColor(color, mode)
    }

    override fun drawLine(
        startX: Float,
        startY: Float,
        stopX: Float,
        stopY: Float,
        paint: Paint
    ) {
        Log.d(TAG, "drawLine: ")
        // original.drawLine(startX, startY, stopX, stopY, paint)
    }

    override fun drawLines(pts: FloatArray, offset: Int, count: Int, paint: Paint) {
        Log.d(TAG, "drawLines: ")
        // original.drawLines(pts, offset, count, paint)
    }

    override fun drawLines(pts: FloatArray, paint: Paint) {
        Log.d(TAG, "drawLines: ")
        // original.drawLines(pts, paint)
    }

    override fun drawOval(oval: RectF, paint: Paint) {
        Log.d(TAG, "drawOval: ")
        // original.drawOval(oval, paint)
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        Log.d(TAG, "drawOval: ")
        // original.drawOval(left, top, right, bottom, paint)
    }

    override fun drawPaint(paint: Paint) {
        Log.d(TAG, "drawPaint: ")
        // original.drawPaint(paint)
    }

    override fun drawPatch(patch: NinePatch, dst: Rect, paint: Paint?) {
        Log.d(TAG, "drawPatch: ")
        // original.drawPatch(patch, dst, paint)
    }

    override fun drawPatch(patch: NinePatch, dst: RectF, paint: Paint?) {
        Log.d(TAG, "drawPatch: ")
        // original.drawPatch(patch, dst, paint)
    }

    override fun drawPath(path: Path, paint: Paint) {
        Log.d(TAG, "drawPath: $path")
        recorder.drawPath(path, paint)
        // original.drawPath(path, paint)
    }

    override fun drawPoint(x: Float, y: Float, paint: Paint) {
        Log.d(TAG, "drawPoint: ")
        // original.drawPoint(x, y, paint)
    }

    override fun drawPoints(pts: FloatArray?, offset: Int, count: Int, paint: Paint) {
        Log.d(TAG, "drawPoints: ")
        // original.drawPoints(pts, offset, count, paint)
    }

    override fun drawPoints(pts: FloatArray, paint: Paint) {
        Log.d(TAG, "drawPoints: ")
        // original.drawPoints(pts, paint)
    }

    override fun drawPosText(
        text: CharArray,
        index: Int,
        count: Int,
        pos: FloatArray,
        paint: Paint
    ) {
        Log.d(TAG, "drawPosText: ")
        // original.drawPosText(text, index, count, pos, paint)
    }

    override fun drawPosText(text: String, pos: FloatArray, paint: Paint) {
        Log.d(TAG, "drawPosText: ")
        // original.drawPosText(text, pos, paint)
    }

    override fun drawRect(rect: RectF, paint: Paint) {
//        Log.d(TAG, "drawRect: 0")
        recorder.drawRect(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            paint
        )
        // original.drawRect(rect, paint)
    }

    override fun drawRect(r: Rect, paint: Paint) {
        // Log.d(TAG, "drawRect: 1")
        recorder.drawRect(
            r.left.toFloat(),
            r.top.toFloat(),
            r.right.toFloat(),
            r.bottom.toFloat(),
            paint
        )
        // original.drawRect(r, paint)
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        // Log.d(TAG, "drawRect: 2")
        recorder.drawRect(
            left,
            top,
            right,
            bottom,
            paint
        )
        // original.drawRect(left, top, right, bottom, paint)
    }

    override fun drawRGB(r: Int, g: Int, b: Int) {
        Log.d(TAG, "drawRGB: ")
        // original.drawRGB(r, g, b)
    }

    override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
        // Log.d(TAG, "drawRoundRect: 0")
        recorder.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint)
    }

    override fun drawRoundRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        rx: Float,
        ry: Float,
        paint: Paint
    ) {
        // Log.d(TAG, "drawRoundRect: 1")
        recorder.drawRoundRect(left, top, right, bottom, rx, ry, paint)
    }

    override fun drawDoubleRoundRect(
        outer: RectF,
        outerRx: Float,
        outerRy: Float,
        inner: RectF,
        innerRx: Float,
        innerRy: Float,
        paint: Paint
    ) {
        Log.d(TAG, "drawDoubleRoundRect: 2")
        // original.drawDoubleRoundRect(outer, outerRx, outerRy, inner, innerRx, innerRy, paint)
    }

    override fun drawDoubleRoundRect(
        outer: RectF,
        outerRadii: FloatArray,
        inner: RectF,
        innerRadii: FloatArray,
        paint: Paint
    ) {
        Log.d(TAG, "drawDoubleRoundRect: ")
        // original.drawDoubleRoundRect(outer, outerRadii, inner, innerRadii, paint)
    }

    override fun drawGlyphs(
        glyphIds: IntArray,
        glyphIdOffset: Int,
        positions: FloatArray,
        positionOffset: Int,
        glyphCount: Int,
        font: Font,
        paint: Paint
    ) {
        Log.d(TAG, "drawGlyphs: ")
    }

    override fun drawText(
        text: CharArray,
        index: Int,
        count: Int,
        x: Float,
        y: Float,
        paint: Paint
    ) {
        // Log.d(
        // TAG,
        // "drawText: text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint"
        // )
        recorder.drawText(text.toString(), 0, text.size, x, y, paint)
    }

    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        // Log.d(TAG, "drawText: text: String, x: Float, y: Float, paint: Paint")
        recorder.drawText(text, 0, text.length, x, y, paint)
    }

    override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
        // Log.d(TAG, "drawText: text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint")
        recorder.drawText(text, start, end, x, y, paint)
    }

    override fun drawText(
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        y: Float,
        paint: Paint
    ) {
        // Log.d(
        //     TAG,
        //     "drawText: text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint"
        // )
        recorder.drawText(text, start, end, x, y, paint)
    }

    override fun drawTextOnPath(
        text: CharArray,
        index: Int,
        count: Int,
        path: Path,
        hOffset: Float,
        vOffset: Float,
        paint: Paint
    ) {
        Log.d(TAG, "drawTextOnPath: ")
        // original.drawTextOnPath(text, index, count, path, hOffset, vOffset, paint)
    }

    override fun drawTextOnPath(
        text: String,
        path: Path,
        hOffset: Float,
        vOffset: Float,
        paint: Paint
    ) {
        Log.d(TAG, "drawTextOnPath: ")
        // original.drawTextOnPath(text, path, hOffset, vOffset, paint)
    }

    override fun drawTextRun(
        text: CharArray,
        index: Int,
        count: Int,
        contextIndex: Int,
        contextCount: Int,
        x: Float,
        y: Float,
        isRtl: Boolean,
        paint: Paint
    ) {
        Log.d(TAG, "drawTextRun: ")
        // original.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint)
    }

    override fun drawTextRun(
        text: CharSequence,
        start: Int,
        end: Int,
        contextStart: Int,
        contextEnd: Int,
        x: Float,
        y: Float,
        isRtl: Boolean,
        paint: Paint
    ) {
        Log.d(TAG, "drawTextRun: ")
        // original.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint)
    }

    override fun drawTextRun(
        text: MeasuredText,
        start: Int,
        end: Int,
        contextStart: Int,
        contextEnd: Int,
        x: Float,
        y: Float,
        isRtl: Boolean,
        paint: Paint
    ) {
        Log.d(TAG, "drawTextRun: ")
        // original.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint)
    }

    override fun drawVertices(
        mode: VertexMode,
        vertexCount: Int,
        verts: FloatArray,
        vertOffset: Int,
        texs: FloatArray?,
        texOffset: Int,
        colors: IntArray?,
        colorOffset: Int,
        indices: ShortArray?,
        indexOffset: Int,
        indexCount: Int,
        paint: Paint
    ) {
        Log.d(TAG, "drawVertices: ")
    }

    override fun drawRenderNode(renderNode: RenderNode) {
        Log.d(TAG, "drawRenderNode: ")
        // original.drawRenderNode(renderNode)
    }
}
