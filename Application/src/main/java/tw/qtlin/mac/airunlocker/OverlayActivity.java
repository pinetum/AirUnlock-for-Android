package tw.qtlin.mac.airunlocker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import javax.inject.Inject;

public class OverlayActivity extends Activity {

    private static final String TAG = OverlayActivity.class.getSimpleName();
    private static final String DIALOG_FRAGMENT_TAG = "FingerFragment";
    private IMyAidlInterface mBleInterface = null;
    private boolean mBound = false;
    @Inject
    FingerprintAuthenticationDialogFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "[onCreate]");
        super.onCreate(savedInstanceState);
        ((InjectedApplication) getApplication()).inject(this);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (getIntent().getIntExtra("REQUEST_CODE", -1) == -1)
            finish();
        mFragment.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        // fragment表示
//        Fragment fragment = getFragmentManager().findFragmentByTag(FragmentType.OVERLAY.getTag());
//        if (fragment == null) {
//            fragment = OverlayFragment.newInstance();
//        }
//        FragmentTransaction ft = getFragmentManager().beginTransaction();
//        ft.replace(android.R.id.content, fragment, FragmentType.OVERLAY.getTag());
//        ft.commit();
    }

    private enum FragmentType {
        OVERLAY("overlay");
        private String tag;

        private FragmentType(String tag) {
            this.tag = tag;
        }

        public String getTag() {
            return tag;
        }
    }
    public void onAuth() {
        try {
            mBleInterface.dolockAction();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            finish();
        }


    }
    ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            mBleInterface = null;
            mBound = false;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleInterface = IMyAidlInterface.Stub.asInterface(service);
            mBound = true;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(OverlayActivity.this, BLEService.class), mConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }
}
