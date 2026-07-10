package com.presyohan.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Shared model used by PdfPreviewDialogHelper ──────────────────────────────
data class PdfPriceItem(
    val category: String,
    val name: String,
    val price: Double,
    val unit: String = "",
    val description: String = ""
)

data class PageCategoryBox(
    val categoryName: String,
    val isContinuation: Boolean,
    val items: MutableList<PdfPriceItem> = mutableListOf()
)

enum class PdfPageSize(
    val labelName: String,
    /** Width in points at 72dpi — used by PdfDocument (Android native) */
    val widthPt: Int,
    val heightPt: Int
) {
    LONG_BOND("Long Bond (8.5\" × 13\")", 612, 936),
    SHORT_BOND("Short Bond (8.5\" × 11\")", 612, 792);
}

// ── Colors & Style Tokens ─────────────────────────────────────────────────────
private val COLOR_CREAM         = Color.parseColor("#FFFBEF") // Pale cream background
private val COLOR_OUTER_BORDER  = Color.parseColor("#0A9396") // Teal outer border
private val COLOR_CATEGORY_TEXT = Color.parseColor("#219EBC") // Teal category headers
private val COLOR_ORANGE        = Color.parseColor("#FB8500") // Store name, titles, items
private val COLOR_PRICE         = Color.parseColor("#023047") // Premium dark blue/teal price
private val COLOR_GRAY_TEXT     = Color.parseColor("#777777") // Description and unit
private val COLOR_BOX_BORDER    = Color.parseColor("#D3D3D3") // Light gray category container border
private val COLOR_WHITE         = Color.WHITE

object PricelistPdfHelper {

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: Build the PdfDocument from a list of items
    // ─────────────────────────────────────────────────────────────────────────
    fun generatePdf(
        context: Context,
        items: List<PdfPriceItem>,
        storeName: String,
        branchName: String,
        pageSize: PdfPageSize
    ): PdfDocument {
        val doc = PdfDocument()

        val typefaceBold = try {
            Typeface.createFromAsset(context.assets, "BalsamiqSans-Bold.ttf")
        } catch (e: Exception) {
            Typeface.DEFAULT_BOLD
        }
        val typefaceRegular = try {
            Typeface.createFromAsset(context.assets, "BalsamiqSans-Regular.ttf")
        } catch (e: Exception) {
            Typeface.DEFAULT
        }

        val COLOR_BRAND_YELLOW = Color.parseColor("#FFB703")

        // Page dimensions
        val pw = pageSize.widthPt.toFloat()
        val ph = pageSize.heightPt.toFloat()
        val marginH = 32f
        val borderTop = 68f
        val borderBottom = ph - 55f

        val contentTop = borderTop + 36f
        val contentBottom = borderBottom - 16f
        val availableH = contentBottom - contentTop
        val boxSpacing = 16f

        // Formatting
        val priceFormat = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

        // Paints
        val paintCreamBg = Paint().apply {
            color = COLOR_CREAM
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val paintOuterBorder = Paint().apply {
            color = COLOR_OUTER_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        val paintBoxBg = Paint().apply {
            color = COLOR_WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val paintBoxBorder = Paint().apply {
            color = COLOR_BOX_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }
        val paintCategoryName = Paint().apply {
            color = COLOR_CATEGORY_TEXT
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintItemName = Paint().apply {
            color = COLOR_ORANGE
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintItemPrice = Paint().apply {
            color = COLOR_PRICE
            textSize = 11f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val paintItemDesc = android.text.TextPaint().apply {
            color = COLOR_GRAY_TEXT
            textSize = 8.5f
            isAntiAlias = true
        }
        val paintItemUnit = Paint().apply {
            color = COLOR_GRAY_TEXT
            textSize = 8.5f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val paintDash = Paint().apply {
            color = COLOR_BOX_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
            isAntiAlias = true
        }
        val paintStoreName = Paint().apply {
            color = COLOR_ORANGE
            textSize = 18f
            typeface = typefaceBold
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val paintStoreBranch = Paint().apply {
            color = COLOR_CATEGORY_TEXT
            textSize = 10f
            typeface = typefaceBold
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val paintPricelistTitle = Paint().apply {
            color = COLOR_ORANGE
            textSize = 14f
            typeface = typefaceBold
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val paintAtong = Paint().apply {
            color = COLOR_BRAND_YELLOW
            textSize = 5.0f
            typeface = typefaceBold
            isAntiAlias = true
        }
        val paintPresyo = Paint().apply {
            color = COLOR_ORANGE
            textSize = 13f
            typeface = typefaceBold
            isAntiAlias = true
        }
        val paintHan = Paint().apply {
            color = COLOR_CATEGORY_TEXT
            textSize = 13f
            typeface = typefaceBold
            isAntiAlias = true
        }
        val paintPageCounter = Paint().apply {
            color = COLOR_GRAY_TEXT
            textSize = 8.5f
            isAntiAlias = true
        }

        // Group and sort items
        val grouped = items.groupBy { it.category.trim().ifBlank { "General" } }
        val sortedCategories = grouped.keys.sortedBy { it.lowercase() }

        // Helper to calculate height of a category box with its current items plus an optional additional item
        fun calculateBoxHeight(box: PageCategoryBox, additionalItem: PdfPriceItem? = null): Float {
            var h = 12f + 12f // top padding (12f) + title text height (12f)
            h += 8f           // space from title to dashed line
            h += 10f          // space from dashed line to first item
            val allItems = if (additionalItem != null) box.items + additionalItem else box.items
            for ((idx, item) in allItems.withIndex()) {
                val hasSecond = item.description.isNotBlank() || item.unit.isNotBlank()
                val itemContentH = if (hasSecond) {
                    11f + 2f + 8.5f // first line (11f) + gap (2f) + second line (8.5f)
                } else {
                    11f // first line only
                }
                val spacing = if (idx == allItems.size - 1) 12f else 8f // bottom padding for last item, else spacing to next
                h += itemContentH + spacing
            }
            return h
        }

        fun calculatePageHeight(boxes: List<PageCategoryBox>): Float {
            if (boxes.isEmpty()) return 0f
            var sum = 0f
            for (box in boxes) {
                sum += calculateBoxHeight(box)
            }
            return sum + (boxes.size - 1) * boxSpacing
        }

        // Pagination algorithm
        val pages = mutableListOf<MutableList<PageCategoryBox>>()
        var currentPageBoxes = mutableListOf<PageCategoryBox>()

        for (category in sortedCategories) {
            val catItems = (grouped[category] ?: emptyList()).sortedBy { it.name.lowercase() }
            if (catItems.isEmpty()) continue

            for (item in catItems) {
                val activeBox = currentPageBoxes.lastOrNull()
                if (activeBox != null && activeBox.categoryName == category) {
                    activeBox.items.add(item)
                    val totalH = calculatePageHeight(currentPageBoxes)

                    if (totalH <= availableH) {
                        // Fits!
                    } else {
                        // Revert and start new page
                        activeBox.items.removeAt(activeBox.items.size - 1)
                        if (currentPageBoxes.isNotEmpty()) {
                            pages.add(currentPageBoxes)
                            currentPageBoxes = mutableListOf()
                        }
                        val newBox = PageCategoryBox(category, true)
                        newBox.items.add(item)
                        currentPageBoxes.add(newBox)
                    }
                } else {
                    val newBox = PageCategoryBox(category, false)
                    newBox.items.add(item)

                    val testBoxes = ArrayList(currentPageBoxes)
                    testBoxes.add(newBox)
                    val totalH = calculatePageHeight(testBoxes)

                    if (totalH <= availableH) {
                        currentPageBoxes.add(newBox)
                    } else {
                        if (currentPageBoxes.isNotEmpty()) {
                            pages.add(currentPageBoxes)
                            currentPageBoxes = mutableListOf()
                        }
                        currentPageBoxes.add(newBox)
                    }
                }
            }
        }
        if (currentPageBoxes.isNotEmpty()) {
            pages.add(currentPageBoxes)
        }

        val totalPages = pages.size

        // Render each page
        for ((pageIdx, pageBoxes) in pages.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // 1. Draw Cream Background
            canvas.drawRect(0f, 0f, pw, ph, paintCreamBg)

            // 2. Draw Outer Rounded Border
            val borderRect = RectF(marginH, borderTop, pw - marginH, borderBottom)
            canvas.drawRoundRect(borderRect, 16f, 16f, paintOuterBorder)

            // 3. Draw Header Text (Outside Border)
            canvas.drawText(storeName, pw / 2f, 38f, paintStoreName)
            if (branchName.isNotBlank()) {
                canvas.drawText(branchName, pw / 2f, 52f, paintStoreBranch)
            }

            // 4. Draw Subtitle "PRICELIST" (Inside Border)
            canvas.drawText("PRICELIST", pw / 2f, borderTop + 24f, paintPricelistTitle)

            // 5. Draw Category Boxes
            var currentY = contentTop
            for (box in pageBoxes) {
                val boxTop = currentY
                val boxH = calculateBoxHeight(box)
                val boxBottom = boxTop + boxH

                val rectLeft = marginH + 12f
                val rectRight = pw - marginH - 12f
                val rRect = RectF(rectLeft, boxTop, rectRight, boxBottom)

                // Background & border
                canvas.drawRoundRect(rRect, 12f, 12f, paintBoxBg)
                canvas.drawRoundRect(rRect, 12f, 12f, paintBoxBorder)

                // Category Title
                val titleY = boxTop + 12f + 12f
                canvas.drawText(box.categoryName.uppercase(), rectLeft + 16f, titleY, paintCategoryName)

                // Dashed separator
                val dashY = titleY + 8f
                val dashPath = android.graphics.Path().apply {
                    moveTo(rectLeft + 16f, dashY)
                    lineTo(rectRight - 16f, dashY)
                }
                canvas.drawPath(dashPath, paintDash)

                // Items
                var itemY = dashY + 10f
                for ((idx, item) in box.items.withIndex()) {
                    val hasSecond = item.description.isNotBlank() || item.unit.isNotBlank()

                    // Name
                    canvas.drawText(item.name, rectLeft + 16f, itemY + 11f, paintItemName)

                    // Price
                    val priceStr = "₱ ${priceFormat.format(item.price)}"
                    canvas.drawText(priceStr, rectRight - 16f, itemY + 11f, paintItemPrice)

                    if (hasSecond) {
                        val secLineY = itemY + 11f + 2f + 8.5f

                        // Description
                        if (item.description.isNotBlank()) {
                            val unitWidth = if (item.unit.isNotBlank()) paintItemUnit.measureText(item.unit) else 0f
                            val maxDescW = (rectRight - rectLeft - 32f) - unitWidth - 24f
                            val truncatedDesc = TextUtils.ellipsize(
                                item.description,
                                paintItemDesc,
                                maxDescW,
                                TextUtils.TruncateAt.END
                            ).toString()
                            canvas.drawText(truncatedDesc, rectLeft + 16f, secLineY, paintItemDesc)
                        }

                        // Unit
                        if (item.unit.isNotBlank()) {
                            canvas.drawText(item.unit, rectRight - 16f, secLineY, paintItemUnit)
                        }

                        itemY += 11f + 2f + 8.5f
                    } else {
                        itemY += 11f
                    }

                    itemY += if (idx == box.items.size - 1) 12f else 8f
                }

                currentY = boxBottom + boxSpacing
            }

            // 6. Draw Footer Branding & Page Number (Outside Border)
            val footerY = ph - 25f

            // Page Number
            canvas.drawText("Page ${pageIdx + 1} of $totalPages", marginH + 12f, footerY, paintPageCounter)

            // Logo Text Aligned to the Right
            val presyoW = paintPresyo.measureText("presyo")
            val hanW = paintHan.measureText("han?")
            val brandTotalW = presyoW + hanW
            val brandEndX = pw - marginH - 12f
            val brandStartX = brandEndX - brandTotalW

            canvas.drawText("atong", brandStartX, footerY - 5f, paintAtong)
            canvas.drawText("presyo", brandStartX, footerY + 5f, paintPresyo)
            canvas.drawText("han?", brandStartX + presyoW, footerY + 5f, paintHan)

            doc.finishPage(page)
        }

        return doc
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: Save PdfDocument to cache and return a shareable FileProvider URI
    // ─────────────────────────────────────────────────────────────────────────
    fun savePdfToCache(context: Context, doc: PdfDocument, filename: String): Uri {
        val pdfDir = File(context.cacheDir, "pdf").also { it.mkdirs() }
        val pdfFile = File(pdfDir, filename)
        FileOutputStream(pdfFile).use { fos ->
            doc.writeTo(fos)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: Render a specific page from a saved PDF URI into a Bitmap
    //         (for on-screen preview inside the dialog)
    // ─────────────────────────────────────────────────────────────────────────
    fun renderPageToBitmap(context: Context, pdfFile: File, pageIndex: Int, targetWidth: Int): Bitmap? {
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return null
            }
            val pdfPage = renderer.openPage(pageIndex)
            val scale = targetWidth.toFloat() / pdfPage.width.toFloat()
            val bitmapH = (pdfPage.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(targetWidth, bitmapH, Bitmap.Config.ARGB_8888)
            // Fill white background first
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pdfPage.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: Convenience to get page count without rendering
    // ─────────────────────────────────────────────────────────────────────────
    fun getPageCount(pdfFile: File): Int {
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: Build the PDF filename
    // ─────────────────────────────────────────────────────────────────────────
    fun buildFilename(storeName: String, branchName: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val slug = "${storeName}_${branchName}"
            .lowercase()
            .replace("\\s+".toRegex(), "_")
            .replace("[^a-z0-9_]".toRegex(), "")
            .take(30)
        return "presyohan_pricelist_${slug}_${ts}.pdf"
    }
}
