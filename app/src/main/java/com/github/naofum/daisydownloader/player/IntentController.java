/**
 * This is controller. It will help to change intents.
 * @author LogiGear
 * @date 2013.03.05
 */

package com.github.naofum.daisydownloader.player;

import com.github.naofum.daisydownloader.apps.DaisyReaderSettingActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.speech.tts.TextToSpeech;

import com.github.naofum.daisydownloader.R;

public class IntentController {
    private Context mContext;
    private static final int LEVEL_1 = 1;
    private static final int LEVEL_2 = 2;
    private static final int LEVEL_3 = 3;
    private static final int LEVEL_4 = 4;
    private static final int LEVEL_5 = 5;
    private static final int LEVEL_6 = 6;

    public IntentController(Context context) {
        this.mContext = context;
    }

    /**
     * push to activity setting
     */
    public void pushToDaisyReaderSettingIntent() {
        Intent i = new Intent(mContext, DaisyReaderSettingActivity.class);
        mContext.startActivity(i);
    }

    /**
     * Show dialog when application has error.
     * 
     * @param message : this will show for user
     * @param title : title of dialog
     * @param resId : icon message
     * @param isBack : previous screen before if true and otherwise.
     * @param isSpeak : application will speak.
     * @param tts : text to speech
     */
    @SuppressWarnings("deprecation")
    public void pushToDialog(String message, String title, int resId, final boolean isBack,
            boolean isSpeak, TextToSpeech tts) {
        AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
        // Setting Dialog Title
        alertDialog.setTitle(title);
        // Setting Dialog Message
        alertDialog.setMessage(message);
        // Setting Icon to Dialog
        alertDialog.setIcon(resId);
        // Setting OK Button
        alertDialog.setButton(mContext.getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (isBack) {
                            Activity a = (Activity) mContext;
                            a.onBackPressed();
                        }
                    }
                });

//        if (isSpeak && tts != null) {
//            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null);
//        }
        // Showing Alert Message
        alertDialog.show();
    }

}
