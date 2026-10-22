package com.uabea.mobile.core;

import java.nio.charset.StandardCharsets;

/** Menampilkan isi objek berdasarkan TypeTree (fitur "View Data" ala UABEA). */
public final class DataDump {
    private static final int MAX_LINES = 4000;
    private static final int MAX_CHARS = 120000;
    private static final int MAX_ARRAY_ITEMS = 48;

    private DataDump() {
    }

    private static final class Ctx {
        int lines;
        boolean truncated;
    }

    public static String fields(Asset a) {
        Value root = a.parse();
        StringBuilder sb = new StringBuilder(8192);
        sb.append(a.typeName).append("  pathId=").append(a.pathId)
                .append("  file=").append(a.fileId).append('\n');
        sb.append("offset=").append(a.obj.byteStart).append("  ukuran=").append(a.obj.byteSize)
                .append(" byte\n\n");
        Ctx c = new Ctx();
        appendValue(root, 0, sb, c);
        if (c.truncated) {
            sb.append("\n… keluaran dipotong karena data terlalu besar.");
        }
        return sb.toString();
    }

    public static String typeTree(Asset a) {
        TypeNode root = a.typeNode();
        StringBuilder sb = new StringBuilder(8192);
        Ctx c = new Ctx();
        appendType(root, 0, sb, c);
        if (c.truncated) {
            sb.append("\n… pohon tipe dipotong.");
        }
        return sb.toString();
    }

    private static boolean add(StringBuilder sb, String text, Ctx c) {
        if (c.truncated) {
            return false;
        }
        sb.append(text).append('\n');
        c.lines++;
        if (c.lines >= MAX_LINES || sb.length() >= MAX_CHARS) {
            c.truncated = true;
            return false;
        }
        return true;
    }

    private static String indent(int depth) {
        StringBuilder sb = new StringBuilder(depth * 2);
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    private static String label(Value v) {
        if (v.node == null) {
            return "?";
        }
        return v.node.name == null || v.node.name.isEmpty() ? v.node.type : v.node.name;
    }

    private static boolean appendValue(Value v, int depth, StringBuilder sb, Ctx c) {
        if (c.truncated || v == null) {
            return false;
        }
        String ind = indent(depth);
        String name = label(v);
        switch (v.kind) {
            case Value.PRIM:
                return add(sb, ind + name + " = " + primitive(v.node.type, v.bytes(), 0) + "   (" + v.node.type + ")", c);
            case Value.STRING: {
                String s = stringPreview(v, 180);
                String tail = v.len > 180 ? "… (" + v.len + " byte)" : "";
                return add(sb, ind + name + " = \"" + escape(s) + "\"" + tail, c);
            }
            case Value.RAW: {
                String extra = v.lengthPrefixed ? " (diawali panjang)" : "";
                return add(sb, ind + name + " = <" + v.len + " byte" + extra + "> "
                        + hexAt(v.replaced != null ? v.replaced : v.src,
                        v.replaced != null ? 0 : v.off, Math.min(v.len, 24)), c);
            }
            case Value.ARRAY:
                return appendArray(v, depth, ind, name, sb, c);
            case Value.STRUCT: {
                if (!add(sb, ind + name + "  (" + v.node.type + ")", c)) {
                    return false;
                }
                for (Value ch : v.children) {
                    if (!appendValue(ch, depth + 1, sb, c)) {
                        return false;
                    }
                }
                return true;
            }
            default:
                return add(sb, ind + name + " = ?", c);
        }
    }

    private static boolean appendArray(Value v, int depth, String ind, String name, StringBuilder sb, Ctx c) {
        TypeNode arr = v.node.children.get(0);
        TypeNode sub = arr.children.size() > 1 ? arr.children.get(1) : null;
        if (!add(sb, ind + name + "  (" + v.node.type + ")  jumlah=" + v.count, c)) {
            return false;
        }
        if (!v.children.isEmpty()) {
            int n = Math.min(v.children.size(), MAX_ARRAY_ITEMS);
            for (int i = 0; i < n; i++) {
                if (!appendValue(v.children.get(i), depth + 1, sb, c)) {
                    return false;
                }
            }
            if (v.children.size() > n) {
                add(sb, ind + "  … " + (v.children.size() - n) + " elemen lagi", c);
            }
            return !c.truncated;
        }
        if (sub != null) {
            int es = TypeTreeIO.primSize(sub.type);
            if (es > 0) {
                int n = Math.min(v.count, 12);
                StringBuilder line = new StringBuilder(ind).append("  nilai:");
                byte[] src = v.replaced != null ? v.replaced : v.src;
                int base = v.replaced != null ? 0 : v.off;
                for (int i = 0; i < n; i++) {
                    line.append(' ').append(primitive(sub.type, src, base + i * es));
                }
                if (v.count > n) {
                    line.append(" …");
                }
                return add(sb, line.toString(), c);
            }
        }
        return add(sb, ind + "  <" + v.len + " byte data>", c);
    }

    private static void appendType(TypeNode n, int depth, StringBuilder sb, Ctx c) {
        if (c.truncated) {
            return;
        }
        String flags = "";
        if ((n.metaFlag & 0x4000) != 0) {
            flags += " align";
        }
        add(sb, indent(depth) + n.type + (n.name.isEmpty() ? "" : "  " + n.name)
                + "   size=" + n.byteSize + " flags=0x" + Integer.toHexString(n.metaFlag) + flags, c);
        for (TypeNode ch : n.children) {
            appendType(ch, depth + 1, sb, c);
        }
    }

    private static String stringPreview(Value v, int max) {
        byte[] src = v.replaced != null ? v.replaced : v.src;
        int off = v.replaced != null ? 0 : v.off;
        int len = Math.min(v.len, max);
        if (src == null || off < 0 || off + len > src.length) {
            return "";
        }
        return new String(src, off, len, StandardCharsets.UTF_8);
    }

    private static String primitive(String type, byte[] d, int p) {
        switch (type) {
            case "bool":
                return d[p] != 0 ? "true" : "false";
            case "SInt8":
                return Byte.toString(d[p]);
            case "UInt8":
            case "char":
                return Integer.toString(d[p] & 0xFF);
            case "short":
            case "SInt16":
                return Short.toString((short) le(d, p, 2));
            case "unsigned short":
            case "UInt16":
                return Integer.toString((int) le(d, p, 2));
            case "int":
            case "SInt32":
            case "Type*":
                return Integer.toString((int) le(d, p, 4));
            case "unsigned int":
            case "UInt32":
                return Long.toString(le(d, p, 4));
            case "long long":
            case "SInt64":
                return Long.toString(le(d, p, 8));
            case "unsigned long long":
            case "UInt64":
            case "FileSize":
                return Long.toString(le(d, p, 8));
            case "float":
                return Float.toString(Float.intBitsToFloat((int) le(d, p, 4)));
            case "double":
                return Double.toString(Double.longBitsToDouble(le(d, p, 8)));
            default:
                int n = Math.min(8, d.length - p);
                return hexAt(d, p, Math.max(0, n));
        }
    }

    private static long le(byte[] d, int p, int n) {
        long v = 0;
        if (p < 0) {
            return 0;
        }
        for (int i = 0; i < n && p + i < d.length; i++) {
            v |= (long) (d[p + i] & 0xFF) << (8 * i);
        }
        return v;
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format(java.util.Locale.US, "\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    private static String hexAt(byte[] d, int p, int n) {
        if (d == null || p < 0 || p >= d.length) {
            return "";
        }
        n = Math.min(n, d.length - p);
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(n * 3);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            int b = d[p + i] & 0xFF;
            sb.append(hex[b >> 4]).append(hex[b & 0xF]);
        }
        if (n > 0 && p + n < d.length) {
            sb.append(" …");
        }
        return sb.toString();
    }
}
