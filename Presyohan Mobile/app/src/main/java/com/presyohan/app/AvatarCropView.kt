package com.presyohan.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class AvatarCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var originalBitmap: Bitmap? = null

    // Layout configuration
    private val viewportRect = RectF()
    private var cropSize = 250f // Initial size, will be dynamically calculated based on view dimensions
    private val outputSize = 512 // Output cropped bitmap resolution

    // Image position/scale state
    private var transX = 0f
    private var transY = 0f
    private var scale = 1f
    private var minScale = 1f

    // Touch gesture helpers
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    // Scale detector
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newScale = scale * scaleFactor
            
            // Limit scale between minScale and 5 * minScale
            val targetScale = max(minScale, min(newScale, minScale * 5f))
            if (targetScale != scale) {
                val focusX = detector.focusX
                val focusY = detector.focusY
                
                val ratio = targetScale / scale
                val newTransX = focusX - (focusX - transX) * ratio
                val newTransY = focusY - (focusY - transY) * ratio
                
                scale = targetScale
                transX = newTransX
                transY = newTransY
                
                clampTranslation()
                invalidate()
            }
            return true
        }
    })

    // Paint objects
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3000000") // 70% black overlay
    }
    
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF") // Subtle transparent white
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private val clipPath = Path()

    init {
        // Required for PorterDuff CLEAR xfermode overlay masking
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setBitmap(bitmap: Bitmap) {
        this.originalBitmap = bitmap
        initializeImagePosition()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Dynamically compute crop size: 80% of the minimum of width and height
        cropSize = min(w, h) * 0.8f
        
        // Center the viewport rect
        viewportRect.set(
            (w - cropSize) / 2f,
            (h - cropSize) / 2f,
            (w + cropSize) / 2f,
            (h + cropSize) / 2f
        )
        
        initializeImagePosition()
    }

    private fun initializeImagePosition() {
        val bitmap = originalBitmap ?: return
        if (width == 0 || height == 0) return

        // Compute min scale to fully cover the viewport
        minScale = max(cropSize / bitmap.width, cropSize / bitmap.height)
        scale = minScale

        // Center the image in the viewport
        transX = viewportRect.left + (cropSize - bitmap.width * scale) / 2f
        transY = viewportRect.top + (cropSize - bitmap.height * scale) / 2f
        
        clampTranslation()
    }

    private fun clampTranslation() {
        val bitmap = originalBitmap ?: return
        
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale

        // Clamping to make sure viewport is always completely covered by the image
        val maxX = viewportRect.left
        val minX = viewportRect.right - scaledWidth
        transX = max(minX, min(maxX, transX))

        val maxY = viewportRect.top
        val minY = viewportRect.bottom - scaledHeight
        transY = max(minY, min(maxY, transY))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        // Skip drag handling if scaling is in progress
        if (scaleDetector.isInProgress) {
            isDragging = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    
                    transX += dx
                    transY += dy
                    
                    clampTranslation()
                    invalidate()
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = originalBitmap ?: return

        // 1. Draw the image with current scale and offset
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(transX, transY)
        }
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)

        // 2. Draw black semi-transparent overlay with a circular hole
        // We use saved layer to allow CLEAR xfermode to punch a hole through the overlay color
        val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawCircle(viewportRect.centerX(), viewportRect.centerY(), cropSize / 2f, clearPaint)
        canvas.restoreToCount(count)

        // 3. Draw circle white outline border
        canvas.drawCircle(viewportRect.centerX(), viewportRect.centerY(), cropSize / 2f, borderPaint)

        // 4. Draw 3x3 crop grid inside the circular viewport
        canvas.save()
        clipPath.reset()
        clipPath.addCircle(viewportRect.centerX(), viewportRect.centerY(), cropSize / 2f, Path.Direction.CW)
        canvas.clipPath(clipPath)
        
        val gridStep = cropSize / 3f
        val x1 = viewportRect.left + gridStep
        val x2 = viewportRect.left + 2f * gridStep
        val y1 = viewportRect.top + gridStep
        val y2 = viewportRect.top + 2f * gridStep

        canvas.drawLine(x1, viewportRect.top, x1, viewportRect.bottom, gridPaint)
        canvas.drawLine(x2, viewportRect.top, x2, viewportRect.bottom, gridPaint)
        canvas.drawLine(viewportRect.left, y1, viewportRect.right, y1, gridPaint)
        canvas.drawLine(viewportRect.left, y2, viewportRect.right, y2, gridPaint)
        
        canvas.restore()
    }

    fun getCroppedBitmap(): Bitmap? {
        val bitmap = originalBitmap ?: return null
        
        // Calculate the crop source rectangle in original image coordinates
        val srcLeft = (viewportRect.left - transX) / scale
        val srcTop = (viewportRect.top - transY) / scale
        val srcWidth = cropSize / scale
        val srcHeight = cropSize / scale

        // Create the output square cropped bitmap
        val cropped = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        
        val srcRect = Rect(
            srcLeft.toInt().coerceAtLeast(0),
            srcTop.toInt().coerceAtLeast(0),
            (srcLeft + srcWidth).toInt().coerceAtMost(bitmap.width),
            (srcTop + srcHeight).toInt().coerceAtMost(bitmap.height)
        )
        val destRect = Rect(0, 0, outputSize, outputSize)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)
        
        return cropped
    }
}
