package android.text;

public interface Editable extends CharSequence, Appendable {
    Editable replace(int st, int en, CharSequence source, int start, int end);
    Editable replace(int st, int en, CharSequence text);
    Editable insert(int where, CharSequence text, int start, int end);
    Editable insert(int where, CharSequence text);
    Editable delete(int st, int en);
    void clear();
    @Override Editable append(CharSequence text);
    @Override Editable append(CharSequence text, int start, int end);
    @Override Editable append(char text);
}
