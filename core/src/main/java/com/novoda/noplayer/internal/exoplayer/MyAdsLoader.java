package com.novoda.noplayer.internal.exoplayer;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Assertions;
import com.novoda.noplayer.Advert;
import com.novoda.noplayer.AdvertBreak;
import com.novoda.noplayer.AdvertsLoader;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class MyAdsLoader implements AdsLoader, Player.EventListener {

    private static final long END_OF_CONTENT_POSITION_THRESHOLD_MS = 5000;

    private final AdvertsLoader loader;
    @Nullable
    private Player player;
    @Nullable
    private AdPlaybackState adPlaybackState;
    @Nullable
    private Timeline timeline;
    @Nullable
    private EventListener eventListener;

    private long contentDurationMs = C.TIME_UNSET;
    private long pendingContentPositionMs = C.TIME_UNSET;

    private final Timeline.Period period = new Timeline.Period();

    public MyAdsLoader(AdvertsLoader loader) {
        this.loader = loader;
    }

    @Override
    public void setSupportedContentTypes(int... contentTypes) {
        for (int contentType : contentTypes) {
            Log.e("LOADER", "setSupportedContentTypes: " + contentType);
        }
    }

    @Override
    public void start(final EventListener eventListener, AdViewProvider adViewProvider) {
        Log.e("LOADER", "Starting load");
        this.eventListener = eventListener;
        if (adPlaybackState == null) {
            Log.e("LOADER", "calling client load");
            loader.load(new AdvertsLoader.Callback() {
                @Override
                public void onAdvertsLoaded(List<AdvertBreak> advertBreaks) {
                    Log.e("LOADER", "adsLoaded transforming");
                    long[] advertOffsets = getAdvertOffsets(advertBreaks);
                    adPlaybackState = new AdPlaybackState(advertOffsets);
                    long[][] advertBreaksWithAdvertDurations = getAdvertBreakDurations(advertBreaks);
                    adPlaybackState = adPlaybackState.withAdDurationsUs(advertBreaksWithAdvertDurations);

                    for (int i = 0; i < advertBreaks.size(); i++) {
                        List<Advert> adverts = advertBreaks.get(i).adverts();

                        adPlaybackState = adPlaybackState.withAdCount(i, adverts.size());

                        for (int j = 0; j < adverts.size(); j++) {
                            Advert advert = adverts.get(j);
                            adPlaybackState = adPlaybackState.withAdUri(i, j, advert.uri());
                        }
                    }

                    Log.e("LOADER", "retrieved adverts");

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Log.e("LOADER", "send to main thread");
                            updateAdPlaybackState();
                        }
                    });
                }

                @Override
                public void onAdvertsError(String message) {
                    eventListener.onAdLoadError(null, null);
                    Log.e("LOADER", "fail: " + message);
                }
            });
        }
    }

    private void updateAdPlaybackState() {
        if (eventListener != null) {
            Log.e("LOADER", "playback state: " + adPlaybackState);
            eventListener.onAdPlaybackState(adPlaybackState);
        }
    }

    @Override
    public void stop() {
        Log.e("LOADER", "Stopping load");
        if (adPlaybackState != null && player != null) {
            adPlaybackState = adPlaybackState.withAdResumePositionUs(TimeUnit.MILLISECONDS.toMicros(player.getCurrentPosition()));
        }
        eventListener = null;
    }

    @Override
    public void setPlayer(@Nullable Player player) {
        Log.e("LOADER", "setPlayer");
        this.player = player;
        this.player.addListener(this);
    }

    @Override
    public void release() {
        Log.e("LOADER", "release");
        adPlaybackState = null;
        player = null;
    }

    @Override
    public void handlePrepareError(int adGroupIndex, int adIndexInAdGroup, IOException exception) {
        if (adPlaybackState != null) {
            Log.e("LOADER", "group: " + adGroupIndex + " ad: " + adIndexInAdGroup + " handlePrepareError: " + exception);
            adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup);
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {
        Log.e("LOADER", "Timeline changed");
        if (reason == Player.TIMELINE_CHANGE_REASON_RESET) {
            // The player is being reset and this source will be released.
            return;
        }
        Assertions.checkArgument(timeline.getPeriodCount() == 1);
        this.timeline = timeline;
        long contentDurationUs = timeline.getPeriod(0, period).durationUs;
        long contentDurationMs = C.usToMs(contentDurationUs);
        if (contentDurationUs != C.TIME_UNSET) {
            adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs);
        }
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        Log.e("LOADER", "Track changed");
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        Log.e("LOADER", "Loading changed");
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.e("LOADER", "PlayerState changed");
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        Log.e("LOADER", "RepeatMode changed");
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        Log.e("LOADER", "ShuffleMode changed");
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.e("LOADER", "PlayerError " + error.getMessage());
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        Log.e("LOADER", "Playback params changed");
    }

    @Override
    public void onSeekProcessed() {
        Log.e("LOADER", "Seek processed");
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        Log.e("LOADER", "Position Discontinuity");
        if (isContentCompleted()) {
            for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
                if (adPlaybackState.adGroupTimesUs[i] != C.TIME_END_OF_SOURCE) {
                    adPlaybackState = adPlaybackState.withSkippedAdGroup(i);
                }
            }
            updateAdPlaybackState();
        } else {
            long positionMs = player.getCurrentPosition();
            timeline.getPeriod(0, period);
            int newAdGroupIndex = period.getAdGroupIndexForPositionUs(C.msToUs(positionMs));
            if (newAdGroupIndex != C.INDEX_UNSET) {
                pendingContentPositionMs = positionMs;
            }
        }
    }

    private boolean isContentCompleted() {
        return contentDurationMs != C.TIME_UNSET && pendingContentPositionMs == C.TIME_UNSET
                && player.getContentPosition() + END_OF_CONTENT_POSITION_THRESHOLD_MS >= contentDurationMs;
    }

    private static long[] getAdvertOffsets(List<AdvertBreak> advertBreaks) {
        long[] advertOffsets = new long[advertBreaks.size()];
        for (int i = 0; i < advertOffsets.length; i++) {
            advertOffsets[i] = advertBreaks.get(i).startTime();
        }
        return advertOffsets;
    }

    private static long[][] getAdvertBreakDurations(List<AdvertBreak> advertBreaks) {
        long[][] advertBreaksWithAdvertDurations = new long[advertBreaks.size()][];
        for (int i = 0; i < advertBreaks.size(); i++) {
            AdvertBreak advertBreak = advertBreaks.get(i);
            List<Advert> adverts = advertBreak.adverts();
            long[] advertDurations = new long[adverts.size()];

            for (int j = 0; j < adverts.size(); j++) {
                advertDurations[j] = adverts.get(j).durationInMicros();
                Log.e("LOADER", "AdvertDuration: " + advertDurations[j]);
            }
            advertBreaksWithAdvertDurations[i] = advertDurations;
        }
        return advertBreaksWithAdvertDurations;
    }
}
