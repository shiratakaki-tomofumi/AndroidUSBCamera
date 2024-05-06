package com.example.androidusbcamera;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.view.Surface;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.serenegiant.usb.widget.UVCCameraTextureView;

// USB Camera の処理についてまとめたクラス
public class USBCamera {
    public UVCCameraTextureView mTextureView;
    public UVCCameraHelper mCameraHelper;
    public CameraViewInterface mUVCCameraView;
    private boolean isPreview;
    private boolean isRequest;

    public USBCamera(Activity activity) {
        // 表示領域
        mTextureView = activity.findViewById(R.id.camera_view);
        mUVCCameraView = (CameraViewInterface) mTextureView;
        mUVCCameraView.setCallback(mCallback);

        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultPreviewSize(1280,720);
        mCameraHelper.initUSBMonitor(activity, mUVCCameraView, listener);
        mCameraHelper.createUVCCamera();
        mCameraHelper.requestPermission(0); // 多分大体0番目
        // USB監視
        mCameraHelper.registerUSB();
    }

    public CameraViewInterface.Callback mCallback = new CameraViewInterface.Callback() {
        @Override
        public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
            // must have
            if (!isPreview && mCameraHelper.isCameraOpened()) {
                mCameraHelper.startPreview(mUVCCameraView);
                isPreview = true;
            }
        }

        @Override
        public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {
        }

        @Override
        public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
            // must have
            if (isPreview && mCameraHelper.isCameraOpened()) {
                mCameraHelper.stopPreview();
                isPreview = false;
            }
        }
    };
    // USBの抜き差しをこの辺のハンドラで取れそうだけど、イマイチ想定の動きをしてなかった
    public UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {
        @Override
        public void onAttachDev(UsbDevice device) {
            // request open permission(must have)
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera(must have)
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {

        }
        @Override
        public void onDisConnectDev(UsbDevice device) {

        }
    };
}
