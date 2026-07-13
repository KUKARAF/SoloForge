package com.kbul.spicycrab.domain.barcode

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Runs on every camera preview frame while the food capture screen is open. Requires the same
 * code on two consecutive frames before reporting a hit, since single-frame ZXing reads
 * occasionally misfire on motion blur.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                )
            )
        )
    }

    private var lastCandidate: String? = null
    private var reportedCode: String? = null

    override fun analyze(image: ImageProxy) {
        try {
            val candidate = decode(image)
            if (candidate == null) {
                lastCandidate = null
                return
            }
            if (candidate == reportedCode) return
            if (candidate == lastCandidate) {
                reportedCode = candidate
                onBarcodeDetected(candidate)
            } else {
                lastCandidate = candidate
            }
        } finally {
            image.close()
        }
    }

    /** Call when leaving the capture screen so a re-entry can report the same code again. */
    fun reset() {
        lastCandidate = null
        reportedCode = null
    }

    private fun decode(image: ImageProxy): String? {
        val plane = image.planes[0]
        val source = PlanarYUVLuminanceSource(
            plane.buffer.toByteArray(),
            plane.rowStride,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap).text
        } catch (_: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun java.nio.ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val bytes = ByteArray(remaining())
        get(bytes)
        return bytes
    }
}
