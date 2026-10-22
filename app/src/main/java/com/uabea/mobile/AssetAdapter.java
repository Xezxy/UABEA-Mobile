package com.uabea.mobile;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.uabea.mobile.core.Asset;

import java.util.ArrayList;
import java.util.List;

/** Adapter daftar aset bergaya DataGrid UABEA. */
public final class AssetAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final ArrayList<Asset> items = new ArrayList<>();
    private Ui ui;
    private Asset selected;

    public AssetAdapter(Context context, Ui ui) {
        this.inflater = LayoutInflater.from(context);
        this.ui = ui;
    }

    public void setUi(Ui ui) {
        this.ui = ui;
        notifyDataSetChanged();
    }

    public void setAssets(List<Asset> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void setSelected(Asset a) {
        selected = a;
        notifyDataSetChanged();
    }

    public Asset getSelected() {
        return selected;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Asset getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = inflater.inflate(R.layout.item_asset, parent, false);
        }
        Asset a = items.get(position);
        TextView badge = v.findViewById(R.id.itemBadge);
        TextView name = v.findViewById(R.id.itemName);
        TextView meta = v.findViewById(R.id.itemMeta);
        TextView type = v.findViewById(R.id.itemType);
        TextView size = v.findViewById(R.id.itemSize);

        int rowBg = (a == selected) ? ui.selection : (position % 2 == 0 ? ui.zebra : ui.bg);
        v.setBackgroundColor(rowBg);

        String label = Ui.typeLabel(a.typeName);
        int color = Ui.typeColor(a.typeName);
        badge.setText(label);
        badge.setTextColor(color);
        GradientDrawable bg = ui.box(v.getContext(), ui.alpha(color, ui.dark ? 0x33 : 0x22), 0, 3);
        badge.setBackground(bg);

        String display = a.displayName();
        if (a.modified) {
            SpannableString sp = new SpannableString("\u25CF " + display);
            sp.setSpan(new ForegroundColorSpan(ui.danger), 0, 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            name.setText(sp);
        } else {
            name.setText(display);
        }
        name.setTextColor(ui.text);

        String sub;
        if (a.container != null && !a.container.isEmpty()) {
            sub = a.container;
        } else {
            sub = "berkas " + (a.fileId + 1) + " \u00b7 pathId " + a.pathId;
        }
        meta.setText(sub);
        meta.setTextColor(ui.muted);

        type.setText(a.typeName);
        type.setTextColor(ui.dark ? 0xFFC8C8C8 : 0xFF3C3C3C);
        size.setText(Util.formatSize(a.obj.byteSize));
        size.setTextColor(ui.muted);
        return v;
    }
}
