package android.content.res;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
public final class AssetManager {
    private File assetsDir;
    public AssetManager() {}
    public void setAssetsDir(File dir) { this.assetsDir = dir; }
    public InputStream open(String fileName) throws IOException {
        if (assetsDir == null) throw new IOException("No assets: " + fileName);
        File f = new File(assetsDir, fileName);
        if (!f.isFile()) throw new IOException("Missing asset: " + fileName);
        return new FileInputStream(f);
    }
    public String[] list(String path) { return new String[0]; }
    public int addAssetPath(String path) { return 0; }
}
