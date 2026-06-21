package com.presyohan.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

class StoreQrActivity : AppCompatActivity() {

    private lateinit var btnBack: FrameLayout
    private lateinit var tvStoreName: TextView
    private lateinit var tvStoreId: TextView
    private lateinit var tvStoreLocation: TextView
    private lateinit var ivQrCode: ImageView
    private lateinit var btnDownload: LinearLayout
    private lateinit var btnShare: LinearLayout

    private var storeId: String = ""
    private var storeName: String = ""
    private var displayId: String = ""
    private var storeLocation: String = ""
    private var qrContent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store_qr)

        // Force a matching yellow status bar with white icons for the QR screen gradient
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#FFC502")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var flags = window.decorView.systemUiVisibility
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                window.decorView.systemUiVisibility = flags
            }
        }

        // Read extras passed from ManageStoreActivity
        storeId = intent.getStringExtra("storeId") ?: ""
        storeName = intent.getStringExtra("storeName") ?: "Store Name"
        displayId = intent.getStringExtra("displayId") ?: ""
        storeLocation = intent.getStringExtra("storeLocation") ?: "Branch Location"

        // QR Code Content Schema
        qrContent = "presyohan://partner?sid=$displayId&uuid=$storeId"

        // Initialize Views
        btnBack = findViewById(R.id.btnBack)
        tvStoreName = findViewById(R.id.tvStoreName)
        tvStoreId = findViewById(R.id.tvStoreId)
        tvStoreLocation = findViewById(R.id.tvStoreLocation)
        ivQrCode = findViewById(R.id.ivQrCode)
        btnDownload = findViewById(R.id.btnDownload)
        btnShare = findViewById(R.id.btnShare)

        // Bind basic text
        tvStoreName.text = storeName
        tvStoreId.text = "Store ID: $displayId"
        tvStoreLocation.text = storeLocation

        // Generate and display QR Code on screen
        try {
            val qrBitmap = generateTealQRCode(qrContent, 512, 512)
            ivQrCode.setImageBitmap(qrBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to generate QR Code preview.", Toast.LENGTH_SHORT).show()
        }

        // Action Listeners
        btnBack.setOnClickListener {
            finish()
        }

        btnDownload.setOnClickListener {
            downloadStoreQrCard()
        }

        btnShare.setOnClickListener {
            shareStoreQrCard()
        }
    }

    /**
     * Programmatic custom colored QR code rendering (Teal #00A5C4)
     */
    private fun generateTealQRCode(content: String, width: Int, height: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val tealColor = Color.parseColor("#00A5C4")
        val whiteColor = Color.WHITE

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) tealColor else whiteColor)
            }
        }
        return bitmap
    }

    /**
     * Renders a high-resolution printable card offscreen based on the XML layout
     */
    private fun generateDownloadCardBitmap(): Bitmap {
        val inflater = LayoutInflater.from(this)
        val cardView = inflater.inflate(R.layout.layout_qr_download_card, null, false)

        val tvName = cardView.findViewById<TextView>(R.id.tvDownloadStoreName)
        val tvId = cardView.findViewById<TextView>(R.id.tvDownloadStoreId)
        val tvLoc = cardView.findViewById<TextView>(R.id.tvDownloadStoreLocation)
        val ivQr = cardView.findViewById<ImageView>(R.id.ivDownloadQr)

        tvName.text = storeName
        tvId.text = "Store ID: $displayId"
        tvLoc.text = storeLocation

        // High-res QR for print
        val qrBitmap = generateTealQRCode(qrContent, 720, 720)
        ivQr.setImageBitmap(qrBitmap)

        // Measure & layout offscreen view
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY)
        cardView.measure(widthSpec, heightSpec)
        cardView.layout(0, 0, cardView.measuredWidth, cardView.measuredHeight)

        // Render card to bitmap
        val cardBitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cardBitmap)
        cardView.draw(canvas)

        return cardBitmap
    }

    /**
     * Save the rendered QR card to the Pictures directory
     */
    private fun downloadStoreQrCard() {
        try {
            val cardBitmap = generateDownloadCardBitmap()
            val filename = "Store_QR_${storeName.replace(" ", "_")}_$displayId.png"
            val uri = saveBitmapToGallery(cardBitmap, filename)
            if (uri != null) {
                Toast.makeText(this, "QR Card downloaded to Gallery/Pictures!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Failed to save QR Card.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error generating card: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Share the rendered QR card via system sharing sheet
     */
    private fun shareStoreQrCard() {
        try {
            val cardBitmap = generateDownloadCardBitmap()
            val filename = "Share_QR_${storeName.replace(" ", "_")}_$displayId.png"

            // Save to Cache folder first to obtain Content URI for sharing
            val cacheDir = File(cacheDir, "shared_images").apply { mkdirs() }
            val file = File(cacheDir, filename)
            FileOutputStream(file).use { out ->
                cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            if (uri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "$storeName QR Code")
                    putExtra(Intent.EXTRA_TEXT, "Scan this QR Code to partner with $storeName!")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share QR Card via"))
            } else {
                Toast.makeText(this, "Failed to build Share link.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error sharing card: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, filename: String): Uri? {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Presyohan")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri).use { out ->
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                return null
            }
        }
        return uri
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}
