// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// benavex fork: in-app self-update for the rolling awg-latest build.
//
// The CI workflow at .github/workflows/awg-aar.yml publishes a single
// rolling GitHub release tagged "awg-latest" with:
//   - tailscale-debug.apk   (universal, arm64 + armv7 + x86_64)
//   - SHA256SUMS.txt        (apk sha256 used to verify download)
// and embeds "versionCode=<n>" in the release body. We parse that
// number, compare with our own BuildConfig.VERSION_CODE, and if the
// remote is newer: download the apk into cacheDir/apk/, verify the
// sha256, hand the file URI to PackageInstaller via a FileProvider.
object UpdateChecker {
  private const val TAG = "UpdateChecker"
  private const val RELEASE_API =
      "https://api.github.com/repos/benavex/tailscale-android/releases/tags/awg-latest"

  private val json = Json { ignoreUnknownKeys = true }

  sealed class CheckResult {
    data class UpToDate(val installed: Long) : CheckResult()
    data class Available(
        val installed: Long,
        val remote: Long,
        val apkUrl: String,
        val sumsUrl: String,
        val sizeBytes: Long
    ) : CheckResult()
    data class Error(val message: String) : CheckResult()
  }

  suspend fun check(context: Context): CheckResult =
      withContext(Dispatchers.IO) {
        try {
          val installed = installedVersionCode(context)
          val release = fetchRelease()
          val remote =
              Regex("""versionCode=(\d+)""").find(release.body ?: "")?.groupValues?.get(1)?.toLong()
                  ?: return@withContext CheckResult.Error(
                      "could not find versionCode=N in release body")

          if (remote <= installed) return@withContext CheckResult.UpToDate(installed)

          val apk =
              release.assets.firstOrNull { it.name == "tailscale-debug.apk" }
                  ?: return@withContext CheckResult.Error("no tailscale-debug.apk in release")
          val sums = release.assets.firstOrNull { it.name == "SHA256SUMS.txt" }

          CheckResult.Available(
              installed = installed,
              remote = remote,
              apkUrl = apk.browser_download_url,
              sumsUrl = sums?.browser_download_url ?: "",
              sizeBytes = apk.size,
          )
        } catch (e: Exception) {
          Log.w(TAG, "check failed", e)
          CheckResult.Error(e.message ?: e.javaClass.simpleName)
        }
      }

  // Streams the APK to cacheDir/apk/tailscale-debug.apk, verifying the
  // sha256 from SHA256SUMS.txt before returning. Progress callback is
  // invoked with a value in [0, 1] on the caller's dispatcher.
  suspend fun download(
      context: Context,
      available: CheckResult.Available,
      onProgress: (Float) -> Unit,
  ): Result<File> =
      withContext(Dispatchers.IO) {
        try {
          val expectedSha =
              if (available.sumsUrl.isNotBlank()) fetchExpectedSha(available.sumsUrl) else null

          val dir = File(context.cacheDir, "apk").apply { mkdirs() }
          // Name includes the version so if the install is cancelled we
          // don't silently reuse a stale partial on the next attempt.
          val out = File(dir, "tailscale-debug-${available.remote}.apk")
          if (out.exists()) out.delete()

          val conn =
              (URL(available.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
              }
          val total = if (available.sizeBytes > 0) available.sizeBytes else conn.contentLengthLong
          val digest = MessageDigest.getInstance("SHA-256")

          conn.inputStream.use { input ->
            FileOutputStream(out).use { fout ->
              val buf = ByteArray(64 * 1024)
              var read = 0L
              while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                fout.write(buf, 0, n)
                digest.update(buf, 0, n)
                read += n
                if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
              }
            }
          }
          conn.disconnect()

          val gotSha = digest.digest().joinToString("") { "%02x".format(it) }
          if (expectedSha != null && !gotSha.equals(expectedSha, ignoreCase = true)) {
            out.delete()
            return@withContext Result.failure(
                IllegalStateException(
                    "sha256 mismatch: got=$gotSha expected=$expectedSha"))
          }
          Result.success(out)
        } catch (e: Exception) {
          Log.w(TAG, "download failed", e)
          Result.failure(e)
        }
      }

  fun launchInstall(context: Context, apk: File) {
    val authority = "${context.packageName}.fileprovider"
    val uri: Uri = FileProvider.getUriForFile(context, authority, apk)
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, "application/vnd.android.package-archive")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    context.startActivity(intent)
  }

  // Delete any *.apk files in cacheDir/apk. Called on app start so a
  // downloaded-but-never-installed APK does not sit on disk forever.
  // Users who cancelled an install can just tap "check for updates"
  // again — the re-download is fast enough that we don't need to
  // cache across sessions.
  fun cleanupStaleApks(context: Context) {
    val dir = File(context.cacheDir, "apk")
    if (!dir.isDirectory) return
    dir.listFiles()?.forEach { f ->
      if (f.isFile && f.name.endsWith(".apk")) {
        val ok = f.delete()
        Log.d(TAG, "cleanup apk=${f.name} deleted=$ok")
      }
    }
  }

  fun installedVersionCode(context: Context): Long {
    val pi = context.packageManager.getPackageInfo(context.packageName, 0)
    return PackageInfoCompat.getLongVersionCode(pi)
  }

  private fun fetchRelease(): GhRelease {
    val body = httpGetString(RELEASE_API)
    return json.decodeFromString(GhRelease.serializer(), body)
  }

  // SHA256SUMS.txt format is "<hex>  <filename>" lines (sha256sum(1)
  // output). We pick the line for tailscale-debug.apk.
  private fun fetchExpectedSha(sumsUrl: String): String? {
    val body = httpGetString(sumsUrl)
    return body
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.endsWith("tailscale-debug.apk") }
        ?.substringBefore(' ')
  }

  private fun httpGetString(url: String): String {
    val conn =
        (URL(url).openConnection() as HttpURLConnection).apply {
          connectTimeout = 10_000
          readTimeout = 15_000
          instanceFollowRedirects = true
          setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
    try {
      if (conn.responseCode !in 200..299) {
        throw IllegalStateException("HTTP ${conn.responseCode} for $url")
      }
      return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }

  @Serializable
  private data class GhRelease(
      val name: String? = null,
      val body: String? = null,
      val tag_name: String? = null,
      val assets: List<GhAsset> = emptyList(),
  )

  @Serializable
  private data class GhAsset(
      val name: String,
      val size: Long = 0,
      val browser_download_url: String,
  )
}
