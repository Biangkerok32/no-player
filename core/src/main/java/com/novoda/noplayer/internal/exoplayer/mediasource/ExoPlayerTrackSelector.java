package com.novoda.noplayer.internal.exoplayer.mediasource;

import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.novoda.noplayer.internal.SupportMapper;
import com.novoda.noplayer.internal.exoplayer.RendererTypeRequester;
import com.novoda.noplayer.internal.utils.Optional;
import com.novoda.noplayer.model.Support;

// We cannot make it final as we need to mock it in tests
@SuppressWarnings({"checkstyle:FinalClass", "PMD.ClassWithOnlyPrivateConstructorsShouldBeFinal"})
public class ExoPlayerTrackSelector {

    private final DefaultTrackSelector trackSelector;
    private final RendererTrackIndexExtractor rendererTrackIndexExtractor;

    public static ExoPlayerTrackSelector newInstance(DefaultTrackSelector trackSelector) {
        RendererTrackIndexExtractor rendererTrackIndexExtractor = new RendererTrackIndexExtractor();
        return new ExoPlayerTrackSelector(trackSelector, rendererTrackIndexExtractor);
    }

    private ExoPlayerTrackSelector(DefaultTrackSelector trackSelector, RendererTrackIndexExtractor rendererTrackIndexExtractor) {
        this.trackSelector = trackSelector;
        this.rendererTrackIndexExtractor = rendererTrackIndexExtractor;
    }

    TrackGroupArray trackGroups(TrackType trackType, RendererTypeRequester rendererTypeRequester) {
        Optional<Integer> rendererIndex = rendererTrackIndexExtractor.extract(trackType, mappedTrackInfoLength(), rendererTypeRequester);
        return rendererIndex.isAbsent() ? TrackGroupArray.EMPTY : trackInfo().getTrackGroups(rendererIndex.get());
    }

    Support trackSupport(TrackType trackType, int groupIndex, int trackIndex, RendererTypeRequester rendererTypeRequester) {
        Optional<Integer> rendererIndex = rendererTrackIndexExtractor.extract(trackType, mappedTrackInfoLength(), rendererTypeRequester);
        if (rendererIndex.isAbsent()) {
            return Support.FORMAT_UNKNOWN;
        }

        MappingTrackSelector.MappedTrackInfo currentMappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (currentMappedTrackInfo == null) {
            return Support.FORMAT_UNKNOWN;
        }
        try {
            int trackSupport = currentMappedTrackInfo.getTrackSupport(rendererIndex.get(), groupIndex, trackIndex);
            return SupportMapper.from(trackSupport);
        } catch (IndexOutOfBoundsException e) {
            return Support.FORMAT_UNKNOWN;
        }
    }

    boolean clearSelectionOverrideFor(TrackType trackType, RendererTypeRequester rendererTypeRequester) {
        Optional<Integer> rendererIndex = rendererTrackIndexExtractor.extract(trackType, mappedTrackInfoLength(), rendererTypeRequester);
        if (rendererIndex.isPresent()) {
            trackSelector.setParameters(trackSelector
                    .buildUponParameters()
                    .clearSelectionOverrides(rendererIndex.get())
            );
            return true;
        } else {
            return false;
        }
    }

    private ExoPlayerMappedTrackInfo trackInfo() {
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();

        if (trackInfo == null) {
            throw new IllegalStateException("Track info is not available.");
        }
        return new ExoPlayerMappedTrackInfo(trackInfo);
    }

    private int mappedTrackInfoLength() {
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (trackInfo == null) {
            return 0;
        } else {
            return trackInfo.getRendererCount();
        }
    }

    boolean setSelectionOverride(TrackType trackType,
                                 RendererTypeRequester rendererTypeRequester,
                                 TrackGroupArray trackGroups,
                                 DefaultTrackSelector.SelectionOverride selectionOverride) {
        Optional<Integer> rendererIndex = rendererTrackIndexExtractor.extract(trackType, mappedTrackInfoLength(), rendererTypeRequester);
        if (rendererIndex.isPresent()) {
            trackSelector.setParameters(trackSelector
                    .buildUponParameters()
                    .setSelectionOverride(rendererIndex.get(), trackGroups, selectionOverride)
            );
            return true;
        } else {
            return false;
        }
    }

    boolean supportsTrackSwitching(TrackType trackType,
                                   RendererTypeRequester rendererTypeRequester,
                                   TrackGroupArray trackGroups,
                                   int groupIndex) {
        Optional<Integer> audioRendererIndex = rendererTrackIndexExtractor.extract(trackType, mappedTrackInfoLength(), rendererTypeRequester);
        return audioRendererIndex.isPresent()
                && trackGroups.get(groupIndex).length > 0
                && trackInfo().getAdaptiveSupport(audioRendererIndex.get(), groupIndex, false) != RendererCapabilities.ADAPTIVE_NOT_SUPPORTED;
    }

    void clearMaxVideoBitrate() {
        setMaxVideoBitrateParameter(Integer.MAX_VALUE);
    }

    void setMaxVideoBitrate(int maxVideoBitrate) {
        setMaxVideoBitrateParameter(maxVideoBitrate);
    }

    private void setMaxVideoBitrateParameter(int maxValue) {
        trackSelector.setParameters(
                trackSelector.buildUponParameters()
                        .setMaxVideoBitrate(maxValue)
                        .build()
        );
    }
}
