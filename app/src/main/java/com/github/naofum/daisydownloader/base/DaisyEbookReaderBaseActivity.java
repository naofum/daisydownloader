package com.github.naofum.daisydownloader.base;

import java.util.Locale;

import com.github.naofum.daisydownloader.apps.DaisyReaderDownloadSiteActivity;
import com.github.naofum.daisydownloader.apps.PrivateException;
import org.androiddaisyreader.model.CurrentInformation;
import com.github.naofum.daisydownloader.sqlite.SQLiteCurrentInformationHelper;
import com.github.naofum.daisydownloader.utils.Constants;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings.System;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

import com.actionbarsherlock.app.SherlockActivity;
import com.splunk.mint.Mint;

import ly.count.android.api.Countly;

/**
 * 
 * @author LogiGear
 * @date Jul 19, 2013
 */

public class DaisyEbookReaderBaseActivity extends SherlockActivity {
    protected TextToSpeech mTts;
    private static final long DOUBLE_PRESS_INTERVAL = 1000;
    private static final long DELAY_MILLIS = 500;
    private static long lastPressTime;
    private static int lastPositionClick = -1;
    private static boolean mHasDoubleClicked = false;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /**
         * You should use cloud.count.ly instead of YOUR_SERVER for the line
         * below if you are using Countly Cloud service
         */
        Countly.sharedInstance()
                .init(this, Constants.COUNTLY_URL_SERVER, Constants.COUNTLY_APP_KEY);

        // start the session
        Mint.initAndStartSession(getApplicationContext(), Constants.BUGSENSE_API_KEY);

    }

    @Override
    protected void onResume() {
        super.onResume();
        final int numberToConvert = 255;
        Window window = getWindow();
        ContentResolver cResolver = getContentResolver();
        int valueScreen = 0;
        try {
            SharedPreferences mPreferences = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());
            valueScreen = mPreferences.getInt(Constants.BRIGHTNESS,
                    System.getInt(cResolver, System.SCREEN_BRIGHTNESS));
            LayoutParams layoutpars = window.getAttributes();
            layoutpars.screenBrightness = valueScreen / (float) numberToConvert;
            // apply attribute changes to this window
            window.setAttributes(layoutpars);
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, getApplicationContext());
            ex.writeLogException();
        }
    }

    /**
     * Check keyguard screen is showing or in restricted key input mode .
     * 
     * @return true, if in keyguard restricted input mode
     */
    public boolean checkKeyguardMode() {
        getApplicationContext();
        KeyguardManager kgMgr = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return kgMgr.inKeyguardRestrictedInputMode();
    }

    /**
     * Back to top screen.
     */
    public void backToTopScreen() {
        Intent intent = new Intent(this, DaisyReaderDownloadSiteActivity.class);
        // Removes other Activities from stack
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * Delete current information.
     */
    public void deleteCurrentInformation() {
        SQLiteCurrentInformationHelper sql = new SQLiteCurrentInformationHelper(
                getApplicationContext());
        CurrentInformation current = sql.getCurrentInformation();
        if (current != null) {
            sql.deleteCurrentInformation(current.getId());
        }
    }

    /**
     * Restart activity when changing configuration.
     */
    private void restartActivity() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        restartActivity();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Countly.sharedInstance().onStart();
    }

    @Override
    protected void onStop() {
        Countly.sharedInstance().onStop();
        super.onStop();
    }
}
