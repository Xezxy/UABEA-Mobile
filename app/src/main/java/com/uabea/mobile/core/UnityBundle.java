package com.uabea.mobile.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Pembaca/penulis file AssetBundle format UnityFS. */
public final class UnityBundle {
    public String signature = "UnityFS";
    public int headerVersion;
    public String unityVersion = "";
    public String unityRevision = "";
    public int flags;
    public boolean usesBlockAlignment;
    public final ArrayList<Node> nodes = new ArrayList<>();
    public byte[] raw;
    /** True bila file sumber adalah SerializedFile mandiri (mis. sharedassets0.assets). */
    public boolean standalone;

    public static final class Node {
        public long offset;
        public long size;
        public int flags;
        public String path = "";
        public byte[] data;
        public SerializedFile sf;
        public boolean modified;

        public String fileName() {
            int i = path.lastIndexOf('/');
            return i >= 0 ? path.substring(i + 1) : path;
        }
    }

    private UnityBundle() {
    }

    /** Membungkus SerializedFile mandiri (.assets) supaya bisa dipakai API yang sama. */
    public static UnityBundle wrap(byte[] data) {
        UnityBundle ub = new UnityBundle();
        ub.raw = data;
        ub.signature = "SerializedFile";
        ub.standalone = true;
        ub.unityVersion = "";
        SerializedFile sf = SerializedFile.load(data);
        ub.unityVersion = sf.unityVersion;
        Node n = new Node();
        n.path = "data";
        n.data = data;
        n.size = data.length;
        n.sf = sf;
        ub.nodes.add(n);
        return ub;
    }

    /** Nama format yang terdeteksi dari awal file (untuk pesan kesalahan). */
    public static String detectSignature(byte[] data) {
        if (data == null || data.length < 8) {
            return "";
        }
        int end = 0;
        while (end < data.length && end < 32 && data[end] != 0) {
            end++;
        }
        return new String(data, 0, end, java.nio.charset.StandardCharsets.US_ASCII);
    }

    public static UnityBundle load(byte[] data) throws IOException {
        UnityBundle ub = new UnityBundle();
        ub.raw = data;
        Buf b = new Buf(data);
        ub.signature = b.cstr();
        if (!"UnityFS".equals(ub.signature)) {
            String sig = ub.signature.length() > 24 ? ub.signature.substring(0, 24) + "…" : ub.signature;
            throw new IOException("Bukan file UnityFS (terdeteksi: \"" + sig + "\")");
        }
        // Bagian header UnityFS memakai big-endian (blocksInfo dan data tetap little-endian).
        ub.headerVersion = (int) b.beU32();
        ub.unityVersion = b.cstr();
        ub.unityRevision = b.cstr();
        long totalSize = b.beI64();
        long compressedInfoSize = b.beU32();
        long uncompressedInfoSize = b.beU32();
        ub.flags = (int) b.beU32();

        int[] rev = parseRevision(ub.unityRevision);
        boolean oldFlags = isOldFlags(rev);
        int compression = ub.flags & 0x3F;
        boolean infoAtEnd = (ub.flags & 0x80) != 0;
        boolean infoPadStart = !oldFlags && (ub.flags & 0x200) != 0;
        int encryptionFlag = oldFlags ? 0x200 : 0x1400;
        if ((ub.flags & encryptionFlag) != 0) {
            throw new IOException("Bundle terenkripsi (UnityCN) belum didukung");
        }

        ub.usesBlockAlignment = ub.headerVersion >= 7 || atLeast(rev, 2019, 4, 15);
        if (ub.headerVersion >= 7) {
            b.align(16);
        }

        int start = b.p;
        byte[] infoComp;
        if (infoAtEnd) {
            long pos = (totalSize > 0 && totalSize <= data.length ? totalSize : data.length) - compressedInfoSize;
            if (pos < 0 || pos + compressedInfoSize > data.length) {
                throw new IOException("Posisi blocksInfo di akhir file tidak valid");
            }
            Buf ib = new Buf(data, (int) pos);
            infoComp = ib.bytes((int) compressedInfoSize);
        } else {
            if (compressedInfoSize > b.remaining()) {
                throw new IOException("blocksInfo melebihi ukuran file");
            }
            infoComp = b.bytes((int) compressedInfoSize);
        }
        byte[] info = decompress(infoComp, compression, (int) uncompressedInfoSize);

        Buf ib = new Buf(info);
        ib.skip(16); // hash data tanpa kompresi
        int blockCount = (int) ib.beU32();
        if (blockCount < 0 || blockCount > 1000000) {
            throw new IOException("Jumlah blok tidak wajar: " + blockCount);
        }
        int[] bUncomp = new int[blockCount];
        int[] bComp = new int[blockCount];
        int[] bFlags = new int[blockCount];
        for (int i = 0; i < blockCount; i++) {
            bUncomp[i] = (int) ib.beU32();
            bComp[i] = (int) ib.beU32();
            bFlags[i] = ib.beU16();
        }
        int nodeCount = (int) ib.beU32();
        if (nodeCount < 0 || nodeCount > 100000) {
            throw new IOException("Jumlah node tidak wajar: " + nodeCount);
        }
        for (int i = 0; i < nodeCount; i++) {
            Node n = new Node();
            n.offset = ib.beI64();
            n.size = ib.beI64();
            n.flags = (int) ib.beU32();
            n.path = ib.cstr();
            ub.nodes.add(n);
        }

        if (infoAtEnd) {
            b.p = start;
        }
        if (infoPadStart) {
            b.align(16);
        }
        int base = b.p;
        Out blockData = new Out(1024 * 1024);
        int off = base;
        for (int i = 0; i < blockCount; i++) {
            if (bComp[i] < 0 || off + bComp[i] > data.length) {
                throw new IOException("Blok data melebihi ukuran file");
            }
            byte[] unc = decompress(java.util.Arrays.copyOfRange(data, off, off + bComp[i]), bFlags[i] & 0x3F,
                    bUncomp[i]);
            blockData.bytes(unc);
            off += bComp[i];
        }
        byte[] all = blockData.toByteArray();
        for (Node n : ub.nodes) {
            if (n.offset < 0 || n.size < 0 || n.offset + n.size > all.length) {
                throw new IOException("Node '" + n.path + "' di luar data bundle");
            }
            n.data = java.util.Arrays.copyOfRange(all, (int) n.offset, (int) (n.offset + n.size));
        }
        // Deteksi CAB (SerializedFile) di dalam bundle
        for (Node n : ub.nodes) {
            if (SerializedFile.looksLikeSerializedFile(n.data)) {
                try {
                    n.sf = SerializedFile.load(n.data);
                } catch (RuntimeException e) {
                    n.sf = null;
                }
            }
        }
        return ub;
    }

    private static byte[] decompress(byte[] src, int compression, int outSize) throws IOException {
        if (outSize == 0) {
            return new byte[0];
        }
        switch (compression) {
            case 0:
                if (src.length != outSize) {
                    throw new IOException("Blok tanpa kompresi ukurannya tidak cocok");
                }
                return src;
            case 1:
                return Lzma.decompress(src, 0, src.length, outSize);
            case 2:
            case 3:
                return Lz4.decompress(src, 0, src.length, outSize);
            default:
                throw new IOException("Tipe kompresi belum didukung: " + compression);
        }
    }

    private static int[] parseRevision(String s) {
        if (s == null) {
            return null;
        }
        try {
            String v = s.trim();
            int a = v.indexOf('.');
            int b = v.indexOf('.', a + 1);
            if (a < 0 || b < 0) {
                return null;
            }
            int major = Integer.parseInt(v.substring(0, a).replaceAll("[^0-9]", ""));
            int minor = Integer.parseInt(v.substring(a + 1, b).replaceAll("[^0-9]", ""));
            String rest = v.substring(b + 1);
            int end = 0;
            while (end < rest.length() && Character.isDigit(rest.charAt(end))) {
                end++;
            }
            int patch = end > 0 ? Integer.parseInt(rest.substring(0, end)) : 0;
            return new int[]{major, minor, patch};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean atLeast(int[] v, int major, int minor, int patch) {
        if (v == null) {
            return true;
        }
        if (v[0] != major) {
            return v[0] > major;
        }
        if (v[1] != minor) {
            return v[1] > minor;
        }
        return v[2] >= patch;
    }

    /** Aturan flag lama vs baru (sama seperti UnityPy). */
    private static boolean isOldFlags(int[] rev) {
        if (rev == null) {
            return false;
        }
        if (rev[0] < 2020) {
            return true;
        }
        if (rev[0] == 2020) {
            return !atLeast(rev, 2020, 3, 34);
        }
        if (rev[0] == 2021) {
            return !atLeast(rev, 2021, 3, 2);
        }
        if (rev[0] == 2022) {
            return !atLeast(rev, 2022, 1, 1);
        }
        return false;
    }

    public Node findNodeByPath(String archivePath) {
        if (archivePath == null) {
            return null;
        }
        String name = archivePath;
        int i = name.lastIndexOf('/');
        if (i >= 0) {
            name = name.substring(i + 1);
        }
        for (Node n : nodes) {
            if (n.fileName().equalsIgnoreCase(name) || n.path.equalsIgnoreCase(archivePath)) {
                return n;
            }
        }
        return null;
    }

    public boolean isModified() {
        for (Node n : nodes) {
            if (n.modified || (n.sf != null && n.sf.isModified())) {
                return true;
            }
        }
        return false;
    }

    /** Menyusun ulang seluruh bundle (dikompres LZ4). */
    public byte[] save() throws IOException {
        for (Node n : nodes) {
            if (n.sf != null) {
                n.data = n.sf.rebuild();
                n.size = n.data.length;
            } else if (n.data == null) {
                n.data = new byte[0];
                n.size = 0;
            }
        }
        if (standalone) {
            // File .assets mandiri ditulis kembali sebagai SerializedFile, bukan UnityFS.
            return nodes.isEmpty() ? new byte[0] : nodes.get(0).data;
        }
        Out dataOut = new Out(1024 * 1024);
        for (Node n : nodes) {
            if (usesBlockAlignment) {
                dataOut.align(16);
            }
            n.offset = dataOut.size();
            n.size = n.data.length;
            dataOut.bytes(n.data);
        }
        byte[] rawData = dataOut.toByteArray();

        int blockSize = 0x20000;
        int blockCount = (rawData.length + blockSize - 1) / blockSize;
        if (blockCount == 0) {
            blockCount = 1;
        }
        byte[][] blocks = new byte[blockCount][];
        int[] blockFlags = new int[blockCount];
        int[] blockUncomp = new int[blockCount];
        for (int i = 0; i < blockCount; i++) {
            int off = i * blockSize;
            int len = Math.min(blockSize, rawData.length - off);
            byte[] comp = Lz4.compress(rawData, off, len);
            if (comp != null) {
                blocks[i] = comp;
                blockFlags[i] = 2;
            } else {
                blocks[i] = java.util.Arrays.copyOfRange(rawData, off, off + len);
                blockFlags[i] = 0;
            }
            blockUncomp[i] = len;
        }

        Out info = new Out(4096);
        info.zeros(16);
        info.beU32(blockCount);
        for (int i = 0; i < blockCount; i++) {
            info.beU32(blockUncomp[i]);
            info.beU32(blocks[i].length);
            info.beU16(blockFlags[i]);
        }
        info.beU32(nodes.size());
        for (Node n : nodes) {
            info.beI64(n.offset);
            info.beI64(n.size);
            info.beU32(n.flags);
            info.cstr(n.path);
        }
        byte[] infoRaw = info.toByteArray();
        byte[] infoComp = Lz4.compress(infoRaw, 0, infoRaw.length);
        int headerFlags = 0x02; // LZ4, blocksInfo di awal
        if (infoComp == null) {
            infoComp = infoRaw;
            headerFlags = 0x00;
        }
        headerFlags |= 0x40; // blocks + directory info tergabung

        Out out = new Out(rawData.length + 4096);
        out.bytes("UnityFS".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.u8(0);
        out.beU32(headerVersion);
        out.cstr(unityVersion);
        out.cstr(unityRevision);
        int sizePos = out.size();
        out.beI64(0); // ditambal nanti
        out.beU32(infoComp.length);
        out.beU32(infoRaw.length);
        out.beU32(headerFlags);
        if (usesBlockAlignment) {
            out.align(16);
        }
        out.bytes(infoComp);
        for (int i = 0; i < blockCount; i++) {
            out.bytes(blocks[i]);
        }
        out.patchBEI64(sizePos, out.size());
        return out.toByteArray();
    }

    public List<Node> cabNodes() {
        List<Node> r = new ArrayList<>();
        for (Node n : nodes) {
            if (n.sf != null) {
                r.add(n);
            }
        }
        return r;
    }
}
