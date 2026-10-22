package com.uabea.mobile.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.tukaani.xz.LZMAInputStream;

/** Dekompresi blok LZMA khas UnityFS (5 byte properti + stream LZMA1 mentah tanpa EOS). */
public final class Lzma {
    private Lzma() {
    }

    public static byte[] decompress(byte[] src, int off, int len, int outSize) throws IOException {
        if (len < 5) {
            throw new IOException("Blok LZMA terlalu pendek");
        }
        byte props = src[off];
        int dictSize = (src[off + 1] & 0xFF) | ((src[off + 2] & 0xFF) << 8) | ((src[off + 3] & 0xFF) << 16)
                | ((src[off + 4] & 0xFF) << 24);
        // Unity tidak menulis penanda akhir stream, jadi ukuran -1 (baca sampai EOF).
        try (InputStream raw = new ByteArrayInputStream(src, off + 5, len - 5);
             LZMAInputStream in = new LZMAInputStream(raw, -1, props, dictSize)) {
            byte[] out = new byte[outSize];
            int read = 0;
            while (read < outSize) {
                int n = in.read(out, read, outSize - read);
                if (n <= 0) {
                    break;
                }
                read += n;
            }
            if (read != outSize && in.read() >= 0) {
                throw new IOException("LZMA: hasil lebih besar dari perkiraan (" + outSize + ")");
            }
            if (read != outSize) {
                throw new IOException("LZMA: hasil " + read + " byte, seharusnya " + outSize);
            }
            return out;
        }
    }
}
