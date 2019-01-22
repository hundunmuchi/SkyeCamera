package cn.skyebefreeman.skyecamera.activity;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.skyebefreeman.skyecamera.R;
import cn.skyebefreeman.skyecamera.utils.ImgUtils;

public class MainActivity extends AppCompatActivity {

    private CameraManager mCameraManager;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceViewHolder;
    private Handler mHandler;
    private String mCameraId;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mSession;
    private ImageButton take_picture_bt;
    private Handler mainHandler;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        take_picture_bt = findViewById(R.id.take_photo);
        mSurfaceView = findViewById(R.id.surfaceView);

        // 初始化SurfaceView
        initSurfaceView();

        // 添加照相按钮监听
        take_picture_bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });

    }

    /**
     * 初始化SurfaceView
     */
    public void initSurfaceView() {
        mSurfaceViewHolder = mSurfaceView.getHolder();// 通过SurfaceViewHolder可以对SurfaceView进行管理
        mSurfaceViewHolder.addCallback(new SurfaceHolder.Callback() {

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                initCameraAndPreview();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                //释放camera
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }
        });
    }

    /**
     * 初始化摄像头和预览界面
     */
    @TargetApi(19)
    public void initCameraAndPreview() {
        HandlerThread handlerThread = new HandlerThread("CAMERA2 of HUAWEI nova 3i");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());// 用来处理ui线程的handler，即ui线程
        try {
            mCameraId = "" + CameraCharacteristics.LENS_FACING_FRONT;

            mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // 获取摄像头支持的最大分辨率尺寸
            Size largest = map.getOutputSizes(ImageFormat.JPEG)[0];
            Log.i("LargestSize", largest.getWidth() + " " + largest.getHeight());

            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG,/*maxImages*/7);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mainHandler);//这里必须传入mainHandler，因为涉及到了Ui操作
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mCameraManager.openCamera(mCameraId, deviceStateCallback, mHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "摄像头获取失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 照片回调
     */
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            // 进行相片存储
            Image image = reader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);//将image对象转化为byte，再转化为bitmap
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            ImgUtils.saveImageToGallery(getApplicationContext(), bitmap);
        }
    };

    /**
     * 摄像头监听回调
     */
    private CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            try {
                takePreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Toast.makeText(MainActivity.this, "摄像头打开失败", Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * 获取预览
     * @throws CameraAccessException
     */
    public void takePreview() throws CameraAccessException {
        mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        mPreviewBuilder.addTarget(mSurfaceViewHolder.getSurface());
        mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceViewHolder.getSurface(), mImageReader.getSurface()), mSessionPreviewStateCallback, mHandler);
    }

    private CameraCaptureSession.StateCallback mSessionPreviewStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mSession = session;
            // 配置预览参数
            try {
                // 自动对焦
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 打开闪光灯
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                // 无限次的重复获取图像
                mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Toast.makeText(MainActivity.this, "摄像头配置失败", Toast.LENGTH_SHORT).show();
        }
    };

    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            mSession = session;
            unlockFocus();
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            mSession = session;
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };

    /**
     * 拍摄照片
     */
    public void takePicture() {
        try {
            CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);//用来设置拍照请求的request
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation(cameraCharacteristics, rotation));//使图片做顺时针旋转
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mSession.capture(mCaptureRequest, mSessionCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取图片应该旋转的角度，使图片竖直
      */
    private int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN)
            return 0;
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // LENS_FACING相对于设备屏幕的方向,LENS_FACING_FRONT相机设备面向与设备屏幕相同的方向
        boolean facingFront = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calculate desired JPEG orientation relative to camera orientation to make the image upright relative to the device orientation
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mSession.capture(mPreviewBuilder.build(), mSessionCaptureCallback,
                    mHandler);
            // After this, the camera will go back to the normal state of preview.
            mSession.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback,
                    mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
