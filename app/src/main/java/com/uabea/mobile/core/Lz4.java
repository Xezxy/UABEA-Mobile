package com.uabea.mobile.core;

/** Implementasi LZ4 block format murni Java (dekompresi + kompresi greedy). */
public final class Lz4 {
    private Lz4() {
    }

    public static byte[] decompress(byte[] src, int off, int len, int outSize) {
        byte[] dst = new byte[outSize];
        int sp = off;
        int end = off + len;
        int dp = 0;
        while (sp < end) {
            int token = src[sp++] & 0xFF;
            int literalLen = token >>> 4;
            if (literalLen == 15) {
                int s;
                do {
                    if (sp >= end) {
                        throw new IllegalArgumentException("LZ4 rusak (literal length)");
                    }
                    s = src[sp++] & 0xFF;
                    literalLen += s;
                } while (s == 255);
            }
            if (dp + literalLen > outSize || sp + literalLen > end) {
                throw new IllegalArgumentException("LZ4 rusak (literal melebihi buffer)");
            }
            System.arraycopy(src, sp, dst, dp, literalLen);
            sp += literalLen;
            dp += literalLen;
            if (sp >= end) {
                break;
            }
            if (sp + 2 > end) {
                throw new IllegalArgumentException("LZ4 rusak (offset)");
            }
            int offset = (src[sp] & 0xFF) | ((src[sp + 1] & 0xFF) << 8);
            sp += 2;
            if (offset <= 0 || offset > dp) {
                throw new IllegalArgumentException("LZ4 rusak (offset tidak valid)");
            }
            int matchLen = token & 0x0F;
            if (matchLen == 15) {
                int s;
                do {
                    if (sp >= end) {
                        throw new IllegalArgumentException("LZ4 rusak (match length)");
                    }
                    s = src[sp++] & 0xFF;
                    matchLen += s;
                } while (s == 255);
            }
            matchLen += 4;
            int mp = dp - offset;
            if (dp + matchLen > outSize) {
                throw new IllegalArgumentException("LZ4 rusak (match melebihi buffer)");
            }
            for (int i = 0; i < matchLen; i++) {
                dst[dp++] = dst[mp++];
            }
        }
        if (dp != outSize) {
            throw new IllegalArgumentException("LZ4: hasil " + dp + " byte, seharusnya " + outSize);
        }
        return dst;
    }

    private static int readInt(byte[] b, int p) {
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8) | ((b[p + 2] & 0xFF) << 16) | ((b[p + 3] & 0xFF) << 24);
    }

    /** Kompres blok LZ4. Mengembalikan null bila hasil kompresi tidak lebih kecil. */
    public static byte[] compress(byte[] src, int off, int len) {
        if (len <= 0) {
            return null;
        }
        Out out = new Out(len + len / 255 + 32);
        int[] table = new int[1 << 16];
        java.util.Arrays.fill(table, -1);
        int anchor = off;
        int sp = off;
        int end = off + len;
        while (sp + 12 <= end) {
            int seq = readInt(src, sp);
            int h = (int) ((seq * 0x9E3779B1L) >>> 16) & 0xFFFF;
            int ref = table[h];
            table[h] = sp;
            if (ref >= 0 && sp - ref <= 0xFFFF && readInt(src, ref) == seq) {
                int mlen = 4;
                // Format LZ4: 5 byte terakhir harus literal, jadi match tidak boleh
                // melewati end-5.
                while (sp + mlen < end - 5 && src[ref + mlen] == src[sp + mlen]) {
                    mlen++;
                }
                int litLen = sp - anchor;
                int token = (Math.min(litLen, 15) << 4) | Math.min(mlen - 4, 15);
                out.u8(token);
                if (litLen >= 15) {
                    writeLen(out, litLen - 15);
                }
                out.bytes(src, anchor, litLen);
                out.u16(sp - ref);
                if (mlen - 4 >= 15) {
                    writeLen(out, mlen - 4 - 15);
                }
                sp += mlen;
                anchor = sp;
            } else {
                sp++;
            }
        }
        int litLen = end - anchor;
        out.u8((Math.min(litLen, 15) << 4));
        if (litLen >= 15) {
            writeLen(out, litLen - 15);
        }
        out.bytes(src, anchor, litLen);
        byte[] result = out.toByteArray();
        if (result.length >= len) {
            return null;
        }
        return result;
    }

    private static void writeLen(Out out, int n) {
        while (n >= 255) {
            out.u8(255);
            n -= 255;
        }
        out.u8(n);
    }
}
