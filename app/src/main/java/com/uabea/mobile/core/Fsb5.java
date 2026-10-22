package com.uabea.mobile.core;

import java.util.ArrayList;

/** Parser FSB5 (format audio FMOD yang dipakai AudioClip Unity) → WAV/MP3/OGG. */
public final class Fsb5 {
    public static final class Result {
        public byte[] data;
        public String extension;
        public String note = "";
    }

    private static final int[] FREQUENCIES = {0, 8000, 11000, 11025, 16000, 22050, 24000, 32000, 44100, 48000};

    private Fsb5() {
    }

    public static boolean isFsb5(byte[] d) {
        return d.length > 4 && d[0] == 'F' && d[1] == 'S' && d[2] == 'B' && d[3] == '5';
    }

    public static Result extract(byte[] d) {
        Result r = new Result();
        Buf b = new Buf(d);
        b.skip(4);
        int version = b.i32();
        int numSamples = b.i32();
        int sampleHeadersSize = b.i32();
        int nameTableSize = b.i32();
        int dataSize = b.i32();
        int mode = b.i32();
        b.skip(8 + 16 + 8);
        if (version == 0) {
            b.skip(4);
        }
        if (numSamples <= 0) {
            throw new IllegalArgumentException("FSB5 tidak berisi sampel audio");
        }
        int headerSize = b.p;
        ArrayList<long[]> headers = new ArrayList<>();
        int[] freqOverride = new int[numSamples];
        int[] channels = new int[numSamples];
        for (int i = 0; i < numSamples; i++) {
            long raw = b.i64();
            int nextChunk = (int) (raw & 1);
            int freqIdx = (int) ((raw >>> 1) & 0xF);
            channels[i] = (int) ((raw >>> 5) & 1) + 1;
            long dataOffset = ((raw >>> 6) & 0xFFFFFFFL) * 16;
            int samples = (int) ((raw >>> 34) & 0x3FFFFFFFL);
            headers.add(new long[]{dataOffset, samples, freqIdx});
            while (nextChunk != 0) {
                int chunk = b.i32();
                nextChunk = chunk & 1;
                int chunkSize = (chunk >>> 1) & 0xFFFFFF;
                int chunkType = (chunk >>> 25) & 0x7F;
                if (chunkType == 2 && chunkSize >= 4) {
                    freqOverride[i] = b.i32();
                    if (chunkSize > 4) {
                        b.skip(chunkSize - 4);
                    }
                } else {
                    b.skip(chunkSize);
                }
            }
        }
        int dataStart = headerSize + sampleHeadersSize + nameTableSize;

        long start = headers.get(0)[0];
        long end = headers.size() > 1 ? headers.get(1)[0] : dataSize;
        int len = (int) (end - start);
        if (len <= 0 || dataStart + start + len > d.length) {
            throw new IllegalArgumentException("Data sampel FSB5 tidak valid");
        }
        byte[] sample = new byte[len];
        System.arraycopy(d, dataStart + (int) start, sample, 0, len);
        int sampleCount = (int) headers.get(0)[1];
        int freq = freqOverride[0] > 0 ? freqOverride[0]
                : (headers.get(0)[2] >= 0 && headers.get(0)[2] < FREQUENCIES.length
                ? FREQUENCIES[(int) headers.get(0)[2]] : 44100);
        if (freq <= 0) {
            freq = 44100;
        }
        if (numSamples > 1) {
            r.note = "Berisi " + numSamples + " sub-sound; hanya sub-sound pertama yang diekspor.";
        }

        if (sample.length > 4 && sample[0] == 'O' && sample[1] == 'g' && sample[2] == 'g' && sample[3] == 'S') {
            r.data = sample;
            r.extension = "ogg";
            return r;
        }
        if (sample.length > 12 && sample[0] == 'R' && sample[1] == 'I' && sample[2] == 'F' && sample[3] == 'F') {
            r.data = sample;
            r.extension = "wav";
            return r;
        }
        switch (mode) {
            case 1: // PCM8
            case 2: // PCM16
            case 4: // PCM32
                r.data = wav(sample, mode == 1 ? 8 : (mode == 2 ? 16 : 32), channels[0], freq, sampleCount);
                r.extension = "wav";
                return r;
            case 11: // MPEG
                r.data = sample;
                r.extension = "mp3";
                return r;
            case 15: // Vorbis (butuh container Ogg, belum didukung)
                r.data = sample;
                r.extension = "fsb";
                r.note = "Audio Vorbis di dalam FSB5 belum bisa dibungkus otomatis.";
                return r;
            default:
                r.data = sample;
                r.extension = "bin";
                r.note = "Mode FSB5 " + mode + " belum didukung.";
                return r;
        }
    }

    private static byte[] wav(byte[] pcm, int bits, int channels, int freq, int sampleCount) {
        int dataLen = pcm.length;
        Out o = new Out(dataLen + 44);
        int byteRate = freq * channels * bits / 8;
        int blockAlign = channels * bits / 8;
        o.bytes(new byte[]{'R', 'I', 'F', 'F'});
        o.u32(36 + dataLen);
        o.bytes(new byte[]{'W', 'A', 'V', 'E'});
        o.bytes(new byte[]{'f', 'm', 't', ' '});
        o.u32(16);
        o.u16(1); // PCM
        o.u16(channels);
        o.u32(freq);
        o.u32(byteRate);
        o.u16(blockAlign);
        o.u16(bits);
        o.bytes(new byte[]{'d', 'a', 't', 'a'});
        o.u32(dataLen);
        o.bytes(pcm);
        return o.toByteArray();
    }
}
