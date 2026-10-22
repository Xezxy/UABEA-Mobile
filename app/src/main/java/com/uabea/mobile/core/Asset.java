package com.uabea.mobile.core;

/** Satu aset (objek) di dalam SerializedFile. */
public final class Asset {
    public final UnityBundle bundle;
    public final SerializedFile sf;
    public final SerializedFile.ObjectInfo obj;
    public final String typeName;
    public final long pathId;
    public final int classId;
    public String name = "";
    public String container = "";
    /** Indeks SerializedFile di dalam bundle (0-based). */
    public int fileId;
    /** Path node SerializedFile di dalam bundle. */
    public String filePath = "";
    public boolean modified;

    public Asset(UnityBundle bundle, SerializedFile sf, SerializedFile.ObjectInfo obj) {
        this.bundle = bundle;
        this.sf = sf;
        this.obj = obj;
        this.typeName = sf.typeName(obj);
        this.pathId = obj.pathId;
        this.classId = obj.classId;
    }

    public TypeNode typeNode() {
        TypeNode n = sf.types.get(obj.typeId).node;
        if (n == null) {
            throw new IllegalStateException("TypeTree untuk " + typeName + " tidak tersedia");
        }
        return n;
    }

    public byte[] raw() {
        return sf.objectData(obj);
    }

    public Value parse() {
        return TypeTreeIO.read(typeNode(), raw());
    }

    public void setData(byte[] data) {
        sf.setObjectData(obj, data);
        obj.byteSize = data.length;
        modified = true;
    }

    public String displayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (container != null && !container.isEmpty()) {
            int i = container.lastIndexOf('/');
            return i >= 0 ? container.substring(i + 1) : container;
        }
        return typeName + " #" + pathId;
    }
}
