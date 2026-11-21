package com.safjnest.bot.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Collection;

import com.safjnest.bot.audio.GuildMusicManager.SoundPlayback;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.format.transcoder.AudioChunkEncoder;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameProvider;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import net.dv8tion.jda.api.audio.AudioSendHandler;

/**
 * Copilot doenst know what this class does
 * 
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @since 5.0
 */
public class AudioForwarder implements AudioSendHandler {
    private static final int FRAME_BYTES;
    private static final int SAMPLES;

    static {
        FRAME_BYTES = StandardAudioDataFormats.DISCORD_PCM_S16_BE.maximumChunkSize();
        SAMPLES = FRAME_BYTES / Short.BYTES;
    }

    private final AudioFrameProvider music;
    private final Collection<SoundPlayback> effects;
    private final AudioConfiguration configuration;

    private final ByteBuffer pcmBytes;
    private final MutableAudioFrame sourceFrame;
    private final long[] samples;
    private final ShortBuffer encoderInput;
    private final ByteBuffer opus;

    private AudioChunkEncoder encoder;

    AudioForwarder(AudioFrameProvider music, Collection<SoundPlayback> effects, AudioConfiguration configuration) {
        this.music = music;
        this.effects = effects;
        this.configuration = configuration;

        this.pcmBytes = ByteBuffer.allocateDirect(FRAME_BYTES);
        this.sourceFrame = new MutableAudioFrame(pcmBytes);
        this.samples = new long[SAMPLES];

        // After mixing, reuse the source frame memory as native-order encoder input.
        this.encoderInput = pcmBytes.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer();
        this.opus = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize());
    }

    @Override
    public synchronized boolean canProvide() {
        ShortBuffer mixed = mixFrame();
        if (mixed == null)
            return false;

        if (encoder == null)
            encoder = StandardAudioDataFormats.DISCORD_OPUS.createEncoder(configuration);

        encoder.encode(mixed, opus);
        return true;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return opus;
    }

    @Override
    public boolean isOpus() {
        return true;
    }

    synchronized void closeEncoder() {
        if (encoder == null)
            return;

        encoder.close();
        encoder = null;
    }

    ShortBuffer mixFrame() {
        Arrays.fill(samples, 0);
        boolean available = mix(music);

        for (SoundPlayback effect : effects)
            available |= mix(effect.player());

        if (!available)
            return null;

        long highest = Short.MAX_VALUE;
        long lowest = Short.MIN_VALUE;
        for (long sample : samples) {
            highest = Math.max(highest, sample);
            lowest = Math.min(lowest, sample);
        }

        boolean needsScaling = highest > Short.MAX_VALUE || lowest < Short.MIN_VALUE;
        double gain = needsScaling ? Math.min((double) Short.MAX_VALUE / highest, (double) Short.MIN_VALUE / lowest) : 1;

        encoderInput.clear();
        for (long sample : samples)
            encoderInput.put((short) (needsScaling ? Math.round(sample * gain) : sample));

        return encoderInput.flip();
    }

    /* -------------------------------------------------------------------------- */

    private boolean mix(AudioFrameProvider source) {
        pcmBytes.clear();
        if (!source.provide(sourceFrame) || sourceFrame.getDataLength() != FRAME_BYTES)
            return false;

        pcmBytes.flip();
        for (int index = 0; index < SAMPLES; index++)
            samples[index] += pcmBytes.getShort();

        return true;
    }
}
