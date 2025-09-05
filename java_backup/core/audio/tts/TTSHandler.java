package com.safjnest.core.audio.tts;

import org.voicerss.tts.Audio.AudioCodec;
import org.voicerss.tts.Audio.AudioFormat;
import org.voicerss.tts.Voice.VoiceParameters;
import org.voicerss.tts.Voice.VoiceProvider;

import com.safjnest.util.SettingsLoader;

/**
 * Class that provides to generate {@code .mp3} files using TTS
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 */
public class TTSHandler {
    /**
     * The TTS provider
     * @see <a href="https://www.voicerss.org/sdk/java.aspx"> Voice RSS</a>
     */
    private static VoiceProvider tts;

    static {
        tts = new VoiceProvider(SettingsLoader.getSettings().getJsonSettings().getTtsApiKey());
    }

    /**
     * Generates a {@code .mp3} gived a speech, the language and voice name
     * @param speech {@code String} with the text to be read
     * @param voiceName {@code String} voice name
     * @param language {@code String} languages of the voice
     */
    public static byte[] makeSpeechBytes(String speech, String voiceName, String language) {
        VoiceParameters params = new VoiceParameters(speech, language);
        params.setCodec(AudioCodec.MP3);
        params.setVoice(voiceName);
        params.setFormat(AudioFormat.Format_44KHZ.AF_44khz_16bit_stereo);
        params.setBase64(false);
        params.setSSML(false);
        params.setRate(0);
        try {
            return tts.speech(params);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
