package com.safjnest.bot.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.sedmelluq.discord.lavaplayer.container.wav.WavAudioTrack;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

public final class AudioEffects {
    public enum Effect {
        NONE,
        REVERSE,
        TELEPHONE,
        ECHO,
        REVERB
    }

    private static final AudioDataFormat PCM = StandardAudioDataFormats.DISCORD_PCM_S16_BE;
    private static final AudioFormat WAV_INPUT = new AudioFormat(PCM.sampleRate, 16, PCM.channelCount, true, true);
    private static final int MAX_CLIP_SECONDS = 60;
    private static final int MAX_CLIP_BYTES = MAX_CLIP_SECONDS * PCM.sampleRate * WAV_INPUT.getFrameSize();
    private static final int FRAME_TIMEOUT_SECONDS = 5;

    private static final List<Reflection> ECHO = List.of(
        new Reflection(180, 0.24),
        new Reflection(360, 0.08),
        new Reflection(540, 0.03)
    );

    private static final int[] ROOM_REFLECTION_SAMPLES = {1423, 1789, 1973, 2341};
    private static final int[] ROOM_DIFFUSION_SAMPLES = {211, 337, 521};
    private static final int ROOM_STEREO_SPREAD = 47;
    private static final double ROOM_DECAY_SECONDS = 0.95;
    private static final int ROOM_TAIL_FRAMES = PCM.sampleRate * 11 / 10;
    private static final int ROOM_FADE_FRAMES = PCM.sampleRate / 10;
    private static final double ROOM_BASS_CUT = filterCoefficient(150);

    private AudioEffects() {}

    static AudioTrack apply(AudioPlayerManager manager, AudioTrack track, Effect effect) {
        if (effect == Effect.NONE)
            return track;

        if (track.getInfo().isStream)
            throw new FriendlyException("Sound effects require a complete audio clip.", FriendlyException.Severity.COMMON, null);

        try {
            byte[] input = decode(manager, track);
            byte[] output = switch (effect) {
                case REVERSE -> reverse(input);
                case TELEPHONE -> telephone(input);
                case ECHO -> reflect(input, ECHO, 0.65);
                case REVERB -> reverb(input);
                case NONE -> input;
            };

            return toTrack(track.getInfo(), output);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FriendlyException("Sound effect preparation was interrupted.", FriendlyException.Severity.COMMON, exception);
        } catch (IOException | TimeoutException exception) {
            throw new FriendlyException("Could not prepare the sound effect.", FriendlyException.Severity.COMMON, exception);
        }
    }

    /* -------------------------------------------------------------------------- */

    private static byte[] decode(AudioPlayerManager manager, AudioTrack track)
            throws IOException, InterruptedException, TimeoutException {
        byte[] chunk = new byte[PCM.maximumChunkSize()];
        MutableAudioFrame frame = new MutableAudioFrame(ByteBuffer.wrap(chunk));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        AtomicReference<FriendlyException> failure = new AtomicReference<>();
        AudioPlayer decoder = manager.createPlayer();
        decoder.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackException(AudioPlayer player, AudioTrack failedTrack, FriendlyException exception) {
                failure.set(exception);
            }
        });

        try {
            decoder.playTrack(track);
            while (decoder.provide(frame, FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                int length = frame.getDataLength();
                if (output.size() + length > MAX_CLIP_BYTES)
                    throw new FriendlyException("Sound effects support clips up to " + MAX_CLIP_SECONDS + " seconds.",
                        FriendlyException.Severity.COMMON, null);

                output.write(chunk, 0, length);
            }

            FriendlyException decodingFailure = failure.get();
            if (decodingFailure != null)
                throw decodingFailure;

            if (output.size() == 0)
                throw new IOException("The audio did not produce any PCM samples.");

            return output.toByteArray();
        } finally {
            decoder.destroy();
        }
    }

    private static byte[] reverse(byte[] input) {
        ByteBuffer frames = ByteBuffer.wrap(input);
        int frameSize = WAV_INPUT.getFrameSize();

        for (int left = 0, right = frames.capacity() - frameSize; left < right; left += frameSize, right -= frameSize) {
            int frame = frames.getInt(left);
            frames.putInt(left, frames.getInt(right));
            frames.putInt(right, frame);
        }

        return input;
    }

    private static byte[] telephone(byte[] input) {
        ShortBuffer samples = ByteBuffer.wrap(input).asShortBuffer();
        double bassCut = filterCoefficient(520);
        double trebleCut = filterCoefficient(2_600);
        double bass = 0;
        double residualBass = 0;
        double voice = 0;
        double filtered = 0;

        for (int index = 0; index < samples.limit(); index += PCM.channelCount) {
            double mono = (samples.get(index) + samples.get(index + 1)) * 0.5;

            bass += bassCut * (mono - bass);
            double highPassed = mono - bass;
            residualBass += bassCut * (highPassed - residualBass);

            voice += trebleCut * (highPassed - residualBass - voice);
            filtered += trebleCut * (voice - filtered);

            double driven = filtered * 3;
            short sample = toSample(driven / (1 + Math.abs(driven) / 20_000));
            samples.put(index, sample);
            samples.put(index + 1, sample);
        }

        return input;
    }

    private static byte[] reverb(byte[] input) {
        ShortBuffer samples = ByteBuffer.wrap(input).asShortBuffer();
        int sourceFrames = samples.limit() / PCM.channelCount;
        int totalFrames = sourceFrames + ROOM_TAIL_FRAMES;
        byte[] output = new byte[totalFrames * WAV_INPUT.getFrameSize()];
        ShortBuffer mixed = ByteBuffer.wrap(output).asShortBuffer();

        RoomReverb left = new RoomReverb(0);
        RoomReverb right = new RoomReverb(ROOM_STEREO_SPREAD);

        for (int frame = 0; frame < totalFrames; frame++) {
            int index = frame * PCM.channelCount;
            double dryLeft = frame < sourceFrames ? samples.get(index) : 0;
            double dryRight = frame < sourceFrames ? samples.get(index + 1) : 0;
            double wetGain = 0.9 * Math.min(1, (totalFrames - frame - 1) / (double) ROOM_FADE_FRAMES);

            mixed.put(toSample(0.72 * dryLeft + wetGain * left.process(dryLeft)));
            mixed.put(toSample(0.72 * dryRight + wetGain * right.process(dryRight)));
        }

        return output;
    }

    private static byte[] reflect(byte[] input, List<Reflection> reflections, double dryGain) {
        ShortBuffer samples = ByteBuffer.wrap(input).asShortBuffer();
        int tail = 0;
        for (Reflection reflection : reflections)
            tail = Math.max(tail, reflection.sampleOffset());

        byte[] output = new byte[input.length + tail * Short.BYTES];
        ShortBuffer mixed = ByteBuffer.wrap(output).asShortBuffer();

        for (int index = 0; index < mixed.limit(); index++) {
            double value = index < samples.limit() ? samples.get(index) * dryGain : 0;

            for (Reflection reflection : reflections) {
                int delayed = index - reflection.sampleOffset();
                if (delayed >= 0 && delayed < samples.limit())
                    value += samples.get(delayed) * reflection.gain();
            }

            mixed.put(index, toSample(value));
        }

        return output;
    }

    private static AudioTrack toTrack(AudioTrackInfo original, byte[] pcm) throws IOException {
        int frames = pcm.length / WAV_INPUT.getFrameSize();
        ByteArrayOutputStream wav = new ByteArrayOutputStream(pcm.length + 44);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), WAV_INPUT, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, wav);
        }

        long duration = frames * 1000L / PCM.sampleRate;
        AudioTrackInfo info = new AudioTrackInfo(original.title, original.author, duration,
            original.identifier, false, original.uri, original.artworkUrl, original.isrc);

        return new WavAudioTrack(info, new ByteArraySeekableInputStream(wav.toByteArray()));
    }

    private static double filterCoefficient(int frequency) {
        return 1 - Math.exp(-2 * Math.PI * frequency / PCM.sampleRate);
    }

    private static short toSample(double value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value)));
    }

    private record Reflection(int milliseconds, double gain) {
        private int sampleOffset() {
            return milliseconds * PCM.sampleRate / 1000 * PCM.channelCount;
        }
    }

    private static final class RoomReverb {
        private final DelayLine[] reflections;
        private final DelayLine[] diffusion;

        private double bass;

        private RoomReverb(int spread) {
            this.reflections = new DelayLine[ROOM_REFLECTION_SAMPLES.length];
            this.diffusion = new DelayLine[ROOM_DIFFUSION_SAMPLES.length];

            for (int index = 0; index < reflections.length; index++) {
                int delay = ROOM_REFLECTION_SAMPLES[index] + spread;
                double feedback = Math.pow(0.001, delay / (PCM.sampleRate * ROOM_DECAY_SECONDS));
                reflections[index] = new DelayLine(delay, feedback);
            }

            for (int index = 0; index < diffusion.length; index++)
                diffusion[index] = new DelayLine(ROOM_DIFFUSION_SAMPLES[index] + spread, 0.55);
        }

        private double process(double input) {
            bass += ROOM_BASS_CUT * (input - bass);
            double signal = input - bass;
            double wet = 0;

            for (DelayLine line : reflections)
                wet += line.reflect(signal);

            wet /= reflections.length;
            for (DelayLine line : diffusion)
                wet = line.diffuse(wet);

            return wet;
        }
    }

    private static final class DelayLine {
        private final double[] history;
        private final double feedback;

        private int cursor;
        private double damped;

        private DelayLine(int samples, double feedback) {
            this.history = new double[samples];
            this.feedback = feedback;
        }

        private double reflect(double input) {
            double delayed = history[cursor];
            damped += 0.4 * (delayed - damped);
            advance(input + feedback * damped);

            return delayed;
        }

        private double diffuse(double input) {
            double result = history[cursor] - feedback * input;
            advance(input + feedback * result);

            return result;
        }

        private void advance(double sample) {
            history[cursor] = sample;
            cursor++;

            if (cursor == history.length)
                cursor = 0;
        }
    }
}
