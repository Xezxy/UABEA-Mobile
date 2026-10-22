package com.uabea.mobile.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pembaca SerializedFile (CAB) Unity. */
public final class SerializedFile {
    public byte[] data;
    public int formatVersion;
    public long fileSize;
    public long dataOffset;
    public boolean bigEndian;
    public boolean headerBigEndian;
    public boolean typeTreeEnabled;
    public String unityVersion = "";
    public final ArrayList<TypeInfo> types = new ArrayList<>();
    public final ArrayList<ObjectInfo> objects = new ArrayList<>();
    public final Map<Long, byte[]> patched = new LinkedHashMap<>();
    private int fileSizePos;
    private boolean sizeIs64;

    public static final class TypeInfo {
        public int classId;
        public boolean stripped;
        public int scriptTypeIndex = -1;
        public byte[] scriptId;
        public byte[] oldTypeHash;
        public TypeNode node;
        public int[] typeDependencies;
    }

    public static final class ObjectInfo {
        public long pathId;
        public long byteStart;
        public long byteSize;
        public int typeId;
        public int classId;
        public int infoPos;
        public int sizePos;
    }

    private SerializedFile() {
    }

    public static boolean looksLikeSerializedFile(byte[] raw) {
        if (raw.length < 48) {
            return false;
        }
        int verLE = le32(raw, 8);
        int verBE = be32(raw, 8);
        int ver = (verLE >= 6 && verLE <= 30) ? verLE : (verBE >= 6 && verBE <= 30 ? verBE : -1);
        if (ver < 0) {
            return false;
        }
        long meta = (ver == verLE) ? (le32(raw, 0) & 0xFFFFFFFFL) : (be32(raw, 0) & 0xFFFFFFFFL);
        long dataOff = (ver == verLE) ? (le32(raw, 12) & 0xFFFFFFFFL) : (be32(raw, 12) & 0xFFFFFFFFL);
        return ver >= 22 || (meta > 0 && meta < raw.length && dataOff > 0 && dataOff <= raw.length);
    }

    private static int le32(byte[] d, int p) {
        return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8) | ((d[p + 2] & 0xFF) << 16) | ((d[p + 3] & 0xFF) << 24);
    }

    private static int be32(byte[] d, int p) {
        return ((d[p] & 0xFF) << 24) | ((d[p + 1] & 0xFF) << 16) | ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
    }

    public static SerializedFile load(byte[] raw) {
        SerializedFile sf = new SerializedFile();
        sf.data = raw;
        if (raw.length < 20) {
            throw new IllegalArgumentException("File terlalu kecil");
        }
        int verLE = le32(raw, 8);
        int verBE = be32(raw, 8);
        boolean headerBE;
        int ver;
        if (verLE >= 6 && verLE <= 30) {
            ver = verLE;
            headerBE = false;
        } else if (verBE >= 6 && verBE <= 30) {
            ver = verBE;
            headerBE = true;
        } else {
            throw new IllegalArgumentException("Versi SerializedFile tidak didukung");
        }
        long metadataSize = (headerBE ? be32(raw, 0) : le32(raw, 0)) & 0xFFFFFFFFL;
        long fileSize32 = (headerBE ? be32(raw, 4) : le32(raw, 4)) & 0xFFFFFFFFL;
        long dataOff32 = (headerBE ? be32(raw, 12) : le32(raw, 12)) & 0xFFFFFFFFL;
        sf.formatVersion = ver;
        sf.headerBigEndian = headerBE;

        Buf b = new Buf(raw, 16);
        sf.bigEndian = b.u8() != 0; // endian metadata (isi objek)
        b.skip(3);
        if (ver >= 22) {
            metadataSize = headerBE ? b.beU32() : b.u32();
            sf.fileSizePos = b.p;
            sf.fileSize = headerBE ? b.beI64() : b.i64();
            sf.sizeIs64 = true;
            sf.dataOffset = headerBE ? b.beI64() : b.i64();
            if (headerBE) {
                b.beI64();
            } else {
                b.i64();
            }
        } else {
            sf.dataOffset = dataOff32;
            sf.fileSize = fileSize32;
            sf.fileSizePos = 4;
            sf.sizeIs64 = false;
            if (sf.dataOffset <= 0 || sf.dataOffset > raw.length) {
                sf.dataOffset = metadataSize + 20;
            }
        }
        if (ver < 22 && sf.dataOffset <= 0) {
            sf.dataOffset = metadataSize + 20;
        }
        if (sf.bigEndian) {
            throw new IllegalArgumentException("File big-endian (data objek) belum didukung");
        }
        if (sf.dataOffset <= 0 || sf.dataOffset > raw.length) {
            throw new IllegalArgumentException("dataOffset tidak valid");
        }

        if (ver >= 7) {
            sf.unityVersion = b.cstr();
        }
        if (ver >= 8) {
            b.i32(); // target platform
        }
        if (ver >= 13) {
            sf.typeTreeEnabled = b.u8() != 0;
        }
        int typeCount = b.i32();
        if (typeCount < 0 || typeCount > 200000) {
            throw new IllegalArgumentException("Jumlah tipe tidak wajar: " + typeCount);
        }
        for (int i = 0; i < typeCount; i++) {
            TypeInfo t = new TypeInfo();
            t.classId = b.i32();
            if (ver >= 16) {
                t.stripped = b.u8() != 0;
            }
            if (ver >= 17) {
                t.scriptTypeIndex = b.i16();
            }
            if (ver >= 13) {
                if ((ver < 16 && t.classId < 0) || (ver >= 16 && t.classId == 114)) {
                    t.scriptId = b.bytes(16);
                }
                t.oldTypeHash = b.bytes(16);
            }
            if (sf.typeTreeEnabled) {
                if (ver >= 12 || ver == 10) {
                    t.node = TypeNode.parse(b, ver);
                } else {
                    throw new IllegalArgumentException("Format TypeTree versi " + ver + " belum didukung");
                }
            }
            if (ver >= 21) {
                int depCount = b.i32();
                if (depCount < 0 || depCount > 10000) {
                    throw new IllegalArgumentException("typeDependencies rusak");
                }
                t.typeDependencies = new int[depCount];
                for (int k = 0; k < depCount; k++) {
                    t.typeDependencies[k] = b.i32();
                }
            }
            sf.types.add(t);
        }
        boolean bigId = false;
        if (ver >= 7 && ver < 14) {
            bigId = b.i32() != 0;
        }
        int objCount = b.i32();
        if (objCount < 0 || objCount > 10000000) {
            throw new IllegalArgumentException("Jumlah objek tidak wajar: " + objCount);
        }
        for (int i = 0; i < objCount; i++) {
            if (ver >= 14) {
                b.align(4);
            }
            ObjectInfo o = new ObjectInfo();
            o.pathId = (ver >= 14 || bigId) ? b.i64() : b.i32();
            o.infoPos = b.p;
            o.byteStart = ver >= 22 ? b.i64() : b.u32();
            o.byteSize = b.u32();
            o.typeId = b.i32();
            if (o.typeId < 0 || o.typeId >= sf.types.size()) {
                throw new IllegalArgumentException("typeId di luar jangkauan: " + o.typeId);
            }
            o.classId = sf.types.get(o.typeId).classId;
            if (ver < 16) {
                o.classId = b.u16();
            }
            if (ver < 17) {
                b.u16(); // isDestroyed
            }
            if (ver == 15 || ver == 16) {
                b.u8();
            }
            o.sizePos = o.infoPos + (ver >= 22 ? 8 : 4);
            if (o.byteStart < 0 || o.byteSize < 0 || o.byteStart + o.byteSize > raw.length) {
                throw new IllegalArgumentException("Data objek di luar file (pathId " + o.pathId + ")");
            }
            sf.objects.add(o);
        }
        // Sebagian repacker menyimpan offset objek relatif terhadap dataOffset.
        long maxEnd = 0;
        long minStart = Long.MAX_VALUE;
        for (ObjectInfo o : sf.objects) {
            minStart = Math.min(minStart, o.byteStart);
            maxEnd = Math.max(maxEnd, o.byteStart + o.byteSize);
        }
        boolean relative = !sf.objects.isEmpty() && minStart < sf.dataOffset
                && sf.dataOffset + maxEnd <= raw.length;
        if (relative) {
            for (ObjectInfo o : sf.objects) {
                o.byteStart += sf.dataOffset;
            }
        }
        return sf;
    }

    public String typeName(ObjectInfo o) {
        return ClassNames.of(o.classId);
    }

    public byte[] objectData(ObjectInfo o) {
        byte[] p = patched.get(o.pathId);
        if (p != null) {
            return p;
        }
        int start = (int) o.byteStart;
        int size = (int) o.byteSize;
        return Arrays.copyOfRange(data, start, start + size);
    }

    public void setObjectData(ObjectInfo o, byte[] d) {
        patched.put(o.pathId, d);
    }

    public boolean isModified() {
        return !patched.isEmpty();
    }

    /** Menyusun ulang file dengan objek yang sudah diedit. */
    public byte[] rebuild() {
        Out out = new Out(data.length + 4096);
        out.bytes(data, 0, (int) dataOffset);
        long pos = dataOffset;
        int n = objects.size();
        long[] starts = new long[n];
        int[] sizes = new int[n];
        for (int i = 0; i < n; i++) {
            ObjectInfo o = objects.get(i);
            long aligned = (pos + 7) & ~7L;
            if (aligned > pos) {
                out.zeros((int) (aligned - pos));
            }
            byte[] d = objectData(o);
            out.bytes(d);
            starts[i] = aligned;
            sizes[i] = d.length;
            pos = aligned + d.length;
        }
        for (int i = 0; i < n; i++) {
            ObjectInfo o = objects.get(i);
            if (formatVersion >= 22) {
                out.patchI64(o.infoPos, starts[i]);
            } else {
                out.patchU32(o.infoPos, starts[i]);
            }
            out.patchU32(o.sizePos, sizes[i]);
        }
        if (sizeIs64) {
            if (headerBigEndian) {
                out.patchBEI64(fileSizePos, out.size());
            } else {
                out.patchI64(fileSizePos, out.size());
            }
        } else if (headerBigEndian) {
            out.patchU32(fileSizePos, Integer.reverseBytes((int) out.size()) & 0xFFFFFFFFL);
        } else {
            out.patchU32(fileSizePos, out.size());
        }
        return out.toByteArray();
    }

    public List<ObjectInfo> objectsOfClass(int classId) {
        List<ObjectInfo> r = new ArrayList<>();
        for (ObjectInfo o : objects) {
            if (o.classId == classId) {
                r.add(o);
            }
        }
        return r;
    }
}
