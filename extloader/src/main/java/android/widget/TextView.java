package android.widget;

import android.content.Context;
import android.view.View;

public class TextView extends View {
    protected CharSequence text = "";
    protected CharSequence hint = "";
    private final java.util.List<android.text.TextWatcher> watchers = new java.util.ArrayList<>();
    public TextView(Context context) { super(context); }
    public void setText(CharSequence t) {
        CharSequence next = t == null ? "" : t;
        if (next.toString().contentEquals(text)) return;
        CharSequence prev = text;
        text = next;
        for (android.text.TextWatcher w : watchers) {
            try {
                w.beforeTextChanged(prev, 0, prev.length(), next.length());
                w.onTextChanged(next, 0, prev.length(), next.length());
                w.afterTextChanged(new android.text.SpannableStringBuilder(next));
            } catch (Throwable ignored) {}
        }
        notifyDesktopTreeChanged();
    }
    public void setText(int resId) {
        if (context == null) return;
        try {
            CharSequence resolved = context.getText(resId);
            if (resolved != null && resolved.length() > 0) setText(resolved);
        } catch (Throwable ignored) {
        }
    }
    public CharSequence getText() { return text; }
    public void setHint(CharSequence hint) { this.hint = hint == null ? "" : hint; }
    public CharSequence getHint() { return hint; }
    public void setTextSize(float size) {}
    public void setTextColor(int color) {}
    public void setHintTextColor(int color) {}
    public void setSingleLine(boolean single) {}
    public void setMaxLines(int lines) {}
    public void setMinLines(int lines) {}
    public void setGravity(int gravity) {}
    public void setTypeface(android.graphics.Typeface tf) {}
    public void setTypeface(android.graphics.Typeface tf, int style) {}
    public void setCompoundDrawables(android.graphics.drawable.Drawable l, android.graphics.drawable.Drawable t, android.graphics.drawable.Drawable r, android.graphics.drawable.Drawable b) {}
    public void setCompoundDrawablePadding(int pad) {}
    public void setEllipsize(android.text.TextUtils.TruncateAt where) {}
    public void setAllCaps(boolean allCaps) {}
    public void setIncludeFontPadding(boolean include) {}
    public void setLineSpacing(float add, float mult) {}
    public void setShadowLayer(float radius, float dx, float dy, int color) {}
    public void append(CharSequence text) { setText(String.valueOf(this.text) + text); }
    public void addTextChangedListener(android.text.TextWatcher watcher) {
        if (watcher != null) watchers.add(watcher);
    }
    public void removeTextChangedListener(android.text.TextWatcher watcher) {
        watchers.remove(watcher);
    }
    public void setFilters(android.text.InputFilter[] filters) {}
    public android.text.Editable getEditableText() {
        return new android.text.SpannableStringBuilder(text);
    }
    public TextView(Context context, android.util.AttributeSet attrs) { super(context); }
    public TextView(Context context, android.util.AttributeSet attrs, int defStyleAttr) { super(context); }
}
