package com.example.fixcap

import android.graphics.SurfaceTexture
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import android.widget.SeekBar
import androidx.core.app.ActivityCompat
import com.example.fixcap.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.ExecutorService
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.util.Log
import android.view.Surface
import android.widget.Button
import androidx.camera.core.ImageCapture
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {


    private var imageCapture: ImageCapture?= null
    private lateinit var cameraMgr: CameraManager
    private lateinit var textureView1: TextureView
    private lateinit var textureView2: TextureView
    private lateinit var surfTexture1: SurfaceTexture
    private lateinit var surfTexture2: SurfaceTexture
    private lateinit var slider: SeekBar
    private var backgroundHandler1: Handler? = null
    private var backgroundHandler2: Handler? = null

    private var backgroundThread1: HandlerThread? = null
    private var backgroundThread2: HandlerThread? = null
    private var surface1Ready = false
    private var surface2Ready = false
    private var cameraDevice: CameraDevice? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor1: ExecutorService
    private lateinit var cameraExecutor2: ExecutorService

    val frontCAM  = 1
    val backCAM = 0

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //--------------------------------------------------//
        // Layout inflated, implement logic here

        if (allPermissionsGranted()){
            //startCamera()
            Log.d(TAG, "Permission status : Granted!")
        }else{
            Log.d(TAG, "Permission status : Not Granted! Asking for permissions.")
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraMgr = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        findViewById<Button>(R.id.camera_capture_button).setOnClickListener { doSomething() }

        outputDirectory = getOutputDir()
        cameraExecutor1 = Executors.newSingleThreadExecutor()
        cameraExecutor2 = Executors.newSingleThreadExecutor()

        textureView1 = findViewById(R.id.surface1)
        textureView2 = findViewById(R.id.surface2)
        slider = findViewById<SeekBar>(R.id.slider)

        slider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Log.d(TAG, "Value of seek $progress")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                //
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                //
            }

        })

        textureView1.surfaceTextureListener = object: TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                surfTexture1 = surface
                surface1Ready = true
                Log.d(TAG, "TextureView1 Width: $width, Height: $height")
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }
        }
        textureView2.surfaceTextureListener = object: TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                surfTexture2 = surface
                surface2Ready = true
                Log.d(TAG, "TextureView2 Width: $width, Height: $height")
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }
        }


        // At last setup the BG Thread handling
        backgroundThread1 = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler1 = backgroundThread1?.looper?.let { Handler(it) }

        backgroundThread2 = HandlerThread("CameraBG2").also { it.start() }
        backgroundHandler2 = backgroundHandler2?.looper?.let{ Handler(it)}

        // Example of a call to a native method
        binding.sampleText.text = stringFromJNI()
    }

    fun getOutputDir() : File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir
        else
            filesDir
    }
    private fun closeBackgroundThread() {
        if (backgroundHandler1 != null)
        {
            backgroundThread1?.quitSafely()
            backgroundThread1 = null
            backgroundHandler1 = null
        }
        if (backgroundHandler2 != null)
        {
            backgroundThread2?.quitSafely()
            backgroundThread2 = null
            backgroundHandler2 = null
        }
    }
   fun doSomething(){
        // if surfaces are ready, create and dispatch captureRequest
       if(surface1Ready and surface2Ready){
           val logical1 = "2"
           val logical2 = "3"
           submitSessionRequest(logical1, logical1,  surfTexture1, cameraExecutor1, backgroundHandler1)
           submitSessionRequest(logical2, logical2, surfTexture2, cameraExecutor2, backgroundHandler2)

       }
       Log.d(TAG, "fun doSomething: Poking Camera")
       for (cameraId in cameraMgr.cameraIdList) { //These are logical cameras
           val characteristics = cameraMgr.getCameraCharacteristics(cameraId)
           val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
           val fps_ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)

           val isManualFocus = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
           if(isManualFocus!!){
               Log.d(TAG, "fun doSomething: ManualFocus is supported!")
           }else{
               Log.d(TAG, "fun doSomething: ManualFocus is not supported!")
           }

           for(range in fps_ranges!!){
               Log.d(TAG, "fun doSomething:  FPS_range :: Upper range: ${range.upper}, Lower range: ${range.lower}")
           }

           val isLogicalCamera = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)

           val scenemode = characteristics.get( CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
           for (scene in scenemode!!){
               Log.d(TAG, "fun doSomething: SceneMode $scene supported by camera: $cameraId")
           }

           if(isLogicalCamera!!){
               Log.d(TAG, "fun doSomething:  $cameraId is a logical camera with following physical camera ids")
               for (camera in characteristics.physicalCameraIds){ //Here we get the physical cameraIds
                   Log.d(TAG, "fun doSomething: --->  $camera")
               }
           }
       }
   }

    private fun submitSessionRequest(cam: String, logical: String, st: SurfaceTexture, camExecutor: ExecutorService, bgHandler: Handler?){
        try{
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                cameraMgr.openCamera(cam, object : CameraDevice.StateCallback(){
                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.d(TAG, "Can't open camera!")
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.d(TAG, "Camera disconnected!")
                    }

                    override fun onOpened(camera: CameraDevice) {
                        Log.d(TAG, "Camera now ready for capture!")
                        val surface = Surface(st)
                        //val surface2 = Surface(surfTexture2)
                        val config1 = OutputConfiguration(surface)
                        //val config2 = OutputConfiguration(surface2)

                        //config1.setPhysicalCameraId(2.toString())
                        //config2.setPhysicalCameraId(3.toString())

                        //val outputConfigs: List<OutputConfiguration> = listOf(config1, config2)
                        val outputConfigs: List<OutputConfiguration> = listOf(config1)
                        val sessionConfiguration= SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigs, camExecutor, object: CameraCaptureSession.StateCallback() {
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.d(TAG, "Configuration Failed")
                            }

                            override fun onConfigured(session: CameraCaptureSession) {
                                Log.d(TAG, "Configuration Succeeded!")
                                val requestTemplate = CameraDevice.TEMPLATE_PREVIEW
                                val capReq = session.device.createCaptureRequest(requestTemplate)
                                capReq.addTarget(surface)
                                //capReq.addTarget(surface2)
                                //capReq.setPhysicalCameraKey(CaptureRequest.DISTORTION_CORRECTION_MODE, CaptureRequest.DISTORTION_CORRECTION_MODE_OFF, 1.toString())
                                //capReq.set(CaptureRequest.DISTORTION_CORRECTION_MODE, CaptureRequest.DISTORTION_CORRECTION_MODE_OFF)
                                //capReq.setPhysicalCameraKey(CaptureRequest.SCALER_CROP_REGION,  )
                                //capReq.setPhysicalCameraKey(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF, "2")
                                //capReq.setPhysicalCameraKey(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF, "2")
                                //capReq.setPhysicalCameraKey(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF, "2")
                                //capReq.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                                //capReq.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_NIGHT)

                                //capReq.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF)
                                //capReq.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)

                                //capReq.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

                                session.setRepeatingRequest(capReq.build(), null, bgHandler)

                            }
                        })
                        camera.createCaptureSession(sessionConfiguration)
                        Log.d(TAG, "Created capture session for $cam with logical stream $logical")
                    }

                                                                               }, bgHandler)
            }else{
                Log.d(TAG, "Permission missing ??")
            }
        }catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun allPermissionsGranted()= REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }

        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}