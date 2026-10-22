package com.uabea.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Palet dan gaya kontrol bergaya UABEA (tema terang/gelap). */
final class Ui {
    final boolean dark;
    final int bg;
    final int panel;
    final int panelAlt;
    final int header;
    final int text;
    final int muted;
    final int border;
    final int accent;
    final int selection;
    final int button;
    final int buttonPressed;
    final int zebra;
    final int danger;
    final int inputBg;
    final int statusBg;

    final Typeface sans = Typeface.create("sans-serif", Typeface.NORMAL);
    final Typeface sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    final Typeface mono = Typeface.MONOSPACE;

    Ui(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = 0xFF1B1B1C;
            panel = 0xFF252526;
            panelAlt = 0xFF2D2D30;
            header = 0xFF333337;
            text = 0xFFF1F1F1;
            muted = 0xFFA9A9A9;
            border = 0xFF3F3F46;
            accent = 0xFF3794FF;
            selection = 0xFF094771;
            button = 0xFF333337;
            buttonPressed = 0xFF45454B;
            zebra = 0xFF202021;
            danger = 0xFFE06C75;
            inputBg = 0xFF1F1F20;
            statusBg = 0xFF2D2D30;
        } else {
            bg = 0xFFF3F3F3;
            panel = 0xFFFFFFFF;
            panelAlt = 0xFFF7F7F7;
            header = 0xFFEAEAEA;
            text = 0xFF1B1B1B;
            muted = 0xFF5F5F5F;
            border = 0xFFC9C9C9;
            accent = 0xFF0067C0;
            selection = 0xFFCCE8FF;
            button = 0xFFFDFDFD;
            buttonPressed = 0xFFE5F1FB;
            zebra = 0xFFF8F8F8;
            danger = 0xFFC42B1C;
            inputBg = 0xFFFFFFFF;
            statusBg = 0xFFEAEAEA;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    GradientDrawable box(Context c, int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        if (stroke != 0) {
            g.setStroke(Math.max(1, dp(c, 1)), stroke);
        }
        g.setCornerRadius(dp(c, radius));
        return g;
    }

    RippleDrawable ripple(Context c, int fill, int stroke, int rippleColor, float radius) {
        GradientDrawable content = box(c, fill, stroke, radius);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    void actionButton(Button b, boolean primary) {
        Context c = b.getContext();
        b.setAllCaps(false);
        b.setTextSize(11.5f);
        b.setTypeface(sansMedium);
        b.setTextColor(primary ? Color.WHITE : text);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 3), 0, dp(c, 3), 0);
        b.setIncludeFontPadding(false);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundTintList(null);
        b.setBackground(ripple(c, primary ? accent : button, primary ? accent : border,
                primary ? alpha(Color.WHITE, 0x33) : alpha(accent, 0x33), 2));
        b.setStateListAnimator(null);
    }

    void input(EditText e) {
        Context c = e.getContext();
        e.setBackground(box(c, inputBg, border, 2));
        e.setTextColor(text);
        e.setHintTextColor(muted);
        e.setTextSize(13f);
        e.setTypeface(sans);
        e.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        e.setSingleLine(true);
    }

    void tint(ProgressBar p) {
        if (p.getIndeterminateDrawable() != null) {
            p.getIndeterminateDrawable().setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        }
    }

    View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(border);
        v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    static int typeColor(String type) {
        if (type == null) {
            return 0xFF8A8A8A;
        }
        switch (type) {
            case "TextAsset":
                return 0xFF3B8FE0;
            case "Texture2D":
            case "Sprite":
                return 0xFF46A45E;
            case "AudioClip":
                return 0xFFD08A18;
            case "Mesh":
            case "GameObject":
                return 0xFF9A78D0;
            case "Material":
            case "Shader":
                return 0xFFE0A030;
            case "AnimationClip":
            case "AnimatorController":
                return 0xFFD06A9A;
            case "Font":
                return 0xFF56B4B4;
            case "MonoScript":
                return 0xFF7FA650;
            case "AssetBundle":
                return 0xFFC07050;
            default:
                return 0xFF8A8A8A;
        }
    }

    static String typeLabel(String type) {
        if (type == null) {
            return "OBJ";
        }
        switch (type) {
            case "TextAsset":
                return "TXT";
            case "Texture2D":
            case "Sprite":
                return "IMG";
            case "AudioClip":
                return "SND";
            case "Mesh":
                return "MSH";
            case "GameObject":
                return "GO";
            case "Material":
                return "MAT";
            case "Shader":
                return "SHD";
            case "AnimationClip":
            case "AnimatorController":
                return "ANM";
            case "Font":
                return "FNT";
            case "MonoScript":
                return "CS";
            case "AssetBundle":
                return "AB";
            default:
                String s = type.isEmpty() ? "OBJ" : type;
                return s.substring(0, Math.min(3, s.length())).toUpperCase(java.util.Locale.US);
        }
    }

    static int px(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    void menuItem(TextView tv) {
        tv.setTextColor(text);
        tv.setTextSize(13f);
        tv.setTypeface(sans);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(tv.getContext(), 10), 0, dp(tv.getContext(), 10), 0);
    }
}
