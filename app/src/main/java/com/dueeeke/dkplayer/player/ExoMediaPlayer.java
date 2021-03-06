package com.dueeeke.dkplayer.player;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.dueeeke.dkplayer.util.Repeater;
import com.dueeeke.videoplayer.player.AbstractPlayer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import tv.danmaku.ijk.media.player.IMediaPlayer;

public class ExoMediaPlayer extends AbstractPlayer implements VideoRendererEventListener {

    private static final int BUFFER_REPEAT_DELAY = 1_000;
    private Context mAppContext;
    private SimpleExoPlayer mInternalPlayer;
    private MediaSource mMediaSource;
    private String mDataSource;
    private Surface mSurface;
    private PlaybackParameters mSpeedPlaybackParameters;
    private int lastReportedPlaybackState;
    private boolean lastReportedPlayWhenReady;
    private boolean mIsPrepareing = true;
    private boolean mIsBuffering = false;
    private DataSource.Factory mediaDataSourceFactory;
    @NonNull
    private Repeater bufferRepeater = new Repeater();

    public ExoMediaPlayer(Context context) {
        mAppContext = context.getApplicationContext();
        lastReportedPlaybackState = Player.STATE_IDLE;
        bufferRepeater.setRepeaterDelay(BUFFER_REPEAT_DELAY);
        bufferRepeater.setRepeatListener(new BufferRepeatListener());
        mediaDataSourceFactory = getDataSourceFactory(true);
    }

    @Override
    public void initPlayer() {
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        mInternalPlayer = ExoPlayerFactory.newSimpleInstance(mAppContext, trackSelector);
        mInternalPlayer.addListener(mDefaultEventListener);
        mInternalPlayer.addVideoDebugListener(this);
    }

    @Override
    public void setDataSource(String path) {
        mDataSource = path;
        mMediaSource = getMediaSource();
    }

    private MediaSource getMediaSource() {
        Uri contentUri = Uri.parse(mDataSource);
        int contentType = Util.inferContentType(mDataSource);
        switch (contentType) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(
                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory),
                        getDataSourceFactory(false))
                        .createMediaSource(contentUri);
            case C.TYPE_SS:
                return new SsMediaSource.Factory(
                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory),
                        getDataSourceFactory(false))
                        .createMediaSource(contentUri);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(mediaDataSourceFactory)
                        .createMediaSource(contentUri);
//            case TYPE_RTMP:
//                RtmpDataSourceFactory rtmpDataSourceFactory = new RtmpDataSourceFactory(null);
//                return new ExtractorMediaSource.Factory(rtmpDataSourceFactory)
//                        .createMediaSource(contentUri, mainHandler, null);
            default:
            case C.TYPE_OTHER:
                return new ExtractorMediaSource.Factory(mediaDataSourceFactory)
                        .createMediaSource(contentUri);
        }
    }


    private DataSource.Factory getHttpDataSourceFactory(boolean useBandwidthMeter) {
        return new DefaultHttpDataSourceFactory(Util.getUserAgent(mAppContext,
                mAppContext.getApplicationInfo().name), useBandwidthMeter ? null : new DefaultBandwidthMeter());
    }

    private DataSource.Factory getDataSourceFactory(boolean useBandwidthMeter) {
        return new DefaultDataSourceFactory(mAppContext, useBandwidthMeter ? null : new DefaultBandwidthMeter(),
                getHttpDataSourceFactory(useBandwidthMeter));
    }

    @Override
    public void start() {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.setPlayWhenReady(false);
    }

    @Override
    public void stop() {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.stop();
    }

    @Override
    public void prepareAsync() {
        if (mSpeedPlaybackParameters != null) {
            mInternalPlayer.setPlaybackParameters(mSpeedPlaybackParameters);
        }
        if (mSurface != null)
            mInternalPlayer.setVideoSurface(mSurface);

        mInternalPlayer.prepare(mMediaSource);
        mInternalPlayer.setPlayWhenReady(true);
    }

    @Override
    public void reset() {
        release();
        initPlayer();
    }

    @Override
    public boolean isPlaying() {
        if (mInternalPlayer == null)
            return false;
        int state = mInternalPlayer.getPlaybackState();
        switch (state) {
            case Player.STATE_BUFFERING:
            case Player.STATE_READY:
                return mInternalPlayer.getPlayWhenReady();
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
            default:
                return false;
        }
    }

    @Override
    public void seekTo(long time) {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.seekTo(time);
    }

    @Override
    public void release() {
        if (mInternalPlayer != null) {
            mInternalPlayer.release();
            mInternalPlayer.removeListener(mDefaultEventListener);
            mInternalPlayer.removeVideoDebugListener(this);
            mInternalPlayer = null;
        }

        mSurface = null;
        mDataSource = null;
        mIsPrepareing = true;
        mIsBuffering = false;
        setBufferRepeaterStarted(false);
    }

    @Override
    public long getCurrentPosition() {
        if (mInternalPlayer == null)
            return 0;
        return mInternalPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        if (mInternalPlayer == null)
            return 0;
        return mInternalPlayer.getDuration();
    }

    @Override
    public void setSurface(Surface surface) {
        mSurface = surface;
        if (mInternalPlayer != null) {
            mInternalPlayer.setVideoSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null)
            setSurface(null);
        else
            setSurface(holder.getSurface());
    }

    @Override
    public void setVolume(int leftVolume, int rightVolume) {
        if (mInternalPlayer != null)
            mInternalPlayer.setVolume((leftVolume + rightVolume) / 2);
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mInternalPlayer != null)
            mInternalPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
    }

    @Override
    public void setEnableMediaCodec(boolean isEnable) {
        // no support
    }

    @Override
    public void setOptions() {
        // no support
    }

    @Override
    public void setSpeed(float speed) {
        PlaybackParameters playbackParameters = new PlaybackParameters(speed, speed);
        mSpeedPlaybackParameters = playbackParameters;
        if (mInternalPlayer != null) {
            mInternalPlayer.setPlaybackParameters(playbackParameters);
        }
    }

    @Override
    public long getTcpSpeed() {
        // no support
        return 0;
    }

    private void setBufferRepeaterStarted(boolean start) {
        if (start && mPlayerEventListener != null) {
            bufferRepeater.start();
        } else {
            bufferRepeater.stop();
        }
    }

    private class BufferRepeatListener implements Repeater.RepeatListener {
        @Override
        public void onRepeat() {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onBufferingUpdate(getBufferedPercentage());
            }
        }
    }

    private int getBufferedPercentage() {
        return mInternalPlayer == null ? 0 : mInternalPlayer.getBufferedPercentage();
    }

    private Player.DefaultEventListener mDefaultEventListener = new Player.DefaultEventListener() {
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            super.onPlayerStateChanged(playWhenReady, playbackState);
            //重新播放状态顺序为：STATE_IDLE -》STATE_BUFFERING -》STATE_READY
            //缓冲时顺序为：STATE_BUFFERING -》STATE_READY
//        L.e("onPlayerStateChanged: playWhenReady = " + playWhenReady + ", playbackState = " + playbackState);
            if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
                if (mIsBuffering) {
                    switch (playbackState) {
                        case Player.STATE_ENDED:
                        case Player.STATE_READY:
                            if (mPlayerEventListener != null) {
                                mPlayerEventListener.onInfo(IMediaPlayer.MEDIA_INFO_BUFFERING_END, mInternalPlayer.getBufferedPercentage());
                            }
                            mIsBuffering = false;
                            break;
                    }
                }

                if (mIsPrepareing) {
                    switch (playbackState) {
                        case Player.STATE_READY:
                            if (mPlayerEventListener != null) {
                                mPlayerEventListener.onPrepared();
                                setBufferRepeaterStarted(true);
                            }
                            mIsPrepareing = false;
                            break;
                    }
                }

                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onInfo(IMediaPlayer.MEDIA_INFO_BUFFERING_START, mInternalPlayer.getBufferedPercentage());
                        }
                        mIsBuffering = true;
                        break;
                    case Player.STATE_READY:
                        break;
                    case Player.STATE_ENDED:
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onCompletion();
                        }
                        break;
                    default:
                        break;
                }
            }
            lastReportedPlayWhenReady = playWhenReady;
            lastReportedPlaybackState = playbackState;
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            super.onPlayerError(error);
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onError();
            }
        }
    };

    //------------------ Start VideoRendererEventListener ---------------------//
    @Override
    public void onVideoEnabled(DecoderCounters counters) {
    }

    @Override
    public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
    }

    @Override
    public void onVideoInputFormatChanged(Format format) {
    }

    @Override
    public void onDroppedFrames(int count, long elapsedMs) {
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onVideoSizeChanged(width, height);
            if (unappliedRotationDegrees > 0) {
                mPlayerEventListener.onInfo(IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED, unappliedRotationDegrees);
            }
        }
    }

    @Override
    public void onRenderedFirstFrame(Surface surface) {
        if (mPlayerEventListener != null && mIsPrepareing)
            mPlayerEventListener.onInfo(IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START, 0);
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
    }
    //------------------ End VideoRendererEventListener ---------------------//
}
