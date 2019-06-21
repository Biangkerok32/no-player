package com.novoda.noplayer.internal.exoplayer.drm;

import android.os.Handler;

import com.novoda.noplayer.UnableToCreatePlayerException;
import com.novoda.noplayer.drm.DrmType;
import com.novoda.noplayer.drm.KeyRequestExecutor;
import com.novoda.noplayer.internal.drm.provision.ProvisionExecutorCreator;
import com.novoda.noplayer.internal.utils.AndroidDeviceVersion;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import utils.ExceptionMatcher;

import static org.fest.assertions.api.Assertions.assertThat;

public class DrmSessionCreatorFactoryTest {

    private static final AndroidDeviceVersion UNSUPPORTED_MEDIA_DRM_DEVICE_VERSION = new AndroidDeviceVersion(17);
    private static final KeyRequestExecutor IGNORED_KEY_REQUEST_EXECUTOR = KeyRequestExecutor.NOT_REQUIRED;
    private static final AndroidDeviceVersion SUPPORTED_MEDIA_DRM_DEVICE = new AndroidDeviceVersion(18);

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private Handler handler;
    @Mock
    private KeyRequestExecutor keyRequestExecutor;
    @Mock
    private ProvisionExecutorCreator provisionExecutorCreator;

    private DrmSessionCreatorFactory drmSessionCreatorFactory;

    @Before
    public void setUp() {
        drmSessionCreatorFactory = new DrmSessionCreatorFactory(SUPPORTED_MEDIA_DRM_DEVICE, provisionExecutorCreator, handler);
    }

    @Test
    public void givenDrmTypeNone_whenCreatingDrmSessionCreator_thenReturnsNoDrmSession() throws DrmSessionCreatorException {
        DrmSessionCreator drmSessionCreator = drmSessionCreatorFactory.createFor(DrmType.NONE, IGNORED_KEY_REQUEST_EXECUTOR, null);

        assertThat(drmSessionCreator).isInstanceOf(NoDrmSessionCreator.class);
    }

    @Test
    public void givenDrmTypeWidevineClassic_whenCreatingDrmSessionCreator_thenReturnsNoDrmSession() throws DrmSessionCreatorException {
        DrmSessionCreator drmSessionCreator = drmSessionCreatorFactory.createFor(DrmType.WIDEVINE_CLASSIC, IGNORED_KEY_REQUEST_EXECUTOR, null);

        assertThat(drmSessionCreator).isInstanceOf(NoDrmSessionCreator.class);
    }

    @Test
    public void givenDrmTypeWidevineModularStream_whenCreatingDrmSessionCreator_thenReturnsStreaming() throws DrmSessionCreatorException {
        DrmSessionCreator drmSessionCreator = drmSessionCreatorFactory.createFor(DrmType.WIDEVINE_MODULAR_STREAM, keyRequestExecutor, null);

        assertThat(drmSessionCreator).isInstanceOf(ExoPlayerDrmSessionCreator.class);
    }

    @Test
    public void givenDrmTypeWidevineModularStream_andAndroidVersionDoesNotSupportMediaDrmApis_whenCreatingDrmSessionCreator_thenThrowsUnableToCreatePlayerException() throws DrmSessionCreatorException {
        drmSessionCreatorFactory = new DrmSessionCreatorFactory(UNSUPPORTED_MEDIA_DRM_DEVICE_VERSION, provisionExecutorCreator, handler);

        String message = "Device must be target: 18 but was: 17 for DRM type: WIDEVINE_MODULAR_STREAM";
        thrown.expect(ExceptionMatcher.matches(message, UnableToCreatePlayerException.class));

        drmSessionCreatorFactory.createFor(DrmType.WIDEVINE_MODULAR_STREAM, IGNORED_KEY_REQUEST_EXECUTOR, null);
    }

    @Test
    public void givenDrmTypeWidevineModularDownload_whenCreatingDrmSessionCreator_thenReturnsDownload() throws DrmSessionCreatorException {
        DrmSessionCreator drmSessionCreator = drmSessionCreatorFactory.createFor(DrmType.WIDEVINE_MODULAR_DOWNLOAD, keyRequestExecutor, null);

        assertThat(drmSessionCreator).isInstanceOf(ExoPlayerDrmSessionCreator.class);
    }

    @Test
    public void givenDrmTypeWidevineDownloadStream_andAndroidVersionDoesNotSupportMediaDrmApis_whenCreatingDrmSessionCreator_thenThrowsUnableToCreatePlayerException() throws DrmSessionCreatorException {
        drmSessionCreatorFactory = new DrmSessionCreatorFactory(UNSUPPORTED_MEDIA_DRM_DEVICE_VERSION, provisionExecutorCreator, handler);

        String message = "Device must be target: 18 but was: 17 for DRM type: WIDEVINE_MODULAR_DOWNLOAD";
        thrown.expect(ExceptionMatcher.matches(message, UnableToCreatePlayerException.class));

        drmSessionCreatorFactory.createFor(DrmType.WIDEVINE_MODULAR_DOWNLOAD, IGNORED_KEY_REQUEST_EXECUTOR, null);
    }
}
