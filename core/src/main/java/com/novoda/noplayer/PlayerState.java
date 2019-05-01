package com.novoda.noplayer;

public interface PlayerState {

    boolean isPlaying();

    boolean isPlayingAdvert();

    boolean isPlayingContent();

    int videoWidth();

    int videoHeight();

    long positionInAdvertBreakInMillis();

    long advertBreakDurationInMillis();

    long playheadPositionInMillis();

    long mediaDurationInMillis();

    int bufferPercentage();
}
