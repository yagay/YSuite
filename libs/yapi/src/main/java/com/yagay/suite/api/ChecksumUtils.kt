package com.yagay.suite.api

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Shared streaming checksum helpers for all YSuite features. */
object ChecksumUtils {
    private const val DEFAULT_BUFFER_SIZE = 256 * 1024

    /** Calculates SHA-256 without taking ownership of [input]. */
    fun sha256(input: InputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): String {
        require(bufferSize > 0) { "bufferSize must be positive" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(bufferSize)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    /** Calculates SHA-256 for [file] and closes the internally opened stream. */
    fun sha256(file: File, bufferSize: Int = DEFAULT_BUFFER_SIZE): String =
        file.inputStream().buffered(bufferSize).use { input -> sha256(input, bufferSize) }
}
