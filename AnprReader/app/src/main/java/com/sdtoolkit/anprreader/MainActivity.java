package com.sdtoolkit.anprreader;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.sdtoolkit.anpr.api.AnprEngineFactory;
import com.sdtoolkit.anpr.api.AnprErrors;
import com.sdtoolkit.anpr.api.AnprResult;
import com.sdtoolkit.anpr.api.DeviceParams;
import com.sdtoolkit.anpr.api.IAnprEngine;
import com.sdtoolkit.anpr.api.IAnprEngineListener;
import com.sdtoolkit.anpr.api.RecognitionParams;
import com.sdtoolkit.anpr.view.CameraView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private final int REQUEST_ALL_PERMISSIONS = 0x4562;
    private final int REQUEST_INSTALL_SDTANPR = 0x4563;
    private final int REQUEST_CONFIGURE_SDTANPR = 0x4563;

    private Button mButStart;
    private Button mButStop;
    private Button mButConfigure;
    private TextView mTextResults;
    private CameraView mCameraView;

    private IAnprEngine mAnprEngine;
    private DeviceParams mDeviceParams = new DeviceParams();
    private RecognitionParams mRecognitionParams = new RecognitionParams();

    private IAnprEngineListener mAnprEngineListener = new IAnprEngineListener() {
        @Override
        public void onAnprOpened(int result,
                                 DeviceParams currentDeviceParams,
                                 RecognitionParams currentRecognitionParams) {
            // Recognition parameters currently set in the ANPR Service
            // This application does not modify any parameters and just update local copy
            if (result != AnprErrors.SUCCESS) {
                return;
            }

            mDeviceParams.copyFrom(currentDeviceParams);
            mRecognitionParams.copyFrom(currentRecognitionParams);

            if (!mAnprEngine.isRecognitionRunning()) {
                mAnprEngine.setup(mDeviceParams);
            }
        }

        @Override
        public void onAnprClosed(int result) {

        }

        @Override
        public void onAnprSetupComplete(int result, DeviceParams currentDeviceParams) {

        }

        @Override
        public void onAnprStarted(int result) {

        }

        @Override
        public void onAnprStopped(int result) {

        }

        @Override
        public void onAnprResult(int status, AnprResult[] results, Bitmap resultFrame) {
            if (status == AnprErrors.SUCCESS) {
                populateRecognitionResults(results, resultFrame);
            }
        }

        @Override
        public void onAnprSettingsChanged(DeviceParams currentDeviceParams,
                                          RecognitionParams currentRecognitionParams) {
            mDeviceParams.copyFrom(currentDeviceParams);
            mRecognitionParams.copyFrom(currentRecognitionParams);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String[] permissions = retrieveNotGrantedPermissions();

        if (permissions != null && permissions.length > 0) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_ALL_PERMISSIONS);
        }

        if (!isAnprServiceIsInstalled()) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Unable to find SD-TOOLKIT ANPR Serivce");
            builder.setCancelable(false);
            builder.setPositiveButton("Install", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.sdtoolkit.anprservice"));
                    startActivityForResult(intent, REQUEST_INSTALL_SDTANPR);
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();

        }

        mCameraView = findViewById(R.id.preview_wnd);

        mButStart = findViewById(R.id.but_start);
        mButStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAnprEngineStart();
            }
        });

        mButStop = findViewById(R.id.but_stop);
        mButStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAnprEngineStop();
            }
        });

        mButConfigure = findViewById(R.id.but_configure);
        mButConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAnprEngineConfigure();
            }
        });

        mTextResults = findViewById(R.id.text_results);

        // Create Anpr Engine instance
        mAnprEngine = AnprEngineFactory.createAnprEngine(this);

        // Create intent to be used when user clicks system notification bar icon
        Intent runIntent = new Intent(this, this.getClass());
        runIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        mAnprEngine.open(mAnprEngineListener, runIntent);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAnprEngine != null) {
            mAnprEngine.close();
            mAnprEngine = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_INSTALL_SDTANPR) {
            recreate();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            for(int gC = 0; gC < grantResults.length; gC++) {
                if (grantResults[gC] != PackageManager.PERMISSION_GRANTED) {
                    // Notify User that not all permissions are granted
                    Toast.makeText(this,
                            "Not all permissions has been granted. Quitting...",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            recreate();
        }
    }

    private String[] retrieveNotGrantedPermissions() {
        ArrayList<String> nonGrantedPerms = new ArrayList<>();
        try {
            String[] manifestPerms = getPackageManager()
                    .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
            if (manifestPerms == null || manifestPerms.length == 0) {
                return null;
            }

            for (String permName : manifestPerms) {
                int permission = ActivityCompat.checkSelfPermission(this, permName);
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    nonGrantedPerms.add(permName);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return nonGrantedPerms.toArray(new String[nonGrantedPerms.size()]);
    }

    private boolean isAnprServiceIsInstalled() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    "com.sdtoolkit.anprservice", 0);

            if (info != null && info.enabled) {
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void onAnprEngineStart() {
        if (mAnprEngine != null) {
            mAnprEngine.beginRecognition(mRecognitionParams);
        }
    }

    private void onAnprEngineStop() {
        if (mAnprEngine != null) {
            mAnprEngine.endRecognition();
        }
    }

    private void onAnprEngineConfigure() {
        // Call SDT Anpr service property here
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.sdtoolkit.anprservice",
                "com.sdtoolkit.anpr.activity.AnprSettingsActivity"));
        startActivityForResult(intent, REQUEST_CONFIGURE_SDTANPR);
    }

    private void populateRecognitionResults(final AnprResult[] results, final Bitmap resultsFrame) {
        if (results == null || results.length == 0) {
            return;
        }

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (AnprResult result : results) {
                    mTextResults.append(result.getPlate() + "\r\n");
                }
            }
        });
    }
}
