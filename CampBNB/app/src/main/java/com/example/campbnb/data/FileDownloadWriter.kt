package com.example.campbnb.data

// AI-assisted (shared file-download sink): written by Qwen 3.8 Flash
// IQ1_M.

/**
 * Provider: the shared half of every file the user downloads. What a download holds —
 * which file it is called and what goes inside — belongs to the feature that offers it
 * (a reservation CSV names itself from its reservation, a QR code file from its campsite);
 * how a named file with its bytes reaches the user's download location is the same for
 * all of them, so this boundary is written once. Files are bytes, and every real file
 * holds bytes: a reservation CSV's bytes are its text, a QR code file's bytes are a
 * real PNG image. The real download system will supply the
 * real provider (the same wait the reservation CSVs hold); NoFileDownloadWriter keeps
 * screens working with no provider at all.
 */

/** Turns any value into a safe file name: letters, digits and . _ - keep, anything else becomes a dash. */
fun downloadSafeName(value: String): String {
    return value.map { character ->
        if (character.isLetterOrDigit() || character in "._-") character else '-'
    }.joinToString("")
}

interface FileDownloadWriter {
    fun downloadFile(
        fileName: String,
        content: ByteArray,
        onDone: (Result<Unit>) -> Unit
    )
}

class NoFileDownloadWriter : FileDownloadWriter {
    override fun downloadFile(
        fileName: String,
        content: ByteArray,
        onDone: (Result<Unit>) -> Unit
    ) {
        onDone(Result.success(Unit))
    }
}

class InMemoryFileDownloadWriter : FileDownloadWriter {
    val writtenFiles = mutableMapOf<String, ByteArray>()

    override fun downloadFile(
        fileName: String,
        content: ByteArray,
        onDone: (Result<Unit>) -> Unit
    ) {
        writtenFiles[fileName] = content
        onDone(Result.success(Unit))
    }
}

/**
 * The real download provider: a named file with its bytes reaches the user's own
 * Downloads folder. The write is asked for through the platform's public Downloads
 * location, which is where a legacy path is allowed up to API 28 and which the
 * manifest declares with WRITE_EXTERNAL_STORAGE capped at that API; a request
 * arriving here already runs off the screen's draw path, so the write answers the
 * feature through its continuation rather than blocking a frame. From API 29 the
 * file would instead be inserted into the media library and written through the
 * address it returns, but this world's platform carries no media-library classes
 * to name that path, so the direct location is used wherever it is allowed.
 */
class PublicDownloadsFileDownloadWriter : FileDownloadWriter {
    override fun downloadFile(
        fileName: String,
        content: ByteArray,
        onDone: (Result<Unit>) -> Unit
    ) {
        try {
            val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            java.io.File(downloads, fileName).writeBytes(content)
            onDone(Result.success(Unit))
        } catch (failure: Exception) {
            onDone(Result.failure(failure))
        }
    }
}
