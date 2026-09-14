package com.example.travianfarmassistant

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import org.json.JSONArray

class CelebrationActivity : Activity() {

    companion object {
        private const val PREFS = "config"
        private const val VILLAGE_DATA_KEY = "village_data_json"
        private const val DEFAULT_SERVER = "https://ts20.x2.europe.travian.com"

        private fun server(value: String): String {
            var s = value.trim().ifBlank { DEFAULT_SERVER }
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
            return s.trimEnd('/')
        }
    }

    private data class VillageSource(
        val id: String,
        val newdid: String,
        val village: String
    )

    private data class Row(
        val id: String,
        val newdid: String,
        val village: String,
        var autoParty: Boolean,
        var culture: String = "0",
        var ongoing: String? = null
    )

    private val handler = Handler(Looper.getMainLooper())
    private val rows = linkedMapOf<String, Row>()
    private var villages = emptyList<VillageSource>()
    private var index = 0
    private var running = false
    private var currentId = ""

    private lateinit var db: CelebrationDatabase
    private lateinit var table: TableLayout
    private lateinit var status: TextView
    private lateinit var refresh: Button
    private var webView: WebView? = null

    private val timeout = Runnable {
        if (running) nextVillage()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = CelebrationDatabase(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun navButton(label: String, action: () -> Unit): Button =
            Button(this).apply {
                text = label
                textSize = 11f
                setOnClickListener { action() }
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
        nav.addView(navButton("Farm Res Builder") { finish() })
        nav.addView(navButton("Capacity") {
            startActivity(android.content.Intent(this@CelebrationActivity, MainActivity::class.java)
                .putExtra("openTab", "capacity"))
        })
        nav.addView(navButton("DB Overview") {
            startActivity(android.content.Intent(this@CelebrationActivity, MainActivity::class.java)
                .putExtra("openTab", "db"))
        })
        nav.addView(navButton("Log") {
            startActivity(android.content.Intent(this@CelebrationActivity, MainActivity::class.java)
                .putExtra("openTab", "log"))
        })

        refresh = Button(this).apply {
            text = "Refresh Celebration"
            setOnClickListener { startRefresh() }
        }
        status = TextView(this).apply {
            text = "Ready"
            setPadding(4, 4, 4, 8)
        }
        table = TableLayout(this).apply {
            isStretchAllColumns = false
            isShrinkAllColumns = false
        }

        val horizontal = HorizontalScrollView(this).apply { addView(table) }
        val scroll = ScrollView(this).apply { addView(horizontal) }

        root.addView(nav)
        root.addView(refresh)
        root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        loadVillagesFromResourceBuilderDb()
        renderTable()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!running || currentId.isBlank()) return
                    handler.postDelayed({ inspectPage() }, 1200)
                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        db.close()
        super.onDestroy()
    }

    /**
     * Resource Builder database saat ini disimpan sebagai village_data_json.
     * Celebration hanya MEMBACA data ini; tidak mengubahnya.
     * Id dari Resource Builder dipakai sebagai newdid Celebration.
     */
    private fun loadVillagesFromResourceBuilderDb() {
        val raw = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(VILLAGE_DATA_KEY, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        val seen = mutableSetOf<String>()
        val out = mutableListOf<VillageSource>()

        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("Id").trim()
            if (id.isBlank() || !seen.add(id)) continue

            val name = o.optString("NamaVillage").trim().ifBlank { "Village $id" }
            out += VillageSource(
                id = id,
                newdid = id,
                village = name
            )
        }

        villages = out
        rows.clear()
        villages.forEach { source ->
            val saved = db.get(source.id)
            rows[source.id] = Row(
                id = source.id,
                newdid = source.newdid,
                village = source.village,
                autoParty = saved?.isAutoParty ?: false,
                culture = saved?.culturePoint?.toString() ?: "0",
                ongoing = saved?.ongoingCelebration
            )
        }
    }

    private fun saveRow(row: Row) {
        db.upsert(
            id = row.id,
            newdid = row.newdid,
            village = row.village,
            culturePoint = row.culture.toIntOrNull() ?: 0,
            ongoingCelebration = row.ongoing,
            isAutoParty = row.autoParty
        )
    }

    private fun renderTable() {
        table.removeAllViews()
        val header = TableRow(this)
        addCell(header, "isAutoParty", true)
        addCell(header, "Village", true)
        addCell(header, "Culture Point", true)
        addCell(header, "Ongoing Celebration", true)
        table.addView(header)

        rows.values.forEach { row ->
            val tr = TableRow(this)
            val cb = CheckBox(this).apply {
                isChecked = row.autoParty
                setOnCheckedChangeListener { _, checked ->
                    row.autoParty = checked
                    saveRow(row)
                }
            }
            tr.addView(cb)
            addCell(tr, row.village, false)
            addCell(tr, row.culture, false)
            addCell(tr, row.ongoing ?: "null", false)
            table.addView(tr)
        }
    }

    private fun addCell(row: TableRow, text: String, header: Boolean) {
        val tv = TextView(this).apply {
            this.text = text
            setPadding(10, 10, 10, 10)
            gravity = Gravity.CENTER_VERTICAL
            if (header) {
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundColor(Color.LTGRAY)
            }
        }
        row.addView(tv)
    }

    private fun startRefresh() {
        if (running) return
        loadVillagesFromResourceBuilderDb()
        renderTable()
        if (villages.isEmpty()) {
            status.text = "Tidak ada village di database Resource Builder."
            return
        }

        running = true
        index = 0
        currentId = ""
        refresh.isEnabled = false
        nextVillage()
    }

    private fun nextVillage() {
        if (!running) return

        if (index >= villages.size) {
            running = false
            currentId = ""
            refresh.isEnabled = true
            status.text = "Refresh Celebration selesai."
            return
        }

        val source = villages[index]
        currentId = source.id
        status.text = "Refresh Celebration: ${index + 1}/${villages.size} — ${source.village}"

        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 10000)

        val s = server(
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("server", DEFAULT_SERVER).orEmpty()
        )

        // newdid WAJIB berasal dari database Resource Builder (Id village).
        webView?.loadUrl("$s/build.php?id=30&gid=24&newdid=${source.newdid}")
    }

    private fun inspectPage() {
        if (!running || currentId.isBlank()) return

        val js = """
            (() => {
                const clean = s => (s || '')
                    .replace(/[\u200B-\u200F\u202A-\u202E\u2060-\u206F]/g, ' ')
                    .replace(/\s+/g, ' ').trim();

                const townHall = document.querySelector('h1.titleInHeader');
                const hasTownHall = !!townHall && /Town Hall/i.test(clean(townHall.innerText || townHall.textContent));

                // Jika Town Hall tidak ditemukan, data harus dianggap 0/null.
                if (!hasTownHall) {
                    return JSON.stringify({
                        hasTownHall: false,
                        culture: 0,
                        ongoing: null,
                        hold: false
                    });
                }

                let culture = 0;
                const points = document.querySelector('span.points');
                if (points) {
                    const m = clean(points.innerText || points.textContent).match(/([\d.,]+)/);
                    if (m) culture = parseInt(m[1].replace(/[^0-9]/g, ''), 10) || 0;
                }

                let ongoing = null;
                const timer = document.querySelector('table.under_progress tbody tr td.dur span.timer');
                if (timer) {
                    ongoing = clean(timer.innerText || timer.textContent) || null;
                }

                const visible = e => {
                    if (!e) return false;
                    const s = getComputedStyle(e), r = e.getBoundingClientRect();
                    return s.display !== 'none' && s.visibility !== 'hidden' &&
                           r.width > 0 && r.height > 0;
                };

                const buttons = [...document.querySelectorAll(
                    'button,input[type=button],input[type=submit],a,[role=button]'
                )].filter(visible);
                const hold = buttons.find(e => {
                    const t = clean(e.innerText || e.textContent || e.value ||
                                    e.title || e.getAttribute('aria-label'));
                    return /^hold$/i.test(t) && !e.disabled &&
                           e.getAttribute('aria-disabled') !== 'true';
                });

                return JSON.stringify({
                    hasTownHall: true,
                    culture: culture,
                    ongoing: ongoing,
                    hold: !!hold
                });
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js) { raw ->
            val result = runCatching {
                org.json.JSONTokener(raw.orEmpty()).nextValue().toString()
            }.getOrElse {
                raw.orEmpty().trim().removeSurrounding("\"")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }

            val hasTownHall = Regex("\\\"hasTownHall\\\"\\s*:\\s*(true|false)")
                .find(result)?.groupValues?.getOrNull(1) == "true"
            val culture = Regex("\\\"culture\\\"\\s*:\\s*(\\d+)")
                .find(result)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val ongoingRaw = Regex("\\\"ongoing\\\"\\s*:\\s*(null|\\\"([^\\\"]*)\\\")")
                .find(result)?.groupValues?.getOrNull(2)
            val ongoing = if (hasTownHall) ongoingRaw?.ifBlank { null } else null
            val hold = Regex("\\\"hold\\\"\\s*:\\s*true").containsMatchIn(result)

            val row = rows[currentId]
            if (row != null) {
                if (hasTownHall) {
                    row.culture = culture.toString()
                    row.ongoing = ongoing
                } else {
                    row.culture = "0"
                    row.ongoing = null
                }
                saveRow(row)
            }

            renderTable()

            if (hasTownHall && row?.autoParty == true && isZero(ongoing) && hold) {
                clickHold()
            } else {
                finishVillage()
            }
        }
    }

    private fun isZero(s: String?): Boolean =
        s == "00:00" || s == "0:00" || s == "00:00:00" || s == "0:00:00"

    private fun clickHold() {
        val js = """
            (() => {
                const clean = s => (s || '').replace(/\s+/g,' ').trim().toLowerCase();
                const visible = e => {
                    if (!e) return false;
                    const s = getComputedStyle(e), r = e.getBoundingClientRect();
                    return s.display !== 'none' && s.visibility !== 'hidden' &&
                           r.width > 0 && r.height > 0;
                };
                const all = [...document.querySelectorAll(
                    'button,input[type=button],input[type=submit],a,[role=button]'
                )];
                const b = all.find(e => visible(e) && !e.disabled &&
                    e.getAttribute('aria-disabled') !== 'true' &&
                    clean(e.innerText || e.textContent || e.value ||
                          e.title || e.getAttribute('aria-label')) === 'hold');
                if (!b) return 'not-found';
                b.scrollIntoView({block:'center'});
                b.click();
                return 'clicked';
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js) {
            handler.postDelayed({ if (running) finishVillage() }, 1000)
        }
    }

    private fun finishVillage() {
        if (!running) return
        handler.removeCallbacks(timeout)
        index++
        currentId = ""
        handler.postDelayed({ if (running) nextVillage() }, 500)
    }
}
