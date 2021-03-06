// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video;


import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.support.multidex.MultiDexApplication;

import com.amazon.device.ads.AdRegistration;
import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.Utils;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.browser.BootupRecommandationService;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.debug.Debug;
import com.archos.mediacenter.video.picasso.SmbRequestHandler;
import com.archos.mediacenter.video.picasso.ThumbnailRequestHandler;
import com.archos.mediacenter.video.player.cast.ArchosVideoCastManager;
import com.archos.medialib.LibAvos;
import com.archos.mediaprovider.video.NetworkAutoRefresh;
import com.archos.mediascraper.ScraperImage;
import com.google.android.libraries.cast.companionlibrary.cast.CastConfiguration;
import com.squareup.picasso.Picasso;

import java.util.List;
import java.util.Locale;

import httpimage.FileSystemPersistence;
import httpimage.HttpImageManager;


public class CustomApplication extends MultiDexApplication {

    public static String BASEDIR;
    private boolean mAutoScraperActive;
    private HttpImageManager mHttpImageManager;

    public CustomApplication() {
        super();

        mAutoScraperActive = false;
    }

    public void setAutoScraperActive(boolean active) {
        mAutoScraperActive = active;
    }

    public boolean isAutoScraperActive() {
        return mAutoScraperActive;
    }

    @Override
    public void onCreate() {
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 16) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectAll()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    //.penaltyDeath()
                    .build());
        }

        super.onCreate();
        Trakt.initApiKeys(this);
        new Thread() {
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                LibAvos.initAsync(getApplicationContext());
            };
        }.start();

        // Initialize picasso thumbnail extension
        Picasso.setSingletonInstance(
                new Picasso.Builder(getApplicationContext())
                        .addRequestHandler(new ThumbnailRequestHandler(getApplicationContext()))
                        .addRequestHandler(new SmbRequestHandler(getApplicationContext()))
                        .build()
        );

        // Set the dimension of the posters to save
        ScraperImage.setGeneralPosterSize(
                getResources().getDimensionPixelSize(R.dimen.details_poster_width),
                getResources().getDimensionPixelSize(R.dimen.details_poster_height));

        BASEDIR = Environment.getExternalStorageDirectory().getPath()+"Android/data/"+getPackageName();

        // Class that keeps track of activities so we can tell is we are foreground
        registerActivityLifecycleCallbacks(AppState.sCallbackHandler);

        // init HttpImageManager manager.
        mHttpImageManager = new HttpImageManager(HttpImageManager.createDefaultMemoryCache(), 
                new FileSystemPersistence(BASEDIR));

        // Note: we do not init UPnP here, we wait for the user to enter the network view

        TraktService.init();

        NetworkAutoRefresh.init();
        //init credentials db
        NetworkCredentialsDatabase.getInstance().loadCredentials(this);
        ArchosUtils.setGlobalContext(this.getApplicationContext());
        if(ArchosFeatures.isAndroidTV(this))
            BootupRecommandationService.init();

        // preload an Ad for free version
        if (ArchosUtils.isFreeVersion(this)) {
            if(ArchosUtils.isAmazonApk())
                AdRegistration.setAppKey(getString(R.string.amazon_ad));
            // if we are running on a pure leanback device OR on a device using the leanback UI (Android4.4 tv box for example)
            if (UiChoiceDialog.applicationIsInLeanbackMode(this)) {
                AdsHelper.createInterstitialAd(this);
                AdsHelper.requestNewAd();
            }
        }
        if(getMyProcessName(this).equals(getPackageName())){
            //if main process
            Utils.clearOldSubDir(this);
            Debug.startLogcatRecording();
        }

        String applicationId = getString(R.string.app_id);
        // Build a CastConfiguration object and initialize VideoCastManager
        CastConfiguration options = new CastConfiguration.Builder(getString(R.string.app_id))
                .enableAutoReconnect()
                .enableCaptionManagement()
                .enableDebug()
                .enableLockScreen()
                .enableNotification()
                .enableWifiReconnection()
                .setTargetActivity(MainActivity.class)
                .setCastControllerImmersive(false)
                .setLaunchOptions(false, Locale.getDefault())
                .setNextPrevVisibilityPolicy(CastConfiguration.NEXT_PREV_VISIBILITY_POLICY_DISABLED)
                .addNotificationAction(CastConfiguration.NOTIFICATION_ACTION_REWIND, false)
                .addNotificationAction(CastConfiguration.NOTIFICATION_ACTION_PLAY_PAUSE, true)
                .addNotificationAction(CastConfiguration.NOTIFICATION_ACTION_DISCONNECT, true)
                .setForwardStep(10)
                .build();
        ArchosVideoCastManager.initialize(this, options);
        ArchosVideoCastManager.getInstance().appId = applicationId;

    }

    private static String getMyProcessName(Context ct) {
        int id = android.os.Process.myPid();
        String myProcessName = ct.getPackageName();

        ActivityManager actvityManager = (ActivityManager) ct.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procInfos = actvityManager.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo procInfo : procInfos) {
            if (id == procInfo.pid) {
                myProcessName = procInfo.processName;
            }
        }
        return myProcessName;
    }

    public HttpImageManager getHttpImageManager() {
        return mHttpImageManager;
    }
}
