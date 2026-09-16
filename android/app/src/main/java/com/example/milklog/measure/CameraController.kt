package com.example.milklog.measure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 相机控制：平时只显示取景画面，用户按下快门时拍一张完整分辨率的照片。
 * 画面只在本机处理，不做任何上传。
 */
class CameraController(private val context: Context) {

    var permissionDenied by mutableStateOf(false)
    var torchAvailable by mutableStateOf(false)
    var isRunning by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var captureExecutor: ExecutorService? = null

    private var previewUseCase: Preview? = null
    private var surfaceOwner: Any? = null
    private var binding = false

    /** 用来"断开"预览画面：告诉相机这一帧不提供画面，比传 null 更兼容 */
    private val detachedSurfaceProvider = Preview.SurfaceProvider { request ->
        request.willNotProvideSurface()
    }

    /**
     * 把相机画面接到这个预览控件上。
     * 相机已经启动时只切换显示目标，不重新启动相机。
     */
    fun bindPreview(previewView: PreviewView, owner: LifecycleOwner) {
        val existing = previewUseCase
        if (isRunning && existing != null) {
            try {
                existing.setSurfaceProvider(previewView.surfaceProvider)
                surfaceOwner = previewView
            } catch (t: Throwable) {
                // 忽略
            }
            return
        }
        if (binding) return
        binding = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                binding = false

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                previewUseCase = preview
                surfaceOwner = previewView

                val rotation = try {
                    previewView.display?.rotation ?: Surface.ROTATION_0
                } catch (t: Throwable) {
                    Surface.ROTATION_0
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(rotation)
                    .build()
                imageCapture = capture

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    owner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                torchAvailable = camera?.cameraInfo?.hasFlashUnit() == true
                isRunning = true
                errorMessage = null
            } catch (t: Throwable) {
                binding = false
                errorMessage = "相机启动失败：" + (t.message ?: t.javaClass.simpleName)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** 预览控件离开界面时调用：只有画面当前接在它上面时才会断开 */
    fun releaseSurface(token: Any) {
        if (surfaceOwner !== token) return
        surfaceOwner = null
        try {
            previewUseCase?.setSurfaceProvider(detachedSurfaceProvider)
        } catch (t: Throwable) {
            // 忽略
        }
    }

    fun unbind() {
        try {
            previewUseCase?.setSurfaceProvider(detachedSurfaceProvider)
        } catch (t: Throwable) {
            // 忽略
        }
        try {
            provider?.unbindAll()
        } catch (t: Throwable) {
            // 忽略
        }
        previewUseCase = null
        surfaceOwner = null
        camera = null
        imageCapture = null
        isRunning = false
    }

    fun setTorch(on: Boolean) {
        val cam = camera ?: return
        try {
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(on)
            }
        } catch (t: Throwable) {
            // 忽略
        }
    }

    /**
     * 拍一张照片，转成 Bitmap 后回调（回调在主线程）。
     * maxDimension 控制最长边的像素数，太大的话识别反而慢。
     */
    fun capture(
        maxDimension: Int = 1800,
        onResult: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("相机还没准备好")
            return
        }
        val executor = captureExecutor
            ?: Executors.newSingleThreadExecutor().also { captureExecutor = it }
        try {
            capture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        var bitmap: Bitmap? = null
                        try {
                            bitmap = decode(image, maxDimension)
                        } catch (t: Throwable) {
                            bitmap = null
                        }
                        try {
                            image.close()
                        } catch (t: Throwable) {
                            // 忽略
                        }
                        val result = bitmap
                        ContextCompat.getMainExecutor(context).execute {
                            if (result != null) {
                                onResult(result)
                            } else {
                                onError("照片读取失败")
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        val text = exception.message ?: "拍照失败"
                        ContextCompat.getMainExecutor(context).execute { onError(text) }
                    }
                }
            )
        } catch (t: Throwable) {
            onError(t.message ?: t.javaClass.simpleName)
        }
    }

    /** 把拍到的 JPEG 解码成 Bitmap，并按相机的方向摆正 */
    private fun decode(image: ImageProxy, maxDimension: Int): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension ||
            bounds.outHeight / (sample * 2) >= maxDimension
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            try {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            } catch (t: Throwable) {
                // 旋转失败就用原图
            }
        }
        return bitmap
    }
}
