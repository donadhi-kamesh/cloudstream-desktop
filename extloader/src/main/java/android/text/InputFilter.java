package android.text;

public interface InputFilter {
    CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend);

    class LengthFilter implements InputFilter {
        private final int max;
        public LengthFilter(int max) { this.max = max; }
        public int getMax() { return max; }
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            return null;
        }
    }
}
