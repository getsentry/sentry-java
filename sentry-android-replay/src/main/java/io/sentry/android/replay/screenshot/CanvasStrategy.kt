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
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.view.View
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.replay.ScreenshotRecorderCallback
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.submitSafely
import java.util.LinkedList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.NONE

@SuppressLint("UseKtx")
internal class CanvasStrategy(
  private val executor: ScheduledExecutorService,
  private val screenshotRecorderCallback: ScreenshotRecorderCallback?,
  private val options: SentryOptions,
  private val config: ScreenshotRecorderConfig,
) : ScreenshotStrategy {

  private var screenshot: Bitmap? = null
  private val prescaledMatrix by
    lazy(NONE) { Matrix().apply { preScale(config.scaleFactorX, config.scaleFactorY) } }
  private val lastCaptureSuccessful = AtomicBoolean(false)
  private val textIgnoringCanvas = TextIgnoringDelegateCanvas()
  private var cachedPixels: IntArray? = null

  private val freePictures: MutableList<PictureReaderHolder> =
    LinkedList(
      listOf(
        PictureReaderHolder(config.recordingWidth, config.recordingHeight),
        PictureReaderHolder(config.recordingWidth, config.recordingHeight),
      )
    )
  private val unprocessedPictures: MutableList<PictureReaderHolder> = LinkedList()

  private val pictureRenderTask = Runnable {
    val holder: PictureReaderHolder? =
      synchronized(unprocessedPictures) {
        when {
          unprocessedPictures.isNotEmpty() -> unprocessedPictures.removeAt(0)
          else -> null
        }
      }
    if (holder == null) {
      return@Runnable
    }
    try {
      holder.reader.setOnImageAvailableListener(
        {
          val hwImage = it?.acquireLatestImage()
          if (hwImage != null) {
            val hwScreenshot = toBitmap(hwImage)
            screenshot = hwScreenshot
            screenshotRecorderCallback?.onScreenshotRecorded(hwScreenshot)
          } else {
            options.logger.log(SentryLevel.DEBUG, "Canvas Strategy: hardware image is null")
          }
        },
        Handler(Looper.getMainLooper()),
      )

      val surface = holder.reader.surface
      val canvas = surface.lockHardwareCanvas()
      canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
      holder.picture.draw(canvas)
      surface.unlockCanvasAndPost(canvas)
    } finally {
      synchronized(freePictures) { freePictures.add(holder) }
    }
  }

  override fun capture(root: View) {
    val holder: PictureReaderHolder? =
      synchronized(freePictures) {
        when {
          freePictures.isNotEmpty() -> freePictures.removeAt(0)
          else -> null
        }
      }
    if (holder == null) {
      options.logger.log(SentryLevel.DEBUG, "No free Picture available, skipping capture")
      executor.submitSafely(options, "screenshot_recorder.canvas", pictureRenderTask)
      return
    }

    val pictureCanvas = holder.picture.beginRecording(root.width, root.height)
    textIgnoringCanvas.delegate = pictureCanvas
    textIgnoringCanvas.setMatrix(prescaledMatrix)
    root.draw(textIgnoringCanvas)
    holder.picture.endRecording()

    synchronized(unprocessedPictures) { unprocessedPictures.add(holder) }

    executor.submitSafely(options, "screenshot_recorder.canvas", pictureRenderTask)
  }

  override fun onContentChanged() {
    // ignored
  }

  override fun close() {
    screenshot?.apply {
      if (!isRecycled) {
        recycle()
      }
    }
  }

  override fun lastCaptureSuccessful(): Boolean {
    return lastCaptureSuccessful.get()
  }

  override fun emitLastScreenshot() {
    if (lastCaptureSuccessful()) {
      screenshot?.let { screenshotRecorderCallback?.onScreenshotRecorded(it) }
    }
  }

  private fun toBitmap(image: Image): Bitmap {
    image.planes!!.let {
      val plane = it[0]
      val pixelCount = image.width * image.height

      val pixels =
        synchronized(this) {
          if (cachedPixels == null || cachedPixels!!.size != pixelCount) {
            cachedPixels = IntArray(pixelCount)
          }
          cachedPixels!!
        }

      // Do a bulk copy as it is more efficient than buffer.get then rearrange the pixels
      // in memory from RGBA to ARGB for bitmap consumption
      plane.buffer.asIntBuffer().get(pixels)
      for (i in 0 until pixelCount) {
        val color = pixels[i]
        val red = color and 0xff
        val green = color shr 8 and 0xff
        val blue = color shr 16 and 0xff
        val alpha = color shr 24 and 0xff
        pixels[i] = Color.argb(alpha, red, green, blue)
      }
      return Bitmap.createBitmap(
        pixels,
        image.width,
        image.height,
        android.graphics.Bitmap.Config.ARGB_8888,
      )
    }
  }
}

@SuppressLint("NewApi", "UseKtx")
private class TextIgnoringDelegateCanvas : Canvas() {

  lateinit var delegate: Canvas
  private val solidPaint = Paint()
  private var colorSamplingReader: ImageReader? = null

  override fun isHardwareAccelerated(): Boolean {
    return true
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
    return delegate.save()
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
  }

  override fun drawRenderNode(renderNode: RenderNode) {
    // TODO should we support this?
    delegate.drawRenderNode(renderNode)
  }

  override fun drawMesh(mesh: Mesh, blendMode: BlendMode?, paint: Paint) {
    // TODO should we support this?
    delegate.drawMesh(mesh, blendMode, paint)
  }

  override fun drawPosText(text: CharArray, index: Int, count: Int, pos: FloatArray, paint: Paint) {
    // ignored
  }

  override fun drawPosText(text: String, pos: FloatArray, paint: Paint) {
    // ignored
  }

  override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
    // ignored
  }

  override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
    // ignored
  }

  override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
    // ignored
  }

  override fun drawText(
    text: CharSequence,
    start: Int,
    end: Int,
    x: Float,
    y: Float,
    paint: Paint,
  ) {
    // ignored
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
    // ignored
  }

  override fun drawTextOnPath(
    text: String,
    path: Path,
    hOffset: Float,
    vOffset: Float,
    paint: Paint,
  ) {
    // ignored
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
    // ignored
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
    // ignored
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
    // ignored
  }

  private fun getOrCreateColorSamplingReader(): ImageReader {
    var reader = colorSamplingReader
    if (reader == null) {
      reader = ImageReader.newInstance(1, 1, PixelFormat.RGBA_8888, 1)
      colorSamplingReader = reader
    }
    return reader
  }

  private fun sampleBitmapColor(bitmap: Bitmap, paint: Paint?, region: Rect?): Int {
    val reader = getOrCreateColorSamplingReader()
    val surface = reader.surface
    val canvas = surface.lockHardwareCanvas()

    val left = region?.left ?: 0
    val top = region?.top ?: 0
    val right = region?.right ?: (left + bitmap.width)
    val bottom = region?.bottom ?: (top + bitmap.height)
    try {
      // Clear and draw the bitmap scaled to 1x1
      canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
      canvas.drawBitmap(bitmap, Rect(left, top, right, bottom), Rect(0, 0, 1, 1), paint)
    } finally {
      surface.unlockCanvasAndPost(canvas)
    }

    // Blocking read - this is the synchronous part
    val image = reader.acquireLatestImage()
    return if (image != null) {
      try {
        image.planes!!.let {
          val plane = it[0]
          val pixel = plane.buffer.asIntBuffer().get(0)
          val red = pixel and 0xff
          val green = pixel shr 8 and 0xff
          val blue = pixel shr 16 and 0xff
          val alpha = pixel shr 24 and 0xff
          Color.argb(alpha, red, green, blue)
        }
      } finally {
        image.close()
      }
    } else {
      // Fallback to direct sampling if hardware fails
      fallbackSampleBitmapColor(bitmap)
    }
  }

  private fun fallbackSampleBitmapColor(bitmap: Bitmap): Int {
    val w = bitmap.width - 1
    val h = bitmap.height - 1

    val colors =
      intArrayOf(
        bitmap.getPixel(0, 0), // left-top
        bitmap.getPixel(w, 0), // right-top
        bitmap.getPixel(0, h), // left-bottom
        bitmap.getPixel(w, h), // right-bottom
        bitmap.getPixel(w / 2, h / 2), // center
      )

    var r = 0
    var g = 0
    var b = 0
    var a = 0
    colors.forEach { color ->
      r += Color.red(color)
      g += Color.green(color)
      b += Color.blue(color)
      a += Color.alpha(color)
    }
    val l = colors.size

    return Color.argb(a / l, r / l, g / l, b / l)
  }
}

private data class PictureReaderHolder(val width: Int, val height: Int) {
  val picture = Picture()
  val reader: ImageReader
    get() = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
}
