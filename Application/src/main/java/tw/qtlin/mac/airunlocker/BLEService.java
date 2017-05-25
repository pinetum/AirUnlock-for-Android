package tw.qtlin.mac.airunlocker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.media.RingtoneManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by QT on 2016/7/7.
 */
public class BLEService extends Service
{
    private static final String TAG = BLEService.class.getSimpleName();


    private static final int NOTIFICATION_BG_ID = 99;

    private String KEY_WORD_LOCK = "lock";
    private String KEY_WORD_UNLOCK = "asdfghj";
    private String GATT_UUID_SERVICE = "A5B288C3-FC55-491F-AF38-27D2F7D7BF25";
    private String GATT_UUID_CHARACTERS_UUID = "A6282AC7-7FCA-4852-A2E6-1D69121FD44A";
    private String GATT_ADDRESS = "60:F8:1D:B9:FA:BF";

    private List<ScanFilter> mLeFilter;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothGatt mBlutoothGatt = null;

    private BluetoothDevice mLeDevice = null;
    private BluetoothGattCharacteristic mBluetoothLeCharacteristic = null;
    private BluetoothLeScanner mBluetoothLeScanner = null;


    public static final int INTENT_REQ_EXIT_SERVICE = 88;
    public static final int INTENT_REQ_UNLOCK = 89;
    public static final int INTENT_REQ_LOCK = 90;
    public static final int INTENT_UPDATE_UNLOCK_INFO = 99;

    public static final String PERFRENCE_KEY = "KEY";
    public static final String PERFRENCE_KEY_LOCK = "KEYL";
    public static final String PERFRENCE_ADDRESS = "ADDRESS";

    private int lastIntentReq = -1;
    private boolean bForeground = false;
    private boolean bUsingFingerprint = false;
    private Boolean m_BshowExitBtn = true;
    private Boolean m_BshowLockBtn = true;
    private Boolean m_BshowUnlockBtn = true;
    private STATE state;

    private SharedPreferences mSharedPreferences;
    private RemoteViews ctlViews;
    private NotificationCompat.Builder mNotifyBuilder;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            lastIntentReq = intent.getIntExtra("REQUEST_CODE",-1);
            bUsingFingerprint = mSharedPreferences
                    .getBoolean(getString(R.string.use_fingerprint_to_authenticate_key), false)
                    && BLEService.checkFingerPrintAvailable(getApplicationContext())
                    && BLEService.checkFingerPrintSensor(getApplicationContext());
            switch (lastIntentReq){
                case INTENT_REQ_LOCK:
                    if(!bUsingFingerprint)
                        doLockScreen();
                    else
                        showOverlayActivity(context, INTENT_REQ_LOCK);
                    break;
                case INTENT_REQ_UNLOCK:
                    if(!bUsingFingerprint)
                        doUnLockScreen();
                    else
                        showOverlayActivity(context, INTENT_REQ_UNLOCK);
                    break;
                case INTENT_REQ_EXIT_SERVICE:
                    stopSelf();
                    break;
                case INTENT_UPDATE_UNLOCK_INFO:
                    mLeDevice = null;
                    mBlutoothGatt = null;
                    checkBTAvailable();
                    break;
            }

        }
    };
    private int getNotificationIcon() {
        boolean useWhiteIcon = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP);
        return useWhiteIcon ? R.drawable.ic_w_unlock : R.drawable.ic_s_unlock;
    }
    private void createRemoteView(){

        ctlViews = new RemoteViews(getPackageName(), R.layout.bgnotification);
        if(!m_BshowExitBtn)
            ctlViews.setViewVisibility(R.id.notify_btn_exit, View.GONE);
        if(!m_BshowLockBtn)
            ctlViews.setViewVisibility(R.id.notify_btn_lock, View.GONE);
        if(!m_BshowUnlockBtn)
            ctlViews.setViewVisibility(R.id.notify_btn_unlock, View.GONE);


        ctlViews.setOnClickPendingIntent(R.id.notify_btn_exit,
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        INTENT_REQ_EXIT_SERVICE,
                        new Intent()
                                .setAction("android.intent.action.airunlockmac")
                                .putExtra("REQUEST_CODE", INTENT_REQ_EXIT_SERVICE),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        ctlViews.setOnClickPendingIntent(R.id.notify_btn_lock,
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        INTENT_REQ_LOCK,
                        new Intent()
                                .setAction("android.intent.action.airunlockmac")
                                .putExtra("REQUEST_CODE", INTENT_REQ_LOCK),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        ctlViews.setOnClickPendingIntent(R.id.notify_btn_unlock,
                PendingIntent.getBroadcast(
                        getApplicationContext(),
                        INTENT_REQ_UNLOCK,
                        new Intent()
                                .setAction("android.intent.action.airunlockmac")
                                .putExtra("REQUEST_CODE", INTENT_REQ_UNLOCK),
                        PendingIntent.FLAG_UPDATE_CURRENT));
    }
    private void updateState(STATE s){
        Log.i(TAG, "UPDATE STATE:"+ s.toString());
        state = s;
        String infoText = "";
        boolean uiEnable = false;
        switch (s){
            case BT_ON:
                // testing
                if(mLeDevice == null)
                    checkBTAvailable();
                else if(mBlutoothGatt !=null)
                    connect2GattService();

                //running
                checkBTAvailable();
                break;
            case BT_OFF:
                //mBlutoothGatt = null;
                mLeDevice = null;
                infoText = getString(R.string.bluetooth_off);
                uiEnable = false;
                break;
            case SCAN_FAILED:
                infoText = getString(R.string.bluetooth_scan_failed);
                uiEnable = false;
                //checkBTAvailable();
                break;
            case SCANNING:
                infoText = getString(R.string.bluetooth_scanning);
                uiEnable = false;
                break;
            case DEVICE_CONNECTED:
                infoText = getString(R.string.bluetooth_device_connected);
                uiEnable = false;
                break;
            case SERVICE_CONNECTED:
                infoText = getString(R.string.bluetooth_service_connected);
                uiEnable = true;
                break;
            case SERVICE_NOT_FOUND:
                infoText = getString(R.string.bluetooth_scan_not_found);
                uiEnable = false;
                break;
            case DEVICE_DISCONNECTED:
                infoText = getString(R.string.bluetooth_disconnected);
                uiEnable = false;
                checkBTAvailable();
                break;
            case UNLOCK_INFO_NOT_SETED:
                infoText = getString(R.string.unlock_info_not_set);
                uiEnable = false;
                break;
        }
        createRemoteView();
        if(uiEnable){
            if(m_BshowLockBtn)
                ctlViews.setViewVisibility(R.id.notify_btn_lock, View.VISIBLE);
            else
                ctlViews.setViewVisibility(R.id.notify_btn_lock, View.GONE);
            if(m_BshowUnlockBtn)
                ctlViews.setViewVisibility(R.id.notify_btn_unlock, View.VISIBLE);
            else
                ctlViews.setViewVisibility(R.id.notify_btn_unlock, View.GONE);
        } else {
            ctlViews.setViewVisibility(R.id.notify_btn_lock, View.GONE);
            ctlViews.setViewVisibility(R.id.notify_btn_unlock, View.GONE);
        }
        if(s == STATE.SERVICE_CONNECTED || s == STATE.UNLOCK_INFO_NOT_SETED)
        {
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            boolean useSoundPerference = mSharedPreferences
                    .getBoolean(getString(R.string.notification_sound_key),
                            true);
            boolean useViberatePerference = mSharedPreferences
                    .getBoolean(getString(R.string.notification_vibrate_key),
                            true);
            if(useSoundPerference)
                mNotifyBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            else
                mNotifyBuilder.setSound(null);
            if(useViberatePerference)
                mNotifyBuilder.setVibrate(new long[] {500, 500});
            else
                mNotifyBuilder.setVibrate(new long[] {0});
            mNotifyBuilder.setPriority(Notification.PRIORITY_HIGH);


        }
        else{
            mNotifyBuilder.setPriority(Notification.PRIORITY_DEFAULT);
            mNotifyBuilder.setSound(null);
        }
        ctlViews.setTextViewText(R.id.notify_info, infoText);
        mNotifyBuilder.setContent(ctlViews);
        mNotifyBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_BG_ID,mNotifyBuilder.build());
    }

    private void checkBTAvailable(){

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        SharedPreferences settings = getSharedPreferences("unlockInfo", 0);
        GATT_ADDRESS = settings.getString(PERFRENCE_ADDRESS, "");
        KEY_WORD_UNLOCK = settings.getString(PERFRENCE_KEY, "");
        KEY_WORD_LOCK = settings.getString(PERFRENCE_KEY_LOCK, "");
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        m_BshowExitBtn = mSharedPreferences.getBoolean(getString(R.string.notification_show_exit_btn_key), true);
        m_BshowLockBtn = mSharedPreferences.getBoolean(getString(R.string.notification_show_lock_btn_key), true);
        m_BshowUnlockBtn = mSharedPreferences.getBoolean(getString(R.string.notification_show_unlock_btn_key), true);

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            updateState(STATE.BT_OFF);
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(enableBtIntent);
            return;
        }
        else if(GATT_ADDRESS.isEmpty()
                || KEY_WORD_UNLOCK.isEmpty()
                || KEY_WORD_LOCK.isEmpty()
                || KEY_WORD_LOCK.isEmpty()){
            updateState(STATE.UNLOCK_INFO_NOT_SETED);
        }
        else{

            startscanDevice();
        }
    }
    private void showOverlayActivity(Context context, int reqCode) {
        Intent intent = new Intent(context, OverlayActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("REQUEST_CODE", reqCode);
        context.startActivity(intent);
    }


    public void doLockScreen(){

        mBluetoothLeCharacteristic.setValue(KEY_WORD_LOCK);
        mBlutoothGatt.writeCharacteristic(mBluetoothLeCharacteristic);
        mBlutoothGatt.executeReliableWrite();
    }
    public void doUnLockScreen(){
        mBluetoothLeCharacteristic.setValue(KEY_WORD_UNLOCK);
        mBlutoothGatt.writeCharacteristic(mBluetoothLeCharacteristic);
        mBlutoothGatt.executeReliableWrite();
    }
    private void startscanDevice(){
        if(mLeDevice != null){
            return;
        }
        Log.i(TAG, GATT_ADDRESS);
        updateState(STATE.SCANNING);
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mLeFilter.clear();
        mLeFilter.add(new ScanFilter.Builder().
                setDeviceAddress(GATT_ADDRESS)
                .build());
        new Thread(){
            @Override
            public void run() {
                Log.i(TAG,"BluetoothLeScanner Start Scan");


                mBluetoothLeScanner.startScan(mLeFilter,
                        new ScanSettings.Builder().
                                setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).
                                setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH).
                                build(),
                        mDeviceScanCallback);
            }
        }.run();

    }
    private void connect2GattService(){
        updateState(STATE.DEVICE_CONNECTED);
        mBluetoothLeScanner.stopScan(mDeviceScanCallback);
        mLeDevice.connectGatt(getApplicationContext(), true, mDeviceGattCallBack, BluetoothDevice.TRANSPORT_LE);

    }
    @Override
    public void onCreate() {
        super.onCreate();

        mLeFilter = new ArrayList<ScanFilter>();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);

        registerReceiver(mBTAReceiver, filter);
        registerReceiver(mReceiver, new IntentFilter("android.intent.action.airunlockmac"));
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    private final IMyAidlInterface.Stub mBinder = new IMyAidlInterface.Stub(){
        @Override
        public void dolockAction() throws RemoteException {
            switch(lastIntentReq){
                case INTENT_REQ_LOCK:
                    doLockScreen();
                case INTENT_REQ_UNLOCK:
                    doUnLockScreen();
            }
        }
    };


    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        unregisterReceiver(mBTAReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //return super.onStartCommand(intent, flags, startId);
        createRemoteView();
        ctlViews.setTextViewText(R.id.notify_info, getText(R.string.service_starting));

        if(!bForeground) {
            bForeground = true;
            Intent targetIntent = new Intent(this, MainActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            mNotifyBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(getNotificationIcon())
                    .setContent(ctlViews)
                    .setContentIntent(contentIntent)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);



            startForeground( NOTIFICATION_BG_ID, mNotifyBuilder.build() );
        }
        checkBTAvailable();
        return START_STICKY;
    }
    private BluetoothGattCallback mDeviceGattCallBack = new BluetoothGattCallback() {
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            mBluetoothLeCharacteristic = mBlutoothGatt.getService(UUID.fromString(GATT_UUID_SERVICE))
                    .getCharacteristic(UUID.fromString(GATT_UUID_CHARACTERS_UUID));
            if(mBluetoothLeCharacteristic==null){
                Log.i(TAG,"not found target service");
                updateState(STATE.SERVICE_NOT_FOUND);
                mBlutoothGatt.disconnect();
            }
            else{
                Log.i(TAG,"found target service");
                updateState(STATE.SERVICE_CONNECTED);
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            Log.i(TAG,"onConnectionStateChange");

            if(newState ==  BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected to GATT Server");
                mBlutoothGatt = null;
                mLeDevice = null;
                mBluetoothLeCharacteristic = null;

                Log.e(TAG,"disconnected to device");
                updateState(STATE.DEVICE_DISCONNECTED);

            }
            if(newState == BluetoothGatt.STATE_CONNECTED){
                Log.i(TAG, "Connected to GATT Server");
                mBlutoothGatt = gatt;
                updateState(STATE.DEVICE_CONNECTED);
                mBlutoothGatt.discoverServices();


            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.i(TAG, "Write to GATT Server complete!");
        }
    };

    private ScanCallback mDeviceScanCallback = new ScanCallback() {
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);

            Log.e(TAG,"onScanFailed");
            updateState(STATE.SCAN_FAILED);
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.i(TAG,"onScanResult");

            mLeDevice = result.getDevice();
            Log.i(TAG,mLeDevice.getAddress());
            connect2GattService();

        }
        @Override
        public void onBatchScanResults(List< android.bluetooth.le.ScanResult > results) {
            super.onBatchScanResults(results);
            Log.i(TAG,"onBatchScanResults");
        }
    };
    private final BroadcastReceiver mBTAReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
                final int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (btState){
                    case BluetoothAdapter.STATE_ON:
                        updateState(STATE.BT_ON);
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        updateState(STATE.BT_OFF);
                        break;
                }
            }
        }
    };
    public enum STATE{
        SCANNING,
        DEVICE_NOT_FOUND,
        SERVICE_NOT_FOUND,
        DEVICE_CONNECTED,
        DEVICE_DISCONNECTED,
        SERVICE_CONNECTED,
        SCAN_FAILED,
        BT_OFF,
        BT_ON,
        UNLOCK_INFO_NOT_SETED;

        @Override
        public String toString() {
            return super.toString();




        }
    }
    public static boolean checkFingerPrintSensor(Context context){
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);

    }
    public static boolean checkFingerPrintAvailable(Context context){
        FingerprintManager fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        return fingerprintManager.hasEnrolledFingerprints();
    }
}
