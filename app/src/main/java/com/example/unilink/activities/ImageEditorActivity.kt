package com.example.unilink.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.unilink.R
import com.example.unilink.databinding.ActivityImageEditorBinding
import com.example.unilink.utils.FilterType
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.ImageEditorUtils
import com.example.unilink.utils.ThemeUtils
import com.google.android.material.chip.Chip

class ImageEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageEditorBinding
    private var sourceBitmap: Bitmap? = null
    private var processedBitmap: Bitmap? = null

    private var currentFilter: FilterType = FilterType.ORIGINAL
    private var currentBrightness: Float = 0f
    private var currentContrast: Float = 1f
    private var currentSaturation: Float = 1f
    private var currentRotation: Float = 0f
    private var isFlipped: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityImageEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val imageUriString = intent.getStringExtra("imageUri")
        if (!imageUriString.isNullOrEmpty()) {
            loadSourceBitmap(Uri.parse(imageUriString))
        } else {
            Toast.makeText(this, "No image found to edit", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupFilterChips()
        setupSliders()
        setupQuickTools()

        binding.btnSaveEditedPhoto.setOnClickListener {
            saveAndReturn()
        }
    }

    private fun loadSourceBitmap(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (original != null) {
                // Downscale if bitmap is huge for fast real-time filter rendering
                val maxDimension = 1920
                sourceBitmap = if (original.width > maxDimension || original.height > maxDimension) {
                    val scale = maxDimension.toFloat() / Math.max(original.width, original.height).toFloat()
                    Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
                } else {
                    original
                }
                applyCurrentTransformations()
            } else {
                Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error opening photo: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupFilterChips() {
        binding.llFilterChipsContainer.removeAllViews()
        FilterType.entries.forEach { filter ->
            val chip = Chip(this).apply {
                text = filter.displayName
                isCheckable = true
                isChecked = (filter == currentFilter)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (filter == currentFilter) android.graphics.Color.parseColor("#A855F7") else android.graphics.Color.parseColor("#1E293B")
                )
                setTextColor(android.graphics.Color.WHITE)
                setOnClickListener {
                    HapticUtils.playSelection(it)
                    currentFilter = filter
                    updateChipSelection()
                    applyCurrentTransformations()
                }
            }
            binding.llFilterChipsContainer.addView(chip)
        }
    }

    private fun updateChipSelection() {
        for (i in 0 until binding.llFilterChipsContainer.childCount) {
            val chip = binding.llFilterChipsContainer.getChildAt(i) as? Chip
            if (chip != null) {
                val filter = FilterType.values().getOrNull(i)
                chip.isChecked = (filter == currentFilter)
            }
        }
    }

    private fun setupSliders() {
        binding.sliderBrightness.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentBrightness = value
                binding.tvBrightnessValue.text = value.toInt().toString()
                applyCurrentTransformations()
            }
        }

        binding.sliderContrast.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentContrast = value / 100f
                binding.tvContrastValue.text = String.format("%.1f", currentContrast)
                applyCurrentTransformations()
            }
        }

        binding.sliderSaturation.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentSaturation = value / 100f
                binding.tvSaturationValue.text = String.format("%.1f", currentSaturation)
                applyCurrentTransformations()
            }
        }
    }

    private fun setupQuickTools() {
        binding.btnRotate.setOnClickListener {
            HapticUtils.playSelection(it)
            currentRotation = (currentRotation + 90f) % 360f
            applyCurrentTransformations()
        }

        binding.btnFlip.setOnClickListener {
            HapticUtils.playSelection(it)
            isFlipped = !isFlipped
            applyCurrentTransformations()
        }

        binding.btnResetFilters.setOnClickListener {
            HapticUtils.playHeavy(it)
            currentFilter = FilterType.ORIGINAL
            currentBrightness = 0f
            currentContrast = 1f
            currentSaturation = 1f
            currentRotation = 0f
            isFlipped = false

            binding.sliderBrightness.value = 0f
            binding.sliderContrast.value = 100f
            binding.sliderSaturation.value = 100f
            binding.tvBrightnessValue.text = "0"
            binding.tvContrastValue.text = "1.0"
            binding.tvSaturationValue.text = "1.0"

            updateChipSelection()
            applyCurrentTransformations()
        }
    }

    private fun applyCurrentTransformations() {
        val bitmap = sourceBitmap ?: return
        processedBitmap = ImageEditorUtils.applyFilterAndAdjustments(
            source = bitmap,
            filterType = currentFilter,
            brightness = currentBrightness,
            contrast = currentContrast,
            saturation = currentSaturation,
            rotationAngle = currentRotation,
            flipHorizontal = isFlipped
        )
        binding.ivPreviewSurface.setImageBitmap(processedBitmap)
    }

    private fun saveAndReturn() {
        val bitmap = processedBitmap ?: sourceBitmap ?: return
        binding.btnSaveEditedPhoto.isEnabled = false
        binding.btnSaveEditedPhoto.text = "Saving..."

        val tempUri = ImageEditorUtils.saveBitmapToTempUri(this, bitmap)
        if (tempUri != null) {
            val resultIntent = Intent().apply {
                data = tempUri
                putExtra("editedImageUri", tempUri.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            Toast.makeText(this, "Photo enhanced & saved! ✨", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            binding.btnSaveEditedPhoto.isEnabled = true
            binding.btnSaveEditedPhoto.text = "Apply Photo ✨"
            Toast.makeText(this, "Failed to save edited photo", Toast.LENGTH_SHORT).show()
        }
    }
}
