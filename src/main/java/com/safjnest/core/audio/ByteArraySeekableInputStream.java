package com.safjnest.core.audio;

import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ByteArraySeekableInputStream extends SeekableInputStream {
    private final byte[] data;
    private int pos = 0;

    public ByteArraySeekableInputStream(byte[] data) {
        // contentLength = data.length, maxSkipDistance = data.length (we can seek anywhere)
        super(data.length, data.length);
        this.data = data;
    }

    @Override
    public long getPosition() {
        return pos;
    }

    @Override
    protected void seekHard(long position) throws IOException {
        if (position < 0 || position > data.length) {
            throw new IOException("Seek position out of bounds: " + position);
        }
        pos = (int) position;
    }

    @Override
    public boolean canSeekHard() {
        return true;
    }

    @Override
    public int read() throws IOException {
        if (pos >= data.length) {
            return -1;
        }
        return data[pos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (pos >= data.length) {
            return -1;
        }
        int toRead = Math.min(len, data.length - pos);
        System.arraycopy(data, pos, b, off, toRead);
        pos += toRead;
        return toRead;
    }

    @Override
    public int available() throws IOException {
        return data.length - pos;
    }

    @Override
    public long skip(long n) throws IOException {
        long k = Math.min(n, data.length - pos);
        pos += k;
        return k;
    }

    @Override
    public List<AudioTrackInfoProvider> getTrackInfoProviders() {
        // no extra info providers for simple byte[] source
        return Collections.emptyList();
    }
}