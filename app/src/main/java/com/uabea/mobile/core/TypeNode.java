package com.uabea.mobile.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Satu node TypeTree Unity. */
public final class TypeNode {
    public int version;
    public int level;
    public int typeFlags;
    public String type = "";
    public String name = "";
    public int byteSize;
    public int index;
    public int metaFlag;
    public long refTypeHash;
    public final ArrayList<TypeNode> children = new ArrayList<>();

    public boolean aligned() {
        return (metaFlag & 0x4000) != 0;
    }

    /** Node "vector" punya anak pertama bertipe "Array" berisi size + elemen. */
    public boolean isVector() {
        return !children.isEmpty() && "Array".equals(children.get(0).type);
    }

    /** Baca TypeTree langsung dari stream (format serialized file >= 12/10). */
    public static TypeNode parse(Buf b, int formatVersion) {
        int count = b.i32();
        int stringBufferSize = b.i32();
        if (count < 0 || count > 200000) {
            throw new IllegalArgumentException("Jumlah node TypeTree tidak wajar: " + count);
        }
        boolean hasRefHash = formatVersion >= 19;
        int nodeSize = hasRefHash ? 32 : 24;
        int nodesBytes = count * nodeSize;
        b.need(nodesBytes);
        byte[] blob = b.b;
        int nodesStart = b.p;
        b.p += nodesBytes;
        byte[] strings = b.bytes(stringBufferSize);

        TypeNode fakeRoot = new TypeNode();
        fakeRoot.level = -1;
        ArrayList<TypeNode> stack = new ArrayList<>();
        stack.add(fakeRoot);
        TypeNode parent = fakeRoot;
        TypeNode prev = fakeRoot;

        for (int i = 0; i < count; i++) {
            int p = nodesStart + i * nodeSize;
            TypeNode n = new TypeNode();
            n.version = (blob[p] & 0xFF) | ((blob[p + 1] & 0xFF) << 8);
            n.level = blob[p + 2] & 0xFF;
            n.typeFlags = blob[p + 3] & 0xFF;
            long typeOff = u32(blob, p + 4);
            long nameOff = u32(blob, p + 8);
            n.byteSize = (int) u32(blob, p + 12);
            n.index = (int) u32(blob, p + 16);
            n.metaFlag = (int) u32(blob, p + 20);
            n.refTypeHash = hasRefHash ? (u32(blob, p + 24) | (u32(blob, p + 28) << 32)) : 0;
            n.type = resolve(strings, typeOff);
            n.name = resolve(strings, nameOff);

            if (n.level > prev.level) {
                stack.add(parent);
                parent = prev;
            } else if (n.level < prev.level) {
                while (!stack.isEmpty() && n.level <= parent.level) {
                    parent = stack.remove(stack.size() - 1);
                }
            }
            parent.children.add(n);
            prev = n;
        }
        if (fakeRoot.children.isEmpty()) {
            throw new IllegalArgumentException("TypeTree kosong");
        }
        return fakeRoot.children.get(0);
    }

    private static long u32(byte[] b, int p) {
        return (b[p] & 0xFFL) | ((b[p + 1] & 0xFFL) << 8) | ((b[p + 2] & 0xFFL) << 16)
                | ((b[p + 3] & 0xFFL) << 24);
    }

    private static String resolve(byte[] strings, long value) {
        if ((value & 0x80000000L) != 0) {
            return CommonStrings.get((int) (value & 0x7FFFFFFFL));
        }
        int off = (int) value;
        if (off < 0 || off >= strings.length) {
            return "";
        }
        int end = off;
        while (end < strings.length && strings[end] != 0) {
            end++;
        }
        return new String(strings, off, end - off, StandardCharsets.UTF_8);
    }
}
