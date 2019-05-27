package me.wolszon.reddigram

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target

import io.flutter.app.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : FlutterActivity() {
    private var oauthResult: MethodChannel.Result? = null

    private var photoDownloadResult: MethodChannel.Result? = null
    private var photoDownloadUrl: String? = null

    companion object {
        private const val CHANNEL = "me.wolszon.reddigram"
        private const val OAUTH_REQUEST_CODE = 1
        private const val WRITE_STORAGE_REQUEST_CODE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GeneratedPluginRegistrant.registerWith(this)

        MethodChannel(flutterView, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "showOauthScreen" -> {
                    val clientId = call.argument<String>("clientId")
                    if (clientId.isNullOrEmpty()) {
                        result.error("No clientId", null, null)
                        return@setMethodCallHandler
                    }

                    oauthResult = result
                    startActivityForResult(OauthActivity.createIntent(this, clientId!!), OAUTH_REQUEST_CODE)
                }
                "downloadPhoto" -> {
                    val photoUrl = call.argument<String>("url")
                    if (photoUrl.isNullOrEmpty()) {
                        result.error("No photoUrl", null, null)
                        return@setMethodCallHandler
                    }

                    photoDownloadResult = result
                    photoDownloadUrl = photoUrl
                    downloadPhoto()
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun downloadPhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    WRITE_STORAGE_REQUEST_CODE)
        } else {
            Picasso.get().load(photoDownloadUrl).into(object : Target {
                override fun onPrepareLoad(placeHolderDrawable: Drawable?) = Unit

                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                    photoDownloadResult?.error("Image error", null, null)
                }

                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
                    val photoUri = Uri.parse(photoDownloadUrl)
                    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path

                    val reddigramDirectory = File("$directory/Reddigram")
                    reddigramDirectory.mkdir()

                    val photoName = Regex("""(.*)\..*""").find(photoUri.lastPathSegment!!)!!.groupValues[1]
                    val file = File(reddigramDirectory.path + "/" + photoName + ".jpg")

                    Thread {
                        try {
                            file.createNewFile()
                            val stream = FileOutputStream(file)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                            stream.close()
                            photoDownloadResult?.success("Photo saved")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            photoDownloadResult?.error("Photo saving error", null, null)
                        }
                    }.start()
                }
            })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            OAUTH_REQUEST_CODE -> {
                try {
                    if (resultCode == Activity.RESULT_OK) {
                        val code = data?.getStringExtra(OauthActivity.CODE_EXTRA)
                        oauthResult?.success(hashMapOf("code" to code))
                    } else {
                        oauthResult?.error("No response", null, null)
                    }
                } catch (e: IllegalStateException) {
                    // Sometimes Result#success/error can be called more than once
                    // which results in a fatal exception, we can safely ignore it.

                    // Or maybe it's onActivityResult that's being called multiple times? :thinking:
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            WRITE_STORAGE_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    downloadPhoto()
                } else {
                    photoDownloadResult?.error("Denied permissions", null, null)
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
