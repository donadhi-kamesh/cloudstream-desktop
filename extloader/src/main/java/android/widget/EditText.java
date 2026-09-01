package android.widget;

import android.content.Context;
import android.text.InputType;

public class EditText extends TextView {
    public EditText(Context context) { super(context); }
    public EditText(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    public EditText(Context context, android.util.AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
    public void setInputType(int type) { /* InputType.TYPE_CLASS_* */ }
    public void setSelection(int index) {}
    public void setMinLines(int lines) {}
    @SuppressWarnings("unused")
    private int ignoredInputType = InputType.TYPE_CLASS_TEXT;
}
