package com.uabea.mobile.core;

import java.nio.charset.StandardCharsets;

/** Pembaca byte little-endian sederhana di atas byte[]. */
public final class Buf {
    public final byte[] b;
    public int p;

    public Buf(byte[] b) {
        this(b, 0);
    }

    public Buf(byte[] b, int p) {
        this.b = b;
        this.p = p;
    }

    public int remaining() {
        return b.length - p;
    }

    public void need(int n) {
        if (p + n > b.length) {
            throw new IllegalArgumentException("Data terpotong: butuh " + n + " byte di offset " + p
                    + ", tersisa " + remaining());
        }
    }

    public int u8() {
        need(1);
        return b[p++] & 0xFF;
    }

    public int i8() {
        need(1);
        return b[p++];
    }

    public int u16() {
        need(2);
        int v = (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8);
        p += 2;
        return v;
    }

    public int i16() {
        return (short) u16();
    }

    public long u32() {
        need(4);
        long v = (b[p] & 0xFFL) | ((b[p + 1] & 0xFFL) << 8) | ((b[p + 2] & 0xFFL) << 16)
                | ((b[p + 3] & 0xFFL) << 24);
        p += 4;
        return v;
    }

    public int i32() {
        return (int) u32();
    }

    public long i64() {
        long lo = u32();
        long hi = u32();
        return lo | (hi << 32);
    }

    public int beU16() {
        need(2);
        int v = ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF);
        p += 2;
        return v;
    }

    /** Header UnityFS memakai big-endian. */
    public long beU32() {
        need(4);
        long v = ((b[p] & 0xFFL) << 24) | ((b[p + 1] & 0xFFL) << 16) | ((b[p + 2] & 0xFFL) << 8)
                | (b[p + 3] & 0xFFL);
        p += 4;
        return v;
    }

    public long beI64() {
        long hi = beU32();
        long lo = beU32();
        return (hi << 32) | lo;
    }

    public float f32() {
        return Float.intBitsToFloat(i32());
    }

    public double f64() {
        return Double.longBitsToDouble(i64());
    }

    public byte[] bytes(int n) {
        need(n);
        byte[] r = new byte[n];
        System.arraycopy(b, p, r, 0, n);
        p += n;
        return r;
    }

    public void skip(int n) {
        need(n);
        p += n;
    }

    public String cstr() {
        int start = p;
        while (p < b.length && b[p] != 0) {
            p++;
        }
        String s = new String(b, start, p - start, StandardCharsets.UTF_8);
        if (p < b.length) {
            p++; // lewati null terminator
        }
        return s;
    }

    public void align(int alignment) {
        int rem = p % alignment;
        if (rem != 0) {
            skip(alignment - rem);
        }
    }
}
