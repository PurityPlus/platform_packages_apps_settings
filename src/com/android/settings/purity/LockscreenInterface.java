/*
 * Copyright (C) 2012 The CyanogenMod Project
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
 * limitations under the License.
 */

package com.android.settings.purity;

import java.io.File;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.ChooseLockSettingsHelper;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

public class LockscreenInterface extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String LOCKSCREEN_WIDGETS_CATEGORY = "lockscreen_widgets_category";
    private static final String KEY_ENABLE_WIDGETS = "keyguard_enable_widgets";
    private static final String KEY_ENABLE_CAMERA = "keyguard_enable_camera";
    private static final String KEY_NOTIFICATON_PEEK = "notification_peek";
    private static final String KEY_PEEK_PICKUP_TIMEOUT = "peek_pickup_timeout";
    private static final String KEY_PEEK_WAKE_TIMEOUT = "peek_wake_timeout";

    private CheckBoxPreference mEnableKeyguardWidgets;
    private CheckBoxPreference mEnableCameraWidget;
    private ListPreference mLockBackground;
    private CheckBoxPreference mNotificationPeek;
    private ListPreference mPeekPickupTimeout;
    private ListPreference mPeekWakeTimeout;

    private ChooseLockSettingsHelper mChooseLockSettingsHelper;
    private LockPatternUtils mLockUtils;
    private DevicePolicyManager mDPM;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lockscreen_interface_settings);

        mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
        mLockUtils = mChooseLockSettingsHelper.utils();
        mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        // Find categories
        PreferenceCategory widgetsCategory = (PreferenceCategory)
                findPreference(LOCKSCREEN_WIDGETS_CATEGORY);

        // Find preferences
        mEnableKeyguardWidgets = (CheckBoxPreference) findPreference(KEY_ENABLE_WIDGETS);
        mEnableCameraWidget = (CheckBoxPreference) findPreference(KEY_ENABLE_CAMERA);

        mNotificationPeek = (CheckBoxPreference) findPreference(KEY_NOTIFICATON_PEEK);
        mNotificationPeek.setChecked(Settings.System.getInt(getContentResolver(), Settings.System.PEEK_STATE, 0) == 1);
        mNotificationPeek.setOnPreferenceChangeListener(this);

        mPeekPickupTimeout = (ListPreference) findPreference(KEY_PEEK_PICKUP_TIMEOUT);
        int peekPickupTimeout = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.PEEK_PICKUP_TIMEOUT, 15000, UserHandle.USER_CURRENT);
        mPeekPickupTimeout.setValue(String.valueOf(peekPickupTimeout));
        mPeekPickupTimeout.setSummary(mPeekPickupTimeout.getEntry());
        mPeekPickupTimeout.setOnPreferenceChangeListener(this);

        mPeekWakeTimeout = (ListPreference) findPreference(KEY_PEEK_WAKE_TIMEOUT);
        int peekWakeTimeout = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.PEEK_WAKE_TIMEOUT, 5000, UserHandle.USER_CURRENT);
        mPeekWakeTimeout.setValue(String.valueOf(peekWakeTimeout));
        mPeekWakeTimeout.setSummary(mPeekWakeTimeout.getEntry());
        mPeekWakeTimeout.setOnPreferenceChangeListener(this);

        // Remove/disable custom widgets based on device RAM and policy
        if (ActivityManager.isLowRamDeviceStatic()) {
            // Widgets take a lot of RAM, so disable them on low-memory devices
            widgetsCategory.removePreference(findPreference(KEY_ENABLE_WIDGETS));
            mEnableKeyguardWidgets = null;
        } else {
            checkDisabledByPolicy(mEnableKeyguardWidgets,
                    DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL);
        }

        // Enable or disable camera widget based on device and policy
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            widgetsCategory.removePreference(mEnableCameraWidget);
            mEnableCameraWidget = null;
        } else {
            checkDisabledByPolicy(mEnableCameraWidget,
                    DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA);
        }

        // Remove maximize widgets on tablets
        if (!Utils.isPhone(getActivity())) {
            widgetsCategory.removePreference(
                    findPreference(Settings.System.LOCKSCREEN_MAXIMIZE_WIDGETS));
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        // Update custom widgets
        if (mEnableKeyguardWidgets != null) {
            mEnableKeyguardWidgets.setChecked(mLockUtils.getWidgetsEnabled());
        }

        if (mEnableCameraWidget != null) {
            mEnableCameraWidget.setChecked(mLockUtils.getCameraEnabled());
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        final String key = preference.getKey();

        if (KEY_ENABLE_WIDGETS.equals(key)) {
            mLockUtils.setWidgetsEnabled(mEnableKeyguardWidgets.isChecked());
            return true;
        } else if (KEY_ENABLE_CAMERA.equals(key)) {
            mLockUtils.setCameraEnabled(mEnableCameraWidget.isChecked());
            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        ContentResolver cr = getActivity().getContentResolver();
        if (preference == mNotificationPeek) {
            boolean value = (Boolean) objValue;
            Settings.System.putInt(cr, Settings.System.PEEK_STATE,
                   value ? 1 : 0);
             return true;
        } else if (preference == mPeekPickupTimeout) {
            int index = mPeekPickupTimeout.findIndexOfValue((String) objValue);
            int peekPickupTimeout = Integer.valueOf((String) objValue);
            Settings.System.putIntForUser(getContentResolver(),
                Settings.System.PEEK_PICKUP_TIMEOUT,
                    peekPickupTimeout, UserHandle.USER_CURRENT);
            mPeekPickupTimeout.setSummary(mPeekPickupTimeout.getEntries()[index]);
            return true;
        } else if (preference == mPeekWakeTimeout) {
            int index = mPeekWakeTimeout.findIndexOfValue((String) objValue);
            int peekWakeTimeout = Integer.valueOf((String) objValue);
            Settings.System.putIntForUser(getContentResolver(),
                Settings.System.PEEK_WAKE_TIMEOUT,
                    peekWakeTimeout, UserHandle.USER_CURRENT);
            mPeekWakeTimeout.setSummary(mPeekWakeTimeout.getEntries()[index]);
            return true;
        }
        return false;
    }


   /**
     * Checks if a specific policy is disabled by a device administrator, and disables the
     * provided preference if so.
     * @param preference Preference
     * @param feature Feature
     */
    private void checkDisabledByPolicy(Preference preference, int feature) {
        boolean disabled = featureIsDisabled(feature);

        if (disabled) {
            preference.setSummary(R.string.security_enable_widgets_disabled_summary);
        }

        preference.setEnabled(!disabled);
    }

    /**
     * Checks if a specific policy is disabled by a device administrator.
     * @param feature Feature
     * @return Is disabled
     */
    private boolean featureIsDisabled(int feature) {
        return (mDPM.getKeyguardDisabledFeatures(null) & feature) != 0;
    }
}
