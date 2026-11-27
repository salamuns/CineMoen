//list

package com.cinemoen.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.text.InputType
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {

    companion object {
        private val DEFAULT_SITES = listOf(
            "https://7movies.ac", 
            "https://popcornmovies.ac", 
            "https://mapple.rip", 
            "https://cinejoy.pk", 
            "https://movy.sx", 
            "https://pstream.cfd", 
            "https://shuttletv.su", 
            "https://cinetaro.to" 
            // tambah yg lain 
        )
        private const val OFFLINE_URL = "file:///android_asset/offline.html"

        // Skrip yang disuntikkan ke semua halaman & bingkai: tekan-tahan elemen (800 ms)
        // untuk menyembunyikannya. Aturan disimpan per situs. __TOKEN__ diganti saat dipasang.
        private val HIDE_JS = """
(function () {
  try {
    if (window.__cmHide) return;
    window.__cmHide = true;
    var TOKEN = "__TOKEN__";
    var KEY = "__cm_hide_" + TOKEN;
    var LONG_MS = 800, MOVE_PX = 12;
    var swallowUntil = 0;

    function load() {
      try {
        var v = JSON.parse(localStorage.getItem(KEY) || "[]");
        return Array.isArray(v) ? v : [];
      } catch (e) { return []; }
    }
    function save(list) {
      try { localStorage.setItem(KEY, JSON.stringify(list)); } catch (e) {}
    }

    var styleEl = null;
    function applyRules() {
      try {
        var list = load();
        if (!styleEl) {
          styleEl = document.createElement("style");
          styleEl.setAttribute("data-cm", "1");
        }
        if (!styleEl.parentNode) {
          var host = document.head || document.documentElement;
          if (!host) return;
          host.appendChild(styleEl);
        }
        styleEl.textContent = list.map(function (s) {
          return s + "{display:none!important}";
        }).join("\n");
      } catch (e) {}
    }

    function esc(s) {
      return (window.CSS && CSS.escape) ? CSS.escape(s) : s.replace(/[^\w-]/g, "_");
    }
    function uniq(sel) {
      try { return document.querySelectorAll(sel).length === 1; } catch (e) { return false; }
    }
    function pathSel(el) {
      var parts = [];
      while (el && el.nodeType === 1 && el !== document.documentElement) {
        if (el === document.body) { parts.unshift("body"); break; }
        var idx = 1, sib = el;
        while ((sib = sib.previousElementSibling)) {
          if (sib.tagName === el.tagName) idx++;
        }
        parts.unshift(el.tagName.toLowerCase() + ":nth-of-type(" + idx + ")");
        el = el.parentElement;
      }
      return parts.join(" > ");
    }
    function selectorFor(el) {
      if (el.id && uniq("#" + esc(el.id))) return "#" + esc(el.id);
      var cls = [];
      for (var i = 0; i < el.classList.length && cls.length < 3; i++) {
        var c = el.classList[i];
        if (c.length <= 30) cls.push("." + esc(c));
      }
      if (cls.length) {
        var s = el.tagName.toLowerCase() + cls.join("");
        if (uniq(s)) return s;
      }
      return pathSel(el);
    }

    function area(el) {
      var r = el.getBoundingClientRect();
      return Math.max(0, r.width) * Math.max(0, r.height);
    }
    function vpArea() { return Math.max(1, window.innerWidth * window.innerHeight); }
    function hasVideo(el) { return !!(el.querySelector && el.querySelector("video")); }
    function isFloating(cs) {
      return cs.position === "fixed" || cs.position === "absolute" || cs.position === "sticky";
    }

    function pickTarget(el) {
      if (!el || el === document.body || el === document.documentElement) return null;
      if (el.tagName === "VIDEO" || (el.closest && el.closest("video"))) return null;
      var limit = 0.9 * vpArea();
      var best = null, n = el;
      // 1) naik lewat rantai elemen melayang (fixed/absolute), tanpa melewati pemutar video
      while (n && n !== document.body && n !== document.documentElement) {
        if (hasVideo(n)) break;
        if (isFloating(getComputedStyle(n)) && area(n) < limit) best = n;
        n = n.parentElement;
      }
      // 2) jika tidak ada: naik selama pembungkusnya berukuran sama
      if (!best) {
        best = el;
        while (best.parentElement && best.parentElement !== document.body) {
          var p = best.parentElement;
          if (hasVideo(p)) break;
          var rb = best.getBoundingClientRect(), rp = p.getBoundingClientRect();
          if (rp.width <= rb.width * 1.1 + 4 && rp.height <= rb.height * 1.1 + 4) best = p;
          else break;
        }
      }
      if (area(best) >= limit) return null;
      return best;
    }

    function toast(msg, undo) {
      try {
        var old = document.getElementById("__cm_toast");
        if (old) old.remove();
        var d = document.createElement("div");
        d.id = "__cm_toast";
        d.style.cssText = "position:fixed;left:50%;bottom:24px;transform:translateX(-50%);" +
          "z-index:2147483647;background:rgba(20,20,30,.92);color:#fff;font:14px sans-serif;" +
          "padding:10px 14px;border-radius:10px;display:flex;gap:12px;align-items:center;max-width:90%";
        var s = document.createElement("span");
        s.textContent = msg;
        d.appendChild(s);
        if (undo) {
          var b = document.createElement("button");
          b.textContent = "Batal";
          b.style.cssText = "background:#3d7bff;color:#fff;border:0;border-radius:8px;" +
            "padding:6px 12px;font:14px sans-serif";
          b.addEventListener("click", function (ev) {
            ev.stopPropagation();
            undo();
            d.remove();
          });
          d.appendChild(b);
        }
        (document.body || document.documentElement).appendChild(d);
        setTimeout(function () { if (d.parentNode) d.remove(); }, 5000);
      } catch (e) {}
    }

    function hideAt(x, y) {
      var target = pickTarget(document.elementFromPoint(x, y));
      swallowUntil = Date.now() + 1200;
      if (!target) { toast("Elemen ini tidak bisa disembunyikan"); return; }
      var sel = selectorFor(target);
      var list = load();
      if (list.indexOf(sel) < 0) { list.push(sel); save(list); }
      applyRules();
      toast("Elemen disembunyikan", function () {
        var l = load(), i = l.indexOf(sel);
        if (i >= 0) { l.splice(i, 1); save(l); applyRules(); }
      });
    }

    var timer = null, sx = 0, sy = 0, fired = false;
    function cancel() { if (timer) { clearTimeout(timer); timer = null; } }
    var opt = { passive: true, capture: true };
    document.addEventListener("touchstart", function (e) {
      cancel();
      if (e.touches.length !== 1) return;
      var t = e.touches[0];
      sx = t.clientX; sy = t.clientY; fired = false;
      timer = setTimeout(function () { timer = null; fired = true; hideAt(sx, sy); }, LONG_MS);
    }, opt);
    document.addEventListener("touchmove", function (e) {
      var t = e.touches[0];
      if (!t) return;
      if (Math.abs(t.clientX - sx) > MOVE_PX || Math.abs(t.clientY - sy) > MOVE_PX) cancel();
    }, opt);
    document.addEventListener("touchend", cancel, opt);
    document.addEventListener("touchcancel", cancel, opt);
    document.addEventListener("contextmenu", function (e) { if (fired) e.preventDefault(); }, true);
    // Cegah "klik" ikutan yang muncul setelah tekan-tahan (supaya tidak mengenai elemen di bawahnya)
    document.addEventListener("click", function (e) {
      if (Date.now() < swallowUntil) { e.preventDefault(); e.stopPropagation(); }
    }, true);

    applyRules();
    document.addEventListener("DOMContentLoaded", applyRules);
  } catch (e) {}
})();

"""

        // Skrip kecil di setiap halaman/bingkai: saat jari menyentuh layar, laporkan ke aplikasi apakah
        // ada kotak gulir (di dalam halaman) yang sedang tergulir ke bawah. Dipakai agar
        // pull-to-refresh tidak ikut terpicu saat Anda menggulir ke atas di dalam kotak itu.
        private val SCROLL_JS = """
(function () {
  try {
    if (window.__cmScroll) return;
    window.__cmScroll = true;
    function canScrollUp(el) {
      while (el && el !== document.documentElement) {
        if (el.nodeType === 1) {
          var oy = getComputedStyle(el).overflowY;
          if ((oy === "auto" || oy === "scroll" || oy === "overlay") &&
              el.scrollHeight > el.clientHeight + 1 && el.scrollTop > 0) return true;
        }
        el = el.parentElement;
      }
      var se = document.scrollingElement;
      return !!(se && se.scrollTop > 0);
    }
    document.addEventListener("touchstart", function (e) {
      try {
        if (!window.cmScroll) return;
        window.cmScroll.postMessage(canScrollUp(e.target) ? "1" : "0");
      } catch (err) {}
    }, { passive: true, capture: true });
  } catch (e) {}
})();
"""

        // Akhiran domain dua tingkat (mis. "co.id") agar "a.co.id" dan "b.co.id" tidak dianggap satu situs
        private val SECOND_LEVEL_SUFFIXES = setOf(
            "co.id", "or.id", "ac.id", "go.id", "web.id", "my.id", "sch.id",
            "co.uk", "org.uk", "ac.uk", "gov.uk",
            "com.au", "net.au", "org.au",
            "co.jp", "co.in", "co.nz", "co.za", "co.kr",
            "com.br", "com.sg", "com.my", "com.tr", "com.pk", "com.hk", "com.tw", "com.ph", "com.vn", "com.mx"
        )
    }

    private lateinit var root: FrameLayout
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var failedUrl: String? = null
    @Volatile private var innerScrolled = false

    // Layar pilihan alamat
    private lateinit var picker: ScrollView
    private lateinit var pickerList: LinearLayout
    private lateinit var homeButton: ImageView

    // Mode Google TV / Android TV (dikendalikan dengan remote)
    // Ditentukan oleh varian build: true untuk APK TV, false untuk APK HP
    private val isTv: Boolean by lazy { resources.getBoolean(R.bool.is_tv) }
    private lateinit var cursorView: View
    private var cursorX = 0f
    private var cursorY = 0f
    private val cursorHandler = Handler(Looper.getMainLooper())
    private val hideCursor = Runnable { cursorView.alpha = 0f }
    private var tvHintShown = false
    private var hideScript: ScriptHandler? = null
    private var pickerVisible = true
    private var currentUrl = "https://7movies.ac"
    private var clearHistoryOnFinish = false

    // true sejak masuk mode PiP sampai aplikasi dibuka lagi (onResume)
    private var pipActive = false

    // Splash gambar saat aplikasi dibuka
    private var splashView: View? = null
    private val splashHandler = Handler(Looper.getMainLooper())
    private val splashRunnable = Runnable { dismissSplash() }
    private val prefs by lazy { getSharedPreferences("mapple", MODE_PRIVATE) }

    // Untuk upload file
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    // Ambil domain induk, misal "www.mapple.rip" -> "mapple.rip"
    private fun rootDomain(host: String): String {
        val h = host.lowercase()
        val parts = h.split(".")
        if (parts.size <= 2) return h
        val lastTwo = parts.takeLast(2).joinToString(".")
        return if (lastTwo in SECOND_LEVEL_SUFFIXES) parts.takeLast(3).joinToString(".") else lastTwo
    }

    // true jika host berasal dari situs yang sedang dibuka (atau salah satu alamat bawaan)
    private fun isSameSite(host: String?): Boolean {
        if (host == null) return false
        val target = rootDomain(host)
        if (DEFAULT_SITES.any { rootDomain(Uri.parse(it).host ?: "") == target }) return true
        val current = webView.url?.let { Uri.parse(it).host }
        return current != null && rootDomain(current) == target
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun loadCustomUrls(): List<String> {
        val raw = prefs.getString("custom_urls", "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    private fun saveCustomUrls(list: List<String>) {
        prefs.edit().putString("custom_urls", list.joinToString("\n")).apply()
    }

    // Rapikan input: tambahkan https:// jika belum ada; null jika tidak valid
    private fun normalizeUrl(raw: String): String? {
        var t = raw.trim()
        if (t.isEmpty() || t.contains(" ")) return null
        if (t.startsWith("http://")) t = "https://" + t.removePrefix("http://")
        if (!t.startsWith("https://")) t = "https://$t"
        val host = Uri.parse(t).host ?: return null
        if (!host.contains(".")) return null
        return t.trimEnd('/')
    }

    private fun makeRow(
        label: String,
        accent: Boolean,
        onClick: () -> Unit,
        onDelete: (() -> Unit)?
    ): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val bg = GradientDrawable()
        bg.setColor(if (accent) 0xFF3D7BFF.toInt() else 0xFF2B2B3D.toInt())
        bg.cornerRadius = dp(14).toFloat()
        row.background = bg
        row.setPadding(dp(20), dp(16), dp(12), dp(16))
        row.setOnClickListener { onClick() }
        // Remote/D-pad: baris bisa difokuskan dan diberi garis putih saat terpilih
        row.isFocusable = true
        row.setOnFocusChangeListener { _, has -> bg.setStroke(if (has) dp(3) else 0, Color.WHITE) }

        val tv = TextView(this)
        tv.text = label
        tv.textSize = 17f
        tv.setTextColor(Color.WHITE)
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (onDelete != null) {
            val x = TextView(this)
            x.text = "✕"
            x.textSize = 18f
            x.setTextColor(0xFFFF8A80.toInt())
            x.setPadding(dp(16), dp(4), dp(16), dp(4))
            x.setOnClickListener { onDelete() }
            x.isFocusable = true
            x.setOnFocusChangeListener { _, has ->
                x.setTextColor(if (has) Color.WHITE else 0xFFFF8A80.toInt())
            }
            row.addView(x)
        }

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(10)
        row.layoutParams = lp
        return row
    }

    private fun refreshPicker() {
        pickerList.removeAllViews()
        // Layar lebar (TV/landscape): batasi lebar daftar agar tidak terlalu melebar
        val side = maxOf(dp(24), (resources.displayMetrics.widthPixels - dp(640)) / 2)
        pickerList.setPadding(side, dp(72), side, dp(40))

        val title = TextView(this)
        title.text = "Pilih alamat"
        title.textSize = 24f
        title.setTextColor(Color.WHITE)
        val sub = TextView(this)
        sub.text = "Ketuk salah satu untuk membuka"
        sub.textSize = 14f
        sub.setTextColor(0xFFA0A0B8.toInt())
        sub.setPadding(0, dp(4), 0, dp(20))
        pickerList.addView(title)
        pickerList.addView(sub)

        for (url in DEFAULT_SITES) {
            pickerList.addView(makeRow(url.removePrefix("https://"), false, { openSite(url) }, null))
        }
        for (url in loadCustomUrls()) {
            pickerList.addView(
                makeRow(url.removePrefix("https://"), false, { openSite(url) }, { confirmDelete(url) })
            )
        }
        pickerList.addView(makeRow("+  Tambah URL lain", true, { showAddDialog() }, null))
        // Di TV, fokus awal ke pilihan pertama
        if (isTv) pickerList.getChildAt(2)?.requestFocus()
    }

    private fun showAddDialog() {
        val input = EditText(this)
        input.hint = "contoh: mapple.example"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        val holder = FrameLayout(this)
        holder.setPadding(dp(20), dp(8), dp(20), 0)
        holder.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        AlertDialog.Builder(this)
            .setTitle("Tambah URL")
            .setView(holder)
            .setPositiveButton("Simpan") { _, _ ->
                val url = normalizeUrl(input.text.toString())
                if (url == null) {
                    Toast.makeText(this, "Alamat tidak valid", Toast.LENGTH_SHORT).show()
                } else if (url in DEFAULT_SITES || url in loadCustomUrls()) {
                    Toast.makeText(this, "Alamat sudah ada", Toast.LENGTH_SHORT).show()
                } else {
                    saveCustomUrls(loadCustomUrls() + url)
                    refreshPicker()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDelete(url: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus alamat ini?")
            .setMessage(url.removePrefix("https://"))
            .setPositiveButton("Hapus") { _, _ ->
                saveCustomUrls(loadCustomUrls().filter { it != url })
                refreshPicker()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showPicker() {
        pickerVisible = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        swipe.visibility = View.GONE
        progress.visibility = View.GONE
        // Tampilkan dulu baru isi daftar, agar fokus remote (TV) ke pilihan pertama berhasil dipasang
        picker.visibility = View.VISIBLE
        refreshPicker()
        refreshHomeButton()
    }

    private fun openSite(url: String) {
        currentUrl = url
        failedUrl = null
        clearHistoryOnFinish = true
        pickerVisible = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        picker.visibility = View.GONE
        swipe.visibility = View.VISIBLE
        webView.loadUrl(url)
        refreshHomeButton()
        if (isTv && !tvHintShown) {
            tvHintShown = true
            Toast.makeText(
                this,
                "Panah: gerakkan kursor  |  OK: klik  |  Menu atau tahan Back: ke daftar situs",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Tampilan penuh sampai ke area notch/poni
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            // Android 11+: boleh memakai area poni di semua sisi (portrait & landscape)
            lp.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            window.attributes = lp
        }

        root = FrameLayout(this)
        webView = WebView(this)
        swipe = SwipeRefreshLayout(this).apply {
            addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 || innerScrolled }
            setOnRefreshListener {
                if (webView.url?.startsWith(OFFLINE_URL) == true) {
                    webView.loadUrl(failedUrl ?: currentUrl)
                } else {
                    webView.reload()
                }
            }
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(swipe, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8))

        pickerList = LinearLayout(this)
        pickerList.orientation = LinearLayout.VERTICAL
        pickerList.setPadding(dp(24), dp(72), dp(24), dp(40))
        picker = ScrollView(this)
        picker.setBackgroundColor(0xFF1E1E2E.toInt())
        picker.isFillViewport = true
        picker.addView(pickerList)
        root.addView(picker, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setupHomeButton()
        setupCursor()
        setContentView(root)
        applyFullscreen()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            // Izinkan gambar/video http di halaman https (konten aktif seperti skrip tetap diblokir)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Semua jendela baru (termasuk yang dibuka skrip setelah klik) dicegat lewat
            // onCreateWindow: hanya situs sendiri yang dibuka, domain lain diblokir.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }

        // Cookie (termasuk pihak ketiga) dibutuhkan situs seperti YouTube untuk tombol
        // persetujuan/verifikasi, login, dan pemutar video.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        installHideScript()
        installScrollBridge()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return when (uri.scheme) {
                    // Semua halaman web dibuka di dalam aplikasi
                    "http", "https", "file", "about", "data", "blob", "javascript" -> false
                    // Email / telepon diserahkan ke aplikasi terkait
                    "mailto", "tel" -> {
                        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
                        true
                    }
                    // Link "buka di aplikasi" (intent:) -> buka versi webnya di dalam aplikasi ini
                    "intent" -> {
                        try {
                            val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                            val target = intent.getStringExtra("browser_fallback_url") ?: intent.dataString
                            if (target != null && (target.startsWith("http://") || target.startsWith("https://"))) {
                                view.loadUrl(target)
                            }
                        } catch (_: Exception) { }
                        true
                    }
                    else -> true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
                swipe.isRefreshing = false
                if (clearHistoryOnFinish && url != null && url.startsWith("http")) {
                    view.clearHistory()
                    clearHistoryOnFinish = false
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame && !request.url.toString().startsWith(OFFLINE_URL)) {
                    failedUrl = request.url.toString()
                    view.loadUrl(OFFLINE_URL)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
            }

            // Popup / jendela baru: hanya situs sendiri yang dibuka, domain lain diblokir
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (resultMsg == null) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val temp = WebView(this@MainActivity)
                temp.webViewClient = object : WebViewClient() {
                    private var handled = false

                    private fun handle(v: WebView, url: String?) {
                        if (handled || url == null || url == "about:blank") return
                        handled = true
                        val host = Uri.parse(url).host
                        if (isSameSite(host)) {
                            webView.loadUrl(url)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Popup iklan diblokir",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        v.stopLoading()
                        Handler(Looper.getMainLooper()).post { v.destroy() }
                    }

                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        handle(v, request.url.toString())
                        return true
                    }

                    override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                        handle(v, url)
                    }
                }
                transport.webView = temp
                resultMsg.sendToTarget()
                return true
            }

            // Upload file
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: WebChromeClient.FileChooserParams?
            ): Boolean {
                if (callback == null || params == null) return false
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                try {
                    fileChooserLauncher.launch(params.createIntent())
                } catch (e: Exception) {
                    filePathCallback = null
                    callback.onReceiveValue(null)
                    Toast.makeText(this@MainActivity, "Tidak bisa membuka pemilih file", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            // Video fullscreen
            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                swipe.visibility = View.GONE
                refreshHomeButton()
                root.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                if (isTv) cursorView.bringToFront()
                applyFullscreen()
            }

            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                swipe.visibility = View.VISIBLE
                refreshHomeButton()
                applyFullscreen()
            }
        }

        // Link unduhan (file) diserahkan ke pengelola unduhan Android
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // Sedang splash: lewati ke layar pilihan
                    splashView != null -> dismissSplash()
                    // Di layar pilihan: keluar aplikasi
                    pickerVisible -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    customView != null -> webView.webChromeClient?.onHideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    // Sudah di halaman pertama: kembali ke layar pilihan
                    else -> showPicker()
                }
            }
        })

        if (savedInstanceState != null && !savedInstanceState.getBoolean("picker", true)) {
            currentUrl = savedInstanceState.getString("current") ?: currentUrl
            pickerVisible = false
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            picker.visibility = View.GONE
            swipe.visibility = View.VISIBLE
            webView.restoreState(savedInstanceState)
            refreshHomeButton()
        } else {
            showPicker()
            // Splash hanya saat aplikasi baru dibuka (bukan saat dipulihkan sistem)
            if (savedInstanceState == null) showSplash()
        }
    }

    // Splash: poster logo tampil sebentar, ketuk layar untuk melewati
    private fun showSplash() {
        val view: View
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            view = buildLandscapeSplash()
        } else {
            val image = ImageView(this)
            image.setImageResource(R.drawable.splash_poster)
            // Isi seluruh layar; kelebihan atas/bawah gambar (putih & biru) dipotong sesuai rasio layar
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setBackgroundColor(0xFF063449.toInt())
            view = image
        }
        view.setOnClickListener { dismissSplash() }

        // Warnai latar jendela & status bar putih selama splash agar strip atas menyatu dengan poster
        setWindowColors(0xFFFEFEFE.toInt())
        splashView = view
        root.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        splashHandler.postDelayed(splashRunnable, 2500)
        refreshHomeButton()
    }

    // Splash untuk layar lebar (TV / landscape): logo di tengah dengan nama aplikasi
    private fun buildLandscapeSplash(): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setBackgroundColor(0xFFFEFEFE.toInt())

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_launcher_foreground)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        val size = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        box.addView(logo, LinearLayout.LayoutParams(size, size))

        val name = TextView(this)
        name.text = "CineMoen"
        name.textSize = 34f
        name.setTextColor(0xFF0A3349.toInt())
        name.gravity = Gravity.CENTER
        box.addView(
            name,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return box
    }

    private fun dismissSplash() {
        val v = splashView ?: return
        splashView = null
        refreshHomeButton()
        splashHandler.removeCallbacks(splashRunnable)
        v.animate().alpha(0f).setDuration(350).withEndAction {
            root.removeView(v)
            setWindowColors(0xFF1E1E2E.toInt()) // kembalikan warna latar aplikasi
        }.start()
    }

    private fun setWindowColors(color: Int) {
        window.setBackgroundDrawable(ColorDrawable(color))
        window.statusBarColor = color
    }

    // ===== Google TV / Android TV: kursor virtual yang digerakkan dengan panah remote =====
    private fun setupCursor() {
        val size = dp(26)
        cursorView = View(this)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(0xCCFFFFFF.toInt())
        bg.setStroke(dp(3), 0xFF1E1E2E.toInt())
        cursorView.background = bg
        cursorView.alpha = 0f
        cursorView.visibility = if (isTv) View.VISIBLE else View.GONE
        root.addView(cursorView, FrameLayout.LayoutParams(size, size))
        root.post {
            cursorX = root.width / 2f
            cursorY = root.height / 2f
            updateCursor()
        }
    }

    private fun updateCursor() {
        cursorView.translationX = cursorX - dp(13)
        cursorView.translationY = cursorY - dp(13)
    }

    private fun showCursorBriefly() {
        cursorView.alpha = 1f
        cursorHandler.removeCallbacks(hideCursor)
        cursorHandler.postDelayed(hideCursor, 4000)
    }

    private fun moveCursor(keyCode: Int, repeat: Int) {
        // Makin lama tombol ditahan, makin cepat
        val step = dp(14) * (1f + minOf(repeat, 20) / 4f)
        var dx = 0f
        var dy = 0f
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> dy = -step
            KeyEvent.KEYCODE_DPAD_DOWN -> dy = step
            KeyEvent.KEYCODE_DPAD_LEFT -> dx = -step
            else -> dx = step
        }
        val maxX = root.width - 1f
        val maxY = root.height - 1f
        val edge = dp(48)
        // Kursor di tepi atas/bawah: gulir halaman
        if (customView == null) {
            if (dy < 0f && cursorY <= edge) webView.scrollBy(0, (dy * 4f).toInt())
            if (dy > 0f && cursorY >= maxY - edge) webView.scrollBy(0, (dy * 4f).toInt())
        }
        cursorX = (cursorX + dx).coerceIn(0f, maxX)
        cursorY = (cursorY + dy).coerceIn(0f, maxY)
        updateCursor()
        showCursorBriefly()
    }

    // Tombol OK pada remote = ketuk layar di posisi kursor
    private fun clickAt(x: Float, y: Float) {
        val target: View = customView ?: webView
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        target.dispatchTouchEvent(down)
        down.recycle()
        cursorHandler.postDelayed({
            val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            target.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
        showCursorBriefly()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isTv && !pickerVisible && splashView == null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) moveCursor(event.keyCode, event.repeatCount)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        clickAt(cursorX, cursorY)
                    }
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (event.action == KeyEvent.ACTION_UP) goHome()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // Layar diputar / ukuran berubah: sesuaikan lebar daftar situs
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (pickerVisible) refreshPicker()
    }

    // Tombol rumah melayang: ketuk untuk kembali ke daftar situs, geser untuk memindahkannya
    private fun setupHomeButton() {
        val size = dp(44)
        homeButton = ImageView(this)
        homeButton.setImageResource(R.drawable.ic_home)
        homeButton.setPadding(dp(10), dp(10), dp(10), dp(10))
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(0xCC1E1E2E.toInt())
        homeButton.background = bg
        homeButton.alpha = 0.7f
        homeButton.contentDescription = "Kembali ke daftar situs"
        homeButton.visibility = View.GONE

        val lp = FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.BOTTOM)
        lp.rightMargin = dp(8)
        lp.bottomMargin = dp(140)
        root.addView(homeButton, lp)

        var downX = 0f
        var downY = 0f
        var startTx = 0f
        var startTy = 0f
        var moved = false
        var downAt = 0L
        homeButton.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startTx = v.translationX
                    startTy = v.translationY
                    moved = false
                    downAt = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true
                    if (moved) {
                        val minX = -v.left.toFloat()
                        val maxX = (root.width - v.left - v.width).toFloat()
                        val minY = -v.top.toFloat()
                        val maxY = (root.height - v.top - v.height).toFloat()
                        v.translationX = (startTx + dx).coerceIn(minX, maxX)
                        v.translationY = (startTy + dy).coerceIn(minY, maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (System.currentTimeMillis() - downAt > 700) confirmResetHidden() else goHome()
                    }
                    true
                }
                else -> true
            }
        }
    }

    // Tombol rumah hanya tampil saat sedang membuka website
    private fun refreshHomeButton() {
        val show = !pickerVisible && splashView == null && customView == null && !isTv
        homeButton.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Langsung ke daftar situs, apa pun halaman yang sedang dibuka
    private fun goHome() {
        if (splashView != null) dismissSplash()
        if (customView != null) webView.webChromeClient?.onHideCustomView()
        showPicker()
    }

    // Tahan tombol Back untuk langsung ke daftar situs
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && !pickerVisible) {
            goHome()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    // Pasang skrip penyembunyi elemen ke semua halaman (termasuk di dalam pemutar/bingkai)
    private fun installHideScript() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val token = prefs.getInt("hide_token", 1)
        hideScript?.remove()
        hideScript = WebViewCompat.addDocumentStartJavaScript(
            webView,
            HIDE_JS.replace("__TOKEN__", token.toString()),
            setOf("*")
        )
    }

    // Terima laporan dari SCROLL_JS (kotak gulir di dalam halaman) untuk pull-to-refresh
    private fun installScrollBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        try {
            WebViewCompat.addWebMessageListener(webView, "cmScroll", setOf("*")) { _, message, _, _, _ ->
                innerScrolled = (message.data == "1")
            }
            WebViewCompat.addDocumentStartJavaScript(webView, SCROLL_JS, setOf("*"))
        } catch (e: Exception) {
            // Jika gagal, pull-to-refresh tetap jalan seperti biasa
        }
    }

    private fun confirmResetHidden() {
        AlertDialog.Builder(this)
            .setTitle("Tampilkan lagi elemen yang disembunyikan?")
            .setMessage("Semua elemen yang pernah Anda sembunyikan akan muncul kembali.")
            .setPositiveButton("Tampilkan lagi") { _, _ ->
                prefs.edit().putInt("hide_token", prefs.getInt("hide_token", 1) + 1).apply()
                installHideScript()
                webView.reload()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(this, "Jenis unduhan ini belum didukung", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url))
            if (mimeType != null) request.setMimeType(mimeType)
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent)
            val cookies = CookieManager.getInstance().getCookie(url)
            if (cookies != null) request.addRequestHeader("Cookie", cookies)
            request.setTitle(fileName)
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Mengunduh: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Cadangan: buka link di aplikasi/browser lain
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e2: Exception) {
                Toast.makeText(this, "Gagal mengunduh", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Sembunyikan status bar & navigation bar. Geser dari tepi layar untuk memunculkannya sebentar.
    private fun applyFullscreen() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("picker", pickerVisible)
        outState.putString("current", currentUrl)
        webView.saveState(outState)
    }

    // Tekan Home saat video layar penuh -> video mengecil jadi jendela PiP
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (customView != null) {
            try {
                pipActive = true
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                pipActive = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pipActive = false
        webView.onResume()
    }

    override fun onPause() {
        // Saat masuk PiP, video harus tetap berjalan
        if (!pipActive) webView.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Jendela PiP ditutup (bukan diperbesar): hentikan video dan suaranya
        if (pipActive) webView.onPause()
    }

    override fun onDestroy() {
        splashHandler.removeCallbacks(splashRunnable)
        root.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}
