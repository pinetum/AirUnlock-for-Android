/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package tw.qtlin.mac.airunlocker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.widget.Toast;


public class SettingsActivity extends Activity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new SettingsFragment()).commit();
    }

    /**
     * Fragment for settings.
     */
    public static class SettingsFragment extends PreferenceFragment{

        private SharedPreferences.OnSharedPreferenceChangeListener mListener;

        private SwitchPreference fingerprintAuthSwitch;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            fingerprintAuthSwitch = (SwitchPreference)findPreference(getString(R.string.use_fingerprint_to_authenticate_key));
            // check fingerprint sensor is ok ?
            if(!BLEService.checkFingerPrintSensor(getActivity())){
                //turn off the switch
                fingerprintAuthSwitch.setChecked(false);
                //remove it!
                getPreferenceScreen().removePreference(fingerprintAuthSwitch);
            }
            mListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if(key == getString(R.string.use_fingerprint_to_authenticate_key)){
                        // check finger print is ok ?
                        if(!BLEService.checkFingerPrintAvailable(getActivity())){
                            fingerprintAuthSwitch.setChecked(false);
                            Toast.makeText(getActivity(),
                                    getString(R.string.go_to_set_fingerprint),
                                    Toast.LENGTH_LONG).show();
                        }

                    }
                }
            };


        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(mListener);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(mListener);
            Intent updateService = new Intent()
                    .setAction("android.intent.action.airunlockmac")
                    .putExtra("REQUEST_CODE", BLEService.INTENT_UPDATE_UNLOCK_INFO);
            getActivity().sendBroadcast(updateService);
        }
    }
}


