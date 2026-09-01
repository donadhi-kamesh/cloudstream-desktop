package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class PreferenceFragmentCompat extends DialogFragment {
    private final List<PrefRow> rows = new ArrayList<>();

    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {}

    public void setPreferencesFromResource(int preferencesResId, String key) {
        addPreferencesFromResource(preferencesResId);
    }

    public void addPreferencesFromResource(int preferencesResId) {
        File xml = android.content.res.Resources.xmlFileForId(getContext(), preferencesResId);
        if (xml != null && xml.isFile()) parsePreferenceXml(xml);
    }

    public Preference findPreference(CharSequence key) {
        if (key == null) return null;
        for (PrefRow row : rows) {
            if (key.toString().equals(row.key)) return row.pref;
        }
        Preference created = new Preference(getContext());
        created.setKey(key.toString());
        rows.add(new PrefRow(key.toString(), created, "string"));
        return created;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onCreatePreferences(savedInstanceState, null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        if (rows.isEmpty()) {
            File xml = android.content.res.Resources.firstXmlFile(ctx);
            if (xml != null) parsePreferenceXml(xml);
        }
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx);
        for (PrefRow row : rows) {
            TextView label = new TextView(ctx);
            label.setText(row.title != null ? row.title : row.key);
            root.addView(label);
            if ("switch".equals(row.kind) || "checkbox".equals(row.kind)) {
                CheckBox box = new CheckBox(ctx);
                boolean def = Boolean.parseBoolean(row.defaultValue);
                box.setChecked(prefs.getBoolean(row.key, def));
                box.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean(row.key, checked).apply());
                root.addView(box);
            } else {
                EditText edit = new EditText(ctx);
                edit.setText(prefs.getString(row.key, row.defaultValue != null ? row.defaultValue : ""));
                edit.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) prefs.edit().putString(row.key, edit.getText() != null ? edit.getText().toString() : "").apply();
                });
                root.addView(edit);
            }
        }
        if (rows.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("No settings entries found for this extension.");
            root.addView(empty);
        }
        setView(root);
        return root;
    }

    private void parsePreferenceXml(File xml) {
        try {
            Element doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml).getDocumentElement();
            walk(doc);
        } catch (Throwable ignored) {}
    }

    private void walk(Element el) {
        String tag = el.getTagName();
        String key = attr(el, "key");
        if (key != null) {
            Preference pref = new Preference(getContext());
            pref.setKey(key);
            pref.setTitle(attr(el, "title"));
            String kind = tag.toLowerCase().contains("switch") || tag.toLowerCase().contains("check") ? "switch" : "string";
            PrefRow row = new PrefRow(key, pref, kind);
            row.title = attr(el, "title");
            row.defaultValue = attr(el, "defaultValue");
            rows.add(row);
        }
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element) walk((Element) n);
        }
    }

    private static String attr(Element el, String name) {
        if (el.hasAttribute(name)) return el.getAttribute(name);
        String ns = "http://schemas.android.com/apk/res/android";
        if (el.hasAttributeNS(ns, name)) return el.getAttributeNS(ns, name);
        String prefixed = el.getAttribute("android:" + name);
        return prefixed.isEmpty() ? null : prefixed;
    }

    static final class PrefRow {
        final String key;
        final Preference pref;
        final String kind;
        String title;
        String defaultValue;
        PrefRow(String key, Preference pref, String kind) {
            this.key = key;
            this.pref = pref;
            this.kind = kind;
        }
    }
}
