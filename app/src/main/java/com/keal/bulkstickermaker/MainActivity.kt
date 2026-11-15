package com.keal.bulkstickermaker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private val selected = mutableListOf<Uri>()
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var nameInput: EditText
    private lateinit var countText: TextView
    private lateinit var photoGrid: LinearLayout
    private lateinit var createButton: TextView

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        selected.clear()
        selected.addAll(uris.take(30))
        renderSelection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.setBackgroundDrawableResource(android.R.color.transparent)
        buildScreen()
    }

    @Deprecated("WhatsApp returns pack validation errors through this result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == RESULT_CANCELED) {
            val validationError = data?.getStringExtra("validation_error")
            if (!validationError.isNullOrBlank()) {
                Toast.makeText(this, "WhatsApp rejected the pack: $validationError", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "WhatsApp closed without adding the pack. Check that WhatsApp is updated.", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == 200 && resultCode == RESULT_OK) {
            Toast.makeText(this, "Sticker pack added to WhatsApp.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(16), bars.top + dp(12), dp(16), bars.bottom + dp(12))
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(StickerIconView(), LinearLayout.LayoutParams(dp(32), dp(32)))
        val title = TextView(this).apply {
            text = "Bulk Sticker Maker"
            textSize = 18f
            setTextColor(WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
            marginStart = dp(12)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, dp(32)).apply {
            bottomMargin = dp(28)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        nameInput = EditText(this).apply {
            hint = "Pack name"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 15f
            setTextColor(WHITE)
            setHintTextColor(HINT)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(dp(14), 0, dp(14), 0)
            background = outlinedRounded(FIELD, BORDER, 12)
            includeFontPadding = false
        }
        content.addView(nameInput, LinearLayout.LayoutParams(-1, dp(56)))

        countText = TextView(this).apply {
            text = "0 / 30"
            textSize = 12f
            setTextColor(HINT)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }
        content.addView(countText, LinearLayout.LayoutParams(-1, dp(18)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })

        photoGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(photoGrid, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        createButton = TextView(this).apply {
            text = "Create pack"
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setOnClickListener { createAndAddPack() }
        }
        root.addView(createButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(12)
        })

        setContentView(root)
        renderSelection()
    }

    private fun renderSelection() {
        countText.text = "${selected.size} / 30"
        val enabled = selected.size in 3..30
        createButton.isEnabled = enabled
        createButton.setTextColor(if (enabled) BG else ACCENT)
        createButton.background = rounded(
            if (enabled) ACCENT else Color.argb(36, 77, 216, 145),
            12
        )
        renderPhotoGrid()
    }

    private fun renderPhotoGrid() {
        photoGrid.removeAllViews()
        val cells = mutableListOf<View>()
        cells.add(createAddTile())

        selected.forEachIndexed { index, uri ->
            cells.add(createPhotoTile(uri, index))
        }

        // Keep six slots visible before photos are selected.
        while (cells.size < 6) cells.add(createPlaceholder())

        cells.chunked(3).forEachIndexed { rowIndex, rowCells ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val completeRow = rowCells.toMutableList()
            while (completeRow.size < 3) completeRow.add(createPlaceholder())

            completeRow.forEachIndexed { columnIndex, cell ->
                val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (columnIndex < 2) params.marginEnd = dp(8)
                row.addView(cell, params)
            }
            photoGrid.addView(row, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (rowIndex > 0) topMargin = dp(8)
            })
        }
    }

    private fun createAddTile(): View {
        val tile = SquareFrameLayout().apply {
            background = dashedRounded(ACCENT, 12)
            isClickable = true
            isFocusable = true
            contentDescription = "Add photos"
            setOnClickListener { launchPhotoPicker() }
        }
        val plus = TextView(this).apply {
            text = "+"
            textSize = 42f
            setTextColor(ACCENT)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        tile.addView(plus, FrameLayout.LayoutParams(-1, -1))
        return tile
    }

    private fun createPlaceholder(): View = SquareFrameLayout().apply {
        background = rounded(FIELD, 12)
        clipToOutline = true
        contentDescription = "Empty photo slot"
    }

    private fun createPhotoTile(uri: Uri, index: Int): View {
        val tile = SquareFrameLayout().apply {
            background = rounded(FIELD, 12)
            clipToOutline = true
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageURI(uri)
            contentDescription = "Selected photo ${index + 1}"
        }
        tile.addView(image, FrameLayout.LayoutParams(-1, -1))

        val removeTouchTarget = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Remove photo"
            setOnClickListener {
                if (index in selected.indices) {
                    selected.removeAt(index)
                    renderSelection()
                }
            }
        }
        val removeBadge = TextView(this).apply {
            text = "×"
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            setTextColor(WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.argb(153, 0, 0, 0), 50)
            includeFontPadding = false
        }
        removeTouchTarget.addView(removeBadge, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        tile.addView(removeTouchTarget, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(4)
            marginEnd = dp(4)
        })
        return tile
    }

    private fun launchPhotoPicker() {
        photoPicker.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    private fun createAndAddPack() {
        val photoUris = selected.toList()
        val packName = nameInput.text.toString().trim().ifBlank { "My Stickers" }.take(128)
        if (photoUris.size !in 3..30) {
            Toast.makeText(this, "Select at least 3 photos.", Toast.LENGTH_SHORT).show()
            return
        }

        createButton.isEnabled = false
        createButton.text = "Creating…"

        // WhatsApp identifies packs by ID, not by their visible name.
        // Give every new pack its own stable ID so it is added separately.
        val packId = "pack_" + UUID.randomUUID().toString().replace("-", "").take(20)
        worker.execute {
            try {
                val names = createStickerFiles(photoUris, packId)
                runOnUiThread {
                    val prefs = getSharedPreferences("pack", MODE_PRIVATE)
                    val ids = prefs.getStringSet("pack_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                    ids.add(packId)
                    prefs.edit()
                        .putStringSet("pack_ids", ids)
                        .putString("${packId}_name", packName)
                        .putString("${packId}_stickers", names.joinToString("|"))
                        .putString("${packId}_version", "1")
                        .apply()

                    val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
                        putExtra("sticker_pack_id", packId)
                        putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
                        putExtra("sticker_pack_name", packName)
                    }
                    try {
                        startActivityForResult(
                            Intent.createChooser(intent, "Add sticker pack to WhatsApp"),
                            200
                        )
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            this,
                            "Stickers were created, but WhatsApp or WhatsApp Business was not found.",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open WhatsApp: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    } finally {
                        createButton.text = "Create pack"
                        renderSelection()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Creation failed: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    createButton.text = "Create pack"
                    renderSelection()
                }
            }
        }
    }

    private fun createStickerFiles(uris: List<Uri>, packId: String): List<String> {
        val dir = File(filesDir, "stickers/$packId")
        dir.deleteRecursively()
        check(dir.mkdirs()) { "Could not create sticker storage folder." }
        val names = mutableListOf<String>()

        uris.forEachIndexed { index, uri ->
            val source = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Could not read photo ${index + 1}. Please select it again.")
            val output = makeSticker(source)
            source.recycle()
            val fileName = "sticker_${index + 1}.webp"
            val file = File(dir, fileName)
            var quality = 92
            do {
                FileOutputStream(file, false).use { stream ->
                    output.compress(Bitmap.CompressFormat.WEBP, quality, stream)
                    stream.flush()
                }
                quality -= 5
            } while (file.length() > 100 * 1024 && quality >= 42)
            output.recycle()
            check(file.length() in 1..(100 * 1024)) {
                "Photo ${index + 1} cannot be compressed below WhatsApp's 100 KB limit."
            }
            names.add(fileName)
        }

        val traySource = BitmapFactory.decodeFile(File(dir, names.first()).absolutePath)
            ?: error("Could not create sticker tray icon.")
        val tray = Bitmap.createScaledBitmap(traySource, 96, 96, true)
        traySource.recycle()
        FileOutputStream(File(dir, "tray.png")).use { tray.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tray.recycle()
        return names
    }

    private fun makeSticker(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.TRANSPARENT)
        val scale = min(512f / source.width, 512f / source.height)
        val width = source.width * scale
        val height = source.height * scale
        val left = (512f - width) / 2f
        val top = (512f - height) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), paint)
        return output
    }

    private inner class StickerIconView : View(this@MainActivity) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BG
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val side = min(width, height).toFloat()
            val scale = side / 32f
            canvas.save()
            canvas.scale(scale, scale)
            canvas.drawRoundRect(RectF(0f, 0f, 32f, 32f), 8f, 8f, fill)
            val sticker = Path().apply {
                moveTo(9f, 8f)
                cubicTo(6f, 8f, 5f, 10f, 5f, 13f)
                lineTo(5f, 19f)
                cubicTo(5f, 24f, 9f, 27f, 14f, 27f)
                cubicTo(20f, 27f, 25f, 22f, 25f, 16f)
                cubicTo(25f, 13f, 23f, 11f, 20f, 10f)
                cubicTo(16f, 9f, 13f, 8f, 9f, 8f)
            }
            canvas.drawPath(sticker, line)
            val fold = Path().apply {
                moveTo(17f, 8f)
                cubicTo(16f, 13f, 19f, 16f, 24f, 16f)
            }
            canvas.drawPath(fold, line)
            canvas.restore()
        }
    }

    private inner class SquareFrameLayout : FrameLayout(this@MainActivity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val exactSize = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            super.onMeasure(exactSize, exactSize)
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun outlinedRounded(color: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dashedRounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        setStroke(dp(1), color, dp(5).toFloat(), dp(4).toFloat())
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.rgb(14, 17, 22)
        private val FIELD = Color.rgb(23, 27, 34)
        private val BORDER = Color.rgb(38, 44, 54)
        private val ACCENT = Color.rgb(77, 216, 145)
        private val HINT = Color.rgb(102, 112, 133)
        private val WHITE = Color.WHITE
    }
}
