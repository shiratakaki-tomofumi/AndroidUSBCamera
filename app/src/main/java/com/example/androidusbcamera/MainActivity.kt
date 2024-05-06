package com.example.androidusbcamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.androidusbcamera.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.serenegiant.usb.common.AbstractUVCCameraHandler
import com.serenegiant.usb.encoder.RecordParams
import com.serenegiant.usb.widget.UVCCameraTextureView
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    // USB Camera
    private var usbCamera: USBCamera? = null

    // android camera
    private lateinit var viewBinding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var preview: Preview
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            try {
                usbCamera = USBCamera(this)
            } catch (e: java.lang.Exception) {
                Alert.alert(e.stackTraceToString(), this)
                return
            }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // -----------------------------------------------------------------------------------------
    // android camera
    // -----------------------------------------------------------------------------------------
    // start android camera
    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            imageCapture = ImageCapture.Builder().build()

//            val imageAnalyzer = ImageAnalysis.Builder().build()
//                .also {
//                    setAnalyzer(
//                        cameraExecutor,
//                        LuminosityAnalyzer { luma ->
//                            Log.d(TAG, "Average luminosity: $luma")
//                        }
//                    )
//                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // take photo by android camera
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return
        // Create time stamped name and MediaStore entry.
        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            getSaveFilePath(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    // save path for android camera
    private fun getSaveFilePath(): ImageCapture.OutputFileOptions {
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }
        // Create output options object which contains file + metadata
        return ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()
    }

    // take video by android camera
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return
        viewBinding.videoCaptureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.stop_capture)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding.videoCaptureButton.apply {
                            text = getString(R.string.start_capture)
                            isEnabled = true
                        }
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // -----------------------------------------------------------------------------------------
    // USB Camera
    // -----------------------------------------------------------------------------------------
    fun onClickStartUsbCameraBtn(view: View) {
        usbCamera?.mCameraHelper?.registerUSB()
        // 権限のメッセージでOK押下後、これを呼び出さないとカメラが起動しない
        // 一度権限のメッセージでOKしてUSBを抜かなければ、次回以降のアプリ起動時にカメラも起動する様になる
        // ★★★バグあり★★★
        // １、カメラ起動→いきなりUSB抜く アプリが落ちる
        // ２、カメラ起動→STOP押下→USB抜く→USB刺す→start押下　2回押下しないと起動しない
        usbCamera?.mCameraHelper?.requestPermission(0)
        this.findViewById<UVCCameraTextureView>(R.id.camera_view).visibility = View.VISIBLE
    }
    fun onClickEndUsbCameraBtn(view: View) {
        usbCamera?.mCameraHelper?.stopPreview()
        usbCamera?.mCameraHelper?.unregisterUSB()
        this.findViewById<UVCCameraTextureView>(R.id.camera_view).visibility = View.INVISIBLE
    }
    fun onClickCaptureUsbCameraBtn(view: View) {
        // File/内部ストレージ/Pictures
        val dir: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "USBCameraTest")
        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss")
        val path = (File(dir, "${df.format(LocalDateTime.now())}.jpg")).toString()

        usbCamera?.mCameraHelper?.capturePicture(path,
            AbstractUVCCameraHandler.OnCaptureListener { path -> Log.i(TAG, "save path：$path") })
    }
    fun onClickVideoStartUsbCameraBtn(view: View) {
        val dir: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "USBCameraTest")
        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss")
        val path = (File(dir, "${df.format(LocalDateTime.now())}.mp4")).toString()
        val params = RecordParams()
        params.recordPath = path
        params.recordDuration = 0 // 0,do not cut save
        params.isVoiceClose = true // is close voice
        params.isSupportOverlay = true // overlay only support armeabi-v7a & arm64-v8a
        usbCamera?.mCameraHelper?.startPusher(params, object : AbstractUVCCameraHandler.OnEncodeResultListener {
            override fun onEncodeResult(
                data: ByteArray,
                offset: Int,
                length: Int,
                timestamp: Long,
                type: Int
            ) {
                // type = 1,h264 video stream
                if (type == 1) {
//                                FileUtils.putFileStream(data, offset, length);
                }
                // type = 0,aac audio stream
                if (type == 0) { }
            }
            override fun onRecordResult(videoPath: String) {
                Log.i(TAG, "videoPath = $videoPath")
            }
        })
    }
    fun onClickVideoEndUsbCameraBtn(view: View) {
        usbCamera?.mCameraHelper?.stopPusher()
    }

    // -----------------------------------------------------------------------------------------
    // android camera
    // -----------------------------------------------------------------------------------------
    fun onClickStartCameraBtn(view: View) {
        startCamera()
    }
    fun onClickEndCameraBtn(view: View) {
        cameraProviderFuture.get().unbind(preview)
    }
}

typealias LumaListener = (luma: Double) -> Unit
private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        val luma = pixels.average()

        listener(luma)
        image.close()
    }
}