package com.uabea.mobile.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Penulis byte little-endian yang bisa ditambal (patch) di posisi tertentu. */
public final class Out {
    private byte[] buf;
    private int len;

    public Out() {
        this(8192);
    }

    public Out(int capacity) {
        buf = new byte[Math.max(64, capacity)];
    }

    public int size() {
        return len;
    }

    private void ensure(int extra) {
        if (len + extra <= buf.length) {
            return;
        }
        int cap = buf.length;
        while (cap < len + extra) {
            cap = cap + (cap >> 1) + 1024;
        }
        buf = Arrays.copyOf(buf, cap);
    }

    public void u8(int v) {
        ensure(1);
        buf[len++] = (byte) v;
    }

    public void u16(int v) {
        ensure(2);
        buf[len++] = (byte) v;
        buf[len++] = (byte) (v >>> 8);
    }

    public void u32(long v) {
        ensure(4);
        buf[len++] = (byte) v;
        buf[len++] = (byte) (v >>> 8);
        buf[len++] = (byte) (v >>> 16);
        buf[len++] = (byte) (v >>> 24);
    }

    public void i32(int v) {
        u32(v & 0xFFFFFFFFL);
    }

    public void i64(long v) {
        u32(v & 0xFFFFFFFFL);
        u32(v >>> 32);
    }

    public void beU16(int v) {
        ensure(2);
        buf[len++] = (byte) (v >>> 8);
        buf[len++] = (byte) v;
    }

    public void beU32(long v) {
        ensure(4);
        buf[len++] = (byte) (v >>> 24);
        buf[len++] = (byte) (v >>> 16);
        buf[len++] = (byte) (v >>> 8);
        buf[len++] = (byte) v;
    }

    public void beI64(long v) {
        beU32(v >>> 32);
        beU32(v & 0xFFFFFFFFL);
    }

    public void patchBEI64(int pos, long v) {
        buf[pos] = (byte) (v >>> 56);
        buf[pos + 1] = (byte) (v >>> 48);
        buf[pos + 2] = (byte) (v >>> 40);
        buf[pos + 3] = (byte) (v >>> 32);
        buf[pos + 4] = (byte) (v >>> 24);
        buf[pos + 5] = (byte) (v >>> 16);
        buf[pos + 6] = (byte) (v >>> 8);
        buf[pos + 7] = (byte) v;
    }

    public void f32(float v) {
        i32(Float.floatToRawIntBits(v));
    }

    public void bytes(byte[] data) {
        bytes(data, 0, data.length);
    }

    public void bytes(byte[] data, int off, int n) {
        ensure(n);
        System.arraycopy(data, off, buf, len, n);
        len += n;
    }

    public void cstr(String s) {
        bytes(s.getBytes(StandardCharsets.UTF_8));
        u8(0);
    }

    public void zeros(int n) {
        ensure(n);
        len += n;
    }

    public void align(int alignment) {
        int rem = len % alignment;
        if (rem != 0) {
            zeros(alignment - rem);
        }
    }

    public void patchU32(int pos, long v) {
        buf[pos] = (byte) v;
        buf[pos + 1] = (byte) (v >>> 8);
        buf[pos + 2] = (byte) (v >>> 16);
        buf[pos + 3] = (byte) (v >>> 24);
    }

    public void patchI64(int pos, long v) {
        patchU32(pos, v & 0xFFFFFFFFL);
        patchU32(pos + 4, v >>> 32);
    }

    public void insertBytes(int pos, byte[] data) {
        if (pos < 0 || pos > len) {
            throw new IllegalArgumentException("pos di luar jangkauan");
        }
        ensure(data.length);
        System.arraycopy(buf, pos, buf, pos + data.length, len - pos);
        System.arraycopy(data, 0, buf, pos, data.length);
        len += data.length;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, len);
    }
}
