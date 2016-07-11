

package tw.qtlin.mac.airunlocker;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.android.fingerprintdialog.R;

import javax.inject.Inject;

/**
 * A dialog which uses fingerprint APIs to authenticate the user, and falls back to password
 * authentication if fingerprint is not available.
 */
public class FingerprintAuthenticationDialogFragment extends DialogFragment
        implements FingerprintUiHelper.Callback {

    private Button mCancelButton;
    private View mFingerprintContent;
    private View mFingerprintEnroll;


    private FingerprintUiHelper mFingerprintUiHelper;

    @Inject FingerprintUiHelper.FingerprintUiHelperBuilder mFingerprintUiHelperBuilder;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do not create a new Fragment when the Activity is re-created such as orientation changes.
        setRetainInstance(true);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        getDialog().setTitle(getString(R.string.unlock));
        View v = inflater.inflate(R.layout.fingerprint_dialog_container, container, false);
        mCancelButton = (Button) v.findViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().finish();
                dismiss();
            }
        });
        mCancelButton.setText(R.string.cancel);
        mFingerprintContent = v.findViewById(R.id.fingerprint_container);
        mFingerprintEnroll = v.findViewById(R.id.fingerprint_enroll);
        mFingerprintUiHelper = mFingerprintUiHelperBuilder.build(
                (ImageView) v.findViewById(R.id.fingerprint_icon),
                (TextView) v.findViewById(R.id.fingerprint_status), this);

//        // If fingerprint authentication is not available, tell user should open finger print auth
//        if (!mFingerprintUiHelper.isFingerprintAuthAvailable()) {
//
//            mFingerprintContent.setVisibility(View.GONE);
//            mFingerprintEnroll.setVisibility(View.VISIBLE);
//            mCancelButton.setText(R.string.ok);
//            mCancelButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    dismiss();
//                    Intent i = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
//                    getActivity().startActivity(i);
//                    getActivity().finish();
//
//
//                }
//            });
//        }
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        mFingerprintUiHelper.startListening();

    }



    @Override
    public void onPause() {
        super.onPause();
        mFingerprintUiHelper.stopListening();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

    }
    @Override
    public void onAuthenticated() {
        ((OverlayActivity)getActivity()).onAuth();
        dismiss();
        getActivity().finish();
    }

    @Override
    public void onError() {

    }


}
