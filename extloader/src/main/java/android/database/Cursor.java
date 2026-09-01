package android.database;
public interface Cursor extends java.io.Closeable {
    int getCount();
    boolean moveToFirst();
    boolean moveToNext();
    String getString(int columnIndex);
    int getInt(int columnIndex);
    long getLong(int columnIndex);
    int getColumnIndex(String columnName);
    int getColumnIndexOrThrow(String columnName);
    void close();
    boolean isClosed();
}
