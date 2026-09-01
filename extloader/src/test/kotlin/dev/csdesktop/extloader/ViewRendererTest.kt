package dev.csdesktop.extloader

import android.content.Context
import android.content.res.XmlResourceParser
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end check for the extension settings rendering pipeline: an inflated
 * Android layout (like Cricify's BottomSheet settings) must produce a real Swing
 * component tree instead of a blank dialog.
 */
class ViewRendererTest {
    private val layout = """
        <?xml version="1.0" encoding="utf-8"?>
        <RelativeLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:background="#FF141418"
            android:padding="12dp">
            <TextView
                android:id="@+id/header_tw"
                android:text="Cricify Settings"
                android:textSize="18sp"
                android:textStyle="bold" />
            <TextView
                android:id="@+id/header2_tw"
                android:text="Select the providers you want to load"
                android:textSize="14sp" />
            <ScrollView>
                <LinearLayout
                    android:orientation="vertical"
                    android:id="@+id/list">
                    <CheckBox android:id="@+id/row_willow" android:text="Will TV" android:checked="true" />
                    <CheckBox android:id="@+id/row_tnt" android:text="TNT Sports" />
                </LinearLayout>
            </ScrollView>
            <ImageButton
                android:id="@+id/save_btn"
                android:contentDescription="Save settings"
                android:layout_alignParentBottom="true" />
        </RelativeLayout>
    """.trimIndent()

    private fun inflate(): View {
        val file = File.createTempFile("settings-layout", ".xml").apply { writeText(layout) }
        val parser: XmlResourceParser = XmlResourceParser.open(file, null)
        val view = LayoutInflater.from(Context()).inflate(parser, null, false)
        assertNotNull(view)
        return view
    }

    @Test
    fun rendersCheckboxRowsAndSaveButton() {
        val root = inflate()
        val rendered = ViewRenderer.render(root)
        assertNotNull(rendered, "root must render to a Swing panel")

        val checkboxes = collect(rendered).filterIsInstance<javax.swing.JCheckBox>()
        assertEquals(2, checkboxes.size, "both playlist checkboxes must render")

        val labels = collect(rendered).filterIsInstance<javax.swing.JLabel>()
            .map { stripHtml(it.text) }
        assertTrue(labels.any { it == "Cricify Settings" }, "header text must render, got $labels")
        assertTrue(labels.any { it.contains("Select the providers") }, "sub header must render, got $labels")

        val buttons = collect(rendered).filterIsInstance<javax.swing.JButton>()
        assertEquals(1, buttons.size)
        assertEquals("Save settings", buttons[0].text)
    }

    @Test
    fun saveButtonTriggersAndroidClickListener() {
        val root = inflate()
        val save: android.view.View = root.findViewById(hash("save_btn"))
        var clicks = 0
        save.setOnClickListener { clicks++ }
        val rendered = ViewRenderer.render(root)!!
        val button = collect(rendered).filterIsInstance<javax.swing.JButton>().first()
        button.doClick()
        assertEquals(1, clicks)
    }

    @Test
    fun checkboxStateBindsBothWays() {
        val root = inflate()
        val willow = root.findViewById(hash("row_willow")) as CheckBox
        assertTrue(willow.isChecked, "android:checked=true must be honored")
        val rendered = ViewRenderer.render(root)!!
        val box = collect(rendered).filterIsInstance<javax.swing.JCheckBox>().first()
        assertTrue(box.isSelected)

        // Swing -> Android
        box.doClick()
        assertTrue(!willow.isChecked)
        // Android -> Swing
        willow.setChecked(true)
        assertTrue(box.isSelected)
    }

    @Test
    fun imageButtonFoundById() {
        val root = inflate()
        val save: android.view.View = root.findViewById(hash("save_btn"))
        assertTrue(save is ImageButton)
    }

    @Test
    fun textViewTextIsReadable() {
        val root = inflate()
        val header = root.findViewById(hash("header_tw")) as TextView
        assertEquals("Cricify Settings", header.text)
    }

    private fun hash(name: String): Int = ("id/" + name).hashCode().let { Math.abs(it) }

    private fun collect(component: java.awt.Component): List<java.awt.Component> {
        val out = ArrayList<java.awt.Component>()
        out.add(component)
        if (component is java.awt.Container) {
            for (child in component.components) out.addAll(collect(child))
        }
        return out
    }

    private fun stripHtml(text: String): String =
        text.replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
}
