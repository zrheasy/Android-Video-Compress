package com.zrh.video.compress

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore.MediaColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.zrh.file.picker.FilePickCallback
import com.zrh.file.picker.FilePickOptions
import com.zrh.file.picker.FilePicker
import com.zrh.file.picker.UriUtils
import com.zrh.permission.PermissionUtils
import com.zrh.video.VideoCompressCallback
import com.zrh.video.VideoCompressUtils
import com.zrh.video.VideoMetadata
import com.zrh.video.VideoUtils
import com.zrh.video.compress.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityMainBinding

    private var selectVideo: Uri? = null
    private var compressFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        mBinding.btnSelectFile.setOnClickListener {
            PermissionUtils.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { _, granted ->
                if (granted) {
                    selectFile()
                }
            }
        }

        mBinding.btnCompress.setOnClickListener {
            compressVideo()
        }

        mBinding.btnPlay.setOnClickListener {
            play()
        }

        mBinding.btnStop.setOnClickListener {
            mBinding.videoView.stopPlayback()
            mBinding.playLayout.isVisible = false
        }
    }

    private fun play() {
        if (compressFile == null) {
            toast("请先压缩视频")
            return
        }
        mBinding.playLayout.isVisible = true
        mBinding.videoView.setVideoPath(compressFile!!.absolutePath)
        mBinding.videoView.start()
    }

    private fun selectFile() {
        val options = FilePickOptions().apply {
            mimeType = "video/*"
            isAllowMultiple = false
        }
        FilePicker.pick(this, options, object : FilePickCallback {
            override fun onResult(data: MutableList<Uri>) {
                parseVideoInfo(data[0])
            }

            override fun onError(p0: Int, p1: String) {

            }
        })
    }

    private fun parseVideoInfo(uri: Uri) {
        selectVideo = uri
        val loading = LoadingDialog(this)
        flow<Map<String, Any>> {
            val fields = UriUtils.getMetaInfo(this@MainActivity, uri)
            val type = fields[MediaColumns.MIME_TYPE] as String
            if (!type.startsWith("video/")) throw java.lang.IllegalArgumentException("文件格式错误：$type")

            val metadata = VideoUtils.getMetadata(this@MainActivity, uri)
            fields["metadata"] = metadata
            emit(fields)
        }.flowOn(Dispatchers.IO)
            .onStart { loading.show() }
            .onEach {
                mBinding.tvOrigin.text = parseInfo("原始视频", it)
            }
            .catch { toast("Error: $it") }
            .onCompletion { loading.dismiss() }
            .launchIn(lifecycleScope)
    }

    private fun compressVideo() {
        if (selectVideo == null) {
            toast("请选择视频文件")
            return
        }
        val loading = LoadingDialog(this)
        loading.show()
        val cache = if (externalCacheDir != null) externalCacheDir else cacheDir
        val dir = File(cache, "video")
        val fileName = "${System.currentTimeMillis()}.mp4"
        VideoCompressUtils.compress(this, selectVideo!!, dir, fileName, object : VideoCompressCallback {
            override fun onComplete(output: File) {
                parseCompressInfo(output, loading)
            }

            override fun onProgress(percent: Float) {
                mBinding.tvCompress.text = String.format("压缩进度：%.1f", percent)+"%"
            }

            override fun onError(code: Int, msg: String) {
                loading.dismiss()
                toast("error:$code $msg")
            }
        })
    }

    private fun parseCompressInfo(file: File, loading: LoadingDialog) {
        flow<Map<String, Any>> {
            val fields = HashMap<String, Any>()
            val metadata = VideoUtils.getMetadata(file)
            fields["metadata"] = metadata
            fields[MediaColumns.SIZE] = file.length()
            emit(fields)
        }.flowOn(Dispatchers.IO)
            .onStart { loading.show() }
            .onEach {
                compressFile = file
                mBinding.tvCompress.text = parseInfo("压缩视频", it)
            }
            .catch { toast("Error: $it") }
            .onCompletion { loading.dismiss() }
            .launchIn(lifecycleScope)
    }

    private fun parseInfo(title: String, fields: Map<String, Any>): String {
        val sb = StringBuffer(title).append("\n\n")
        val metadata = fields["metadata"] as VideoMetadata
        val size = fields[MediaColumns.SIZE].toString().toLong()
        sb.append("Size: ").append(String.format("%.1fmb", size / (1024 * 1024f))).append("\n\n")
        sb.append("MimeType: ").append(metadata.mimeType).append("\n\n")
        sb.append("Width: ").append(metadata.width).append("\n\n")
        sb.append("Height: ").append(metadata.height).append("\n\n")
        sb.append("Bitrate: ").append(metadata.bitrate).append("\n\n")
        return sb.toString()
    }

    override fun onBackPressed() {
        if (mBinding.playLayout.isVisible) {
            mBinding.playLayout.isVisible = false
        } else {
            super.onBackPressed()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}