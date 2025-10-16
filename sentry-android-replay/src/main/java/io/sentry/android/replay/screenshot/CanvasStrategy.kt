@file:Suppress("DEPRECATION")

package io.sentry.android.replay.screenshot

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapShader
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
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryReplayOptions
import io.sentry.SentryReplayOptions.IMAGE_VIEW_CLASS_NAME
import io.sentry.SentryReplayOptions.TEXT_VIEW_CLASS_NAME
import io.sentry.android.replay.ExecutorProvider
import io.sentry.android.replay.ScreenshotRecorderCallback
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.util.submitSafely
import io.sentry.util.AutoClosableReentrantLock
import io.sentry.util.IntegrationUtils
import java.io.Closeable
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
  private val textIgnoringCanvas = TextIgnoringDelegateCanvas(options.sessionReplay)

  private val isClosed = AtomicBoolean(false)

  private val onImageAvailableListener: (holder: PictureReaderHolder) -> Unit = { holder ->
    if (isClosed.get()) {
      options.logger.log(SentryLevel.ERROR, "CanvasStrategy already closed, skipping image")
      holder.close()
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
          try {
            image.close()
          } catch (_: Throwable) {
            // ignored
          }
        }
      } catch (e: Throwable) {
        options.logger.log(SentryLevel.ERROR, "CanvasStrategy: image processing failed", e)
      } finally {
        if (isClosed.get()) {
          holder.close()
        } else {
          freePictureRef.set(holder)
        }
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
    val holder = unprocessedPictureRef.getAndSet(null) ?: return@Runnable

    try {
      if (!holder.setup.getAndSet(true)) {
        holder.reader.setOnImageAvailableListener(holder, executor.getBackgroundHandler())
      }

      val surface = holder.reader.surface
      val canvas = surface.lockHardwareCanvas()
      try {
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
        holder.picture.draw(canvas)
      } finally {
        surface.unlockCanvasAndPost(canvas)
      }
    } catch (t: Throwable) {
      if (isClosed.get()) {
        holder.close()
      } else {
        freePictureRef.set(holder)
      }
      options.logger.log(SentryLevel.ERROR, "Canvas Strategy: picture render failed", t)
    }
  }

  @SuppressLint("UnclosedTrace")
  override fun capture(root: View) {
    if (isClosed.get()) {
      return
    }
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

    if (isClosed.get()) {
      holder.close()
    } else {
      unprocessedPictureRef.set(holder)
      executor.getExecutor().submitSafely(options, "screenshot_recorder.canvas", pictureRenderTask)
    }
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
      screenshot = null
    }
    // the image can be free, unprocessed or in transit
    freePictureRef.getAndSet(null)?.reader?.close()
    unprocessedPictureRef.getAndSet(null)?.reader?.close()
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

@SuppressLint("UseKtx")
private class TextIgnoringDelegateCanvas(sessionReplay: SentryReplayOptions) : Canvas() {

  lateinit var delegate: Canvas
  private val solidPaint = Paint()
  private val textPaint = Paint()
  private val tmpRect = Rect()

  val singlePixelBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
  val singlePixelCanvas = Canvas(singlePixelBitmap)

  val singlePixelBitmapBounds = Rect(0, 0, 1, 1)

  private val bitmapColorCache = WeakHashMap<Bitmap, Pair<Int, Int>>()

  private val maskAllText =
    sessionReplay.maskViewClasses.contains(TEXT_VIEW_CLASS_NAME) ||
      sessionReplay.maskViewClasses.size > 1
  private val maskAllImages =
    sessionReplay.maskViewClasses.contains(IMAGE_VIEW_CLASS_NAME) ||
      sessionReplay.maskViewClasses.size > 1

  override fun isHardwareAccelerated(): Boolean {
    return false
  }

  override fun setBitmap(bitmap: Bitmap?) {
    delegate.setBitmap(bitmap)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  override fun enableZ() {
    delegate.enableZ()
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  override fun disableZ() {
    delegate.disableZ()
  }

  override fun isOpaque(): Boolean {
    return delegate.isOpaque()
  }

  override fun getWidth(): Int {
    return delegate.width
  }

  override fun getHeight(): Int {
    return delegate.height
  }

  override fun getDensity(): Int {
    return delegate.density
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

  @Suppress("unused")
  fun save(saveFlags: Int): Int {
    return save()
  }

  @Deprecated("Deprecated in Java")
  override fun saveLayer(bounds: RectF?, paint: Paint?, saveFlags: Int): Int {
    return delegate.saveLayer(bounds, paint, saveFlags)
  }

  override fun saveLayer(bounds: RectF?, paint: Paint?): Int {
    val shader = removeBitmapShader(paint)
    val result = delegate.saveLayer(bounds, paint)
    shader.let { paint?.shader = it }
    return result
  }

  @Deprecated("Deprecated in Java")
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
    val shader = removeBitmapShader(paint)
    val result = delegate.saveLayer(left, top, right, bottom, paint)
    shader.let { paint?.shader = it }
    return result
  }

  @Deprecated("Deprecated in Java")
  override fun saveLayerAlpha(bounds: RectF?, alpha: Int, saveFlags: Int): Int {
    return delegate.saveLayerAlpha(bounds, alpha, saveFlags)
  }

  override fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int {
    return delegate.saveLayerAlpha(bounds, alpha)
  }

  @Deprecated("Deprecated in Java")
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

  @Deprecated("Deprecated in Java")
  override fun getMatrix(ctm: Matrix) {
    delegate.getMatrix(ctm)
  }

  @Deprecated("Deprecated in Java")
  override fun clipRect(rect: RectF, op: Region.Op): Boolean {
    return delegate.clipRect(rect, op)
  }

  @Deprecated("Deprecated in Java")
  override fun clipRect(rect: Rect, op: Region.Op): Boolean {
    return delegate.clipRect(rect, op)
  }

  override fun clipRect(rect: RectF): Boolean {
    return delegate.clipRect(rect)
  }

  override fun clipRect(rect: Rect): Boolean {
    return delegate.clipRect(rect)
  }

  @Deprecated("Deprecated in Java")
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

  @RequiresApi(Build.VERSION_CODES.O)
  override fun clipOutRect(rect: RectF): Boolean {
    return delegate.clipOutRect(rect)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  override fun clipOutRect(rect: Rect): Boolean {
    return delegate.clipOutRect(rect)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  override fun clipOutRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
    return delegate.clipOutRect(left, top, right, bottom)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  override fun clipOutRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
    return delegate.clipOutRect(left, top, right, bottom)
  }

  @Deprecated("Deprecated in Java")
  override fun clipPath(path: Path, op: Region.Op): Boolean {
    return delegate.clipPath(path, op)
  }

  override fun clipPath(path: Path): Boolean {
    return delegate.clipPath(path)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  override fun clipOutPath(path: Path): Boolean {
    return delegate.clipOutPath(path)
  }

  override fun getDrawFilter(): DrawFilter? {
    return delegate.drawFilter
  }

  override fun setDrawFilter(filter: DrawFilter?) {
    delegate.setDrawFilter(filter)
  }

  @Deprecated("Deprecated in Java")
  override fun quickReject(rect: RectF, type: EdgeType): Boolean {
    return delegate.quickReject(rect, type)
  }

  @RequiresApi(Build.VERSION_CODES.R)
  override fun quickReject(rect: RectF): Boolean {
    return delegate.quickReject(rect)
  }

  @Deprecated("Deprecated in Java")
  override fun quickReject(path: Path, type: EdgeType): Boolean {
    return delegate.quickReject(path, type)
  }

  @RequiresApi(Build.VERSION_CODES.R)
  override fun quickReject(path: Path): Boolean {
    return delegate.quickReject(path)
  }

  @Deprecated("Deprecated in Java")
  override fun quickReject(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    type: EdgeType,
  ): Boolean {
    return delegate.quickReject(left, top, right, bottom, type)
  }

  @RequiresApi(Build.VERSION_CODES.R)
  override fun quickReject(left: Float, top: Float, right: Float, bottom: Float): Boolean {
    return delegate.quickReject(left, top, right, bottom)
  }

  override fun getClipBounds(bounds: Rect): Boolean {
    return delegate.getClipBounds(bounds)
  }

  override fun drawPicture(picture: Picture) {
    solidPaint.colorFilter = null
    solidPaint.color = Color.BLACK
    delegate.drawRect(0f, 0f, picture.width.toFloat(), picture.height.toFloat(), solidPaint)
  }

  override fun drawPicture(picture: Picture, dst: RectF) {
    solidPaint.colorFilter = null
    solidPaint.color = Color.BLACK
    delegate.drawRect(dst, solidPaint)
  }

  override fun drawPicture(picture: Picture, dst: Rect) {
    solidPaint.colorFilter = null
    solidPaint.color = Color.BLACK
    delegate.drawRect(dst, solidPaint)
  }

  override fun drawArc(
    oval: RectF,
    startAngle: Float,
    sweepAngle: Float,
    useCenter: Boolean,
    paint: Paint,
  ) {
    val shader = removeBitmapShader(paint)
    delegate.drawArc(oval, startAngle, sweepAngle, useCenter, paint)
    shader.let { paint.shader = it }
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
    val shader = removeBitmapShader(paint)
    delegate.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint)
    shader.let { paint.shader = it }
  }

  override fun drawARGB(a: Int, r: Int, g: Int, b: Int) {
    delegate.drawARGB(a, r, g, b)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
    val sampledColor = sampleBitmapColor(bitmap, paint, null)
    solidPaint.setColor(sampledColor)
    solidPaint.colorFilter = null
    delegate.drawRect(left, top, left + bitmap.width, top + bitmap.height, solidPaint)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
    val sampledColor = sampleBitmapColor(bitmap, paint, src)
    solidPaint.setColor(sampledColor)
    solidPaint.colorFilter = null
    delegate.drawRect(dst, solidPaint)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
    val sampledColor = sampleBitmapColor(bitmap, paint, src)
    solidPaint.setColor(sampledColor)
    solidPaint.colorFilter = null
    delegate.drawRect(dst, solidPaint)
  }

  @Deprecated("Deprecated in Java")
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
    // not supported
  }

  @Deprecated("Deprecated in Java")
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
    // not supported
  }

  @RequiresApi(Build.VERSION_CODES.O)
  override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
    val sampledColor = sampleBitmapColor(bitmap, paint, null)
    solidPaint.setColor(sampledColor)
    solidPaint.colorFilter = null

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
    // not supported
  }

  override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawCircle(cx, cy, radius, paint)
    shader.let { paint.shader = it }
  }

  override fun drawColor(color: Int) {
    delegate.drawColor(color)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  override fun drawColor(color: Long) {
    delegate.drawColor(color)
  }

  override fun drawColor(color: Int, mode: PorterDuff.Mode) {
    delegate.drawColor(color, mode)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  override fun drawColor(color: Int, mode: BlendMode) {
    delegate.drawColor(color, mode)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  override fun drawColor(color: Long, mode: BlendMode) {
    delegate.drawColor(color, mode)
  }

  override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawLine(startX, startY, stopX, stopY, paint)
    shader.let { paint.shader = it }
  }

  override fun drawLines(pts: FloatArray, offset: Int, count: Int, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawLines(pts, offset, count, paint)
    shader.let { paint.shader = it }
  }

  override fun drawLines(pts: FloatArray, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawLines(pts, paint)
    shader.let { paint.shader = it }
  }

  override fun drawOval(oval: RectF, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawOval(oval, paint)
    shader.let { paint.shader = it }
  }

  override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawOval(left, top, right, bottom, paint)
    shader.let { paint.shader = it }
  }

  override fun drawPaint(paint: Paint) {
    delegate.drawPaint(paint)
  }

  @RequiresApi(Build.VERSION_CODES.S)
  override fun drawPatch(patch: NinePatch, dst: Rect, paint: Paint?) {
    val shader = removeBitmapShader(paint)
    delegate.drawPatch(patch, dst, paint)
    shader.let { paint?.shader = it }
  }

  @RequiresApi(Build.VERSION_CODES.S)
  override fun drawPatch(patch: NinePatch, dst: RectF, paint: Paint?) {
    val shader = removeBitmapShader(paint)
    delegate.drawPatch(patch, dst, paint)
    shader.let { paint?.shader = it }
  }

  override fun drawPath(path: Path, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawPath(path, paint)
    shader.let { paint.shader = it }
  }

  override fun drawPoint(x: Float, y: Float, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawPoint(x, y, paint)
    shader.let { paint.shader = it }
  }

  override fun drawPoints(pts: FloatArray?, offset: Int, count: Int, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawPoints(pts, offset, count, paint)
    shader.let { paint.shader = it }
  }

  override fun drawPoints(pts: FloatArray, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawPoints(pts, paint)
    shader.let { paint.shader = it }
  }

  override fun drawRect(rect: RectF, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawRect(rect, paint)
    shader.let { paint.shader = it }
  }

  override fun drawRect(r: Rect, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawRect(r, paint)
    shader.let { paint.shader = it }
  }

  override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawRect(left, top, right, bottom, paint)
    shader.let { paint.shader = it }
  }

  override fun drawRGB(r: Int, g: Int, b: Int) {
    delegate.drawRGB(r, g, b)
  }

  override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
    val shader = removeBitmapShader(paint)
    delegate.drawRoundRect(rect, rx, ry, paint)
    shader.let { paint.shader = it }
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
    val shader = removeBitmapShader(paint)
    delegate.drawRoundRect(left, top, right, bottom, rx, ry, paint)
    shader.let { paint.shader = it }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  override fun drawDoubleRoundRect(
    outer: RectF,
    outerRx: Float,
    outerRy: Float,
    inner: RectF,
    innerRx: Float,
    innerRy: Float,
    paint: Paint,
  ) {
    val shader = removeBitmapShader(paint)
    delegate.drawDoubleRoundRect(outer, outerRx, outerRy, inner, innerRx, innerRy, paint)
    shader.let { paint.shader = it }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  override fun drawDoubleRoundRect(
    outer: RectF,
    outerRadii: FloatArray,
    inner: RectF,
    innerRadii: FloatArray,
    paint: Paint,
  ) {
    val shader = removeBitmapShader(paint)
    delegate.drawDoubleRoundRect(outer, outerRadii, inner, innerRadii, paint)
    shader.let { paint.shader = it }
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
    // not supported
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
    // not supported
  }

  override fun drawRenderNode(renderNode: RenderNode) {
    // not supported
  }

  override fun drawMesh(mesh: Mesh, blendMode: BlendMode?, paint: Paint) {
    // not supported
  }

  @Deprecated("Deprecated in Java")
  override fun drawPosText(text: CharArray, index: Int, count: Int, pos: FloatArray, paint: Paint) {
    // not supported
  }

  @Deprecated("Deprecated in Java")
  override fun drawPosText(text: String, pos: FloatArray, paint: Paint) {
    // not supported
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
    paint.getTextBounds(text.toString(), 0, text.length, tmpRect)
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
    // not supported
  }

  override fun drawTextOnPath(
    text: String,
    path: Path,
    hOffset: Float,
    vOffset: Float,
    paint: Paint,
  ) {
    // not supported
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
    paint.getTextBounds(text.toString(), start, end, tmpRect)
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
    paint.getTextBounds(text.toString(), start, end, tmpRect)
    drawMaskedText(paint, x, y)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun sampleBitmapColor(bitmap: Bitmap, paint: Paint?, src: Rect?): Int {
    if (bitmap.isRecycled) {
      return Color.BLACK
    }

    val cache = bitmapColorCache[bitmap]
    if (cache != null && cache.first == bitmap.generationId) {
      return cache.second
    } else {
      val color =
        if (
          bitmap.config == Bitmap.Config.HARDWARE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
          // bitmap.asShared() ensures that the bitmap, even if it is hardware bitmap,
          // can be drawn onto the single pixel software canvas
          val shader = removeBitmapShader(paint)
          singlePixelCanvas.drawBitmap(bitmap.asShared(), src, singlePixelBitmapBounds, paint)
          shader?.let { paint?.shader = it }
          singlePixelBitmap.getPixel(0, 0)
        } else if (bitmap.config != Bitmap.Config.HARDWARE) {
          // fallback for older android versions
          val shader = removeBitmapShader(paint)
          singlePixelCanvas.drawBitmap(bitmap, src, singlePixelBitmapBounds, paint)
          shader?.let { paint?.shader = it }
          singlePixelBitmap.getPixel(0, 0)
        } else {
          // fallback for older android versions
          Color.BLACK
        }
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

  private fun removeBitmapShader(paint: Paint?): BitmapShader? {
    return if (paint == null) {
      null
    } else {
      val shader = paint.shader
      if (shader is BitmapShader) {
        paint.shader = null
        shader
      } else {
        null
      }
    }
  }
}

private class PictureReaderHolder(
  val width: Int,
  val height: Int,
  val listener: (holder: PictureReaderHolder) -> Unit,
) : ImageReader.OnImageAvailableListener, Closeable {
  val picture = Picture()

  @SuppressLint("InlinedApi")
  val reader: ImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)

  var setup = AtomicBoolean(false)

  override fun onImageAvailable(reader: ImageReader?) {
    if (reader != null) {
      listener(this)
    }
  }

  override fun close() {
    try {
      reader.close()
    } catch (_: Throwable) {
      // ignored
    }
  }
}
