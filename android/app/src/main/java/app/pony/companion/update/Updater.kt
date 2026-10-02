package app.pony.companion.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import app.pony.companion.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpToDate(val version: String) : UpdateState()
    data object NoRelease : UpdateState()
    data class Available(val manifest: UpdateManifest) : UpdateState()
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateState()
    data class Verifying(val manifest: UpdateManifest) : UpdateState()
    data class NeedsPermission(val manifest: UpdateManifest) : UpdateState()
    data class Installing(val manifest: UpdateManifest) : UpdateState()
    data class Failed(val message: String, val manifest: UpdateManifest? = null) : UpdateState()
}

/**
 * "Check for updates" against the Pony download server. An update installs
 * only if its SHA-256 matches the manifest and it is signed with the same
 * certificate as the Pony already on this phone.
 */
object Updater {
    private val io = Executors.newSingleThreadExecutor { Thread(it, "pony-update") }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun check(context: Context) {
        val app = context.applicationContext
        _state.value = UpdateState.Checking
        io.execute {
            _state.value = try {
                val request = Request.Builder().url(UpdateManifests.MANIFEST_URL).header("Cache-Control", "no-cache").build()
                http.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 -> UpdateState.NoRelease
                        !response.isSuccessful -> UpdateState.Failed("The update server answered ${response.code}. Try again later.")
                        else -> {
                            val manifest = UpdateManifests.parse(response.body?.string().orEmpty())
                            when {
                                !UpdateManifests.isNewer(manifest, BuildConfig.VERSION_CODE) -> UpdateState.UpToDate(BuildConfig.VERSION_NAME)
                                !UpdateManifests.supports(manifest, Build.VERSION.SDK_INT) ->
                                    UpdateState.Failed("Pony ${manifest.versionName} needs a newer version of Android.", manifest)
                                else -> UpdateState.Available(manifest)
                            }
                        }
                    }
                }
            } catch (err: Exception) {
                UpdateState.Failed(friendly(err))
            }
            if (_state.value is UpdateState.Available) cleanup(app)
        }
    }

    fun download(context: Context, manifest: UpdateManifest) {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(manifest)
            return
        }
        _state.value = UpdateState.Downloading(manifest, 0f)
        io.execute {
            val file = File(File(app.cacheDir, "updates").apply { mkdirs() }, "pony-${manifest.versionCode}.apk")
            try {
                val request = Request.Builder().url(manifest.apkUrl).build()
                val digest = MessageDigest.getInstance("SHA-256")
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("The download failed (${response.code}).")
                    val body = response.body ?: error("The download was empty.")
                    val total = body.contentLength().takeIf { it > 0 } ?: manifest.size ?: -1L
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                copied += read
                                if (total > 0) _state.value = UpdateState.Downloading(manifest, (copied.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                _state.value = UpdateState.Verifying(manifest)
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!sha.equals(manifest.sha256, ignoreCase = true)) {
                    file.delete()
                    error("The download didn't match its checksum, so Pony deleted it.")
                }
                verifyPackage(app, file, manifest)
                install(app, file, manifest)
            } catch (err: Exception) {
                file.delete()
                _state.value = UpdateState.Failed(friendly(err), manifest)
            }
        }
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }

    internal fun onInstallStatus(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val manifest = (state.value as? UpdateState.Installing)?.manifest
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.UpToDate(manifest?.versionName ?: BuildConfig.VERSION_NAME)
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                _state.value = UpdateState.Failed(
                    if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "The update was cancelled." else "Android didn't install the update. ${message.orEmpty()}".trim(),
                    manifest,
                )
            }
        }
    }

    private fun verifyPackage(context: Context, file: File, manifest: UpdateManifest) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: error("That file isn't an Android app.")
        if (archive.packageName != context.packageName) error("This update is for a different build of Pony (${archive.packageName}).")
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
        if (archiveCode < manifest.versionCode) error("The download is older than the update it claims to be.")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val expected = certificates(installed)
        if (expected.isEmpty() || certificates(archive) != expected) {
            error("This update isn't signed by the same key as your Pony, so Pony won't install it.")
        }
    }

    private fun certificates(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun install(context: Context, file: File, manifest: UpdateManifest) {
        _state.value = UpdateState.Installing(manifest)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("pony.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = Intent(context, UpdateStatusReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, status, flags).intentSender)
        }
    }

    private fun cleanup(context: Context) {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    private fun friendly(err: Exception): String = when (err) {
        is java.net.UnknownHostException -> "Pony couldn't reach the update server. Check your connection."
        is java.net.SocketTimeoutException -> "The update server took too long to answer."
        is javax.net.ssl.SSLException -> "The update server's certificate didn't check out, so Pony stopped."
        else -> err.message ?: "Something went wrong while updating."
    }

    const val ACTION_STATUS = "app.pony.companion.UPDATE_STATUS"
}

class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Updater.ACTION_STATUS) Updater.onInstallStatus(context, intent)
    }
}
