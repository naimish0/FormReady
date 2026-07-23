package com.rameshta.formready.core.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.rameshta.formready.core.model.OutputArtifact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

class AndroidImageMetadataReader @Inject constructor() : ImageMetadataReader {
    override suspend fun inspect(input: File): ImageMetadata = withContext(Dispatchers.IO) {
        if (!input.isFile || input.length() <= 0L) {
            throw PhotoProcessingException(PhotoProcessingException.Code.CORRUPT_INPUT)
        }
        val header = input.inputStream().buffered().use { stream ->
            ByteArray(MAX_HEADER_BYTES).let { buffer ->
                val read = stream.read(buffer)
                if (read <= 0) ByteArray(0) else buffer.copyOf(read)
            }
        }
        val format = detectFormat(header)
            ?: throw PhotoProcessingException(PhotoProcessingException.Code.UNSUPPORTED_FORMAT)
        if (isAnimated(format, header)) {
            throw PhotoProcessingException(
                PhotoProcessingException.Code.ANIMATED_IMAGE_UNSUPPORTED,
            )
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw PhotoProcessingException(PhotoProcessingException.Code.CORRUPT_INPUT)
        }
        val pixels = options.outWidth.toLong() * options.outHeight.toLong()
        if (pixels > MAX_IMAGE_PIXELS) {
            throw PhotoProcessingException(PhotoProcessingException.Code.IMAGE_DIMENSIONS_UNSAFE)
        }

        val exif = runCatching { ExifInterface(input.absolutePath) }.getOrNull()
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val swapsAxes = orientation in setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
        ImageMetadata(
            widthPx = if (swapsAxes) options.outHeight else options.outWidth,
            heightPx = if (swapsAxes) options.outWidth else options.outHeight,
            byteCount = input.length(),
            mimeType = options.outMimeType ?: format.defaultMimeType,
            exifOrientation = orientation,
            format = format,
            dpi = if (format == InputImageFormat.PNG) {
                PngDpiMetadata.read(input)
            } else {
                exif?.readDpi()
            },
            hasAlpha = format == InputImageFormat.PNG || format == InputImageFormat.WEBP,
        )
    }

    private fun detectFormat(header: ByteArray): InputImageFormat? = when {
        header.size >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte() -> InputImageFormat.JPEG
        header.size >= 8 &&
            header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> InputImageFormat.PNG
        header.size >= 12 &&
            header.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            header.copyOfRange(8, 12).decodeToString() == "WEBP" -> InputImageFormat.WEBP
        header.size >= 12 &&
            header.copyOfRange(4, 8).decodeToString() == "ftyp" &&
            header.copyOfRange(8, 12).decodeToString() in HEIF_BRANDS -> InputImageFormat.HEIF
        else -> null
    }

    private fun isAnimated(format: InputImageFormat, header: ByteArray): Boolean = when (format) {
        InputImageFormat.WEBP -> header.indexOfAscii("ANIM") >= 0
        InputImageFormat.PNG -> header.indexOfAscii("acTL") >= 0
        InputImageFormat.JPEG, InputImageFormat.HEIF -> false
    }

    private fun ExifInterface.readDpi(): Int? {
        val xResolution = getAttributeDouble(ExifInterface.TAG_X_RESOLUTION, 0.0)
        if (xResolution <= 0.0) return null
        return when (getAttributeInt(ExifInterface.TAG_RESOLUTION_UNIT, 2)) {
            3 -> (xResolution * 2.54).roundToInt()
            else -> xResolution.roundToInt()
        }
    }

    private fun ByteArray.indexOfAscii(value: String): Int {
        val needle = value.encodeToByteArray()
        return indices.firstOrNull { start ->
            start + needle.size <= size &&
                needle.indices.all { offset -> this[start + offset] == needle[offset] }
        } ?: -1
    }

    private val InputImageFormat.defaultMimeType: String
        get() = when (this) {
            InputImageFormat.JPEG -> "image/jpeg"
            InputImageFormat.PNG -> "image/png"
            InputImageFormat.WEBP -> "image/webp"
            InputImageFormat.HEIF -> "image/heif"
        }

    companion object {
        const val MAX_IMAGE_PIXELS = 100_000_000L
        private const val MAX_HEADER_BYTES = 1024 * 1024
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
        private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
    }
}

class PrivatePhotoPreparationService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val inputStager: InputStager,
    private val metadataReader: ImageMetadataReader,
) : PhotoPreparationService {
    override suspend fun stageAndInspect(
        source: Uri,
        jobId: UUID,
        reportedMimeType: String?,
    ): ImageMetadata {
        val staged = inputStager.stage(source, jobId, reportedMimeType)
        return try {
            metadataReader.inspect(resolve(staged.privateRelativePath))
        } catch (error: Exception) {
            inputStager.remove(jobId)
            throw error
        }
    }

    override suspend fun inspectStaged(jobId: UUID): ImageMetadata =
        metadataReader.inspect(resolve(stagedRelativePath(jobId)))

    override suspend fun loadPreview(jobId: UUID, maximumSidePx: Int): Bitmap =
        withContext(Dispatchers.IO) {
            require(maximumSidePx in 128..2_048)
            val file = resolve(stagedRelativePath(jobId))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maximumSidePx) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: throw PhotoProcessingException(PhotoProcessingException.Code.DECODE_FAILED)
            val orientation = metadataReader.inspect(file).exifOrientation
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                else -> return@withContext decoded
            }
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                matrix,
                true,
            ).also {
                if (it !== decoded) decoded.recycle()
            }
        }

    override fun stagedRelativePath(jobId: UUID): String = "staged-inputs/$jobId"

    override suspend fun duplicateStaged(sourceJobId: UUID, destinationJobId: UUID) =
        withContext(Dispatchers.IO) {
            val source = resolve(stagedRelativePath(sourceJobId))
            val destination = resolve(stagedRelativePath(destinationJobId))
            check(source.isFile && source.length() in 1..PrivateInputStager.MAX_STAGED_INPUT_BYTES)
            source.copyTo(destination, overwrite = false)
            if (destination.length() != source.length()) {
                destination.delete()
                throw PhotoProcessingException(PhotoProcessingException.Code.OUTPUT_WRITE_FAILED)
            }
        }

    override suspend fun discard(jobId: UUID) = inputStager.remove(jobId)

    private fun resolve(relativePath: String): File {
        val root = context.noBackupFilesDir.canonicalFile
        val file = File(root, relativePath).canonicalFile
        if (!file.path.startsWith("${root.path}${File.separator}")) {
            throw PhotoProcessingException(PhotoProcessingException.Code.CORRUPT_INPUT)
        }
        return file
    }
}

class PrivatePhotoOutputAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PhotoOutputAccess {
    override fun outputFile(artifact: OutputArtifact): File {
        val root = File(context.filesDir, OUTPUT_DIRECTORY).canonicalFile
        if (!artifact.uri.startsWith("$OUTPUT_DIRECTORY/")) {
            throw PhotoProcessingException(PhotoProcessingException.Code.OUTPUT_VALIDATION_FAILED)
        }
        val relative = artifact.uri.removePrefix("$OUTPUT_DIRECTORY/")
        val file = File(root, relative).canonicalFile
        if (!file.path.startsWith("${root.path}${File.separator}")) {
            throw PhotoProcessingException(PhotoProcessingException.Code.OUTPUT_VALIDATION_FAILED)
        }
        return file
    }

    override fun shareUri(artifact: OutputArtifact): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.files",
        outputFile(artifact),
    )

    override suspend fun copyTo(artifact: OutputArtifact, destination: Uri) =
        withContext(Dispatchers.IO) {
            val source = outputFile(artifact)
            try {
                context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                    source.inputStream().buffered().use { input -> input.copyTo(output) }
                } ?: throw PhotoProcessingException(
                    PhotoProcessingException.Code.DESTINATION_WRITE_FAILED,
                )
                val reopenedBytes = context.contentResolver.openFileDescriptor(destination, "r")
                    ?.use { descriptor -> descriptor.statSize }
                if (reopenedBytes != null && reopenedBytes >= 0 && reopenedBytes != source.length()) {
                    throw PhotoProcessingException(
                        PhotoProcessingException.Code.DESTINATION_WRITE_FAILED,
                    )
                }
                context.contentResolver.openInputStream(destination)?.buffered()?.use { reopened ->
                    if (!source.sha256().contentEquals(reopened.sha256())) {
                        throw PhotoProcessingException(
                            PhotoProcessingException.Code.DESTINATION_WRITE_FAILED,
                        )
                    }
                }
            } catch (error: Exception) {
                runCatching { context.contentResolver.delete(destination, null, null) }
                if (error is PhotoProcessingException) throw error
                throw PhotoProcessingException(
                    PhotoProcessingException.Code.DESTINATION_WRITE_FAILED,
                    error,
                )
            }
            Unit
        }

    companion object {
        const val OUTPUT_DIRECTORY = "outputs"
    }
}

private fun File.sha256(): ByteArray = inputStream().buffered().use { it.sha256() }

private fun java.io.InputStream.sha256(): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest()
}
