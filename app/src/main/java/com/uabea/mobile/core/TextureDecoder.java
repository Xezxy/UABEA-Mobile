package com.uabea.mobile.core;

/** Dekoder tekstur Unity ke pixel ARGB (int[]), tanpa dependensi Android. */
public final class TextureDecoder {
    private TextureDecoder() {
    }

    public static String formatName(int fmt) {
        switch (fmt) {
            case 1: return "Alpha8";
            case 2: return "ARGB4444";
            case 3: return "RGB24";
            case 4: return "RGBA32";
            case 5: return "ARGB32";
            case 7: return "RGB565";
            case 9: return "R16";
            case 10: return "DXT1";
            case 11: return "DXT3";
            case 12: return "DXT5";
            case 13: return "RGBA4444";
            case 14: return "BGRA32";
            case 24: return "BC6H";
            case 25: return "BC7";
            case 26: return "BC4";
            case 27: return "BC5";
            case 28: return "DXT1 Crunched";
            case 29: return "DXT5 Crunched";
            case 34: return "ETC_RGB4";
            case 45: return "ETC2_RGB";
            case 46: return "ETC2_RGBA1";
            case 47: return "ETC2_RGBA8";
            case 48: case 49: case 50: case 51: case 52: case 53:
                return "ASTC_RGB_" + astcSize(fmt) + "x" + astcSize(fmt);
            case 54: case 55: case 56: case 57: case 58: case 59:
                return "ASTC_RGBA_" + astcSize(fmt) + "x" + astcSize(fmt);
            case 63: return "R8";
            default: return "Format_" + fmt;
        }
    }

    private static int astcSize(int fmt) {
        switch (fmt) {
            case 48: case 54: return 4;
            case 49: case 55: return 5;
            case 50: case 56: return 6;
            case 51: case 57: return 8;
            case 52: case 58: return 10;
            default: return 12;
        }
    }

    public static boolean isSupported(int fmt) {
        switch (fmt) {
            case 1: case 2: case 3: case 4: case 5: case 7: case 9:
            case 10: case 11: case 12: case 13: case 14:
            case 26: case 27: case 63:
            case 34: case 45: case 46: case 47:
                return true;
            default:
                return false;
        }
    }

    /** Mengembalikan pixel ARGB top-down (baris pertama = atas). */
    public static int[] decode(int fmt, int width, int height, byte[] data) {
        int[] out = new int[width * height];
        switch (fmt) {
            case 1: { // Alpha8
                for (int i = 0; i < out.length; i++) {
                    int a = data[i] & 0xFF;
                    out[i] = (a << 24) | 0x00FFFFFF;
                }
                return out;
            }
            case 2: { // ARGB4444
                for (int i = 0; i < out.length; i++) {
                    int p = u16(data, i * 2);
                    int b = (p & 0xF) * 17;
                    int g = ((p >> 4) & 0xF) * 17;
                    int r = ((p >> 8) & 0xF) * 17;
                    int a = ((p >> 12) & 0xF) * 17;
                    out[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                return out;
            }
            case 3: { // RGB24
                for (int i = 0; i < out.length; i++) {
                    int r = data[i * 3] & 0xFF;
                    int g = data[i * 3 + 1] & 0xFF;
                    int b = data[i * 3 + 2] & 0xFF;
                    out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                return out;
            }
            case 4: { // RGBA32
                for (int i = 0; i < out.length; i++) {
                    int r = data[i * 4] & 0xFF;
                    int g = data[i * 4 + 1] & 0xFF;
                    int b = data[i * 4 + 2] & 0xFF;
                    int a = data[i * 4 + 3] & 0xFF;
                    out[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                return out;
            }
            case 5: { // ARGB32 (byte order: a,r,g,b)
                for (int i = 0; i < out.length; i++) {
                    int a = data[i * 4] & 0xFF;
                    int r = data[i * 4 + 1] & 0xFF;
                    int g = data[i * 4 + 2] & 0xFF;
                    int b = data[i * 4 + 3] & 0xFF;
                    out[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                return out;
            }
            case 7: { // RGB565
                for (int i = 0; i < out.length; i++) {
                    int p = u16(data, i * 2);
                    int r = expand5((p >> 11) & 0x1F);
                    int g = expand6((p >> 5) & 0x3F);
                    int b = expand5(p & 0x1F);
                    out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                return out;
            }
            case 9: { // R16
                for (int i = 0; i < out.length; i++) {
                    int r = (u16(data, i * 2) * 255 + 32767) / 65535;
                    out[i] = 0xFF000000 | (r << 16) | (r << 8) | r;
                }
                return out;
            }
            case 13: { // RGBA4444
                for (int i = 0; i < out.length; i++) {
                    int p = u16(data, i * 2);
                    int a = (p & 0xF) * 17;
                    int b = ((p >> 4) & 0xF) * 17;
                    int g = ((p >> 8) & 0xF) * 17;
                    int r = ((p >> 12) & 0xF) * 17;
                    out[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                return out;
            }
            case 14: { // BGRA32
                for (int i = 0; i < out.length; i++) {
                    int b = data[i * 4] & 0xFF;
                    int g = data[i * 4 + 1] & 0xFF;
                    int r = data[i * 4 + 2] & 0xFF;
                    int a = data[i * 4 + 3] & 0xFF;
                    out[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                return out;
            }
            case 63: { // R8
                for (int i = 0; i < out.length; i++) {
                    int r = data[i] & 0xFF;
                    out[i] = 0xFF000000 | (r << 16) | (r << 8) | r;
                }
                return out;
            }
            case 10:
                decodeDxt(data, width, height, out, 0);
                return out;
            case 11:
                decodeDxt(data, width, height, out, 1);
                return out;
            case 12:
                decodeDxt(data, width, height, out, 2);
                return out;
            case 26:
                decodeBc4(data, width, height, out, 0);
                return out;
            case 27:
                decodeBc4(data, width, height, out, 0);
                decodeBc4(data, width, height, out, 1);
                for (int i = 0; i < out.length; i++) {
                    int r = (out[i] >> 16) & 0xFF;
                    int g = (out[i] >> 8) & 0xFF;
                    out[i] = 0xFF000000 | (r << 16) | (g << 8) | 0xFF;
                }
                return out;
            case 34: // ETC1 RGB4
                decodeEtc1(data, width, height, out);
                return out;
            case 45: // ETC2 RGB
                decodeEtc2(data, width, height, out, false, false);
                return out;
            case 46: // ETC2 RGBA1 (punch-through)
                decodeEtc2(data, width, height, out, false, true);
                return out;
            case 47: // ETC2 RGBA8 (EAC alpha)
                decodeEtc2(data, width, height, out, true, false);
                return out;
            default:
                throw new IllegalArgumentException("Format tekstur " + formatName(fmt) + " belum didukung");
        }
    }

    private static int u16(byte[] d, int p) {
        return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8);
    }

    private static int expand5(int v) {
        return (v << 3) | (v >> 2);
    }

    private static int expand6(int v) {
        return (v << 2) | (v >> 4);
    }

    private static int color565(int c) {
        int r = expand5((c >> 11) & 0x1F);
        int g = expand6((c >> 5) & 0x3F);
        int b = expand5(c & 0x1F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * @param mode 0 = DXT1 (tanpa alpha / 1-bit), 1 = DXT3 (alpha 4-bit eksplisit),
     *             2 = DXT5 (alpha terinterpolasi).
     */
    private static void decodeDxt(byte[] data, int width, int height, int[] out, int mode) {
        int bw = (width + 3) / 4;
        int bh = (height + 3) / 4;
        int blockSize = mode == 0 ? 8 : 16;
        int off = 0;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                if (off + blockSize > data.length) {
                    return;
                }
                int[] alphas = null;
                long alphaBits = 0;
                boolean explicitAlpha = mode == 1;
                int colorOff = off;
                if (mode == 1) {
                    // DXT3: 16 nilai alpha 4-bit, langsung (tanpa interpolasi).
                    alphas = new int[16];
                    for (int i = 0; i < 8; i++) {
                        int b = data[off + i] & 0xFF;
                        alphas[i * 2] = (b & 0xF) * 17;
                        alphas[i * 2 + 1] = (b >> 4) * 17;
                    }
                    colorOff = off + 8;
                } else if (mode == 2) {
                    // DXT5: dua endpoint + 6 byte indeks 3-bit.
                    int a0 = data[off] & 0xFF;
                    int a1 = data[off + 1] & 0xFF;
                    alphas = new int[8];
                    alphas[0] = a0;
                    alphas[1] = a1;
                    if (a0 > a1) {
                        for (int i = 1; i <= 6; i++) {
                            alphas[i + 1] = ((7 - i) * a0 + i * a1) / 7;
                        }
                    } else {
                        for (int i = 1; i <= 4; i++) {
                            alphas[i + 1] = ((5 - i) * a0 + i * a1) / 5;
                        }
                        alphas[6] = 0;
                        alphas[7] = 255;
                    }
                    for (int i = 0; i < 6; i++) {
                        alphaBits |= ((long) (data[off + 2 + i] & 0xFF)) << (8 * i);
                    }
                    colorOff = off + 8;
                }

                int c0 = u16(data, colorOff);
                int c1 = u16(data, colorOff + 2);
                int[] colors = new int[4];
                colors[0] = color565(c0);
                colors[1] = color565(c1);
                boolean punch = false;
                if (c0 > c1 || mode != 0) {
                    colors[2] = blend(colors[0], colors[1], 1, 2);
                    colors[3] = blend(colors[0], colors[1], 2, 1);
                } else {
                    colors[2] = blend(colors[0], colors[1], 1, 1);
                    colors[3] = 0;
                    punch = true;
                }
                int idx = (data[colorOff + 4] & 0xFF) | ((data[colorOff + 5] & 0xFF) << 8)
                        | ((data[colorOff + 6] & 0xFF) << 16) | ((data[colorOff + 7] & 0xFF) << 24);
                for (int py = 0; py < 4; py++) {
                    for (int px = 0; px < 4; px++) {
                        int px2 = bx * 4 + px;
                        int py2 = by * 4 + py;
                        if (px2 >= width || py2 >= height) {
                            continue;
                        }
                        int p = py * 4 + px;
                        int ci = (idx >> (2 * p)) & 3;
                        if (mode == 0) {
                            out[py2 * width + px2] = (punch && ci == 3) ? 0 : colors[ci];
                        } else if (explicitAlpha) {
                            int a = alphas[p];
                            out[py2 * width + px2] = (a << 24) | (colors[ci] & 0x00FFFFFF);
                        } else {
                            int a = alphas[(int) ((alphaBits >> (3 * p)) & 7)];
                            out[py2 * width + px2] = (a << 24) | (colors[ci] & 0x00FFFFFF);
                        }
                    }
                }
                off += blockSize;
            }
        }
    }

    private static int blend(int c1, int c2, int w1, int w2) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (r1 * w1 + r2 * w2) / (w1 + w2);
        int g = (g1 * w1 + g2 * w2) / (w1 + w2);
        int b = (b1 * w1 + b2 * w2) / (w1 + w2);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** BC4: satu kanal; channel 0 = merah, 1 = hijau. */
    private static void decodeBc4(byte[] data, int width, int height, int[] out, int channel) {
        int bw = (width + 3) / 4;
        int bh = (height + 3) / 4;
        int off = channel * bw * bh * 8;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                if (off + 8 > data.length) {
                    return;
                }
                int a0 = data[off] & 0xFF;
                int a1 = data[off + 1] & 0xFF;
                int[] vals = new int[8];
                vals[0] = a0;
                vals[1] = a1;
                if (a0 > a1) {
                    for (int i = 1; i <= 6; i++) {
                        vals[i + 1] = ((7 - i) * a0 + i * a1) / 7;
                    }
                } else {
                    for (int i = 1; i <= 4; i++) {
                        vals[i + 1] = ((5 - i) * a0 + i * a1) / 5;
                    }
                    vals[6] = 0;
                    vals[7] = 255;
                }
                long bits = 0;
                for (int i = 0; i < 6; i++) {
                    bits |= ((long) (data[off + 2 + i] & 0xFF)) << (8 * i);
                }
                for (int py = 0; py < 4; py++) {
                    for (int px = 0; px < 4; px++) {
                        int px2 = bx * 4 + px;
                        int py2 = by * 4 + py;
                        if (px2 >= width || py2 >= height) {
                            continue;
                        }
                        int p = py * 4 + px;
                        int v = vals[(int) ((bits >> (3 * p)) & 7)];
                        int idx = py2 * width + px2;
                        int cur = out[idx];
                        if (channel == 0) {
                            out[idx] = 0xFF000000 | (v << 16) | (cur & 0x0000FFFF);
                        } else {
                            out[idx] = (cur & 0xFFFF0000) | (v << 8) | (cur & 0xFF);
                        }
                    }
                }
                off += 8;
            }
        }
    }

    // ---------------------------------------------------------------- ETC1/ETC2

    private static final int[] WRITE_ORDER = {0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15};
    private static final int[] WRITE_ORDER_REV = {15, 11, 7, 3, 14, 10, 6, 2, 13, 9, 5, 1, 12, 8, 4, 0};
    private static final int[][] ETC1_MOD = {
            {2, 8}, {5, 17}, {9, 29}, {13, 42}, {18, 60}, {24, 80}, {33, 106}, {47, 183}
    };
    /** Tabel modifier ETC2 punch-through mode individual (negatif selalu 0). */
    private static final int[][] ETC2A_MOD = {
            {0, 8}, {0, 17}, {0, 29}, {0, 42}, {0, 60}, {0, 80}, {0, 106}, {0, 183}
    };
    private static final int[] ETC2_DIST = {3, 6, 11, 16, 23, 32, 41, 64};
    private static final int[][] ETC2_ALPHA_MOD = {
            {-3, -6, -9, -15, 2, 5, 8, 14}, {-3, -7, -10, -13, 2, 6, 9, 12},
            {-2, -5, -8, -13, 1, 4, 7, 12}, {-2, -4, -6, -13, 1, 3, 5, 12},
            {-3, -6, -8, -12, 2, 5, 7, 11}, {-3, -7, -9, -11, 2, 6, 8, 10},
            {-4, -7, -8, -11, 3, 6, 7, 10}, {-3, -5, -8, -11, 2, 4, 7, 10},
            {-2, -6, -8, -10, 1, 5, 7, 9}, {-2, -5, -8, -10, 1, 4, 7, 9},
            {-2, -4, -8, -10, 1, 3, 7, 9}, {-2, -5, -7, -10, 1, 4, 6, 9},
            {-3, -4, -7, -10, 2, 3, 6, 9}, {-1, -2, -3, -10, 0, 1, 2, 9},
            {-4, -6, -8, -9, 3, 5, 7, 8}, {-3, -5, -7, -9, 2, 4, 6, 8}
    };

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int argb(int r, int g, int b, int a) {
        return (a << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int subblock(int flip, int i) {
        if (flip == 0) {
            return i < 8 ? 0 : 1;
        }
        return (i & 3) < 2 ? 0 : 1;
    }

    private static void putBlock(int[] buf, int[] out, int width, int height, int bx, int by) {
        for (int y = 0; y < 4; y++) {
            int py = by * 4 + y;
            if (py >= height) {
                break;
            }
            for (int x = 0; x < 4; x++) {
                int px = bx * 4 + x;
                if (px < width) {
                    out[py * width + px] = buf[y * 4 + x];
                }
            }
        }
    }

    private static void decodeEtc1(byte[] data, int width, int height, int[] out) {
        int bw = (width + 3) / 4;
        int bh = (height + 3) / 4;
        int[] buf = new int[16];
        int off = 0;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++, off += 8) {
                if (off + 8 > data.length) {
                    return;
                }
                etc1Block(data, off, buf);
                putBlock(buf, out, width, height, bx, by);
            }
        }
    }

    private static void etc1Block(byte[] d, int p, int[] buf) {
        int d0 = d[p] & 0xFF, d1 = d[p + 1] & 0xFF, d2 = d[p + 2] & 0xFF, d3 = d[p + 3] & 0xFF;
        int j = ((d[p + 6] & 0xFF) << 8) | (d[p + 7] & 0xFF);
        int k = ((d[p + 4] & 0xFF) << 8) | (d[p + 5] & 0xFF);
        int[] code = {d3 >> 5, (d3 >> 2) & 7};
        int flip = d3 & 1;
        int[][] c = new int[2][3];
        if ((d3 & 2) != 0) {
            c[0][0] = d0 & 0xF8;
            c[0][1] = d1 & 0xF8;
            c[0][2] = d2 & 0xF8;
            c[1][0] = c[0][0] + ((d0 << 3 & 0x18) - (d0 << 3 & 0x20));
            c[1][1] = c[0][1] + ((d1 << 3 & 0x18) - (d1 << 3 & 0x20));
            c[1][2] = c[0][2] + ((d2 << 3 & 0x18) - (d2 << 3 & 0x20));
            for (int s = 0; s < 2; s++) {
                for (int ch = 0; ch < 3; ch++) {
                    // Referensi AssetStudio memakai uint8 (wrap 8-bit) untuk nilai diferensial
                    // yang keluar rentang 0..255; wrap dilakukan sebelum replikasi bit.
                    c[s][ch] &= 0xFF;
                    c[s][ch] |= c[s][ch] >> 5;
                }
            }
        } else {
            c[0][0] = (d0 & 0xF0) | (d0 >> 4);
            c[1][0] = (d0 & 0x0F) | ((d0 & 0x0F) << 4);
            c[0][1] = (d1 & 0xF0) | (d1 >> 4);
            c[1][1] = (d1 & 0x0F) | ((d1 & 0x0F) << 4);
            c[0][2] = (d2 & 0xF0) | (d2 >> 4);
            c[1][2] = (d2 & 0x0F) | ((d2 & 0x0F) << 4);
        }
        for (int i = 0; i < 16; i++) {
            int s = subblock(flip, i);
            int m = ETC1_MOD[code[s]][(j >> i) & 1];
            boolean neg = ((k >> i) & 1) != 0;
            int mm = neg ? -m : m;
            buf[WRITE_ORDER[i]] = argb(c[s][0] + mm, c[s][1] + mm, c[s][2] + mm, 255);
        }
    }

    /**
     * @param eac   true untuk ETC2 RGBA8 (blok alpha EAC 8 byte setelah blok warna)
     * @param punch true untuk ETC2 RGBA1 (alpha 1-bit)
     */
    private static void decodeEtc2(byte[] data, int width, int height, int[] out, boolean eac, boolean punch) {
        int bw = (width + 3) / 4;
        int bh = (height + 3) / 4;
        int blockSize = eac ? 16 : 8;
        int[] buf = new int[16];
        int off = 0;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++, off += blockSize) {
                if (off + blockSize > data.length) {
                    return;
                }
                etc2ColorBlock(data, eac ? off + 8 : off, buf, punch);
                if (eac) {
                    etc2AlphaBlock(data, off, buf);
                }
                putBlock(buf, out, width, height, bx, by);
            }
        }
    }

    private static void etc2ColorBlock(byte[] d, int p, int[] buf, boolean punch) {
        int d0 = d[p] & 0xFF, d1 = d[p + 1] & 0xFF, d2 = d[p + 2] & 0xFF, d3 = d[p + 3] & 0xFF;
        int d4 = d[p + 4] & 0xFF, d5 = d[p + 5] & 0xFF, d6 = d[p + 6] & 0xFF, d7 = d[p + 7] & 0xFF;
        int j = (d6 << 8) | d7;
        int k = (d4 << 8) | d5;
        int flip = d3 & 1;
        int[][] c = new int[3][3];

        if ((d3 & 2) == 0) {
            // Individual mode (sama seperti ETC1, tabel bisa khusus punch-through).
            c[0][0] = (d0 & 0xF0) | (d0 >> 4);
            c[1][0] = (d0 & 0x0F) | ((d0 & 0x0F) << 4);
            c[0][1] = (d1 & 0xF0) | (d1 >> 4);
            c[1][1] = (d1 & 0x0F) | ((d1 & 0x0F) << 4);
            c[0][2] = (d2 & 0xF0) | (d2 >> 4);
            c[1][2] = (d2 & 0x0F) | ((d2 & 0x0F) << 4);
            int[] code = {d3 >> 5, (d3 >> 2) & 7};
            int[][] mods = punch ? ETC2A_MOD : ETC1_MOD;
            for (int i = 0; i < 16; i++) {
                int s = subblock(flip, i);
                int m = mods[code[s]][(j >> i) & 1];
                boolean msb = ((k >> i) & 1) != 0;
                int mm = msb ? -m : m;
                int a = 255;
                if (punch && msb && ((j >> i) & 1) == 0) {
                    a = 0; // indeks 2 = transparan
                }
                buf[WRITE_ORDER[i]] = argb(c[s][0] + mm, c[s][1] + mm, c[s][2] + mm, a);
            }
            return;
        }

        // Differential mode; bisa berupa mode T, H, planar, atau differential biasa.
        int r = d0 & 0xF8;
        int dr = (d0 << 3 & 0x18) - (d0 << 3 & 0x20);
        int g = d1 & 0xF8;
        int dg = (d1 << 3 & 0x18) - (d1 << 3 & 0x20);
        int b = d2 & 0xF8;
        int db = (d2 << 3 & 0x18) - (d2 << 3 & 0x20);

        if (r + dr < 0 || r + dr > 255) {
            // Mode T
            c[0][0] = (d0 << 3 & 0xC0) | (d0 << 4 & 0x30) | (d0 >> 1 & 0x0C) | (d0 & 3);
            c[0][1] = (d1 & 0xF0) | (d1 >> 4);
            c[0][2] = (d1 & 0x0F) | ((d1 & 0x0F) << 4);
            c[1][0] = (d2 & 0xF0) | (d2 >> 4);
            c[1][1] = (d2 & 0x0F) | ((d2 & 0x0F) << 4);
            c[1][2] = (d3 & 0xF0) | (d3 >> 4);
            int dist = ETC2_DIST[((d3 >> 1) & 6) | (d3 & 1)];
            int[] colors = {
                    argb(c[0][0], c[0][1], c[0][2], 255),
                    argb(c[1][0] + dist, c[1][1] + dist, c[1][2] + dist, 255),
                    argb(c[1][0], c[1][1], c[1][2], 255),
                    argb(c[1][0] - dist, c[1][1] - dist, c[1][2] - dist, 255)
            };
            for (int i = 0; i < 16; i++) {
                int idx = (((k >> i) & 1) << 1) | ((j >> i) & 1);
                buf[WRITE_ORDER[i]] = colors[idx];
            }
        } else if (g + dg < 0 || g + dg > 255) {
            // Mode H
            c[0][0] = (d0 << 1 & 0xF0) | (d0 >> 3 & 0xF);
            c[0][1] = (d0 << 5 & 0xE0) | (d1 & 0x10);
            c[0][1] |= c[0][1] >> 4;
            c[0][2] = (d1 & 8) | (d1 << 1 & 6) | (d2 >> 7);
            c[0][2] |= c[0][2] << 4;
            c[1][0] = (d2 << 1 & 0xF0) | (d2 >> 3 & 0xF);
            c[1][1] = (d2 << 5 & 0xE0) | (d3 >> 3 & 0x10);
            c[1][1] |= c[1][1] >> 4;
            c[1][2] = (d3 << 1 & 0xF0) | (d3 >> 3 & 0xF);
            int dist = (d3 & 4) | (d3 << 1 & 2);
            if (c[0][0] > c[1][0] || (c[0][0] == c[1][0] && (c[0][1] > c[1][1]
                    || (c[0][1] == c[1][1] && c[0][2] >= c[1][2])))) {
                dist++;
            }
            dist = ETC2_DIST[dist];
            int[] colors = {
                    argb(c[0][0] + dist, c[0][1] + dist, c[0][2] + dist, 255),
                    argb(c[0][0] - dist, c[0][1] - dist, c[0][2] - dist, 255),
                    argb(c[1][0] + dist, c[1][1] + dist, c[1][2] + dist, 255),
                    argb(c[1][0] - dist, c[1][1] - dist, c[1][2] - dist, 255)
            };
            for (int i = 0; i < 16; i++) {
                int idx = (((k >> i) & 1) << 1) | ((j >> i) & 1);
                buf[WRITE_ORDER[i]] = colors[idx];
            }
        } else if (b + db < 0 || b + db > 255) {
            // Mode planar
            c[0][0] = (d0 << 1 & 0xFC) | (d0 >> 5 & 3);
            c[0][1] = (d0 << 7 & 0x80) | (d1 & 0x7E) | (d0 & 1);
            c[0][2] = (d1 << 7 & 0x80) | (d2 << 2 & 0x60) | (d2 << 3 & 0x18) | (d3 >> 5 & 4);
            c[0][2] |= c[0][2] >> 6;
            c[1][0] = (d3 << 1 & 0xF8) | (d3 << 2 & 4) | (d3 >> 5 & 3);
            c[1][1] = (d4 & 0xFE) | (d4 >> 7);
            c[1][2] = (d4 << 7 & 0x80) | (d5 >> 1 & 0x7C);
            c[1][2] |= c[1][2] >> 6;
            c[2][0] = (d5 << 5 & 0xE0) | (d6 >> 3 & 0x1C) | (d5 >> 1 & 3);
            c[2][1] = (d6 << 3 & 0xF8) | (d7 >> 5 & 0x6) | (d6 >> 4 & 1);
            c[2][2] = ((d7 << 2) | (d7 >> 4 & 3)) & 0xFF;
            for (int y = 0, i = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++, i++) {
                    int rr = clamp255((x * (c[1][0] - c[0][0]) + y * (c[2][0] - c[0][0]) + 4 * c[0][0] + 2) >> 2);
                    int gg = clamp255((x * (c[1][1] - c[0][1]) + y * (c[2][1] - c[0][1]) + 4 * c[0][1] + 2) >> 2);
                    int bb = clamp255((x * (c[1][2] - c[0][2]) + y * (c[2][2] - c[0][2]) + 4 * c[0][2] + 2) >> 2);
                    buf[i] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
                }
            }
        } else {
            // Differential biasa
            c[0][0] = r | (r >> 5);
            c[0][1] = g | (g >> 5);
            c[0][2] = b | (b >> 5);
            c[1][0] = (r + dr) | ((r + dr) >> 5);
            c[1][1] = (g + dg) | ((g + dg) >> 5);
            c[1][2] = (b + db) | ((b + db) >> 5);
            int[] code = {d3 >> 5, (d3 >> 2) & 7};
            for (int i = 0; i < 16; i++) {
                int s = subblock(flip, i);
                int m = ETC1_MOD[code[s]][(j >> i) & 1];
                boolean msb = ((k >> i) & 1) != 0;
                int mm = msb ? -m : m;
                buf[WRITE_ORDER[i]] = argb(c[s][0] + mm, c[s][1] + mm, c[s][2] + mm, 255);
            }
        }
    }

    /** Blok alpha EAC (8 byte) untuk ETC2 RGBA8. */
    private static void etc2AlphaBlock(byte[] d, int p, int[] buf) {
        int base = d[p] & 0xFF;
        int d1 = d[p + 1] & 0xFF;
        if ((d1 & 0xF0) != 0) {
            int mult = d1 >> 4;
            int[] table = ETC2_ALPHA_MOD[d1 & 0xF];
            long l = 0;
            for (int i = 0; i < 8; i++) {
                l = (l << 8) | (d[p + i] & 0xFF);
            }
            for (int i = 0; i < 16; i++) {
                int idx = (int) (l & 7);
                l >>>= 3;
                int a = clamp255(base + mult * table[idx]);
                int px = WRITE_ORDER_REV[i];
                buf[px] = (buf[px] & 0x00FFFFFF) | (a << 24);
            }
        } else {
            for (int i = 0; i < 16; i++) {
                buf[i] = (buf[i] & 0x00FFFFFF) | (base << 24);
            }
        }
    }

    /** Balik vertikal (Unity menyimpan tekstur dari bawah ke atas). */
    public static void flipVertically(int[] pixels, int width, int height) {
        for (int y = 0; y < height / 2; y++) {
            int a = y * width;
            int b = (height - 1 - y) * width;
            for (int x = 0; x < width; x++) {
                int t = pixels[a + x];
                pixels[a + x] = pixels[b + x];
                pixels[b + x] = t;
            }
        }
    }
}
