package com.uabea.mobile.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Nilai hasil pembacaan TypeTree. Data besar disimpan sebagai referensi offset. */
public final class Value {
    public static final int PRIM = 0;
    public static final int STRING = 1;
    public static final int ARRAY = 2;
    public static final int STRUCT = 3;
    public static final int RAW = 4;

    public TypeNode node;
    public int kind;
    public byte[] src;
    public int off;
    public int len;
    public int count;
    public boolean lengthPrefixed;
    public ArrayList<Value> children = new ArrayList<>();

    /** Pengganti data (hasil edit). */
    public byte[] replaced;

    public Value child(String name) {
        for (Value c : children) {
            if (c.node != null && name.equals(c.node.name)) {
                return c;
            }
        }
        return null;
    }

    public byte[] bytes() {
        if (replaced != null) {
            return replaced;
        }
        if (src == null) {
            return new byte[0];
        }
        byte[] r = new byte[len];
        System.arraycopy(src, off, r, 0, len);
        return r;
    }

    public String asString() {
        if (kind == STRING) {
            return new String(bytes(), StandardCharsets.UTF_8);
        }
        return "";
    }

    public long asLong() {
        byte[] d = bytes();
        if (d.length == 0) {
            return 0;
        }
        long v = 0;
        for (int i = d.length - 1; i >= 0; i--) {
            v = (v << 8) | (d[i] & 0xFF);
        }
        return v;
    }

    public int asInt() {
        return (int) asLong();
    }

    public void setString(String s) {
        replaced = s.getBytes(StandardCharsets.UTF_8);
        len = replaced.length;
        kind = STRING;
    }

    public void setInt(long v) {
        int size = len > 0 ? len : 4;
        byte[] d = new byte[size];
        for (int i = 0; i < size; i++) {
            d[i] = (byte) (v >>> (8 * i));
        }
        replaced = d;
        len = size;
        kind = PRIM;
    }

    public void setBytes(byte[] data) {
        replaced = data;
        len = data.length;
    }

    public int elementSize() {
        if (node == null || !node.isVector()) {
            return 1;
        }
        TypeNode arr = node.children.get(0);
        if (arr.children.size() < 2) {
            return 1;
        }
        int s = TypeTreeIO.primSize(arr.children.get(1).type);
        return s > 0 ? s : 1;
    }
}
