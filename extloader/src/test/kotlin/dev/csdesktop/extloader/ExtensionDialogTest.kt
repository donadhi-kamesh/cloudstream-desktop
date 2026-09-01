package dev.csdesktop.extloader

import android.app.AlertDialog
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the two ways extension UI used to come up blank: a dialog whose rows are added
 * after `show()`, and a notice popup that was misrouted to the captcha browser.
 */
class ExtensionDialogTest {

    /**
     * The shape Cricify-style plugins inflate: a header, a scrollable but *empty*
     * container with id `list`, and a save button. Rows are added at runtime.
     */
    private val settingsLayout = """
        <?xml version="1.0" encoding="utf-8"?>
        <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:orientation="vertical"
            android:padding="12dp">
            <TextView
                android:id="@+id/header_tw"
                android:text="Cricify Settings"
                android:textSize="18sp"
                android:textStyle="bold" />
            <ScrollView android:layout_width="match_parent">
                <LinearLayout
                    android:orientation="vertical"
                    android:layout_width="match_parent"
                    android:id="@+id/list" />
            </ScrollView>
            <ImageButton
                android:id="@+id/save_btn"
                android:contentDescription="Save settings" />
        </LinearLayout>
    """.trimIndent()

    private fun inflate(xml: String, resources: Resources? = null): View {
        val file = File.createTempFile("layout", ".xml").apply { writeText(xml); deleteOnExit() }
        val parser: XmlResourceParser = XmlResourceParser.open(file, null)
        if (resources != null) parser.owner = resources
        val context = Context()
        val view = LayoutInflater.from(context).inflate(parser, null, false)
        assertNotNull(view)
        return view
    }

    private fun checkBox(context: Context, label: String, checked: Boolean): CheckBox =
        CheckBox(context).apply {
            text = label
            setChecked(checked)
        }

    // ---- rows added after the dialog is already on screen ----

    @Test
    fun rowsAddedAfterMountAppearInTheDialog() {
        val root = inflate(settingsLayout)
        val list = root.findViewByName<LinearLayout>("list")
        assertNotNull(list, "the container the plugin populates must be findable by name")

        val container = JPanel()
        val mounted = ViewRenderer.mount(container, root) {}
        assertEquals(0, checkBoxes(container).size, "nothing is added yet")

        // What the plugin does after show(): one row per provider.
        val context = Context()
        listOf("Willow TV", "TNT Sports", "Fox Cricket").forEachIndexed { index, name ->
            list.addView(checkBox(context, name, index == 0))
        }
        mounted.refresh()

        val boxes = checkBoxes(container)
        assertEquals(3, boxes.size, "every runtime-added provider row must render")
        assertEquals(listOf("Willow TV", "TNT Sports", "Fox Cricket"), boxes.map { it.text })
        assertTrue(boxes[0].isSelected, "checked state must carry over")
        assertFalse(boxes[1].isSelected)
    }

    @Test
    fun runtimeAdditionsRenderWithoutAnExplicitRefresh() {
        val root = inflate(settingsLayout)
        val list = root.findViewByName<LinearLayout>("list")!!
        val container = JPanel()
        ViewRenderer.mount(container, root) {}

        val context = Context()
        repeat(4) { list.addView(checkBox(context, "Provider $it", false)) }

        // The tree listener rebuilds on the EDT after a short debounce.
        assertTrue(
            awaitCheckBoxes(container, 4),
            "adding views must trigger a rebuild on its own, got ${checkBoxes(container).size}",
        )
    }

    @Test
    fun removingRowsClearsThem() {
        val root = inflate(settingsLayout)
        val list = root.findViewByName<LinearLayout>("list")!!
        val container = JPanel()
        val mounted = ViewRenderer.mount(container, root) {}
        val context = Context()
        repeat(3) { list.addView(checkBox(context, "Provider $it", false)) }
        mounted.refresh()
        assertEquals(3, checkBoxes(container).size)

        list.removeAllViews()
        mounted.refresh()
        assertEquals(0, checkBoxes(container).size, "removeAllViews must empty the dialog body")
    }

    @Test
    fun rowsKeepTheirNaturalHeightInAVerticalStack() {
        val root = inflate(settingsLayout)
        val list = root.findViewByName<LinearLayout>("list")!!
        val container = JPanel()
        val mounted = ViewRenderer.mount(container, root) {}
        val context = Context()
        repeat(5) { list.addView(checkBox(context, "Provider $it", false)) }
        mounted.refresh()

        for (box in checkBoxes(container)) {
            assertEquals(
                box.preferredSize.height,
                box.maximumSize.height,
                "a row must not be allowed to stretch vertically",
            )
        }
    }

    @Test
    fun aDialogBodyIsNeverEmpty() {
        // A layout with nothing renderable still has to say something.
        val root = inflate(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical" />
            """.trimIndent()
        )
        val container = JPanel()
        ViewRenderer.mount(container, root) {}
        assertTrue(container.componentCount > 0, "an empty layout must still produce a body")
    }

    // ---- checkbox / text binding ----

    @Test
    fun checkboxTogglesBothWaysAndFiresPluginListeners() {
        val root = inflate(settingsLayout)
        val list = root.findViewByName<LinearLayout>("list")!!
        val box = checkBox(Context(), "Willow TV", false)
        val changes = ArrayList<Boolean>()
        box.setOnCheckedChangeListener { _, isChecked -> changes.add(isChecked) }
        list.addView(box)

        val container = JPanel()
        val mounted = ViewRenderer.mount(container, root) {}
        mounted.refresh()
        val swing = checkBoxes(container).single()

        swing.doClick()
        assertTrue(box.isChecked, "clicking the Swing checkbox must update the android view")
        assertEquals(listOf(true), changes, "the plugin's listener must fire")

        box.setChecked(false)
        assertFalse(swing.isSelected, "an android-side change must update the Swing checkbox")
    }

    @Test
    fun textChangedAfterMountIsShown() {
        val root = inflate(settingsLayout)
        val header = root.findViewByName<TextView>("header_tw")!!
        val container = JPanel()
        val mounted = ViewRenderer.mount(container, root) {}
        assertTrue(labels(container).any { it == "Cricify Settings" })

        header.text = "Cricify Settings (3 enabled)"
        mounted.refresh()
        assertTrue(
            labels(container).any { it == "Cricify Settings (3 enabled)" },
            "an updated label must re-render, got ${labels(container)}",
        )
    }

    // ---- id resolution across Resources instances ----

    @Test
    fun idsResolveThroughADifferentResourcesInstance() {
        // Plugins look ids up via their own Resources: plugin.resources.getIdentifier(...).
        // That instance is not the one the inflater used, so both must agree.
        val pluginResources = Resources(AssetManager())
        val root = inflate(settingsLayout, pluginResources)

        val other = Resources(AssetManager())
        val listId = other.getIdentifier("list", "id", "com.example.plugin")
        assertTrue(listId != 0, "getIdentifier must not return 0 for a known name")

        val found = root.findViewById<View>(listId)
        assertNotNull(found, "findViewById must work with an id from another Resources instance")
        assertTrue(found is LinearLayout)
        assertSame(root.findViewByName<View>("list"), found)
    }

    @Test
    fun unknownIdsReturnNull() {
        val root = inflate(settingsLayout)
        assertNull(root.findViewById<View>(Resources(AssetManager()).getIdentifier("nope", "id", "x")))
        assertNull(root.findViewByName<View>("nope"))
    }

    // ---- captcha routing ----

    @Test
    fun aNoticeWithDontShowAgainStaysAnOrdinaryDialog() {
        val context = Context()
        val body = LinearLayout(context)
        body.addView(TextView(context).apply { text = "Cricify uses public playlists." })
        body.addView(checkBox(context, "Don't show again", false))

        assertFalse(
            AlertDialog.routesToBrowser("Notice", "Please read before continuing.", body),
            "a plugin notice must not be sent to the captcha browser",
        )
    }

    @Test
    fun realBotChecksStillGoToTheBrowser() {
        val context = Context()
        val webViewBody = LinearLayout(context)
        webViewBody.addView(android.webkit.WebView(context))
        assertTrue(
            AlertDialog.routesToBrowser("Verification", null, webViewBody),
            "a dialog hosting a WebView belongs in the browser window",
        )
        assertTrue(AlertDialog.routesToBrowser("Cloudflare", null, null))
        assertTrue(AlertDialog.routesToBrowser(null, "Solve the captcha to continue", null))
    }

    @Test
    fun everydayWordsDoNotTriggerTheBrowser() {
        for (text in listOf(
            "Don't show again",
            "Verify your account settings",
            "Use the d-pad to select a provider",
            "Select the providers you want to load",
        )) {
            assertFalse(AlertDialog.looksLikeCaptcha(text), "\"$text\" must not read as a bot check")
        }
    }

    @Test
    fun noticeDialogExposesItsCustomViewForFindViewById() {
        val root = inflate(settingsLayout)
        val dialog = AlertDialog.Builder(Context())
            .setTitle("Cricify")
            .setMessage("Pick your providers")
            .setView(root)
            .create()
        val list = dialog.findViewByName("list")
        assertNotNull(list, "dialog.findViewById must reach the custom view, not an empty decor view")
        assertSame(root.findViewByName<View>("list"), list)
    }

    @Test
    fun recyclerViewAdapterRowsRender() {
        val context = Context()
        val list = androidx.recyclerview.widget.RecyclerView(context)
        list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        list.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            private val names = listOf("Willow TV", "TNT Sports", "Fox Cricket")
            override fun getItemCount() = names.size
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) =
                androidx.recyclerview.widget.RecyclerView.ViewHolder(CheckBox(context))
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as CheckBox).text = names[position]
            }
        }
        val rendered = ViewRenderer.render(list)
        assertNotNull(rendered)
        assertEquals(3, checkBoxes(rendered!!).size, "RecyclerView adapter rows must appear in the settings sheet")
        assertEquals(listOf("Willow TV", "TNT Sports", "Fox Cricket"), checkBoxes(rendered).map { it.text })
    }

    @Test
    fun pluginLoadDoesNotOpenSettingsByItself() {
        ExtensionUi.suppressPopups = true
        try {
            val before = ExtensionUi.sessions.value.size
            ExtensionUi.present("Cricify", "Don't show again", CheckBox(Context()), emptyList(), null)
            assertEquals(before, ExtensionUi.sessions.value.size, "load()-time popups must wait for Settings")
        } finally {
            ExtensionUi.suppressPopups = false
        }
    }

    // ---- helpers ----

    private fun components(root: java.awt.Component): List<java.awt.Component> {
        val out = ArrayList<java.awt.Component>()
        out.add(root)
        if (root is java.awt.Container) root.components.forEach { out.addAll(components(it)) }
        return out
    }

    private fun checkBoxes(root: java.awt.Component): List<JCheckBox> =
        components(root).filterIsInstance<JCheckBox>()

    private fun labels(root: java.awt.Component): List<String> =
        components(root).filterIsInstance<JLabel>().map { stripHtml(it.text) }

    private fun stripHtml(text: String?): String =
        (text ?: "").replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "").trim()

    /** Drains the EDT until the debounced rebuild has produced [expected] checkboxes. */
    private fun awaitCheckBoxes(container: JPanel, expected: Int): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (SwingUtilities.isEventDispatchThread()) {
                if (checkBoxes(container).size == expected) return true
                return false
            }
            var count = 0
            SwingUtilities.invokeAndWait { count = checkBoxes(container).size }
            if (count == expected) return true
            Thread.sleep(25)
        }
        return false
    }
}
