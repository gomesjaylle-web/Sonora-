package com.example.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RewardedAdStatus {
    IDLE,
    LOADING,
    READY,
    SHOWING,
    FAILED
}

class RewardedAdManager {

    companion object {
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-6285196863394658/5561786505"
    }

    private var rewardedAd: RewardedAd? = null

    private val _status = MutableStateFlow(RewardedAdStatus.IDLE)
    val status: StateFlow<RewardedAdStatus> = _status.asStateFlow()

    fun preload(context: Context) {
        if (rewardedAd != null) {
            _status.value = RewardedAdStatus.READY
            return
        }
        if (_status.value == RewardedAdStatus.LOADING || _status.value == RewardedAdStatus.SHOWING) {
            return
        }

        _status.value = RewardedAdStatus.LOADING
        val appContext = context.applicationContext
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            appContext,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    _status.value = RewardedAdStatus.READY
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    _status.value = RewardedAdStatus.FAILED
                }
            }
        )
    }

    fun showAd(
        activity: Activity,
        wasMusicPlaying: Boolean,
        onPauseMusic: () -> Unit,
        onResumeMusic: () -> Unit,
        onRewardEarned: () -> Unit,
        onAdNotReady: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            preload(activity)
            onAdNotReady()
            return
        }

        var pausedForAd = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                _status.value = RewardedAdStatus.SHOWING
                rewardedAd = null
                if (wasMusicPlaying && !pausedForAd) {
                    pausedForAd = true
                    onPauseMusic()
                }
            }

            override fun onAdDismissedFullScreenContent() {
                _status.value = RewardedAdStatus.IDLE
                if (pausedForAd) {
                    onResumeMusic()
                }
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                _status.value = RewardedAdStatus.FAILED
                if (pausedForAd) {
                    onResumeMusic()
                }
                preload(activity)
            }
        }

        if (wasMusicPlaying) {
            pausedForAd = true
            onPauseMusic()
        }

        ad.show(activity) {
            onRewardEarned()
        }
    }
}
