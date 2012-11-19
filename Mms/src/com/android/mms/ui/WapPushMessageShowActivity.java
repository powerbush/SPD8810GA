
package com.android.mms.ui;

import com.android.mms.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WapPushMessageShowActivity extends Activity {
    /** Called when the activity is first created. */

    private static final String TAG = "WapPushMessageShowActivity";

    private Map<AlertDialog , Intent> dialogMap = new HashMap<AlertDialog, Intent>();

    private final OnClickListener mOKListener = new OnClickListener() {

        public void onClick(DialogInterface dialog, int whichButton) {
            closeDialog(dialog);
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlertDialog dialog  = showConfirmDialog();
        synchronized (dialogMap) {
            dialogMap.put(dialog, this.getIntent());
        }
    }
    
    protected void onNewIntent(Intent intent) {
        AlertDialog dialog  = showConfirmDialog();
        synchronized (dialogMap) {
            dialogMap.put(dialog, this.getIntent());
        }
    }

    private void closeDialog(DialogInterface dialog){
        dialog.dismiss();
        synchronized (dialogMap) {
            dialogMap.remove(dialog);
            if (dialogMap.size() == 0)
                WapPushMessageShowActivity.this.finish();
        }
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
    }

//    @Override
//    protected void onResume() {
//        // TODO Auto-generated method stub
//        super.onResume();
//        if(dialogMap != null && dialogMap.size() > 0){
//            Set<AlertDialog> dialogset = dialogMap.keySet();
//            Iterator<AlertDialog> it = dialogset.iterator();
//            while(it.hasNext()){
//                AlertDialog dialog = it.next();
//                dialog.dismiss();
//                dialog = null;
//            }
//            dialogMap.clear();
//            WapPushMessageShowActivity.this.finish();
//        }
//    }

    private AlertDialog showConfirmDialog() {
        String pushbody = this.getIntent().getExtras().getString("pushBody");
        pushbody = pushbody==null?"":pushbody;
        final String href = this.getIntent().getExtras().getString("href");
        return new AlertDialog.Builder(this).setTitle(R.string.WAPPush_Message_title)
                .setIcon(android.R.drawable.ic_dialog_alert).setCancelable(true)
                .setMessage(pushbody)//R.string.OTAConfig_Message
                .setPositiveButton(R.string.open_website, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            
                            Uri uri = Uri.parse(href);
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.putExtra(Browser.EXTRA_APPLICATION_ID, WapPushMessageShowActivity.this.getPackageName());
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                            WapPushMessageShowActivity.this.startActivity(intent);
                        }catch(NullPointerException ex){
                            Log.e(TAG, "href is null!!!");
                            Toast.makeText(WapPushMessageShowActivity.this, "href is null!!!", Toast.LENGTH_SHORT).show();
                        }
                        catch(ActivityNotFoundException ex){
                            Log.e(TAG, "send intent to browserApp happened exception !!:::"+ex.toString(),ex);
                            Toast.makeText(WapPushMessageShowActivity.this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                        }
                        closeDialog(dialog);
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        closeDialog(dialog);
                    }
                })
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        closeDialog(dialog);
                    }
                })
                .show();
    }

}
