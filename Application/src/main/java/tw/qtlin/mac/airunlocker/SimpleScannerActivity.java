package tw.qtlin.mac.airunlocker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.dm7.barcodescanner.zbar.BarcodeFormat;
import me.dm7.barcodescanner.zbar.Result;
import me.dm7.barcodescanner.zbar.ZBarScannerView;

/**
 * Created by QT on 2016/7/6.
 */
public class SimpleScannerActivity extends Activity implements ZBarScannerView.ResultHandler {
    private ZBarScannerView mScannerView;
    public static final String EXTRA_CONTENT_KEY = "content";
    private final int CAMERA_PERRMISSION_REQUEST = 99;

    private static final String TAG = SimpleScannerActivity.class.getSimpleName();
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        Log.i(TAG,"OnCreate");
        List<BarcodeFormat> mformats = new ArrayList<BarcodeFormat>();
        mScannerView = new ZBarScannerView(this);    // Programmatically initialize the scanner view
        mformats.add(BarcodeFormat.QRCODE);
        mScannerView.setFormats(mformats);
        setContentView(mScannerView);                // Set the scanner view as the content view



        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {


                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        CAMERA_PERRMISSION_REQUEST);


        }



    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAMERA_PERRMISSION_REQUEST:
                if (! (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    finish();
                }
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mScannerView.setResultHandler(this); // Register ourselves as a handler for scan results.
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.i(TAG,"OnResume");

        mScannerView.resumeCameraPreview(this);
        mScannerView.startCamera();          // Start camera on resume


    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG,"OnPause");
        mScannerView.stopCamera();           // Stop camera on pause

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    public void handleResult(Result rawResult) {
        // Do something with the result here
        //Log.v("BarcodeFormat-Content", rawResult.getContents()); // Prints scan results
        //Log.v("BarcodeFormat-Type", rawResult.getBarcodeFormat().getName()); // Prints the scan format (qrcode, pdf417 etc.)
        String dd[] =  rawResult.getContents().split(",");
        if(dd.length!=3){
            Toast.makeText(this, "QR code error.", Toast.LENGTH_SHORT).show();
            mScannerView.resumeCameraPreview(this);
            return;
        }

        SharedPreferences settings = getSharedPreferences("unlockInfo", 0);
        String address =dd[0];
        String key=dd[1];
        String lkey=dd[2];
        address = address.replaceAll("-",":");
        address = address.toUpperCase();
        Pattern macAddPattern = Pattern.compile("^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$");
        Matcher matcher = macAddPattern.matcher(address);
        if(!matcher.find()){
            Toast.makeText(this, "QR code error.", Toast.LENGTH_SHORT).show();
            mScannerView.resumeCameraPreview(this);
            return;
        }
        SharedPreferences.Editor ed = settings.edit();
        ed.putString(BLEService.PERFRENCE_ADDRESS, address);
        ed.putString(BLEService.PERFRENCE_KEY, key);
        ed.putString(BLEService.PERFRENCE_KEY_LOCK, lkey);
        ed.commit();
        Toast.makeText(getApplicationContext(), "Setting success.", Toast.LENGTH_SHORT).show();

        Intent updateService = new Intent()
                .setAction("android.intent.action.airunlockmac")
                .putExtra("REQUEST_CODE", BLEService.INTENT_UPDATE_UNLOCK_INFO);
        sendBroadcast(updateService);

//        Log.i(TAG, settings.getString(BLEService.PERFRENCE_KEY, "key fail"));
//        Log.i(TAG, settings.getString(BLEService.PERFRENCE_KEY_LOCK, "key_lock fail"));
//        Log.i(TAG, settings.getString(BLEService.PERFRENCE_ADDRESS, "address fail"));
        finish();
        // If you would like to resume scanning, call this method below:
        //mScannerView.resumeCameraPreview(this);
    }
}
