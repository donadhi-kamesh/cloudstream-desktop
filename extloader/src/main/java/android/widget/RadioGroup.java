package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class RadioGroup extends LinearLayout {
    private int checkedId = -1;
    private OnCheckedChangeListener listener;

    public RadioGroup(Context context) { super(context); }
    public RadioGroup(Context context, AttributeSet attrs) { super(context, attrs); }

    public void check(int id) { checkedId = id; }
    public int getCheckedRadioButtonId() { return checkedId; }
    public void clearCheck() { checkedId = -1; }
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) { this.listener = listener; }

    @Override public void addView(View child) {
        super.addView(child);
        if (child instanceof RadioButton && ((RadioButton) child).isChecked()) {
            checkedId = child.getId();
        }
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(RadioGroup group, int checkedId);
    }
}
