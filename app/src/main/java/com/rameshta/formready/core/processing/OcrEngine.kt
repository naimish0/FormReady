package com.rameshta.formready.core.processing

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class OcrScript {
    LATIN,
    DEVANAGARI,
}

interface OcrEngine {
    suspend fun recognize(file: File, script: OcrScript): String
}

class MlKitOcrEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OcrEngine {
    override suspend fun recognize(file: File, script: OcrScript): String {
        val recognizer = when (script) {
            OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.DEVANAGARI -> TextRecognition.getClient(
                DevanagariTextRecognizerOptions.Builder().build(),
            )
        }
        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    .addOnCanceledListener { continuation.cancel() }
            }
        } finally {
            recognizer.close()
        }
    }
}
