package com.kumarsunil.flutter_face_filters

import ai.deepar.ar.ARErrorType
import ai.deepar.ar.AREventListener
import ai.deepar.ar.DeepAR
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

//class MainActivity: FlutterFragmentActivity() {
//    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
//        GeneratedPluginRegistrant.registerWith(flutterEngine)
//        flutterEngine.plugins.add(CameraPlugin())
//    }
//}
class MainActivity : AppCompatActivity(), AREventListener {
    private var isFrontFacing: Boolean=true
    private var sensorOrientation: Int?=0
    private var textureView: TextureView? = null

    companion object {
        private const val TAG = "AndroidCameraApi"
        private val ORIENTATIONS: SparseIntArray = SparseIntArray()
        private const val REQUEST_CAMERA_PERMISSION = 200

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    protected var cameraDevice: CameraDevice? = null
    protected var cameraCaptureSessions: CameraCaptureSession? = null
    protected var captureRequest: CaptureRequest? = null
    protected var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageDimension: Size? = null
    private var imageReader: ImageReader? = null
    private val file: File? = null
    private val mFlashSupported = false
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    private lateinit var deepAR: DeepAR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_AppCompat)
        setContentView(R.layout.camera_view)
        textureView = findViewById(R.id.texture)
        textureView!!.surfaceTextureListener = textureListener
        initializeDeepAR()
    }
    private fun initializeDeepAR() {
        deepAR = DeepAR(applicationContext)
        deepAR.setLicenseKey("c0b1b83e3993d279efcc709841052431abe6fd2ed9245984bd0494279bf29ddc9b57f61f79fd8506")
        deepAR.initialize(applicationContext, this)
    }

    private var textureListener: TextureView.SurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
            // open your camera here
            val surface = Surface(surfaceTexture)
            Log.e("height_width", "$height $width .....")
            deepAR.setRenderSurface(surface, width, height)
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
            // Transform you image captured size according to the surface width and height
            val surface = Surface(surfaceTexture)
            deepAR.setRenderSurface(surface, width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            Log.e(TAG, "onSurfaceTextureDestroyed")
            deepAR.setRenderSurface(null, 0, 0)
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}
    }
    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
             override fun onOpened(camera: CameraDevice) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened")
            cameraDevice = camera

            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice!!.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice!!.close()
            cameraDevice = null
        }

    }
    val captureCallbackListener: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
//        fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult?) {
//            super.onCaptureCompleted(session, request, result)
//            Toast.makeText(this@AndroidCameraApi, "Saved:$file", Toast.LENGTH_SHORT).show()
//            createCameraPreview()
//        }

    }

    protected fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    protected fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
    private var currentBuffer = 0

    private fun createCameraPreview() {
        try {

            val texture: SurfaceTexture = textureView!!.surfaceTexture!!

            val width = imageDimension!!.width
            val height = imageDimension!!.height
            texture.setDefaultBufferSize(width, height)
            val surface = Surface(texture)

            val imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
            imageReader.setOnImageAvailableListener({
                val img: Image = it.acquireLatestImage()

                val bytes: ByteArray
                val yBuffer: ByteBuffer = img.planes[0].buffer
                val uBuffer: ByteBuffer = img.planes[1].buffer
                val vBuffer: ByteBuffer = img.planes[2].buffer

                val ySize: Int = yBuffer.remaining()
                val uSize: Int = uBuffer.remaining()
                val vSize: Int = vBuffer.remaining()

                bytes = ByteArray(ySize + uSize + vSize)

                yBuffer.get(bytes, 0, ySize)
                vBuffer.get(bytes, ySize, vSize)
                uBuffer.get(bytes, ySize + vSize, uSize)

                val byteBuffer:ByteBuffer = ByteBuffer.allocateDirect(width * height * 3 / 2)
                byteBuffer.order(ByteOrder.nativeOrder())
                byteBuffer.position(0)

                // underflow
//                byteBuffer.put(yuvImageToByteArray(image = img))

                // green screen
//                byteBuffer.put(ByteArray(width * height * 3 / 2))

                // overflow
//                byteBuffer.put(bytes)

//                byteBuffer.position(0)

                val buffers: Array<ByteBuffer?> = arrayOfNulls(2)
                for (i in 0 until 2) {
                    Log.e("i_value",i.toString())
                    buffers[i] = ByteBuffer.allocateDirect(width * height * 3 / 2)
                    buffers[i]?.order(ByteOrder.nativeOrder())
                    buffers[i]?.position(0)
//                    val buffer = ByteArray(width * height * 3 / 2)
                    val buffer = yuvImageToByteArray(img)
//                    Log.e("byte_data", buffer.contentToString());
                    buffers[currentBuffer]!!.put(buffer)
                    buffers[currentBuffer]!!.position(0)

                    deepAR.receiveFrame(buffers[currentBuffer], img.width, img.height, sensorOrientation!!, isFrontFacing)

                    currentBuffer = (currentBuffer + 1) % 2
                }
//                deepAR.receiveFrame(byteBuffer, width, height, sensorOrientation!!, isFrontFacing)
                img.close()
                Log.e("i_value","image closed")
            }, null)
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
//            captureRequestBuilder!!.addTarget(surface)
            captureRequestBuilder!!.addTarget(imageReader.surface)

            val surfaceList: MutableList<Surface> = ArrayList()
//            surfaceList.add(surface)
            surfaceList.add(imageReader.surface)
            cameraDevice!!.createCaptureSession(surfaceList, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    //The camera is already closed
                    Log.e(TAG, "configured")
                    if (null == cameraDevice) {
                        return
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession
                    updatePreview()
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    Toast.makeText(this@MainActivity, "Configuration fail", Toast.LENGTH_SHORT).show()
                }
            }, null)


        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun yuvImageToByteArray(image: Image): ByteArray {
//        Log.e("image_type",image.format.toString());
        if (BuildConfig.DEBUG && image.format != ImageFormat.YUV_420_888) {
            error("Assertion failed")
        }
        val width = image.width
        val height = image.height
        val planes = image.planes
        val result = ByteArray(width * height * 3 / 2)
        var stride = planes[0].rowStride
        if (BuildConfig.DEBUG && 1 != planes[0].pixelStride) {
            error("Assertion failed")
        }
        if (stride == width) {
            planes[0].buffer[result, 0, width * height]
        } else {
            for (row in 0 until height) {
                planes[0].buffer.position(row * stride)
                planes[0].buffer[result, row * width, width]
            }
        }
        stride = planes[1].rowStride
        if (BuildConfig.DEBUG && stride != planes[2].rowStride) {
            error("Assertion failed")
        }
        val pixelStride = planes[1].pixelStride
        if (BuildConfig.DEBUG && pixelStride != planes[2].pixelStride) {
            error("Assertion failed")
        }
        val rowBytesCb = ByteArray(stride)
        val rowBytesCr = ByteArray(stride)
        for (row in 0 until height / 2) {
            val rowOffset = width * height + width / 2 * row
            planes[1].buffer.position(row * stride)
//            planes[1].buffer[rowBytesCb]
            planes[2].buffer.position(row * stride)
//            planes[2].buffer[rowBytesCr]
            for (col in 0 until width / 2) {
                result[rowOffset + col * 2] = rowBytesCr[col * pixelStride]
                result[rowOffset + col * 2 + 1] = rowBytesCb[col * pixelStride]
            }
        }
        Log.e("result",result.contentToString())
        return result
    }

    private fun openCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[1]
            val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            isFrontFacing = (characteristics.get(CameraCharacteristics.LENS_FACING)
                    == CameraMetadata.LENS_FACING_FRONT)
            val map: StreamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !== PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CAMERA_PERMISSION)
                return
            }

            cameraManager.openCamera(cameraId!!, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        Log.e(TAG, "openCamera X")
    }

    protected fun updatePreview() {
        Log.e(TAG, "update preview X")
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return")
        }
        captureRequestBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSessions!!.setRepeatingRequest(captureRequestBuilder!!.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        if (null != cameraDevice) {
            cameraDevice!!.close()
            cameraDevice = null
        }
        if (null != imageReader) {
            imageReader!!.close()
            imageReader = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "onResume")
        startBackgroundThread()
        if (textureView!!.isAvailable) {
            openCamera()
        } else {
            textureView!!.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        Log.e(TAG, "onPause")
        closeCamera();
        stopBackgroundThread()
        super.onPause()
    }

    override fun screenshotTaken(p0: Bitmap?) {
        
    }

    override fun videoRecordingStarted() {
        
    }

    override fun videoRecordingFinished() {
    }

    override fun videoRecordingFailed() {
        
    }

    override fun videoRecordingPrepared() {
        
    }

    override fun shutdownFinished() {

    }

    override fun initialized() {

    }

    override fun faceVisibilityChanged(p0: Boolean) {

    }

    override fun imageVisibilityChanged(p0: String?, p1: Boolean) {
    }

    override fun frameAvailable(p0: Image?) {

    }

    override fun error(p0: ARErrorType?, p1: String?) {
    }

    override fun effectSwitched(p0: String?) {
    }
}