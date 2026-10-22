package com.uabea.mobile.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Ekstraksi & penggantian isi aset (TextAsset, Texture2D, AudioClip). */
public final class AssetExtractor {
    public static final class Texture {
        public int width;
        public int height;
        public int format;
        public String formatName = "";
        public int[] pixels;
        public byte[] rawData = new byte[0];
        public boolean streamed;
        public String source = "";
        public String note = "";
    }

    private AssetExtractor() {
    }

    private static Value child(Value v, String... names) {
        if (v == null) {
            return null;
        }
        for (String n : names) {
            for (Value c : v.children) {
                if (c.node != null && n.equals(c.node.name)) {
                    return c;
                }
            }
        }
        return null;
    }

    public static Texture texture(Asset a) {
        Value v = a.parse();
        Texture t = new Texture();
        t.width = intOf(child(v, "m_Width"));
        t.height = intOf(child(v, "m_Height"));
        t.format = intOf(child(v, "m_TextureFormat"));
        t.formatName = TextureDecoder.formatName(t.format);
        if (t.width <= 0 || t.height <= 0) {
            throw new IllegalArgumentException("Ukuran tekstur tidak valid");
        }
        if ((long) t.width * t.height > 64L * 1024 * 1024) {
            throw new IllegalArgumentException("Tekstur terlalu besar: " + t.width + "x" + t.height);
        }

        Value stream = child(v, "m_StreamData");
        long streamSize = 0;
        long streamOffset = 0;
        String streamPath = "";
        if (stream != null) {
            Value p = child(stream, "path", "m_Source", "m_Path");
            Value o = child(stream, "offset", "m_Offset");
            Value s = child(stream, "size", "m_Size");
            streamPath = p != null ? p.asString() : "";
            streamOffset = o != null ? o.asLong() : 0;
            streamSize = s != null ? s.asLong() : 0;
        }

        byte[] data = null;
        if (streamSize > 0 && streamPath != null && !streamPath.isEmpty()) {
            UnityBundle.Node node = a.bundle.findNodeByPath(streamPath);
            if (node != null && streamOffset >= 0 && streamOffset + streamSize <= node.data.length) {
                data = Arrays.copyOfRange(node.data, (int) streamOffset, (int) (streamOffset + streamSize));
                t.streamed = true;
                t.source = node.fileName();
            } else {
                t.note = "Data stream tidak ditemukan di bundle (" + streamPath + ")";
            }
        }
        if (data == null || data.length == 0) {
            Value img = child(v, "m_ImageData", "image data", "m_TextureData");
            if (img != null) {
                data = img.bytes();
                t.source = "m_ImageData";
            }
        }
        if (data == null) {
            throw new IllegalArgumentException("Data piksel tekstur tidak ditemukan");
        }
        t.rawData = data;
        if (TextureDecoder.isSupported(t.format)) {
            t.pixels = TextureDecoder.decode(t.format, t.width, t.height, data);
            TextureDecoder.flipVertically(t.pixels, t.width, t.height);
        } else {
            t.note = (t.note.isEmpty() ? "" : t.note + " ") + "Format " + t.formatName + " belum didukung untuk pratinjau.";
        }
        return t;
    }

    private static int intOf(Value v) {
        return v == null ? 0 : v.asInt();
    }

    public static byte[] texturePng(Texture t) {
        if (t.pixels == null) {
            throw new IllegalArgumentException("Tidak ada piksel untuk diekspor");
        }
        return Png.encode(t.pixels, t.width, t.height);
    }

    /** Isi mentah TextAsset. */
    public static byte[] textBytes(Asset a) {
        Value v = a.parse();
        Value s = child(v, "m_Script");
        if (s == null) {
            throw new IllegalArgumentException("Field m_Script tidak ditemukan");
        }
        return s.bytes();
    }

    public static String textPreview(Asset a, int maxChars) {
        byte[] d = textBytes(a);
        String s = new String(d, StandardCharsets.UTF_8);
        if (s.length() > maxChars) {
            return s.substring(0, maxChars) + "\u2026";
        }
        return s;
    }

    /** Ganti isi TextAsset (byte apa pun). */
    public static void replaceText(Asset a, byte[] bytes) {
        Value v = a.parse();
        Value s = child(v, "m_Script");
        if (s == null) {
            throw new IllegalArgumentException("Field m_Script tidak ditemukan");
        }
        s.replaced = bytes;
        s.len = bytes.length;
        a.setData(TypeTreeIO.write(v));
    }

    /** Ganti field m_Name pada objek (bila ada). */
    public static void rename(Asset a, String newName) {
        if (newName == null) {
            throw new IllegalArgumentException("Nama tidak boleh kosong");
        }
        Value v = a.parse();
        Value n = child(v, "m_Name");
        if (n == null) {
            throw new IllegalArgumentException("Aset ini tidak punya field m_Name");
        }
        n.setString(newName);
        a.setData(TypeTreeIO.write(v));
        a.name = newName;
    }

    /** Ganti tekstur dengan piksel RGBA top-down (hasil decode PNG/JPG di UI). */
    public static void replaceTexture(Asset a, int width, int height, byte[] rgbaTopDown) {
        Value v = a.parse();
        Value w = child(v, "m_Width");
        Value h = child(v, "m_Height");
        Value fmt = child(v, "m_TextureFormat");
        if (w == null || h == null || fmt == null) {
            throw new IllegalArgumentException("Field tekstur tidak lengkap");
        }
        byte[] bottomUp = flipRows(rgbaTopDown, width, height);
        w.setInt(width);
        h.setInt(height);
        fmt.setInt(4); // RGBA32
        Value mip = child(v, "m_MipCount");
        if (mip != null) {
            mip.setInt(1);
        }
        Value complete = child(v, "m_CompleteImageSize");
        if (complete != null) {
            complete.setInt(bottomUp.length);
        }

        Value stream = child(v, "m_StreamData");
        boolean streamed = false;
        UnityBundle.Node node = null;
        Value offField = null;
        Value sizeField = null;
        if (stream != null) {
            Value p = child(stream, "path", "m_Source", "m_Path");
            offField = child(stream, "offset", "m_Offset");
            sizeField = child(stream, "size", "m_Size");
            String path = p != null ? p.asString() : "";
            if (!path.isEmpty()) {
                node = a.bundle.findNodeByPath(path);
                streamed = node != null && sizeField != null && offField != null;
            }
        }
        if (streamed) {
            int aligned = (node.data.length + 15) & ~15;
            byte[] newData = Arrays.copyOf(node.data, aligned + bottomUp.length);
            System.arraycopy(bottomUp, 0, newData, aligned, bottomUp.length);
            node.data = newData;
            node.size = newData.length;
            node.modified = true;
            offField.setInt(aligned);
            sizeField.setInt(bottomUp.length);
        } else {
            Value img = child(v, "m_ImageData", "image data", "m_TextureData");
            if (img == null) {
                throw new IllegalArgumentException("Tekstur tidak punya m_ImageData maupun stream");
            }
            img.replaced = bottomUp;
            img.len = bottomUp.length;
            if (stream != null) {
                // Data kini disimpan di dalam objek: kosongkan referensi stream lama.
                Value p = child(stream, "path", "m_Source", "m_Path");
                if (p != null) {
                    p.replaced = new byte[0];
                    p.len = 0;
                }
                if (offField != null) {
                    offField.setInt(0);
                }
                if (sizeField != null) {
                    sizeField.setInt(0);
                }
            }
        }
        a.setData(TypeTreeIO.write(v));
    }

    private static byte[] flipRows(byte[] rgba, int width, int height) {
        int stride = width * 4;
        if (rgba.length < stride * height) {
            throw new IllegalArgumentException("Data piksel kurang dari ukuran tekstur");
        }
        byte[] out = new byte[stride * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(rgba, (height - 1 - y) * stride, out, y * stride, stride);
        }
        return out;
    }

    public static Fsb5.Result audio(Asset a) {
        byte[] data = audioBytes(a);
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data audio tidak ditemukan");
        }
        Fsb5.Result r;
        if (Fsb5.isFsb5(data)) {
            r = Fsb5.extract(data);
        } else {
            r = new Fsb5.Result();
            r.data = data;
            if (data.length > 4 && data[0] == 'O' && data[1] == 'g' && data[2] == 'g' && data[3] == 'S') {
                r.extension = "ogg";
            } else if (data.length > 4 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
                r.extension = "wav";
            } else if (data.length > 2 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
                r.extension = "mp3";
            } else {
                r.extension = "bin";
                r.note = "Format audio tidak dikenali";
            }
        }
        return r;
    }

    public static byte[] audioBytes(Asset a) {
        Value v = a.parse();
        Value res = child(v, "m_Resource");
        if (res != null) {
            Value src = child(res, "m_Source", "path", "m_Path");
            Value off = child(res, "m_Offset", "offset");
            Value size = child(res, "m_Size", "size");
            String path = src != null ? src.asString() : "";
            long o = off != null ? off.asLong() : 0;
            long s = size != null ? size.asLong() : 0;
            if (s > 0 && path != null && !path.isEmpty()) {
                UnityBundle.Node node = a.bundle.findNodeByPath(path);
                if (node != null && o >= 0 && o + s <= node.data.length) {
                    return Arrays.copyOfRange(node.data, (int) o, (int) (o + s));
                }
            }
        }
        Value inline = child(v, "m_AudioData");
        if (inline != null) {
            return inline.bytes();
        }
        return null;
    }

    public static String audioSummary(Asset a) {
        Value v = a.parse();
        int channels = intOf(child(v, "m_Channels"));
        int freq = intOf(child(v, "m_Frequency"));
        int bits = intOf(child(v, "m_BitsPerSample"));
        Value lenField = child(v, "m_Length");
        float len = 0;
        if (lenField != null) {
            byte[] d = lenField.bytes();
            if (d.length == 4) {
                len = Float.intBitsToFloat((d[0] & 0xFF) | ((d[1] & 0xFF) << 8) | ((d[2] & 0xFF) << 16)
                        | ((d[3] & 0xFF) << 24));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(channels > 0 ? channels + " ch" : "? ch");
        sb.append(" \u00b7 ").append(freq > 0 ? freq + " Hz" : "? Hz");
        if (bits > 0) {
            sb.append(" \u00b7 ").append(bits).append("-bit");
        }
        if (len > 0) {
            sb.append(" \u00b7 ").append(String.format(java.util.Locale.US, "%.2f", len)).append(" s");
        }
        return sb.toString();
    }
}
