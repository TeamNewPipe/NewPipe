package org.schabi.newpipe.util;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.ironsource.mediationsdk.ISBannerSize;
import com.ironsource.mediationsdk.IronSource;
import com.ironsource.mediationsdk.IronSourceBannerLayout;
import com.ironsource.mediationsdk.integration.IntegrationHelper;
import com.ironsource.mediationsdk.logger.IronSourceError;
import com.ironsource.mediationsdk.model.Placement;
import com.ironsource.mediationsdk.sdk.InterstitialListener;
import com.ironsource.mediationsdk.sdk.RewardedVideoListener;
import com.startapp.sdk.ads.banner.Banner;
import com.startapp.sdk.ads.banner.BannerListener;
import com.startapp.sdk.adsbase.Ad;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.StartAppSDK;
import com.startapp.sdk.adsbase.adlisteners.AdEventListener;

import java.util.Random;

public class Ads {
    public static final int INT_ADS_SHOW_RATE = 7;
    public static Ads mInstance;
    private Activity activity;
    private OnEventListener mOnEventListener;

    //STARTAPP
    private StartAppAd startAppAd;

    //IRONSOURCE
    private static final String appKey = "db6285f1";
    private IronSourceBannerLayout ironSourceBannerLayout;

    public static Ads getInstance(Activity activity) {

        if (mInstance == null) {
            mInstance = new Ads(activity);
        }
        return mInstance;
    }

    private Ads(Activity activity) {
        this.activity = activity;

        //STARTAPP
        StartAppSDK.init(activity, "205066782", true);
        StartAppSDK.setUserConsent(activity,
                "pas",
                System.currentTimeMillis(),
                true);
        startAppAd = new StartAppAd(activity);

        //IRONSOURCE
        IronSource.init(activity, appKey);
        IronSource.shouldTrackNetworkState(activity, true);
        IntegrationHelper.validateIntegration(activity);
        IronSource.setMetaData("Facebook_IS_CacheFlag", "ALL");
//        IronSource.setAdaptersDebug(true);
        //ADMOB
        MobileAds.initialize(activity, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });
    }

    public interface OnEventListener {
        void onAdClosed();

        void onAdShowFailed();
        void onAdOpened();
    }

    public void loadInterstitialAd() {
        loadIronSrcInterstitialAd();
    }

    public void showStartAppInterstitial(OnEventListener onEventListener) {
        mOnEventListener = onEventListener;
        startAppAd.loadAd(StartAppAd.AdMode.AUTOMATIC, new AdEventListener() {
            @Override
            public void onReceiveAd(Ad ad) {
                Log.d("StartApp Ads:", "int onReceiveAd");

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //Do something after 100ms
                        startAppAd.showAd();
                    }
                }, 1000);

                if (mOnEventListener != null) {
                    mOnEventListener.onAdClosed();
                }
            }

            @Override
            public void onFailedToReceiveAd(Ad ad) {
                Log.d("StartApp Ads:", "int onAdFailedToLoad - " + ad.getErrorMessage());
                if (mOnEventListener != null) {
                    mOnEventListener.onAdShowFailed();
                }
            }
        });

    }

    public void showIronSrcInterstitial(OnEventListener onEventListener) {
        mOnEventListener = onEventListener;
        IronSource.showInterstitial();
    }

    public void showInterstitialAd(OnEventListener onEventListener) {
        if (IronSource.isInterstitialReady()) {
            showIronSrcInterstitial(onEventListener);
        } else {
            loadIronSrcInterstitialAd();
            showStartAppInterstitial(onEventListener);
        }
    }

    public void loadIronSrcInterstitialAd() {

        if (!IronSource.isInterstitialReady()) {
            IronSource.setInterstitialListener(new InterstitialListener() {
                @Override
                public void onInterstitialAdReady() {
                    Log.d("IronSource Ads:", "int onInterstitialAdReady");
                }

                @Override
                public void onInterstitialAdLoadFailed(IronSourceError ironSourceError) {
                    Log.d("IronSource Ads:", "int onInterstitialAdLoadFailed : " + ironSourceError.getErrorMessage());
                }

                @Override
                public void onInterstitialAdOpened() {
                    Log.d("IronSource Ads:", "int onInterstitialAdOpened");
                    if (mOnEventListener != null) {
                        mOnEventListener.onAdOpened();
                    }
                }

                @Override
                public void onInterstitialAdClosed() {
                    Log.d("IronSource Ads:", "int onInterstitialAdClosed");
                    loadIronSrcInterstitialAd();
                    if (mOnEventListener != null) {
                        mOnEventListener.onAdClosed();
                    }
                }

                @Override
                public void onInterstitialAdShowSucceeded() {
                    Log.d("IronSource Ads:", "int onInterstitialAdShowSucceeded");
                }

                @Override
                public void onInterstitialAdShowFailed(IronSourceError ironSourceError) {
                    Log.d("IronSource Ads:", "int onInterstitialAdShowFailed : " + ironSourceError.getErrorMessage());
                    if (mOnEventListener != null) {
                        mOnEventListener.onAdShowFailed();
                    }
                }

                @Override
                public void onInterstitialAdClicked() {
                    Log.d("IronSource Ads:", "int onInterstitialAdClicked");
                }
            });

            IronSource.loadInterstitial();
        }

    }

    public void loadRewardedVideoAd() {
        IronSource.setRewardedVideoListener(new RewardedVideoListener() {
            /**
             * Invoked when the RewardedVideo ad view has opened.
             * Your Activity will lose focus. Please avoid performing heavy
             * tasks till the video ad will be closed.
             */
            @Override
            public void onRewardedVideoAdOpened() {
                Log.d("IronSource Ads:", "rwd onRewardedVideoAdOpened");
                if (mOnEventListener != null) {
                    mOnEventListener.onAdOpened();
                }
//                Toast.makeText(activity,"Watch an ad to begin downloading...",Toast.LENGTH_LONG).show();
            }

            /*Invoked when the RewardedVideo ad view is about to be closed.
            Your activity will now regain its focus.*/
            @Override
            public void onRewardedVideoAdClosed() {
                Log.d("IronSource Ads:", "rwd onRewardedVideoAdClosed");
                if (mOnEventListener != null) {
                    mOnEventListener.onAdClosed();
                }
            }

            /**
             * Invoked when there is a change in the ad availability status.
             *
             * @param - available - value will change to true when rewarded videos are *available.
             *          You can then show the video by calling showRewardedVideo().
             *          Value will change to false when no videos are available.
             */
            @Override
            public void onRewardedVideoAvailabilityChanged(boolean available) {
                Log.d("IronSource Ads:", "rwd onRewardedVideoAvailabilityChanged - Available = " + available);
                //Change the in-app 'Traffic Driver' state according to availability.
            }

            /**
             /**
             * Invoked when the user completed the video and should be rewarded.
             * If using server-to-server callbacks you may ignore this events and wait *for the callback from the ironSource server.
             *
             * @param - placement - the Placement the user completed a video from.
             */
            @Override
            public void onRewardedVideoAdRewarded(Placement placement) {
                Log.d("IronSource Ads:", "rwd onRewardedVideoAdRewarded");
                /** here you can reward the user according to the given amount.
                 String rewardName = placement.getRewardName();
                 int rewardAmount = placement.getRewardAmount();
                 */
            }

            /* Invoked when RewardedVideo call to show a rewarded video has failed
             * IronSourceError contains the reason for the failure.
             */
            @Override
            public void onRewardedVideoAdShowFailed(IronSourceError error) {
                Log.d("IronSource Ads:", "rwd onRewardedVideoAdShowFailed");
                if (mOnEventListener != null) {
                    mOnEventListener.onAdShowFailed();
                }
            }

            /*Invoked when the end user clicked on the RewardedVideo ad
             */
            @Override
            public void onRewardedVideoAdClicked(Placement placement) {
                Log.d("IronSource Ads:", "rwd onRewardedVideoAdClicked");
            }

            //    * Note: the events AdStarted and AdEnded below are not available for all supported rewarded video
//            ad networks. Check which events are available per ad network you choose
//            to include in your build.
//            We recommend only using events which register to ALL ad networks you
//            include in your build.
//                    * Invoked when the video ad starts playing.
//    */
            @Override
            public void onRewardedVideoAdStarted() {
                Log.d("IronSource Ads:", "rwd onRewardedVideoAdStarted");
            }

            /* Invoked when the video ad finishes plating. */
            @Override
            public void onRewardedVideoAdEnded() {
                Log.d("IronSource Ads:", "rwd onRewardedVideoAdEnded");
            }
        });

    }

    public void showRewardedVideoAd(OnEventListener onEventListener) {
        mOnEventListener = onEventListener;
        IronSource.showRewardedVideo("DefaultRewardedVideo");
    }

    public void loadBannerAd(final FrameLayout view) {
        loadIronSrcBannerAd(view);
    }

    public void loadIronSrcBannerAd(final FrameLayout view) {
        ironSourceBannerLayout = IronSource.createBanner(activity, ISBannerSize.BANNER);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);

        ironSourceBannerLayout.setBannerListener(new com.ironsource.mediationsdk.sdk.BannerListener() {
            @Override
            public void onBannerAdLoaded() {
                Log.d("IronSource Ads:", "ban onBannerAdLoaded");

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        view.removeAllViews();
                        view.addView(ironSourceBannerLayout, 0, layoutParams);
                    }
                });
            }

            @Override
            public void onBannerAdLoadFailed(IronSourceError ironSourceError) {
                Log.d("IronSource Ads:", "ban onBannerAdLoadFailed : " + ironSourceError.getErrorMessage());

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        view.removeAllViews();
                        loadStartAppBannerAd(view);
                    }
                });
            }

            @Override
            public void onBannerAdClicked() {
                Log.d("IronSource Ads:", "ban onBannerAdClicked");

            }

            @Override
            public void onBannerAdScreenPresented() {
                Log.d("IronSource Ads:", "ban onBannerAdScreenPresented");

            }

            @Override
            public void onBannerAdScreenDismissed() {
                Log.d("IronSource Ads:", "ban onBannerAdScreenDismissed");

            }

            @Override
            public void onBannerAdLeftApplication() {
                Log.d("IronSource Ads:", "ban onBannerAdLeftApplication");

            }

        });

        IronSource.loadBanner(ironSourceBannerLayout);

    }

    public void removeBannerAd(){
    }

    public void loadStartAppBannerAd(FrameLayout ad_view_fl) {

        // Get the Main relative layout of the entire activity
        // Define StartApp Banner
        Banner startAppBanner = new Banner(activity);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        startAppBanner.setBannerListener(new BannerListener() {
            @Override
            public void onReceiveAd(View view) {
                Log.d("StartApp Ads:", "ban onReceiveAd");
                // Add to main Layout
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ad_view_fl.removeAllViews();
                                ad_view_fl.addView(startAppBanner, layoutParams);
                            }
                        });

                    }
                });
            }

            @Override
            public void onFailedToReceiveAd(View view) {
                Log.d("StartApp Ads:", "ban onFailedToReceiveAd");
                // Add to main Layout
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ad_view_fl.removeAllViews();
                                ad_view_fl.addView(startAppBanner, layoutParams);
                            }
                        });
                    }
                });
            }

            @Override
            public void onImpression(View view) {
                Log.d("StartApp Ads:", "ban onImpression");
            }

            @Override
            public void onClick(View view) {
                Log.d("StartApp Ads:", "ban onClick");
            }
        });

        startAppBanner.loadAd();


    }

    public void loadNativeAd(View view) {
        loadAdMobNativeAds(view);
    }

    private void loadAdMobNativeAds(final View view) {

    }

    public static int getRandNumber() {
        Random r = new Random();
        int rand = r.nextInt(10);
        Log.i("rand ads", "rand " + rand);
        return rand;
    }


}