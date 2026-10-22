package com.uabea.mobile.core;

import java.nio.charset.StandardCharsets;

/** Pembaca/penulis generik berdasarkan TypeTree. */
public final class TypeTreeIO {
    private TypeTreeIO() {
    }

    public static int primSize(String type) {
        switch (type) {
            case "SInt8":
            case "UInt8":
            case "char":
            case "bool":
                return 1;
            case "short":
            case "unsigned short":
            case "SInt16":
            case "UInt16":
                return 2;
            case "int":
            case "unsigned int":
            case "SInt32":
            case "UInt32":
            case "Type*":
            case "float":
                return 4;
            case "long long":
            case "unsigned long long":
            case "SInt64":
            case "UInt64":
            case "FileSize":
            case "double":
                return 8;
            default:
                return 0;
        }
    }

    private static int align4(int p) {
        int rem = p & 3;
        return rem == 0 ? p : p + (4 - rem);
    }

    private static int readI32(byte[] d, int p) {
        if (p + 4 > d.length) {
            throw new IllegalArgumentException("Data objek terpotong di " + p);
        }
        return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8) | ((d[p + 2] & 0xFF) << 16) | ((d[p + 3] & 0xFF) << 24);
    }

    public static Value read(TypeNode root, byte[] data) {
        int[] pos = {0};
        return readValue(root, data, pos);
    }

    private static Value readValue(TypeNode n, byte[] data, int[] pos) {
        boolean align = n.aligned();
        Value v = new Value();
        v.node = n;
        v.src = data;
        int p = pos[0];
        int ps = primSize(n.type);
        if (ps > 0) {
            checkBounds(data, p, ps, n);
            v.kind = Value.PRIM;
            v.off = p;
            v.len = ps;
            p += ps;
        } else if ("string".equals(n.type)) {
            int len = readI32(data, p);
            p += 4;
            if (len < 0 || p + len > data.length) {
                len = 0;
            }
            v.kind = Value.STRING;
            v.off = p;
            v.len = len;
            p += len;
            p = align4(p);
        } else if ("TypelessData".equals(n.type)) {
            int len = readI32(data, p);
            p += 4;
            if (len < 0 || p + len > data.length) {
                len = 0;
            }
            v.kind = Value.RAW;
            v.lengthPrefixed = true;
            v.off = p;
            v.len = len;
            p += len;
        } else if (n.isVector()) {
            TypeNode arr = n.children.get(0);
            if (arr.aligned()) {
                align = true;
            }
            TypeNode sub = arr.children.size() > 1 ? arr.children.get(1) : null;
            if (sub == null) {
                throw new IllegalArgumentException("Array tanpa tipe elemen: " + n.name);
            }
            int cnt = readI32(data, p);
            p += 4;
            if (cnt < 0) {
                throw new IllegalArgumentException("Jumlah elemen negatif: " + n.name);
            }
            v.kind = Value.ARRAY;
            v.count = cnt;
            int subSize = primSize(sub.type);
            if (subSize > 0) {
                long total = (long) cnt * subSize;
                if (total > data.length - p) {
                    throw new IllegalArgumentException("Array " + n.name + " melebihi ukuran data (" + cnt + ")");
                }
                v.off = p;
                v.len = (int) total;
                p += (int) total;
            } else {
                pos[0] = p;
                for (int i = 0; i < cnt; i++) {
                    v.children.add(readValue(sub, data, pos));
                }
                p = pos[0];
            }
        } else if (n.children.isEmpty() && n.byteSize > 0) {
            checkBounds(data, p, n.byteSize, n);
            v.kind = Value.RAW;
            v.off = p;
            v.len = n.byteSize;
            p += n.byteSize;
        } else {
            v.kind = Value.STRUCT;
            pos[0] = p;
            for (TypeNode c : n.children) {
                v.children.add(readValue(c, data, pos));
            }
            p = pos[0];
        }
        if (align) {
            p = align4(p);
        }
        if (p > data.length || p < 0) {
            throw new IllegalArgumentException("Data objek terpotong pada field '" + n.name + "'");
        }
        pos[0] = p;
        return v;
    }

    private static void checkBounds(byte[] data, int p, int n, TypeNode node) {
        if (p < 0 || n < 0 || p + n > data.length) {
            throw new IllegalArgumentException("Data objek terpotong pada field '" + node.name + "' ("
                    + p + "+" + n + " > " + data.length + ")");
        }
    }

    /** Menulis kembali Value ke buffer (termasuk hasil edit). */
    public static byte[] write(Value v) {
        Out out = new Out(4096);
        writeValue(v, out);
        return out.toByteArray();
    }

    public static void writeValue(Value v, Out out) {
        TypeNode n = v.node;
        boolean align = n != null && n.aligned();
        if (v.kind == Value.STRUCT) {
            for (Value c : v.children) {
                writeValue(c, out);
            }
        } else if (v.kind == Value.ARRAY) {
            TypeNode arr = n.children.get(0);
            if (arr.aligned()) {
                align = true;
            }
            TypeNode sub = arr.children.size() > 1 ? arr.children.get(1) : null;
            int subSize = sub != null ? primSize(sub.type) : 0;
            byte[] data = v.replaced;
            int cnt = v.count;
            if (data != null && subSize > 0) {
                cnt = data.length / subSize;
            }
            out.i32(cnt);
            if (subSize > 0) {
                if (data != null) {
                    out.bytes(data);
                } else if (v.src != null) {
                    out.bytes(v.src, v.off, v.len);
                }
            } else {
                for (Value c : v.children) {
                    writeValue(c, out);
                }
            }
        } else if (v.kind == Value.STRING) {
            byte[] data = v.replaced != null ? v.replaced : v.bytes();
            out.i32(data.length);
            out.bytes(data);
            out.align(4);
        } else if (v.kind == Value.RAW) {
            byte[] data = v.replaced != null ? v.replaced : v.bytes();
            if (v.lengthPrefixed) {
                out.i32(data.length);
            }
            out.bytes(data);
        } else {
            if (v.replaced != null) {
                out.bytes(v.replaced);
            } else if (v.src != null) {
                out.bytes(v.src, v.off, v.len);
            }
        }
        if (align) {
            out.align(4);
        }
    }

    /** Membaca field string kelas teratas tanpa mem-parse seluruh objek. */
    public static String peekString(TypeNode root, byte[] data, String... fieldNames) {
        int p = 0;
        for (TypeNode c : root.children) {
            for (String want : fieldNames) {
                if (want.equals(c.name) && "string".equals(c.type)) {
                    int len = readI32(data, p);
                    int start = p + 4;
                    if (len > 0 && start + len <= data.length) {
                        return new String(data, start, len, StandardCharsets.UTF_8);
                    }
                    return null;
                }
            }
            p = skip(c, data, p);
        }
        return null;
    }

    /** Melewati satu nilai tanpa alokasi. */
    public static int skip(TypeNode n, byte[] data, int p) {
        boolean align = n.aligned();
        int ps = primSize(n.type);
        if (ps > 0) {
            p += ps;
        } else if ("string".equals(n.type)) {
            int len = readI32(data, p);
            p += 4;
            if (len < 0) {
                len = 0;
            }
            p += len;
            p = align4(p);
        } else if ("TypelessData".equals(n.type)) {
            int len = readI32(data, p);
            p += 4;
            if (len < 0) {
                len = 0;
            }
            p += len;
        } else if (n.isVector()) {
            TypeNode arr = n.children.get(0);
            if (arr.aligned()) {
                align = true;
            }
            int cnt = readI32(data, p);
            p += 4;
            if (cnt < 0) {
                cnt = 0;
            }
            TypeNode sub = arr.children.size() > 1 ? arr.children.get(1) : null;
            if (sub != null) {
                int subSize = primSize(sub.type);
                if (subSize > 0) {
                    long total = (long) cnt * subSize;
                    if (total > Integer.MAX_VALUE || p + total > data.length) {
                        throw new IllegalArgumentException("Array " + n.name + " rusak");
                    }
                    p += (int) total;
                } else {
                    for (int i = 0; i < cnt; i++) {
                        p = skip(sub, data, p);
                    }
                }
            }
        } else if (n.children.isEmpty() && n.byteSize > 0) {
            p += n.byteSize;
        } else {
            for (TypeNode c : n.children) {
                p = skip(c, data, p);
            }
        }
        if (align) {
            p = align4(p);
        }
        if (p < 0 || p > data.length) {
            throw new IllegalArgumentException("Data objek terpotong / TypeTree tidak cocok");
        }
        return p;
    }

    /** Jumlah byte yang terbaca; -1 bila TypeTree tidak cocok dengan data. */
    public static int consumed(TypeNode root, byte[] data) {
        try {
            return skip(root, data, 0);
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
