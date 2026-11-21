package com.safjnest.bot.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.safjnest.bot.audio.AudioEffects.Effect;
import com.safjnest.bot.audio.types.AudioType;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.format.transcoder.AudioChunkDecoder;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBuffer;

import net.dv8tion.jda.api.audio.AudioSendHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SoundOverlapTest {
    private static final AudioFormat PCM = AudioSendHandler.INPUT_FORMAT;
    private static final int SAMPLES_PER_CHANNEL = (int) PCM.getSampleRate() / 50;
    private static final int FRAME_BYTES = SAMPLES_PER_CHANNEL * PCM.getFrameSize();

    @Rule
    public final TemporaryFolder files = new TemporaryFolder();

    private final AtomicBoolean overlap = new AtomicBoolean();

    private DefaultAudioPlayerManager players;
    private GuildMusicManager guild;

    @Before
    public void setup() {
        players = new DefaultAudioPlayerManager();
        players.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);
        AudioSourceManagers.registerLocalSource(players);

        guild = new GuildMusicManager(players, overlap::get);
    }

    @After
    public void tearDown() {
        guild.resetPlayback();
        players.shutdown();
    }

    @Test
    public void sendsDecodableOpusBeforeAndAfterReconnecting() throws IOException, InterruptedException {
        File sound = wave(10000, -8000, 2);
        AudioSendHandler output = guild.getAudioForwarder();
        assertTrue(output.isOpus());

        for (int session = 0; session < 2; session++) {
            AudioTrack track = load(sound);
            guild.playSound(track);
            awaitBuffered(track, 2);

            assertTrue(output.canProvide());
            ByteBuffer packet = output.provide20MsAudio();
            assertTrue(packet.hasArray());
            byte[] encoded = new byte[packet.remaining()];
            packet.get(encoded);
            assertTrue(encoded.length > 0);

            AudioChunkDecoder decoder = StandardAudioDataFormats.DISCORD_OPUS.createDecoder();
            try {
                ShortBuffer decoded = ByteBuffer.allocateDirect(FRAME_BYTES).order(ByteOrder.nativeOrder()).asShortBuffer();
                decoder.decode(encoded, decoded);
                assertEquals(FRAME_BYTES / Short.BYTES, decoded.remaining());

                boolean audible = false;
                while (decoded.hasRemaining())
                    audible |= decoded.get() != 0;

                assertTrue(audible);
            } finally {
                decoder.close();
            }

            guild.resetPlayback();
            assertFalse(output.canProvide());
        }
    }

    @Test
    public void theFlagChangesOnlyWhichEffectsKeepPlaying() throws IOException, InterruptedException {
        File sound = wave(1000, -700, 4);
        UUID first = guild.playSound(load(sound));

        AudioTrack secondTrack = load(sound);
        UUID second = guild.playSound(secondTrack);
        assertFalse(guild.stopSound(first));
        awaitBuffered(secondTrack, 4);
        assertFrame(1000, -700);

        overlap.set(true);
        AudioTrack thirdTrack = load(sound);
        AudioTrack fourthTrack = load(sound);
        UUID third = guild.playSound(thirdTrack);
        UUID fourth = guild.playSound(fourthTrack);
        assertNotEquals(third, fourth);
        awaitBuffered(thirdTrack, 4);
        awaitBuffered(fourthTrack, 4);
        assertFrame(3000, -2100);

        assertTrue(guild.stopSound(third));
        assertFrame(2000, -1400);

        overlap.set(false);
        assertFrame(2000, -1400);
        AudioTrack replacement = load(sound);
        guild.playSound(replacement);
        assertFalse(guild.stopSound(second));
        assertFalse(guild.stopSound(fourth));
        awaitBuffered(replacement, 4);
        assertFrame(1000, -700);
    }

    @Test
    public void schedulerMixesTwoDifferentEffectsWhenOverlapIsEnabled() throws IOException, InterruptedException {
        overlap.set(true);
        TrackScheduler scheduler = guild.getTrackScheduler();
        AudioTrack sound = load(wave(1200, -800, 2));
        AudioTrack speech = load(wave(500, -300, 2));

        scheduler.play(sound, AudioType.SOUND);
        awaitBuffered(sound, 2);

        scheduler.play(speech, AudioType.SOUND);
        awaitBuffered(speech, 2);

        assertFrame(1700, -1100);

        scheduler.stop();
        assertFalse(guild.getAudioForwarder().canProvide());
    }

    @Test
    public void reversesTheWholeClipWhileKeepingTheStereoChannelsTogether() throws IOException, InterruptedException {
        ByteBuffer samples = ByteBuffer.allocate(2 * FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < SAMPLES_PER_CHANNEL; index++) {
            samples.putShort((short) 900);
            samples.putShort((short) -300);
        }

        for (int index = 0; index < SAMPLES_PER_CHANNEL; index++) {
            samples.putShort((short) 2100);
            samples.putShort((short) 600);
        }

        AudioTrack track = AudioEffects.apply(players, load(writeWave(samples.array())), Effect.REVERSE);
        guild.getTrackScheduler().play(track, AudioType.SOUND);
        awaitBuffered(track, 2);

        assertFrame(2100, 600);
        assertFrame(900, -300);
    }

    @Test
    public void echoPlaysItsTailAfterTheOriginalClip() throws IOException, InterruptedException {
        AudioTrack track = AudioEffects.apply(players, load(wave(12000, -8000, 1)), Effect.ECHO);
        guild.getTrackScheduler().play(track, AudioType.SOUND);
        awaitBuffered(track, 28);

        assertEquals(560, track.getDuration());
        assertFrame(7800, -5200);

        for (int index = 0; index < 8; index++)
            assertFrame(0, 0);

        assertFrame(2880, -1920);
    }

    @Test
    public void telephoneCentersTheVoiceAndCutsBassAndTreble() throws IOException, InterruptedException {
        int voice = telephonePeak(1_200);
        int bass = telephonePeak(120);
        int treble = telephonePeak(8_000);

        assertTrue("the voice band stays audible", voice > 5_000);
        assertTrue("bass is well below the voice band", bass < voice / 4);
        assertTrue("treble is well below the voice band", treble < voice / 3);
    }

    @Test
    public void reverbLeavesADenseStereoTailThatDecays() throws IOException, InterruptedException {
        AudioTrack reverb = AudioEffects.apply(players, load(wave(12000, 12000, 1)), Effect.REVERB);
        guild.getTrackScheduler().play(reverb, AudioType.SOUND);
        awaitBuffered(reverb, 56);
        assertEquals(1_120, reverb.getDuration());

        assertNotNull(guild.getAudioForwarder().mixFrame());

        int audibleBlocks = 0;
        int earlyPeak = 0;
        int latePeak = 0;
        boolean stereo = false;

        for (int block = 0; block < 55; block++) {
            ShortBuffer tail = guild.getAudioForwarder().mixFrame();
            assertNotNull(tail);

            int peak = 0;
            while (tail.hasRemaining()) {
                int left = tail.get();
                int right = tail.get();
                peak = Math.max(peak, Math.max(Math.abs(left), Math.abs(right)));
                stereo |= left != right;
            }

            if (block < 15) {
                earlyPeak = Math.max(earlyPeak, peak);
                if (peak > 20)
                    audibleBlocks++;
            }

            if (block >= 40)
                latePeak = Math.max(latePeak, peak);
        }

        assertTrue("the start of the tail has sound in consecutive blocks", audibleBlocks >= 14);
        assertTrue("the room tail becomes quieter", latePeak < earlyPeak / 10);
        assertTrue("the room spreads the signal across both channels", stereo);
    }

    @Test
    public void selectingAndClearingEffectsOnlyChangesTheCurrentGuild() throws IOException {
        GuildMusicManager otherGuild = new GuildMusicManager(players, () -> false);
        assertEquals(Effect.NONE, guild.getSoundEffect());

        guild.setSoundEffect(Effect.REVERSE);
        assertEquals(Effect.REVERSE, guild.getSoundEffect());
        assertEquals(Effect.NONE, otherGuild.getSoundEffect());

        guild.setSoundEffect(Effect.NONE);
        AudioTrack original = load(wave(900, -300, 1));
        assertSame(original, AudioEffects.apply(players, original, guild.getSoundEffect()));
    }

    @Test
    public void twentyFiveEffectsMixWithoutClippingAndReleaseOnCompletion() throws IOException, InterruptedException {
        overlap.set(true);
        File sound = wave(2500, -1000, 2);
        List<AudioTrack> tracks = new ArrayList<>();
        List<UUID> played = new ArrayList<>();

        for (int i = 0; i < 25; i++) {
            AudioTrack track = load(sound);
            tracks.add(track);
            played.add(guild.playSound(track));
        }

        for (AudioTrack track : tracks)
            awaitBuffered(track, 2);

        assertEquals(25, new HashSet<>(played).size());
        assertFrame(Short.MAX_VALUE, -13107);
        assertFrame(Short.MAX_VALUE, -13107);

        for (AudioTrack track : tracks)
            pumpUntil(() -> track.getState() == AudioTrackState.INACTIVE);

        assertFalse(guild.getAudioForwarder().canProvide());
        for (UUID id : played)
            assertFalse(guild.stopSound(id));
    }

    @Test
    public void musicAdvancesItsQueueWhileEffectsArePlaying() throws IOException, InterruptedException {
        AudioTrack first = load(wave(400, -300, 1));
        AudioTrack next = load(wave(900, -600, 4));
        first.setUserData(new TrackData(AudioType.AUDIO));
        next.setUserData(new TrackData(AudioType.AUDIO));

        TrackScheduler queue = guild.getTrackScheduler();
        queue.queue(first);
        queue.queue(next);
        queue.play();

        AudioTrack playing = queue.getPlayer().getPlayingTrack();
        awaitBuffered(playing, 1);

        AudioTrack effect = load(wave(1000, -1000, 50));
        queue.play(effect, AudioType.SOUND);
        awaitBuffered(effect, 50);
        assertFrame(1400, -1300);

        pumpUntil(() -> queue.getIndex() == 1);
        AudioTrack nextPlaying = queue.getPlayer().getPlayingTrack();
        assertEquals(next.getIdentifier(), nextPlaying.getIdentifier());
        awaitBuffered(nextPlaying, 1);
        assertFrame(1900, -1600);

        overlap.set(true);
        AudioTrack replay = effect.makeClone();
        queue.play(replay, AudioType.SOUND);
        awaitBuffered(replay, 1);
        assertFrame(2900, -2600);

        queue.stop();
        assertFalse(queue.isPlaying());
        assertEquals(-1, queue.getIndex());
        assertEquals(2, queue.getQueue().size());
        assertFalse(guild.getAudioForwarder().canProvide());
    }

    /* -------------------------------------------------------------------------- */

    private AudioTrack load(File sound) {
        AudioTrack track = (AudioTrack) players.loadItemSync(sound.getAbsolutePath());
        assertNotNull(track);

        return track;
    }

    private int telephonePeak(int frequency) throws IOException, InterruptedException {
        int blocks = 6;
        ByteBuffer pcm = ByteBuffer.allocate(blocks * FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < blocks * SAMPLES_PER_CHANNEL; index++) {
            double phase = 2 * Math.PI * frequency * index / PCM.getSampleRate();
            short sample = (short) Math.round(12_000 * Math.sin(phase));
            pcm.putShort(sample);
            pcm.putShort((short) (sample / 2));
        }

        AudioTrack telephone = AudioEffects.apply(players, load(writeWave(pcm.array())), Effect.TELEPHONE);
        guild.getTrackScheduler().play(telephone, AudioType.SOUND);
        awaitBuffered(telephone, blocks);

        for (int index = 0; index < blocks - 1; index++)
            assertNotNull(guild.getAudioForwarder().mixFrame());

        ShortBuffer filtered = guild.getAudioForwarder().mixFrame();
        assertNotNull(filtered);

        int peak = 0;
        while (filtered.hasRemaining()) {
            int left = filtered.get();
            assertEquals(left, filtered.get());
            peak = Math.max(peak, Math.abs(left));
        }

        return peak;
    }

    private File wave(int left, int right, int frames) throws IOException {
        ByteBuffer samples = ByteBuffer.allocate(frames * FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        while (samples.hasRemaining()) {
            samples.putShort((short) left);
            samples.putShort((short) right);
        }

        return writeWave(samples.array());
    }

    private File writeWave(byte[] pcm) throws IOException {
        AudioFormat wav = new AudioFormat(PCM.getSampleRate(), PCM.getSampleSizeInBits(), PCM.getChannels(), true, false);
        File file = files.newFile(UUID.randomUUID() + ".wav");
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), wav, pcm.length / wav.getFrameSize())) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
        }

        return file;
    }

    private void assertFrame(int left, int right) {
        ShortBuffer pcm = guild.getAudioForwarder().mixFrame();
        assertNotNull(pcm);
        assertEquals(FRAME_BYTES / Short.BYTES, pcm.remaining());
        while (pcm.hasRemaining()) {
            assertEquals(left, pcm.get());
            assertEquals(right, pcm.get());
        }
    }

    private void awaitBuffered(AudioTrack track, int count) throws InterruptedException {
        AudioFrameBuffer buffer = ((InternalAudioTrack) track).getActiveExecutor().getAudioBuffer();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (buffer.getFullCapacity() - buffer.getRemainingCapacity() < count && System.nanoTime() < deadline)
            Thread.sleep(5);

        assertTrue("WAV frames reached the playback buffer", buffer.getFullCapacity() - buffer.getRemainingCapacity() >= count);
    }

    private void pumpUntil(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            guild.getAudioForwarder().canProvide();
            Thread.sleep(5);
        }

        assertTrue(done.getAsBoolean());
    }
}
