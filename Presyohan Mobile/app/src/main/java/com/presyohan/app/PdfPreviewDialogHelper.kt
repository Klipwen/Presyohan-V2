package com.presyohan.app

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.graphics.pdf.PdfDocument

object PdfPreviewDialogHelper {

    /**
     * Shows the PDF preview dialog.
     * @param activity  — the host Activity (must be a LifecycleOwner)
     * @param items     — list of PdfPriceItem to render
     * @param storeName — display name of the store
     * @param branchName — branch / location name
     * @param pageSize  — paper size chosen by the user in the convert dialog
     */
    fun show(
        activity: Activity,
        items: List<PdfPriceItem>,
        storeName: String,
        branchName: String,
        pageSize: PdfPageSize,
        onBack: () -> Unit = {}
    ) {
        if (activity !is LifecycleOwner) return
        if (items.isEmpty()) {
            Toast.makeText(activity, "No items to generate PDF.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_pdf_preview, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.94).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Views
        val tvPageCounter     = view.findViewById<TextView>(R.id.tvPageCounter)
        val tvSubtitle        = view.findViewById<TextView>(R.id.tvPdfPreviewSubtitle)
        val tvFooter          = view.findViewById<TextView>(R.id.tvPdfInfoFooter)
        val imgPreview        = view.findViewById<ImageView>(R.id.imgPagePreview)
        val progressBar       = view.findViewById<ProgressBar>(R.id.progressPageLoading)
        val btnPrev           = view.findViewById<ImageView>(R.id.btnPrevPage)
        val btnNext           = view.findViewById<ImageView>(R.id.btnNextPage)
        val btnBack           = view.findViewById<AppCompatButton>(R.id.btnPdfBack)
        val btnPrint          = view.findViewById<android.widget.ImageButton>(R.id.btnPdfPrint)
        val btnShare          = view.findViewById<AppCompatButton>(R.id.btnPdfShare)

        tvSubtitle.text = "${pageSize.labelName} · Generating…"
        tvFooter.text = ""

        // State
        var currentPage = 0
        var totalPages = 0
        var pdfFile: File? = null
        var pdfUri: android.net.Uri? = null
        val filename = PricelistPdfHelper.buildFilename(storeName, branchName)

        fun updateNav() {
            tvPageCounter.text = "Page ${currentPage + 1} of $totalPages"
            btnPrev.isEnabled = currentPage > 0
            btnPrev.alpha = if (currentPage > 0) 1.0f else 0.5f
            btnNext.isEnabled = currentPage < totalPages - 1
            btnNext.alpha = if (currentPage < totalPages - 1) 1.0f else 0.5f
        }

        fun loadPage(index: Int) {
            val f = pdfFile ?: return
            progressBar.visibility = View.VISIBLE
            imgPreview.setImageBitmap(null)
            activity.lifecycleScope.launch {
                val previewWidth = (activity.resources.displayMetrics.widthPixels * 0.85).toInt()
                val bmp = withContext(Dispatchers.IO) {
                    PricelistPdfHelper.renderPageToBitmap(activity, f, index, previewWidth)
                }
                progressBar.visibility = View.GONE
                if (bmp != null) {
                    imgPreview.setImageBitmap(bmp)
                } else {
                    tvFooter.text = "Could not render page ${index + 1}."
                }
            }
        }

        // Generate PDF in background
        progressBar.visibility = View.VISIBLE
        activity.lifecycleScope.launch {
            val pdfDoc: PdfDocument
            val savedFile: File
            val savedUri: android.net.Uri
            try {
                pdfDoc = withContext(Dispatchers.Default) {
                    PricelistPdfHelper.generatePdf(activity, items, storeName, branchName, pageSize)
                }
                val pdfDir = File(activity.cacheDir, "pdf").also { it.mkdirs() }
                savedFile = File(pdfDir, filename)
                withContext(Dispatchers.IO) {
                    FileOutputStream(savedFile).use { fos -> pdfDoc.writeTo(fos) }
                }
                pdfDoc.close()
                savedUri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    savedFile
                )
                pdfFile = savedFile
                pdfUri = savedUri
                totalPages = withContext(Dispatchers.IO) {
                    PricelistPdfHelper.getPageCount(savedFile)
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(activity, "Failed to generate PDF: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }

            // Initial render
            currentPage = 0
            updateNav()
            tvSubtitle.text = "${pageSize.labelName}"
            tvFooter.text = "$totalPages ${if (totalPages == 1) "page" else "pages"} · $filename"
            loadPage(0)
        }

        // Navigation
        btnPrev.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updateNav()
                loadPage(currentPage)
            }
        }
        btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updateNav()
                loadPage(currentPage)
            }
        }

        // Back
        btnBack.setOnClickListener {
            dialog.dismiss()
            onBack()
        }

        // Print
        btnPrint.setOnClickListener {
            val f = pdfFile
            if (f == null || !f.exists()) {
                Toast.makeText(activity, "PDF not ready yet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val printManager = activity.getSystemService(Activity.PRINT_SERVICE) as PrintManager
                val adapter = object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: LayoutResultCallback,
                        extras: Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onLayoutCancelled()
                            return
                        }
                        val info = PrintDocumentInfo.Builder(filename)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(totalPages)
                            .build()
                        callback.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out android.print.PageRange>?,
                        destination: android.os.ParcelFileDescriptor,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: WriteResultCallback
                    ) {
                        try {
                            FileInputStream(f).use { input ->
                                FileOutputStream(destination.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback.onWriteFailed(e.message)
                        }
                    }
                }

                val mediaSize = when (pageSize) {
                    PdfPageSize.LONG_BOND  -> PrintAttributes.MediaSize.NA_LEGAL
                    PdfPageSize.SHORT_BOND -> PrintAttributes.MediaSize.NA_LETTER
                }
                val printAttribs = PrintAttributes.Builder()
                    .setMediaSize(mediaSize)
                    .build()
                printManager.print("Presyohan Pricelist", adapter, printAttribs)
            } catch (e: Exception) {
                Toast.makeText(activity, "Print failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Share
        btnShare.setOnClickListener {
            val uri = pdfUri
            if (uri == null) {
                Toast.makeText(activity, "PDF not ready yet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Pricelist — $storeName")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(intent, "Share Pricelist PDF via"))
            } catch (e: Exception) {
                Toast.makeText(activity, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        dialog.show()
    }
}
