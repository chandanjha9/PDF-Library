package com.example.pdflibrary.premium

import android.app.Activity
import com.example.pdflibrary.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * AdMob rewarded ad. IDs come from app/build.gradle.kts (currently Google's
 * official TEST IDs — replace with your own before publishing).
 */
object RewardedAds {

    fun show(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissedWithoutReward: () -> Unit,
        onError: (String) -> Unit,
    ) {
        RewardedAd.load(
            activity,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    var earned = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            if (earned) onRewarded() else onDismissedWithoutReward()
                        }
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            onError("Ad couldn't be shown. Please try again.")
                        }
                    }
                    ad.show(activity) { earned = true }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    onError("No ad available right now. Please try again in a moment.")
                }
            },
        )
    }
}
