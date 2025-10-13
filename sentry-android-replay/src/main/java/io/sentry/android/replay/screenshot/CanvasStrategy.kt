@file:Suppress("DEPRECATION")

package io.sentry.android.replay.screenshot

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DrawFilter
import android.graphics.Matrix
import android.graphics.Mesh
import android.graphics.NinePatch
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.RenderNode
import android.graphics.fonts.Font
import android.graphics.text.MeasuredText
import android.media.ImageReader
import android.view.View
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.replay.ExecutorProvider
import io.sentry.android.replay.ScreenshotRecorderCallback
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.submitSafely
import io.sentry.util.AutoClosableReentrantLock
import io.sentry.util.IntegrationUtils
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.use

@SuppressLint("UseKtx")
internal class CanvasStrategy(
  private val executor: ExecutorProvider,
  private val screenshotRecorderCallback: ScreenshotRecorderCallback?,
  private val options: SentryOptions,
  private val config: ScreenshotRecorderConfig,
) : ScreenshotStrategy {

  @Volatile private var screenshot: Bitmap? = null

  private val screenshotLock = AutoClosableReentrantLock()
  private val prescaledMatrix by
    lazy(NONE) { Matrix().apply { preScale(config.scaleFactorX, config.scaleFactorY) } }
  private val lastCaptureSuccessful = AtomicBoolean(false)
  private val textIgnoringCanvas = TextIgnoringDelegateCanvas()

  private val isClosed = AtomicBoolean(false)

  private val onImageAvailableListener: (holder: PictureReaderHolder) -> Unit = { holder ->
    if (isClosed.get()) {
      options.logger.log(SentryLevel.ERROR, "CanvasStrategy already closed, skipping image")
    } else {
      try {
        val image = holder.reader.acquireLatestImage()
        try {
          if (image.planes.size > 0) {
            val plane = image.planes[0]

            screenshotLock.acquire().use {
              if (screenshot == null) {
                screenshot =
                  Bitmap.createBitmap(holder.width, holder.height, Bitmap.Config.ARGB_8888)
              }
              val bitmap = screenshot
              if (bitmap == null || bitmap.isRecycled) {
                return@use
              }

              val buffer = plane.buffer.rewind()
              bitmap.copyPixelsFromBuffer(buffer)
              lastCaptureSuccessful.set(true)
              screenshotRecorderCallback?.onScreenshotRecorded(bitmap)
            }
          }
        } finally {
          image.close()
        }
      } catch (e: Throwable) {
        options.logger.log(SentryLevel.ERROR, "CanvasStrategy: image processing failed", e)
      } finally {
        freePictureRef.set(holder)
      }
    }
  }

  private var freePictureRef =
    AtomicReference(
      PictureReaderHolder(config.recordingWidth, config.recordingHeight, onImageAvailableListener)
    )

  private var unprocessedPictureRef = AtomicReference<PictureReaderHolder>(null)

  init {
    IntegrationUtils.addIntegrationToSdkVersion("ReplayCanvasStrategy")
  }

  @SuppressLint("NewApi")
  private val pictureRenderTask = Runnable {
    if (isClosed.get()) {
      options.logger.log(
        SentryLevel.DEBUG,
        "Canvas Strategy already closed, skipping picture render",
      )
      return@Runnable
    }
    val holder = unprocessedPictureRef.getAndSet(null)
    if (holder == null) {
      return@Runnable
    }

    try {
      if (!holder.setup.getAndSet(true)) {
        holder.reader.setOnImageAvailableListener(holder, executor.getBackgroundHandler())
      }

      val surface = holder.reader.surface
      val canvas = surface.lockHardwareCanvas()
      canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
      holder.picture.draw(canvas)
      surface.unlockCanvasAndPost(canvas)
    } catch (t: Throwable) {
      freePictureRef.set(holder)
      options.logger.log(SentryLevel.ERROR, "Canvas Strategy: picture render failed", t)
    }
  }

  @SuppressLint("UnclosedTrace")
  override fun capture(root: View) {

    val holder = freePictureRef.getAndSet(null)
    if (holder == null) {
      options.logger.log(SentryLevel.DEBUG, "No free Picture available, skipping capture")
      lastCaptureSuccessful.set(false)
      return
    }

    val pictureCanvas = holder.picture.beginRecording(config.recordingWidth, config.recordingHeight)
    textIgnoringCanvas.delegate = pictureCanvas
    textIgnoringCanvas.setMatrix(prescaledMatrix)
    root.draw(textIgnoringCanvas)
    holder.picture.endRecording()

    unprocessedPictureRef.set(holder)

    executor.getExecutor().submitSafely(options, "screenshot_recorder.canvas", pictureRenderTask)
  }

  override fun onContentChanged() {
    // ignored
  }

  override fun close() {
    isClosed.set(true)
    screenshotLock.acquire().use {
      screenshot?.apply {
        if (!isRecycled) {
          recycle()
        }
      }
    }
  }

  override fun lastCaptureSuccessful(): Boolean {
    return lastCaptureSuccessful.get()
  }

  override fun emitLastScreenshot() {
    if (lastCaptureSuccessful()) {
      val bitmap = screenshot
      if (bitmap != null && !bitmap.isRecycled) {
        screenshotRecorderCallback?.onScreenshotRecorded(bitmap)
      }
    }
  }
}

@SuppressLint("NewApi", "UseKtx")
private class TextIgnoringDelegateCanvas : Canvas() {

  lateinit var delegate: Canvas
  private val solidPaint = Paint()
  private val textPaint = Paint()
  private val tmpRect = Rect()

  val singlePixelBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
  val singlePixelCanvas = Canvas(singlePixelBitmap)

  val singlePixelBitmapBounds = Rect(0, 0, 1, 1)

  private val bitmapColorCache = WeakHashMap<Bitmap, Pair<Int, Int>>()

  override fun isHardwareAccelerated(): Boolean {
    return false
  }

  override fun setBitmap(bitmap: Bitmap?) {
    delegate.setBitmap(bitmap)
  }

  override fun enableZ() {
    delegate.enableZ()
  }

  override fun disableZ() {
    delegate.disableZ()
  }

  override fun isOpaque(): Boolean {
    return delegate.isOpaque()
  }

  override fun getWidth(): Int {
    return delegate.getWidth()
  }

  override fun getHeight(): Int {
    return delegate.getHeight()
  }

  override fun getDensity(): Int {
    return delegate.getDensity()
  }

  override fun setDensity(density: Int) {
    delegate.setDensity(density)
  }

  override fun getMaximumBitmapWidth(): Int {
    return delegate.maximumBitmapWidth
  }

  override fun getMaximumBitmapHeight(): Int {
    return delegate.maximumBitmapHeight
  }

  override fun save(): Int {
    val result = delegate.save()
    return result
  }

  fun save(saveFlags: Int): Int {
    return save()
  }

  override fun saveLayer(bounds: RectF?, paint: Paint?, saveFlags: Int): Int {
    return delegate.saveLayer(bounds, paint, saveFlags)
  }

  override fun saveLayer(bounds: RectF?, paint: Paint?): Int {
    return delegate.saveLayer(bounds, paint)
  }

  override fun saveLayer(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    paint: Paint?,
    saveFlags: Int,
  ): Int {
    return delegate.saveLayer(left, top, right, bottom, paint, saveFlags)
  }

  override fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?): Int {
    return delegate.saveLayer(left, top, right, bottom, paint)
  }

  override fun saveLayerAlpha(bounds: RectF?, alpha: Int, saveFlags: Int): Int {
    return delegate.saveLayerAlpha(bounds, alpha, saveFlags)
  }

  override fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int {
    return delegate.saveLayerAlpha(bounds, alpha)
  }

  override fun saveLayerAlpha(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    alpha: Int,
    saveFlags: Int,
  ): Int {
    return delegate.saveLayerAlpha(left, top, right, bottom, alpha, saveFlags)
  }

  override fun saveLayerAlpha(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    alpha: Int,
  ): Int {
    return delegate.saveLayerAlpha(left, top, right, bottom, alpha)
  }

  override fun restore() {
    delegate.restore()
  }

  override fun getSaveCount(): Int {
    return delegate.saveCount
  }

  override fun restoreToCount(saveCount: Int) {
    delegate.restoreToCount(saveCount)
  }

  override fun translate(dx: Float, dy: Float) {
    delegate.translate(dx, dy)
  }

  override fun scale(sx: Float, sy: Float) {
    delegate.scale(sx, sy)
  }

  override fun rotate(degrees: Float) {
    delegate.rotate(degrees)
  }

  override fun skew(sx: Float, sy: Float) {
    delegate.skew(sx, sy)
  }

  override fun concat(matrix: Matrix?) {
    delegate.concat(matrix)
  }

  override fun setMatrix(matrix: Matrix?) {
    delegate.setMatrix(matrix)
  }

  override fun getMatrix(ctm: Matrix) {
    delegate.getMatrix(ctm)
  }

  override fun clipRect(rect: RectF, op: Region.Op): Boolean {
    return delegate.clipRect(rect, op)
  }

  override fun clipRect(rect: Rect, op: Region.Op): Boolean {
    return delegate.clipRect(rect, op)
  }

  override fun clipRect(rect: RectF): Boolean {
    return delegate.clipRect(rect)
  }

  override fun clipRect(rect: Rect): Boolean {
    return delegate.clipRect(rect)
  }

  override fun clipRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    op: Region.Op,
  ): Boolean {
    return delegate.clipRect(left, top, right, bottom, op)
  }

  override fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
    return delegate.clipRect(left, top, right, bottom)
  }

  override fun clipRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
    return delegate.clipRect(left, top, right, bottom)
  }

  override fun clipOutRect(rect: RectF): Boolean {
    return delegate.clipOutRect(rect)
  }

  override fun clipOutRect(rect: Rect): Boolean {
    return delegate.clipOutRect(rect)
  }

  override fun clipOutRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
    return delegate.clipOutRect(left, top, right, bottom)
  }

  override fun clipOutRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
    return delegate.clipOutRect(left, top, right, bottom)
  }

  override fun clipPath(path: Path, op: Region.Op): Boolean {
    return delegate.clipPath(path, op)
  }

  override fun clipPath(path: Path): Boolean {
    return delegate.clipPath(path)
  }

  override fun clipOutPath(path: Path): Boolean {
    return delegate.clipOutPath(path)
  }

  override fun getDrawFilter(): DrawFilter? {
    return delegate.getDrawFilter()
  }

  override fun setDrawFilter(filter: DrawFilter?) {
    delegate.setDrawFilter(filter)
  }

  override fun quickReject(rect: RectF, type: EdgeType): Boolean {
    return delegate.quickReject(rect, type)
  }

  override fun quickReject(rect: RectF): Boolean {
    return delegate.quickReject(rect)
  }

  override fun quickReject(path: Path, type: EdgeType): Boolean {
    return delegate.quickReject(path, type)
  }

  override fun quickReject(path: Path): Boolean {
    return delegate.quickReject(path)
  }

  override fun quickReject(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    type: EdgeType,
  ): Boolean {
    return delegate.quickReject(left, top, right, bottom, type)
  }

  override fun quickReject(left: Float, top: Float, right: Float, bottom: Float): Boolean {
    return delegate.quickReject(left, top, right, bottom)
  }

  override fun getClipBounds(bounds: Rect): Boolean {
    return delegate.getClipBounds(bounds)
  }

  override fun drawPicture(picture: Picture) {
    delegate.drawPicture(picture)
  }

  override fun drawPicture(picture: Picture, dst: RectF) {
    delegate.drawPicture(picture, dst)
  }

  override fun drawPicture(picture: Picture, dst: Rect) {
    delegate.drawPicture(picture, dst)
  }

  override fun drawArc(
    oval: RectF,
    startAngle: Float,
    sweepAngle: Float,
    useCenter: Boolean,
    paint: Paint,
  ) {
    delegate.drawArc(oval, startAngle, sweepAngle, useCenter, paint)
  }

  override fun drawArc(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    startAngle: Float,
    sweepAngle: Float,
    useCenter: Boolean,
    paint: Paint,
  ) {
    delegate.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint)
  }

  override fun drawARGB(a: Int, r: Int, g: Int, b: Int) {
    delegate.drawARGB(a, r, g, b)
  }

  override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
    val sampledColor = sampleBitmapColor(bitmap, paint, null)
    solidPaint.setColor(sampledColor)
    delegate.drawRect(left, top, left + bitmap.width, top + bitmap.height, solidPaint)
  }

  override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
    val sampledColor = sampleBitmapColor(bitmap, paint, src)
    solidPaint.setColor(sampledColor)
    delegate.drawRect(dst, solidPaint)
  }

  override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
    val sampledColor = sampleBitmapColor(bitmap, paint, src)
    solidPaint.setColor(sampledColor)
    delegate.drawRect(dst, solidPaint)
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
    paint: Paint?,
  ) {
    // TODO
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
    paint: Paint?,
  ) {
    // TODO
  }

  override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
    val sampledColor = sampleBitmapColor(bitmap, paint, null)
    solidPaint.setColor(sampledColor)
    val count = delegate.save()
    delegate.setMatrix(matrix)
    delegate.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), solidPaint)
    delegate.restoreToCount(count)
  }

  override fun drawBitmapMesh(
    bitmap: Bitmap,
    meshWidth: Int,
    meshHeight: Int,
    verts: FloatArray,
    vertOffset: Int,
    colors: IntArray?,
    colorOffset: Int,
    paint: Paint?,
  ) {
    // TODO should we support this?
    delegate.drawBitmapMesh(
      bitmap,
      meshWidth,
      meshHeight,
      verts,
      vertOffset,
      colors,
      colorOffset,
      paint,
    )
  }

  override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
    delegate.drawCircle(cx, cy, radius, paint)
  }

  override fun drawColor(color: Int) {
    delegate.drawColor(color)
  }

  override fun drawColor(color: Long) {
    delegate.drawColor(color)
  }

  override fun drawColor(color: Int, mode: PorterDuff.Mode) {
    delegate.drawColor(color, mode)
  }

  override fun drawColor(color: Int, mode: BlendMode) {
    delegate.drawColor(color, mode)
  }

  override fun drawColor(color: Long, mode: BlendMode) {
    delegate.drawColor(color, mode)
  }

  override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
    delegate.drawLine(startX, startY, stopX, stopY, paint)
  }

  override fun drawLines(pts: FloatArray, offset: Int, count: Int, paint: Paint) {
    delegate.drawLines(pts, offset, count, paint)
  }

  override fun drawLines(pts: FloatArray, paint: Paint) {
    delegate.drawLines(pts, paint)
  }

  override fun drawOval(oval: RectF, paint: Paint) {
    delegate.drawOval(oval, paint)
  }

  override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
    delegate.drawOval(left, top, right, bottom, paint)
  }

  override fun drawPaint(paint: Paint) {
    delegate.drawPaint(paint)
  }

  override fun drawPatch(patch: NinePatch, dst: Rect, paint: Paint?) {
    delegate.drawPatch(patch, dst, paint)
  }

  override fun drawPatch(patch: NinePatch, dst: RectF, paint: Paint?) {
    delegate.drawPatch(patch, dst, paint)
  }

  override fun drawPath(path: Path, paint: Paint) {
    delegate.drawPath(path, paint)
  }

  override fun drawPoint(x: Float, y: Float, paint: Paint) {
    delegate.drawPoint(x, y, paint)
  }

  override fun drawPoints(pts: FloatArray?, offset: Int, count: Int, paint: Paint) {
    delegate.drawPoints(pts, offset, count, paint)
  }

  override fun drawPoints(pts: FloatArray, paint: Paint) {
    delegate.drawPoints(pts, paint)
  }

  override fun drawRect(rect: RectF, paint: Paint) {
    delegate.drawRect(rect, paint)
  }

  override fun drawRect(r: Rect, paint: Paint) {
    delegate.drawRect(r, paint)
  }

  override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
    delegate.drawRect(left, top, right, bottom, paint)
  }

  override fun drawRGB(r: Int, g: Int, b: Int) {
    delegate.drawRGB(r, g, b)
  }

  override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
    delegate.drawRoundRect(rect, rx, ry, paint)
  }

  override fun drawRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    rx: Float,
    ry: Float,
    paint: Paint,
  ) {
    delegate.drawRoundRect(left, top, right, bottom, rx, ry, paint)
  }

  override fun drawDoubleRoundRect(
    outer: RectF,
    outerRx: Float,
    outerRy: Float,
    inner: RectF,
    innerRx: Float,
    innerRy: Float,
    paint: Paint,
  ) {
    delegate.drawDoubleRoundRect(outer, outerRx, outerRy, inner, innerRx, innerRy, paint)
  }

  override fun drawDoubleRoundRect(
    outer: RectF,
    outerRadii: FloatArray,
    inner: RectF,
    innerRadii: FloatArray,
    paint: Paint,
  ) {
    delegate.drawDoubleRoundRect(outer, outerRadii, inner, innerRadii, paint)
  }

  override fun drawGlyphs(
    glyphIds: IntArray,
    glyphIdOffset: Int,
    positions: FloatArray,
    positionOffset: Int,
    glyphCount: Int,
    font: Font,
    paint: Paint,
  ) {
    // TODO should we support this?
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
    paint: Paint,
  ) {
    // TODO should we support this?
    delegate.drawVertices(
      mode,
      vertexCount,
      verts,
      vertOffset,
      texs,
      texOffset,
      colors,
      colorOffset,
      indices,
      indexOffset,
      indexCount,
      paint,
    )
    // TODO should we support this?
  }

  override fun drawRenderNode(renderNode: RenderNode) {
    // TODO should we support this?
    // delegate.drawRenderNode(renderNode)
  }

  override fun drawMesh(mesh: Mesh, blendMode: BlendMode?, paint: Paint) {
    // TODO should we support this?
    // delegate.drawMesh(mesh, blendMode, paint)
  }

  override fun drawPosText(text: CharArray, index: Int, count: Int, pos: FloatArray, paint: Paint) {
    // TODO implement
  }

  override fun drawPosText(text: String, pos: FloatArray, paint: Paint) {
    // TODO implement
  }

  override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
    paint.getTextBounds(text, index, count, tmpRect)
    drawMaskedText(paint, x, y)
  }

  override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
    paint.getTextBounds(text, 0, text.length, tmpRect)
    drawMaskedText(paint, x, y)
  }

  override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
    paint.getTextBounds(text, start, end, tmpRect)
    drawMaskedText(paint, x, y)
  }

  override fun drawText(
    text: CharSequence,
    start: Int,
    end: Int,
    x: Float,
    y: Float,
    paint: Paint,
  ) {
    paint.getTextBounds(text, 0, text.length, tmpRect)
    drawMaskedText(paint, x, y)
  }

  override fun drawTextOnPath(
    text: CharArray,
    index: Int,
    count: Int,
    path: Path,
    hOffset: Float,
    vOffset: Float,
    paint: Paint,
  ) {
    // TODO implement
  }

  override fun drawTextOnPath(
    text: String,
    path: Path,
    hOffset: Float,
    vOffset: Float,
    paint: Paint,
  ) {
    // TODO implement
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
    paint: Paint,
  ) {
    paint.getTextBounds(text, 0, index + count, tmpRect)
    drawMaskedText(paint, x, y)
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
    paint: Paint,
  ) {
    paint.getTextBounds(text, start, end, tmpRect)
    drawMaskedText(paint, x, y)
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
    paint: Paint,
  ) {
    text.getBounds(start, end, tmpRect)
    drawMaskedText(paint, x, y)
  }

  private fun sampleBitmapColor(bitmap: Bitmap, paint: Paint?, region: Rect?): Int {
    if (bitmap.isRecycled) {
      return Color.BLACK
    }

    val cache = bitmapColorCache[bitmap]
    if (cache != null && cache.first == bitmap.generationId) {
      return cache.second
    } else {
      singlePixelCanvas.drawBitmap(bitmap.asShared(), region, singlePixelBitmapBounds, paint)
      val color = singlePixelBitmap.getPixel(0, 0)
      bitmapColorCache[bitmap] = Pair(bitmap.generationId, color)
      return color
    }
  }

  private fun drawMaskedText(paint: Paint, x: Float, y: Float) {
    textPaint.colorFilter = paint.colorFilter
    val color = paint.color
    textPaint.color = Color.argb(100, Color.red(color), Color.green(color), Color.blue(color))
    drawRoundRect(
      tmpRect.left.toFloat() + x,
      tmpRect.top.toFloat() + y,
      tmpRect.right.toFloat() + x,
      tmpRect.bottom.toFloat() + y,
      10f,
      10f,
      textPaint,
    )
  }
}

private class PictureReaderHolder(
  val width: Int,
  val height: Int,
  val listener: (holder: PictureReaderHolder) -> Unit,
) : ImageReader.OnImageAvailableListener {
  val picture = Picture()

  @SuppressLint("InlinedApi")
  val reader: ImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)

  var setup = AtomicBoolean(false)

  override fun onImageAvailable(reader: ImageReader?) {
    if (reader != null) {
      listener(this)
    }
  }
}
