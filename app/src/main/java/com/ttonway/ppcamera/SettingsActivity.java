package com.ttonway.ppcamera;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by ttonway on 2017/10/15.
 */

public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.perference);
    }
}
