package com.uabea.mobile.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Indeks seluruh aset di dalam bundle + path container dari AssetBundle. */
public final class AssetIndex {
    public final UnityBundle bundle;
    public final ArrayList<Asset> assets = new ArrayList<>();
    public final HashMap<Long, String> containers = new HashMap<>();
    public String bundleName = "";

    private AssetIndex(UnityBundle bundle) {
        this.bundle = bundle;
    }

    public static AssetIndex open(byte[] data) throws IOException {
        UnityBundle ub;
        if (data != null && data.length >= 8 && data[0] == 'U' && data[1] == 'n' && data[2] == 'i' && data[3] == 't'
                && data[4] == 'y' && data[5] == 'F' && data[6] == 'S' && data[7] == 0) {
            ub = UnityBundle.load(data);
        } else if (SerializedFile.looksLikeSerializedFile(data)) {
            ub = UnityBundle.wrap(data);
        } else {
            String sig = UnityBundle.detectSignature(data);
            throw new IOException(sig.isEmpty()
                    ? "File tidak dikenali sebagai AssetBundle Unity."
                    : "Format belum didukung: \"" + sig + "\" (hanya UnityFS dan SerializedFile).");
        }
        AssetIndex idx = new AssetIndex(ub);
        int fileId = 0;
        for (UnityBundle.Node n : ub.nodes) {
            if (n.sf == null) {
                continue;
            }
            for (SerializedFile.ObjectInfo o : n.sf.objects) {
                Asset a = new Asset(ub, n.sf, o);
                a.fileId = fileId;
                a.filePath = n.path == null ? "" : n.path;
                idx.assets.add(a);
            }
            fileId++;
        }
        idx.readContainers();
        idx.readNames();
        return idx;
    }

    private void readContainers() {
        for (Asset a : assets) {
            if (a.classId != 142) { // AssetBundle
                continue;
            }
            try {
                Value v = a.parse();
                Value name = v.child("m_AssetBundleName");
                if (name != null && bundleName.isEmpty()) {
                    bundleName = name.asString();
                }
                Value cont = v.child("m_Container");
                if (cont == null) {
                    continue;
                }
                for (Value pair : cont.children) {
                    Value first = pair.child("first");
                    Value second = pair.child("second");
                    if (first == null || second == null) {
                        continue;
                    }
                    long pid = findPathId(second);
                    if (pid != 0) {
                        containers.put(pid, first.asString());
                    }
                }
            } catch (RuntimeException ignored) {
                // Container opsional.
            }
        }
    }

    private static long findPathId(Value v) {
        if (v.node != null && "m_PathID".equals(v.node.name)) {
            return v.asLong();
        }
        for (Value c : v.children) {
            long r = findPathId(c);
            if (r != 0) {
                return r;
            }
        }
        return 0;
    }

    private void readNames() {
        for (Asset a : assets) {
            String c = containers.get(a.pathId);
            if (c != null) {
                a.container = c;
            }
            try {
                TypeNode root = a.typeNode();
                byte[] raw = a.raw();
                String n = TypeTreeIO.peekString(root, raw, "m_Name", "m_AssetBundleName");
                if (n != null) {
                    a.name = n;
                }
            } catch (RuntimeException ignored) {
                // Nama opsional; tetap tampil dengan pathId.
            }
        }
    }

    public List<Asset> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(assets);
        }
        String q = query.toLowerCase();
        List<Asset> r = new ArrayList<>();
        for (Asset a : assets) {
            if (a.displayName().toLowerCase().contains(q) || a.typeName.toLowerCase().contains(q)
                    || (a.container != null && a.container.toLowerCase().contains(q))) {
                r.add(a);
            }
        }
        return r;
    }

    public Asset findByPathId(long pathId) {
        for (Asset a : assets) {
            if (a.pathId == pathId) {
                return a;
            }
        }
        return null;
    }

    public Map<Long, String> containerMap() {
        return containers;
    }
}
