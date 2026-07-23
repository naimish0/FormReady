package com.rameshta.formready.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.rameshta.formready.core.model.MaskStroke
import java.nio.ByteOrder
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

data class FaceGuidance(
    val faceCount: Int,
    val left: Float? = null,
    val top: Float? = null,
    val right: Float? = null,
    val bottom: Float? = null,
    val yawDegrees: Float? = null,
    val rollDegrees: Float? = null,
    val eyeLine: Float? = null,
) {
    val warnings: List<String>
        get() = buildList {
            if (faceCount == 0) add("NO_FACE_DETECTED")
            if (faceCount > 1) add("MULTIPLE_FACES_DETECTED")
            if (yawDegrees != null && abs(yawDegrees) > 12f) add("FACE_NOT_FORWARD")
            if (rollDegrees != null && abs(rollDegrees) > 8f) add("HEAD_TILTED")
            if (
                left != null && top != null && right != null && bottom != null &&
                (right - left < 0.18f || bottom - top < 0.18f)
            ) {
                add("FACE_TOO_SMALL")
            }
            if (eyeLine != null && eyeLine !in 0.25f..0.60f) add("EYE_LINE_REVIEW")
        }
}

interface FaceGuidanceEngine {
    suspend fun analyze(bitmap: Bitmap): FaceGuidance
}

interface PersonSegmentationEngine {
    suspend fun replaceBackground(
        source: Bitmap,
        backgroundArgb: Int,
        strokes: List<MaskStroke>,
    ): Bitmap
}

class MlKitFaceGuidanceEngine @Inject constructor() : FaceGuidanceEngine {
    override suspend fun analyze(bitmap: Bitmap): FaceGuidance {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build(),
        )
        return try {
            val faces = suspendCancellableCoroutine { continuation ->
                detector.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resumeWithException(it)
                    }
                    .addOnCanceledListener { continuation.cancel() }
            }
            val face = faces.singleOrNull()
            val leftEye = face?.getLandmark(FaceLandmark.LEFT_EYE)?.position
            val rightEye = face?.getLandmark(FaceLandmark.RIGHT_EYE)?.position
            FaceGuidance(
                faceCount = faces.size,
                left = face?.boundingBox?.left?.toFloat()?.div(bitmap.width),
                top = face?.boundingBox?.top?.toFloat()?.div(bitmap.height),
                right = face?.boundingBox?.right?.toFloat()?.div(bitmap.width),
                bottom = face?.boundingBox?.bottom?.toFloat()?.div(bitmap.height),
                yawDegrees = face?.headEulerAngleY,
                rollDegrees = face?.headEulerAngleZ,
                eyeLine = if (leftEye != null && rightEye != null) {
                    ((leftEye.y + rightEye.y) / 2f) / bitmap.height
                } else {
                    null
                },
            )
        } finally {
            detector.close()
        }
    }
}

class MlKitPersonSegmentationEngine @Inject constructor() : PersonSegmentationEngine {
    override suspend fun replaceBackground(
        source: Bitmap,
        backgroundArgb: Int,
        strokes: List<MaskStroke>,
    ): Bitmap {
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .enableRawSizeMask()
                .build(),
        )
        val mask = try {
            suspendCancellableCoroutine { continuation ->
                segmenter.process(InputImage.fromBitmap(source, 0))
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resumeWithException(it)
                    }
                    .addOnCanceledListener { continuation.cancel() }
            }
        } finally {
            segmenter.close()
        }
        val buffer = mask.buffer
            .duplicate()
            .order(ByteOrder.nativeOrder())
            .apply { rewind() }
        val pixels = IntArray(mask.width * mask.height) {
            val confidence = buffer.float.coerceIn(0f, 1f)
            Color.argb((confidence * 255).toInt(), 255, 255, 255)
        }
        val rawMask = Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val scaledMask = Bitmap.createScaledBitmap(rawMask, source.width, source.height, true)
        rawMask.recycle()
        val maskCanvas = Canvas(scaledMask)
        strokes.forEach { stroke ->
            maskCanvas.drawCircle(
                stroke.x * source.width,
                stroke.y * source.height,
                stroke.radius * maxOf(source.width, source.height),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (stroke.restore) Color.WHITE else Color.TRANSPARENT
                    xfermode = if (stroke.restore) {
                        PorterDuffXfermode(PorterDuff.Mode.SRC)
                    } else {
                        PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    }
                },
            )
        }
        val foreground = source.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(foreground).drawBitmap(
            scaledMask,
            0f,
            0f,
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) },
        )
        scaledMask.recycle()
        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply {
                drawColor(backgroundArgb)
                drawBitmap(foreground, 0f, 0f, null)
            }
            foreground.recycle()
        }
    }
}
