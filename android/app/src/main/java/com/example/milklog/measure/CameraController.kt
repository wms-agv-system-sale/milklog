package com.example.milklog.measure

import android.content.Context
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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

/** 相机采集：画面只在本机处理，不做任何上传。 */
class CameraController(private val context: Context) {

    /** 每帧回调，运行在后台线程；请在回调内同步完成分析。 */
    var onFrame: ((ImageProxy) -> Unit)? = null

    var permissionDenied by mutableStateOf(false)
    var torchAvailable by mutableStateOf(false)
    var isRunning by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analysis: ImageAnalysis? = null
    private var executor: ExecutorService? = null
    private var previewUseCase: Preview? = null

    /** 用来"断开"预览画面：告诉相机这一帧不提供画面，比传 null 更兼容 */
    private val detachedSurfaceProvider = Preview.SurfaceProvider { request ->
        request.willNotProvideSurface()
    }

    /** 当前预览画面接到了哪个控件上 */
    private var surfaceOwner: Any? = null
    private var binding = false

    /**
     * 把相机画面接到这个预览控件上。
     * 相机已经启动时只切换显示目标（例如从记录页切到标定页），不重新启动相机。
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

                val analysisExecutor =
                    executor ?: Executors.newSingleThreadExecutor().also { executor = it }

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                previewUseCase = preview
                surfaceOwner = previewView

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        onFrame?.invoke(proxy)
                    } catch (t: Throwable) {
                        // 单帧失败不影响后续帧
                    } finally {
                        proxy.close()
                    }
                }
                analysis = imageAnalysis

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    owner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
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
        analysis = null
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
}
