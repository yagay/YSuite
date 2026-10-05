package com.yagay.ysuite.feature.yfiles

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class YFilesShareServerSettings(
    val rootPath: String,
    val username: String = "",
    val password: String = "",
    val bindLan: Boolean = false,
    val httpPort: Int = 8088,
    val ftpPort: Int = 2121,
    val httpEnabled: Boolean = true,
    val ftpEnabled: Boolean = false,
)

data class YFilesShareServerState(
    val running: Boolean = false,
    val httpPort: Int? = null,
    val ftpPort: Int? = null,
    val error: String? = null,
)

class YFilesShareServerStore(
    context: Context,
) {
    private val cipher =
        YFilesSecretCipher(
            "ysuite_yfiles_share_server",
        )
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                "yfiles_share_server",
                Context.MODE_PRIVATE,
            )

    fun settings():
        YFilesShareServerSettings {
        val root =
            prefs.getString(
                "root",
                android.os.Environment
                    .getExternalStorageDirectory()
                    .absolutePath,
            ).orEmpty()
        return YFilesShareServerSettings(
            rootPath = root,
            username =
                prefs.getString(
                    "username",
                    "",
                ).orEmpty(),
            password =
                cipher.decrypt(
                    prefs.getString(
                        "password",
                        "",
                    ).orEmpty(),
                ),
            bindLan =
                prefs.getBoolean(
                    "bind_lan",
                    false,
                ),
            httpPort =
                prefs.getInt(
                    "http_port",
                    8088,
                ),
            ftpPort =
                prefs.getInt(
                    "ftp_port",
                    2121,
                ),
            httpEnabled =
                prefs.getBoolean(
                    "http_enabled",
                    true,
                ),
            ftpEnabled =
                prefs.getBoolean(
                    "ftp_enabled",
                    false,
                ),
        )
    }

    fun save(
        settings: YFilesShareServerSettings,
    ) {
        prefs.edit()
            .putString(
                "root",
                settings.rootPath,
            )
            .putString(
                "username",
                settings.username,
            )
            .putString(
                "password",
                cipher.encrypt(
                    settings.password,
                ),
            )
            .putBoolean(
                "bind_lan",
                settings.bindLan,
            )
            .putInt(
                "http_port",
                settings.httpPort
                    .coerceIn(
                        1024,
                        65535,
                    ),
            )
            .putInt(
                "ftp_port",
                settings.ftpPort
                    .coerceIn(
                        1024,
                        65535,
                    ),
            )
            .putBoolean(
                "http_enabled",
                settings.httpEnabled,
            )
            .putBoolean(
                "ftp_enabled",
                settings.ftpEnabled,
            )
            .apply()
    }
}

object YFilesShareServerController {
    private val mutableState =
        MutableStateFlow(
            YFilesShareServerState(),
        )
    val state: StateFlow<
        YFilesShareServerState,
        > = mutableState.asStateFlow()

    fun start(context: Context) {
        context.startForegroundService(
            Intent(
                context,
                YFilesShareServerService::class.java,
            ).setAction(
                YFilesShareServerService
                    .ACTION_START,
            ),
        )
    }

    fun stop(context: Context) {
        context.startService(
            Intent(
                context,
                YFilesShareServerService::class.java,
            ).setAction(
                YFilesShareServerService
                    .ACTION_STOP,
            ),
        )
    }

    internal fun update(
        value: YFilesShareServerState,
    ) {
        mutableState.value = value
    }
}

class YFilesShareServerService : Service() {
    private val running =
        AtomicBoolean(false)
    private val clients =
        Executors.newFixedThreadPool(
            MAX_CLIENTS,
        )
    private var httpServer:
        ServerSocket? = null
    private var ftpServer:
        ServerSocket? = null
    private var settings:
        YFilesShareServerSettings? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServers()
                stopSelf()
            }
            else -> startServers()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopServers()
        clients.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?,
    ): IBinder? = null

    private fun startServers() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        val config =
            YFilesShareServerStore(this)
                .settings()
        settings = config
        val root =
            runCatching {
                File(
                    config.rootPath,
                ).canonicalFile
            }.getOrNull()
        if (
            root == null ||
            !root.isDirectory
        ) {
            fail("Share root is unavailable")
            return
        }

        startForeground(
            NOTIFICATION_ID,
            notification(config),
        )

        val address =
            if (config.bindLan) {
                null
            } else {
                InetAddress.getLoopbackAddress()
            }

        try {
            if (config.httpEnabled) {
                httpServer =
                    ServerSocket(
                        config.httpPort,
                        50,
                        address,
                    )
                clients.execute {
                    acceptHttp(
                        root,
                        config,
                    )
                }
            }
            if (config.ftpEnabled) {
                ftpServer =
                    ServerSocket(
                        config.ftpPort,
                        20,
                        address,
                    )
                clients.execute {
                    acceptFtp(
                        root,
                        config,
                    )
                }
            }
            YFilesShareServerController
                .update(
                    YFilesShareServerState(
                        running = true,
                        httpPort =
                            if (
                                config.httpEnabled
                            ) {
                                config.httpPort
                            } else {
                                null
                            },
                        ftpPort =
                            if (
                                config.ftpEnabled
                            ) {
                                config.ftpPort
                            } else {
                                null
                            },
                    ),
                )
        } catch (error: Throwable) {
            fail(
                error.message
                    ?: "Unable to start share server",
            )
        }
    }

    private fun stopServers() {
        running.set(false)
        runCatching {
            httpServer?.close()
        }
        runCatching {
            ftpServer?.close()
        }
        httpServer = null
        ftpServer = null
        YFilesShareServerController
            .update(
                YFilesShareServerState(),
            )
    }

    private fun fail(message: String) {
        running.set(false)
        runCatching {
            httpServer?.close()
        }
        runCatching {
            ftpServer?.close()
        }
        YFilesShareServerController
            .update(
                YFilesShareServerState(
                    running = false,
                    error = message,
                ),
            )
        stopSelf()
    }

    private fun acceptHttp(
        root: File,
        config: YFilesShareServerSettings,
    ) {
        while (running.get()) {
            val socket =
                try {
                    httpServer?.accept()
                        ?: break
                } catch (_: Throwable) {
                    break
                }
            clients.execute {
                socket.use {
                    handleHttp(
                        it,
                        root,
                        config,
                    )
                }
            }
        }
    }

    private fun handleHttp(
        socket: Socket,
        root: File,
        config: YFilesShareServerSettings,
    ) {
        socket.soTimeout =
            CLIENT_TIMEOUT_MS
        val reader =
            BufferedReader(
                InputStreamReader(
                    socket.getInputStream(),
                    StandardCharsets
                        .ISO_8859_1,
                ),
            )
        val output =
            socket.getOutputStream()
        val requestLine =
            reader.readLine()
                ?: return
        val parts =
            requestLine.split(' ')
        if (parts.size < 2) {
            writeHttp(
                output,
                400,
                "Bad Request",
                "Bad Request"
                    .toByteArray(),
                "text/plain",
            )
            return
        }
        val method =
            parts[0].uppercase()
        val rawPath =
            parts[1]
                .substringBefore('?')
        val headers =
            linkedMapOf<String, String>()
        while (true) {
            val line =
                reader.readLine()
                    ?: break
            if (line.isBlank()) break
            val index =
                line.indexOf(':')
            if (index > 0) {
                headers[
                    line.substring(
                        0,
                        index,
                    ).trim()
                        .lowercase()
                ] =
                    line.substring(
                        index + 1,
                    ).trim()
            }
        }

        if (
            !authorized(
                headers[
                    "authorization"
                ],
                config,
            )
        ) {
            val body =
                "Authentication required"
                    .toByteArray()
            output.write(
                (
                    "HTTP/1.1 401 Unauthorized\r\n" +
                        "WWW-Authenticate: Basic realm=\"YFiles\"\r\n" +
                        "Content-Length: " +
                        body.size +
                        "\r\nConnection: close\r\n\r\n"
                    ).toByteArray(
                        StandardCharsets
                            .ISO_8859_1,
                    ),
            )
            output.write(body)
            output.flush()
            return
        }

        val file =
            resolvePath(
                root,
                rawPath,
            )
                ?: run {
                    writeHttp(
                        output,
                        403,
                        "Forbidden",
                        "Forbidden"
                            .toByteArray(),
                        "text/plain",
                    )
                    return
                }

        when (method) {
            "GET", "HEAD" -> {
                if (!file.exists()) {
                    writeHttp(
                        output,
                        404,
                        "Not Found",
                        "Not Found"
                            .toByteArray(),
                        "text/plain",
                    )
                    return
                }
                if (file.isDirectory) {
                    val body =
                        directoryHtml(
                            root,
                            file,
                        ).toByteArray(
                            StandardCharsets
                                .UTF_8,
                        )
                    writeHttp(
                        output,
                        200,
                        "OK",
                        if (
                            method == "HEAD"
                        ) {
                            ByteArray(0)
                        } else {
                            body
                        },
                        "text/html; charset=utf-8",
                        advertisedLength =
                            body.size.toLong(),
                    )
                } else {
                    val range =
                        parseRange(
                            headers["range"],
                            file.length(),
                        )
                    val start =
                        range?.first ?: 0L
                    val end =
                        range?.last
                            ?: (
                                file.length() -
                                    1L
                                )
                    val length =
                        if (
                            file.length() == 0L
                        ) {
                            0L
                        } else {
                            end - start + 1L
                        }
                    val status =
                        if (range == null) {
                            "HTTP/1.1 200 OK"
                        } else {
                            "HTTP/1.1 206 Partial Content"
                        }
                    val header =
                        buildString {
                            append(status)
                            append("\r\n")
                            append(
                                "Content-Type: application/octet-stream\r\n",
                            )
                            append(
                                "Accept-Ranges: bytes\r\n",
                            )
                            append(
                                "Content-Length: ",
                            )
                            append(length)
                            append("\r\n")
                            if (range != null) {
                                append(
                                    "Content-Range: bytes ",
                                )
                                append(start)
                                append('-')
                                append(end)
                                append('/')
                                append(
                                    file.length(),
                                )
                                append("\r\n")
                            }
                            append(
                                "Connection: close\r\n\r\n",
                            )
                        }
                    output.write(
                        header.toByteArray(
                            StandardCharsets
                                .ISO_8859_1,
                        ),
                    )
                    if (
                        method != "HEAD" &&
                        length > 0L
                    ) {
                        FileInputStream(file)
                            .use { input ->
                                var remainingSkip =
                                    start
                                while (
                                    remainingSkip >
                                    0L
                                ) {
                                    val skipped =
                                        input.skip(
                                            remainingSkip,
                                        )
                                    if (
                                        skipped <= 0L
                                    ) {
                                        break
                                    }
                                    remainingSkip -=
                                        skipped
                                }
                                val buffer =
                                    ByteArray(
                                        BUFFER_SIZE,
                                    )
                                var remaining =
                                    length
                                while (
                                    remaining > 0L
                                ) {
                                    val count =
                                        input.read(
                                            buffer,
                                            0,
                                            minOf(
                                                buffer
                                                    .size
                                                    .toLong(),
                                                remaining,
                                            ).toInt(),
                                        )
                                    if (count < 0) {
                                        break
                                    }
                                    output.write(
                                        buffer,
                                        0,
                                        count,
                                    )
                                    remaining -= count
                                }
                            }
                    }
                    output.flush()
                }
            }
            "PUT" -> {
                val length =
                    headers[
                        "content-length"
                    ]?.toLongOrNull()
                        ?: 0L
                if (
                    length >
                    MAX_UPLOAD_BYTES
                ) {
                    writeHttp(
                        output,
                        413,
                        "Payload Too Large",
                        ByteArray(0),
                        "text/plain",
                    )
                    return
                }
                file.parentFile?.mkdirs()
                FileOutputStream(file)
                    .use { target ->
                        val input =
                            socket.getInputStream()
                        var remaining =
                            length
                        val buffer =
                            ByteArray(
                                BUFFER_SIZE,
                            )
                        while (
                            remaining > 0L
                        ) {
                            val count =
                                input.read(
                                    buffer,
                                    0,
                                    minOf(
                                        remaining,
                                        buffer.size
                                            .toLong(),
                                    ).toInt(),
                                )
                            if (count < 0) break
                            target.write(
                                buffer,
                                0,
                                count,
                            )
                            remaining -= count
                        }
                    }
                writeHttp(
                    output,
                    201,
                    "Created",
                    ByteArray(0),
                    "text/plain",
                )
            }
            "DELETE" -> {
                val ok =
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                writeHttp(
                    output,
                    if (ok) 204 else 409,
                    if (ok) "No Content"
                    else "Conflict",
                    ByteArray(0),
                    "text/plain",
                )
            }
            "MKCOL" -> {
                val ok =
                    file.mkdirs() ||
                        file.isDirectory
                writeHttp(
                    output,
                    if (ok) 201 else 409,
                    if (ok) "Created"
                    else "Conflict",
                    ByteArray(0),
                    "text/plain",
                )
            }
            else ->
                writeHttp(
                    output,
                    405,
                    "Method Not Allowed",
                    ByteArray(0),
                    "text/plain",
                )
        }
    }

    private fun acceptFtp(
        root: File,
        config: YFilesShareServerSettings,
    ) {
        while (running.get()) {
            val socket =
                try {
                    ftpServer?.accept()
                        ?: break
                } catch (_: Throwable) {
                    break
                }
            clients.execute {
                socket.use {
                    handleFtp(
                        it,
                        root,
                        config,
                    )
                }
            }
        }
    }

    private fun handleFtp(
        socket: Socket,
        root: File,
        config: YFilesShareServerSettings,
    ) {
        socket.soTimeout =
            CLIENT_TIMEOUT_MS
        val reader =
            socket.getInputStream()
                .bufferedReader()
        val writer =
            BufferedWriter(
                OutputStreamWriter(
                    socket.getOutputStream(),
                    StandardCharsets
                        .ISO_8859_1,
                ),
            )
        fun reply(
            code: Int,
            text: String,
        ) {
            writer.write(
                "$code $text\r\n",
            )
            writer.flush()
        }

        var authenticated =
            config.username.isBlank() &&
                config.password.isBlank()
        var pendingUser = ""
        var cwd = "/"
        var passive:
            ServerSocket? = null

        reply(220, "YFiles FTP ready")
        while (running.get()) {
            val line =
                reader.readLine()
                    ?: break
            val command =
                line.substringBefore(' ')
                    .uppercase()
            val argument =
                line.substringAfter(
                    ' ',
                    "",
                ).trim()
            when (command) {
                "USER" -> {
                    pendingUser =
                        argument
                    reply(
                        331,
                        "Password required",
                    )
                }
                "PASS" -> {
                    authenticated =
                        (
                            config.username
                                .isBlank() ||
                                pendingUser ==
                                    config.username
                            ) &&
                            (
                                config.password
                                    .isBlank() ||
                                    argument ==
                                        config.password
                                )
                    reply(
                        if (authenticated) {
                            230
                        } else {
                            530
                        },
                        if (authenticated) {
                            "Logged in"
                        } else {
                            "Login incorrect"
                        },
                    )
                }
                "QUIT" -> {
                    reply(221, "Bye")
                    break
                }
                else -> {
                    if (!authenticated) {
                        reply(
                            530,
                            "Please login",
                        )
                        continue
                    }
                    when (command) {
                        "SYST" ->
                            reply(
                                215,
                                "UNIX Type: L8",
                            )
                        "TYPE" ->
                            reply(
                                200,
                                "Type set",
                            )
                        "PWD" ->
                            reply(
                                257,
                                "\"$cwd\"",
                            )
                        "CWD" -> {
                            val target =
                                ftpResolve(
                                    root,
                                    cwd,
                                    argument,
                                )
                            if (
                                target != null &&
                                target.isDirectory
                            ) {
                                cwd =
                                    ftpPath(
                                        root,
                                        target,
                                    )
                                reply(
                                    250,
                                    "Directory changed",
                                )
                            } else {
                                reply(
                                    550,
                                    "Directory unavailable",
                                )
                            }
                        }
                        "PASV" -> {
                            runCatching {
                                passive?.close()
                            }
                            passive =
                                ServerSocket(
                                    0,
                                    1,
                                    socket.localAddress,
                                )
                            val port =
                                passive!!
                                    .localPort
                            val addr =
                                socket
                                    .localAddress
                                    .address
                            if (
                                addr.size != 4
                            ) {
                                reply(
                                    522,
                                    "IPv4 PASV required",
                                )
                            } else {
                                reply(
                                    227,
                                    "Entering Passive Mode (" +
                                        addr.joinToString(
                                            ",",
                                        ) {
                                            (
                                                it.toInt() and
                                                    0xff
                                                ).toString()
                                        } +
                                        "," +
                                        port / 256 +
                                        "," +
                                        port % 256 +
                                        ")",
                                )
                            }
                        }
                        "LIST",
                        "NLST",
                        "RETR",
                        "STOR" -> {
                            if (passive == null) {
                                reply(
                                    425,
                                    "Use PASV first",
                                )
                                continue
                            }
                            val dataServer =
                                passive
                                    ?: continue
                            reply(
                                150,
                                "Opening data connection",
                            )
                            val dataSocket =
                                runCatching {
                                    dataServer
                                        .accept()
                                }.getOrNull()
                            runCatching {
                                dataServer.close()
                            }
                            passive = null
                            if (dataSocket == null) {
                                reply(
                                    425,
                                    "Data connection failed",
                                )
                                continue
                            }
                            dataSocket.use {
                                data ->
                                val target =
                                    ftpResolve(
                                        root,
                                        cwd,
                                        argument,
                                    )
                                when (command) {
                                    "LIST",
                                    "NLST" -> {
                                        val dir =
                                            if (
                                                argument
                                                    .isBlank()
                                            ) {
                                                ftpResolve(
                                                    root,
                                                    cwd,
                                                    ".",
                                                )
                                            } else {
                                                target
                                            }
                                        if (
                                            dir == null ||
                                            !dir.isDirectory
                                        ) {
                                            reply(
                                                550,
                                                "Directory unavailable",
                                            )
                                            return@use
                                        }
                                        val out =
                                            data.getOutputStream()
                                                .bufferedWriter()
                                        dir.listFiles()
                                            .orEmpty()
                                            .sortedBy {
                                                it.name
                                                    .lowercase()
                                            }
                                            .forEach {
                                                file ->
                                                if (
                                                    command ==
                                                    "NLST"
                                                ) {
                                                    out.write(
                                                        file.name +
                                                            "\r\n",
                                                    )
                                                } else {
                                                    out.write(
                                                        (
                                                            if (
                                                                file.isDirectory
                                                            ) {
                                                                "drwxr-xr-x"
                                                            } else {
                                                                "-rw-r--r--"
                                                            }
                                                            ) +
                                                            " 1 owner group " +
                                                            file.length() +
                                                            " Jan 01 00:00 " +
                                                            file.name +
                                                            "\r\n",
                                                    )
                                                }
                                            }
                                        out.flush()
                                    }
                                    "RETR" -> {
                                        if (
                                            target == null ||
                                            !target.isFile
                                        ) {
                                            reply(
                                                550,
                                                "File unavailable",
                                            )
                                            return@use
                                        }
                                        FileInputStream(
                                            target,
                                        ).use {
                                            input ->
                                            input.copyTo(
                                                data.getOutputStream(),
                                                BUFFER_SIZE,
                                            )
                                        }
                                    }
                                    "STOR" -> {
                                        if (
                                            target == null
                                        ) {
                                            reply(
                                                550,
                                                "Invalid target",
                                            )
                                            return@use
                                        }
                                        target.parentFile
                                            ?.mkdirs()
                                        FileOutputStream(
                                            target,
                                        ).use {
                                            output ->
                                            data.getInputStream()
                                                .copyTo(
                                                    output,
                                                    BUFFER_SIZE,
                                                )
                                        }
                                    }
                                }
                            }
                            reply(
                                226,
                                "Transfer complete",
                            )
                        }
                        "DELE",
                        "RMD" -> {
                            val target =
                                ftpResolve(
                                    root,
                                    cwd,
                                    argument,
                                )
                            val ok =
                                when {
                                    target == null ->
                                        false
                                    command ==
                                        "RMD" ->
                                        target
                                            .deleteRecursively()
                                    else ->
                                        target.delete()
                                }
                            reply(
                                if (ok) 250 else 550,
                                if (ok) {
                                    "Deleted"
                                } else {
                                    "Delete failed"
                                },
                            )
                        }
                        "MKD" -> {
                            val target =
                                ftpResolve(
                                    root,
                                    cwd,
                                    argument,
                                )
                            val ok =
                                target != null &&
                                    (
                                        target.mkdirs() ||
                                            target
                                                .isDirectory
                                        )
                            reply(
                                if (ok) 257 else 550,
                                if (ok) {
                                    "Created"
                                } else {
                                    "Create failed"
                                },
                            )
                        }
                        else ->
                            reply(
                                502,
                                "Command not implemented",
                            )
                    }
                }
            }
        }
        runCatching {
            passive?.close()
        }
    }

    private fun authorized(
        authorization: String?,
        config: YFilesShareServerSettings,
    ): Boolean {
        if (
            config.username.isBlank() &&
            config.password.isBlank()
        ) {
            return true
        }
        if (
            authorization.isNullOrBlank() ||
            !authorization.startsWith(
                "Basic ",
                ignoreCase = true,
            )
        ) {
            return false
        }
        val value =
            runCatching {
                String(
                    Base64.getDecoder()
                        .decode(
                            authorization
                                .substringAfter(
                                    ' ',
                                ),
                        ),
                    StandardCharsets
                        .UTF_8,
                )
            }.getOrNull()
                ?: return false
        return value ==
            config.username +
                ":" +
                config.password
    }

    private fun resolvePath(
        root: File,
        encodedPath: String,
    ): File? =
        runCatching {
            val decoded =
                URLDecoder.decode(
                    encodedPath,
                    StandardCharsets.UTF_8
                        .name(),
                )
            val relative =
                decoded.trimStart('/')
            if (
                relative.split('/')
                    .any {
                        it == ".."
                    }
            ) {
                return null
            }
            val target =
                File(
                    root,
                    relative,
                ).canonicalFile
            if (
                target == root ||
                target.path.startsWith(
                    root.path +
                        File.separator,
                )
            ) {
                target
            } else {
                null
            }
        }.getOrNull()

    private fun ftpResolve(
        root: File,
        cwd: String,
        argument: String,
    ): File? {
        val raw =
            if (
                argument.startsWith("/")
            ) {
                argument
            } else {
                cwd.trimEnd('/') +
                    "/" +
                    argument
            }
        return resolvePath(root, raw)
    }

    private fun ftpPath(
        root: File,
        file: File,
    ): String =
        if (file == root) {
            "/"
        } else {
            "/" +
                file.relativeTo(root)
                    .invariantSeparatorsPath
        }

    private fun directoryHtml(
        root: File,
        directory: File,
    ): String =
        buildString {
            append(
                "<!doctype html><meta charset=utf-8><title>YFiles</title><h1>",
            )
            append(
                escapeHtml(
                    ftpPath(
                        root,
                        directory,
                    ),
                ),
            )
            append("</h1><ul>")
            if (directory != root) {
                append(
                    "<li><a href=\"../\">..</a></li>",
                )
            }
            directory.listFiles()
                .orEmpty()
                .sortedWith(
                    compareByDescending<File> {
                        it.isDirectory
                    }.thenBy {
                        it.name.lowercase()
                    },
                )
                .forEach {
                    append("<li><a href=\"")
                    append(
                        urlEncodeSegment(
                            it.name,
                        ),
                    )
                    if (it.isDirectory) {
                        append('/')
                    }
                    append("\">")
                    append(
                        escapeHtml(it.name),
                    )
                    if (it.isDirectory) {
                        append('/')
                    }
                    append("</a></li>")
                }
            append("</ul>")
        }

    private fun writeHttp(
        output: java.io.OutputStream,
        code: Int,
        reason: String,
        body: ByteArray,
        contentType: String,
        advertisedLength: Long =
            body.size.toLong(),
    ) {
        output.write(
            (
                "HTTP/1.1 $code $reason\r\n" +
                    "Content-Type: $contentType\r\n" +
                    "Content-Length: $advertisedLength\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(
                    StandardCharsets
                        .ISO_8859_1,
                ),
        )
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }

    private fun parseRange(
        value: String?,
        length: Long,
    ): LongRange? {
        if (
            value.isNullOrBlank() ||
            !value.startsWith("bytes=")
        ) {
            return null
        }
        val first =
            value.removePrefix(
                "bytes=",
            ).substringBefore(',')
        val start =
            first.substringBefore('-')
                .toLongOrNull()
                ?: return null
        val end =
            first.substringAfter(
                '-',
                "",
            ).toLongOrNull()
                ?: (
                    length - 1L
                    )
        if (
            start < 0L ||
            end < start ||
            start >= length
        ) {
            return null
        }
        return start..
            end.coerceAtMost(
                length - 1L,
            )
    }

    private fun urlEncodeSegment(
        value: String,
    ): String =
        java.net.URLEncoder
            .encode(
                value,
                StandardCharsets.UTF_8
                    .name(),
            )
            .replace("+", "%20")

    private fun escapeHtml(
        value: String,
    ): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun notification(
        settings: YFilesShareServerSettings,
    ): Notification =
        NotificationCompat.Builder(
            this,
            CHANNEL_ID,
        )
            .setSmallIcon(
                android.R.drawable
                    .stat_sys_upload,
            )
            .setContentTitle(
                "YFiles share server",
            )
            .setContentText(
                buildString {
                    if (
                        settings.httpEnabled
                    ) {
                        append(
                            "HTTP :",
                        )
                        append(
                            settings.httpPort,
                        )
                    }
                    if (
                        settings.ftpEnabled
                    ) {
                        if (isNotEmpty()) {
                            append(" • ")
                        }
                        append(
                            "FTP :",
                        )
                        append(
                            settings.ftpPort,
                        )
                    }
                },
            )
            .setOngoing(true)
            .build()

    private fun createChannel() {
        val manager =
            getSystemService(
                NotificationManager::class.java,
            )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "YFiles share server",
                NotificationManager
                    .IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val ACTION_START =
            "com.yagay.ysuite.yfiles.SHARE_START"
        const val ACTION_STOP =
            "com.yagay.ysuite.yfiles.SHARE_STOP"
        private const val CHANNEL_ID =
            "yfiles_share_server"
        private const val NOTIFICATION_ID =
            19427
        private const val MAX_CLIENTS = 8
        private const val CLIENT_TIMEOUT_MS =
            30_000
        private const val BUFFER_SIZE =
            64 * 1024
        private const val MAX_UPLOAD_BYTES =
            8L * 1024L * 1024L * 1024L
    }
}
