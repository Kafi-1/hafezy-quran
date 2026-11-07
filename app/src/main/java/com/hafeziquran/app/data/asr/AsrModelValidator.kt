package com.hafeziquran.app.data.asr

import java.io.File

/**
 * Cheap structural checks applied before a downloaded file is handed to ONNX
 * Runtime.
 *
 * A size floor alone cannot tell a real graph from an HTML error page saved under
 * the model name, and a truncated protobuf makes `createSession` fail inside
 * native code where Kotlin cannot catch it. These checks keep the obvious bad
 * files out of the native loader.
 */
internal object AsrModelValidator {

    private const val HEADER_SIZE = 16

    /** ONNX `ModelProto.ir_version` is field 1, so a graph starts with tag 0x08. */
    private const val ONNX_PROTOBUF_TAG: Byte = 0x08

    fun isValidModel(file: File, minBytes: Long): Boolean {
        if (!file.isFile || file.length() < minBytes) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(HEADER_SIZE)
                val read = input.read(header)
                read >= 4 &&
                    header[0] == ONNX_PROTOBUF_TAG &&
                    header.copyOfRange(1, read)
                        .toString(Charsets.US_ASCII)
                        .contains("onnx", ignoreCase = true)
            }
        }.getOrDefault(false)
    }

    fun isValidVocab(file: File, minBytes: Long): Boolean {
        if (!file.isFile || file.length() < minBytes) return false
        return runCatching {
            val text = file.readText().trim()
            text.startsWith("{") && text.endsWith("}") && text.contains("<blank>")
        }.getOrDefault(false)
    }
}