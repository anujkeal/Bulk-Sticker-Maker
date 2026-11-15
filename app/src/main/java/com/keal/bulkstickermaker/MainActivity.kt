package com.keal.bulkstickermaker

import android.app.Activity
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
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private val selected = mutableListOf<Uri>()
    private lateinit var nameInput: EditText
    private lateinit var countText: TextView
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
        window.decorView.systemUiVisibility = 0
        buildScreen()
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
            background = rounded(Color.rgb(49, 105, 76), 14)
            setOnClickListener { createAndAddPack() }
        }
        content.addView(addButton, LinearLayout.LayoutParams(-1, dp(56)))

        content.addView(TextView(this).apply {
            text = "Made for personal sticker packs • Works offline"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(110, 119, 129))
            setPadding(0, dp(24), 0, dp(8))
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun renderSelection() {
        countText.text = "${selected.size} / 30 photos selected"
        addButton.isEnabled = selected.size in 3..30
        addButton.background = rounded(if (addButton.isEnabled) Color.rgb(25, 145, 83) else Color.rgb(49, 59, 54), 14)
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
            row.addView(image, LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                marginEnd = dp(6)
            })
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
        val packName = nameInput.text.toString().trim().ifBlank { "My Stickers" }.take(128)
        if (selected.size !in 3..30) {
            toast("Select between 3 and 30 photos.")
            return
        }
        try {
            val dir = File(filesDir, "stickers/pack1")
            dir.deleteRecursively()
            dir.mkdirs()
            val names = mutableListOf<String>()
            selected.forEachIndexed { index, uri ->
                val input = contentResolver.openInputStream(uri) ?: error("Cannot read photo ${index + 1}")
                val source = input.use { BitmapFactory.decodeStream(it) } ?: error("Unsupported image ${index + 1}")
                val output = makeSticker(source)
                source.recycle()
                val fileName = "sticker_${index + 1}.webp"
                val file = File(dir, fileName)
                FileOutputStream(file).use { stream ->
                    var quality = 92
                    do {
                        stream.channel.truncate(0)
                        stream.channel.position(0)
                        output.compress(if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP, quality, stream)
                        stream.flush()
                        quality -= 5
                    } while (file.length() > 100 * 1024 && quality >= 45)
                }
                output.recycle()
                if (file.length() > 100 * 1024) error("Photo ${index + 1} is too detailed to compress below 100 KB. Try another image.")
                names.add(fileName)
            }
            getSharedPreferences("pack", MODE_PRIVATE).edit()
                .putString("name", packName)
                .putString("stickers", names.joinToString("|"))
                .putString("version", System.currentTimeMillis().toString())
                .apply()

            val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
                putExtra("sticker_pack_id", "pack1")
                putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
                putExtra("sticker_pack_name", packName)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                toast("Sticker pack created. WhatsApp was not found.")
            }
        } catch (e: Exception) {
            toast(e.message ?: "Could not create sticker pack.")
        }
    }

    private fun makeSticker(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
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
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
