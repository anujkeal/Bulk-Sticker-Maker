package com.keal.bulkstickermaker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.UUID
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private val selected = mutableListOf<Uri>()
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var nameInput: EditText
    private lateinit var countText: TextView
    private lateinit var statusText: TextView
    private lateinit var preview: LinearLayout
    private lateinit var addButton: Button

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        selected.clear()
        selected.addAll(uris.take(30))
        renderSelection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 14, 17)
        window.navigationBarColor = Color.rgb(12, 14, 17)
        buildScreen()
    }

    @Deprecated("WhatsApp returns pack validation errors through this result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == RESULT_CANCELED) {
            val validationError = data?.getStringExtra("validation_error")
            if (!validationError.isNullOrBlank()) {
                statusText.text = "WhatsApp rejected the pack: $validationError"
                Toast.makeText(this, "WhatsApp rejected the sticker pack. See the message below the button.", Toast.LENGTH_LONG).show()
            } else {
                statusText.text = "WhatsApp closed without adding the pack. Try again after checking WhatsApp is updated."
            }
        } else if (requestCode == 200 && resultCode == RESULT_OK) {
            statusText.text = "Sticker pack added to WhatsApp successfully."
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(Color.rgb(12, 14, 17))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        content.addView(TextView(this).apply {
            text = "Bulk Sticker Maker"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
        content.addView(TextView(this).apply {
            text = "Turn up to 30 gallery photos into one WhatsApp sticker pack."
            textSize = 14f
            setTextColor(Color.rgb(165, 172, 181))
            setPadding(0, dp(7), 0, dp(22))
        })

        content.addView(sectionTitle("Sticker pack name"))
        nameInput = EditText(this).apply {
            setSingleLine(true)
            setText("My Stickers")
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(Color.rgb(27, 31, 37), 14)
        }
        content.addView(nameInput, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(20) })

        content.addView(sectionTitle("Your photos"))
        countText = TextView(this).apply {
            text = "No photos selected"
            textSize = 13f
            setTextColor(Color.rgb(165, 172, 181))
            setPadding(0, dp(4), 0, dp(12))
        }
        content.addView(countText)

        val chooseButton = Button(this).apply {
            text = "SELECT UP TO 30 PHOTOS"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.rgb(10, 18, 13))
            background = rounded(Color.rgb(76, 214, 142), 14)
            setOnClickListener {
                photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        content.addView(chooseButton, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(18) })

        preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        content.addView(preview)

        content.addView(TextView(this).apply {
            text = "Photos are kept whole. No background removal or cropping. Images are resized to 512 × 512 and compressed to meet WhatsApp's sticker size limit."
            textSize = 12f
            setTextColor(Color.rgb(145, 153, 163))
            setPadding(0, dp(10), 0, dp(18))
        })

        addButton = Button(this).apply {
            text = "CREATE STICKER PACK"
            isAllCaps = false
            textSize = 15f
            isEnabled = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(49, 59, 54), 14)
            setOnClickListener { createAndAddPack() }
        }
        content.addView(addButton, LinearLayout.LayoutParams(-1, dp(56)))

        statusText = TextView(this).apply {
            text = "Select at least 3 photos to create a pack."
            textSize = 12f
            setTextColor(Color.rgb(165, 172, 181))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }
        content.addView(statusText)

        content.addView(TextView(this).apply {
            text = "Made for personal sticker packs • Works offline"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(110, 119, 129))
            setPadding(0, dp(18), 0, dp(8))
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun renderSelection() {
        countText.text = "${selected.size} / 30 photos selected"
        addButton.isEnabled = selected.size in 3..30
        addButton.background = rounded(if (addButton.isEnabled) Color.rgb(25, 145, 83) else Color.rgb(49, 59, 54), 14)
        statusText.text = if (selected.size >= 3) "Ready to create your sticker pack." else "Select at least 3 photos to create a pack."
        preview.removeAllViews()
        if (selected.isEmpty()) return
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        selected.take(6).forEach { uri ->
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
                background = rounded(Color.rgb(35, 40, 47), 10)
                clipToOutline = true
            }
            row.addView(image, LinearLayout.LayoutParams(0, dp(76), 1f).apply { marginEnd = dp(6) })
        }
        preview.addView(row)
        if (selected.size > 6) {
            preview.addView(TextView(this).apply {
                text = "and ${selected.size - 6} more photos"
                setTextColor(Color.rgb(165, 172, 181))
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun createAndAddPack() {
        val photoUris = selected.toList()
        val packName = nameInput.text.toString().trim().ifBlank { "My Stickers" }.take(128)
        if (photoUris.size !in 3..30) {
            statusText.text = "Please select between 3 and 30 photos."
            return
        }

        addButton.isEnabled = false
        addButton.text = "CREATING STICKERS…"
        statusText.text = "Preparing ${photoUris.size} stickers. Please wait…"

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

                    statusText.text = "Created ${names.size} stickers. Opening WhatsApp…"
                    val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
                        putExtra("sticker_pack_id", packId)
                        putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
                        putExtra("sticker_pack_name", packName)
                    }
                    try {
                        // Match WhatsApp's official sample: show a chooser if WhatsApp and
                        // WhatsApp Business can both receive the sticker-pack request.
                        startActivityForResult(Intent.createChooser(intent, "Add sticker pack to WhatsApp"), 200)
                    } catch (e: ActivityNotFoundException) {
                        statusText.text = "Stickers were created, but WhatsApp or WhatsApp Business was not found."
                        Toast.makeText(this, "Install or update WhatsApp, then try again.", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        statusText.text = "Could not open WhatsApp: ${e.message ?: "unknown error"}"
                    } finally {
                        addButton.isEnabled = true
                        addButton.text = "CREATE STICKER PACK"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Creation failed: ${e.message ?: e.javaClass.simpleName}"
                    Toast.makeText(this, "Creation failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    addButton.isEnabled = true
                    addButton.text = "CREATE STICKER PACK"
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

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, dp(8))
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
