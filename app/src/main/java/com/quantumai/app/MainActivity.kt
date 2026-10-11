package com.quantumai.app

import android.content.*
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.random.Random

val GOLD = Color(0xFFD4AF37)
val THEMES = listOf(
    darkColorScheme(primary = GOLD, background = Color(0xFF0B0B0F), surface = Color(0xFF17171C), onBackground = Color.White, onSurface = Color.White),
    lightColorScheme(primary = Color(0xFF1A73E8), background = Color(0xFFF6F8FC), surface = Color.White),
    lightColorScheme(primary = Color(0xFF6B8E23), background = Color(0xFFEAF3DC), surface = Color(0xFFF4F9EC)),
    darkColorScheme(primary = Color(0xFFB26CFF), background = Color(0xFF140A22), surface = Color(0xFF21123A), onBackground = Color.White, onSurface = Color.White))
val THEME_NAMES = listOf("Black", "White", "Pistachio", "Purple")

fun nowStr(): String = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

class AppState(ctx: Context) {
    private val p = ctx.getSharedPreferences("q", 0)
    var nick by mutableStateOf(p.getString("nick", "Quantum User")!!)
    var uid by mutableStateOf(p.getString("uid", null) ?: ("" + (1..9).random() + (1..14).map { (0..9).random() }.joinToString("")).also { p.edit().putString("uid", it).apply() })
    var theme by mutableStateOf(p.getInt("theme", 0))
    var lang by mutableStateOf(p.getString("lang", "en")!!)
    var pin by mutableStateOf(p.getString("pin", "")!!)
    var real by mutableStateOf(p.getFloat("real", 0f).toDouble())     // внесено, можно вернуть
    var bonus by mutableStateOf(p.getFloat("bonus", 0f).toDouble())   // только на траты в ИИ, не выводится
    var lastDep by mutableStateOf(p.getLong("lastDep", 0L))
    var tier by mutableStateOf(p.getInt("tier", -1))
    // формат записи: "P:дата|сумма|детали" (в обработке) или "D:дата|сумма|детали" (выполнено)
    var hist by mutableStateOf(p.getString("hist", "")!!.split("~").filter { it.isNotBlank() })
    var pendingRef by mutableStateOf(p.getString("pref", "")!!)
    var refUsed by mutableStateOf(p.getBoolean("refUsed", false))
    var usedTx by mutableStateOf(p.getString("used", "")!!.split("~").filter { it.isNotBlank() })
    // подпись баланса: если файл настроек правили вручную — tampered = true
    val tampered = p.getString("mac", null).let { it != null && it != Guard.mac("${real.toFloat()}|${bonus.toFloat()}|$lastDep|$tier") }
    var blocked by mutableStateOf(false)   // true = пополнение и вывод отключены
    var av by mutableStateOf(0)
    val total get() = real + bonus
    fun save() = p.edit().putString("nick", nick).putInt("theme", theme).putString("lang", lang).putString("pin", pin)
        .putFloat("real", real.toFloat()).putFloat("bonus", bonus.toFloat()).putLong("lastDep", lastDep).putInt("tier", tier)
        .putString("hist", hist.take(100).joinToString("~")).putString("pref", pendingRef).putBoolean("refUsed", refUsed).putString("used", usedTx.joinToString("~")).putString("mac", Guard.mac("${real.toFloat()}|${bonus.toFloat()}|$lastDep|$tier")).apply()

    // Вызывается ТОЛЬКО после успешной проверки в ChainVerifier. Без бонусов.
    fun deposit(a: Double, hash: String, netName: String) {
        val i = Config.TIERS.indexOfLast { a >= it.min }
        if (i >= 0) tier = maxOf(tier, i)
        real += a; lastDep = System.currentTimeMillis(); usedTx = usedTx + hash.lowercase()
        hist = listOf("D:" + nowStr() + "|+" + "%.2f".format(a) + " \$T|" + netName) + hist
        save()
    }

    // TODO(SERVER): заявка уходит тебе на сервер; статус "D:" ставишь после ручной выплаты
    // Выводится только внесённое (real). Реферальный бонус тратится только в ИИ.
    fun withdraw(a: Double, addr: String) {
        real -= a
        hist = listOf("P:" + nowStr() + "|-" + "%.2f".format(a) + " \$T|" + addr) + hist
        save()
    }
}

fun isOnline(c: Context): Boolean {
    val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

class MainActivity : ComponentActivity() {
    lateinit var st: AppState
    override fun onCreate(b: Bundle?) { super.onCreate(b); st = AppState(this); st.blocked = st.tampered || Guard.compromised(this); handle(intent); setContent { App(st) } }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handle(i) }
    private fun handle(i: Intent?) {
        val code = i?.data?.lastPathSegment ?: return
        if (code != st.uid && !st.refUsed && st.pendingRef.isEmpty()) { st.pendingRef = code; st.save() }
    }
}

@Composable fun App(s: AppState) {
    val ctx = LocalContext.current
    var online by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(s.pin.isEmpty()) }
    LaunchedEffect(Unit) { while (true) {
        online = isOnline(ctx)
        // TODO(SERVER): проверять реферальный код на сервере
        if (online && s.pendingRef.isNotEmpty()) { s.bonus += Config.REFERRAL_BONUS; s.refUsed = true; s.pendingRef = ""; s.save() }
        delay(3000) } }
    val cs = THEMES[s.theme]
    MaterialTheme(colorScheme = cs) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            if (!unlocked) PinScreen(s) { unlocked = true } else Home(s, online)
        }
    }
}

@Composable fun PinScreen(s: AppState, ok: () -> Unit) {
    var v by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(12.dp))
        Text("QUANTUM Ai", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(v, { v = it.filter(Char::isDigit); if (v == s.pin) ok() }, visualTransformation = PasswordVisualTransformation(), label = { Text("PIN") })
    }
}

@Composable fun Dlg(title: String, close: () -> Unit, body: @Composable ColumnScope.() -> Unit) = AlertDialog(
    onDismissRequest = close, shape = RoundedCornerShape(24.dp), containerColor = MaterialTheme.colorScheme.surface,
    confirmButton = { TextButton(close) { Text("✕", fontSize = 18.sp) } },
    title = { Text(title, fontWeight = FontWeight.Bold) },
    text = { Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(10.dp), content = body) })

/** QR-код рисуется на самом телефоне (ZXing), интернет не нужен. */
@Composable fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        val hints = mapOf(
            com.google.zxing.EncodeHintType.MARGIN to 0,
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M)
        com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints)
    }
    Canvas(modifier) {
        val n = matrix.width
        val cell = size.width / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

@Composable fun HistCard(raw: String, pending: Boolean, label: String) {
    val c = MaterialTheme.colorScheme
    val p = raw.drop(2).split("|")
    val green = Color(0xFF4CAF50); val amber = Color(0xFFFFB300)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (p.size >= 3) {
                Text(p[1], fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (p[1].startsWith("+")) green else c.onSurface)
                Text(p[2], fontSize = 11.sp, color = c.onSurface.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p[0], fontSize = 10.sp, color = c.onSurface.copy(alpha = .45f))
            } else {
                Text(raw.drop(2), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(RoundedCornerShape(50)).background((if (pending) amber else green).copy(alpha = .18f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (pending) amber else green)
        }
    }
}

/** Раскрывающийся список: нажми на заголовок — откроется. */
@Composable fun HistSection(title: String, items: List<String>, pending: Boolean, empty: String) {
    val c = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.background.copy(alpha = .5f)).border(1.dp, c.primary.copy(alpha = .25f), RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (pending) "⏳" else "✅", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(CircleShape).background(c.primary).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("${items.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.background)
                }
            }
            Text(if (open) "▾" else "▸", fontSize = 18.sp, color = c.primary)
        }
        if (open) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (items.isEmpty()) Text(empty, fontSize = 12.sp, color = c.onSurface.copy(alpha = .6f), modifier = Modifier.padding(4.dp))
                items.take(30).forEach { HistCard(it, pending, title) }
            }
        }
    }
}

@Composable fun Home(s: AppState, online: Boolean) {
    val ctx = LocalContext.current; val c = MaterialTheme.colorScheme
    fun t(k: String) = L.t(s.lang, k)
    var dlg by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { ctx.contentResolver.openInputStream(u)?.use { i -> File(ctx.filesDir, "avatar.jpg").outputStream().use { o -> i.copyTo(o) } }; s.av++ } }
    val bmp = remember(s.av) { File(ctx.filesDir, "avatar.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(10.dp))
                Text("QUANTUM Ai", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.primary)
            }
            Text("⋮", fontSize = 30.sp, modifier = Modifier.clickable { dlg = "set" }.padding(8.dp)) }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(c.surface), border = BorderStroke(1.dp, c.primary.copy(alpha = .35f))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(c.primary).clickable { pick.launch("image/*") }, Alignment.Center) {
                    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) else Text(s.nick.take(1), fontSize = 26.sp, color = c.background) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(s.nick, fontSize = 13.sp, modifier = Modifier.clickable { dlg = "nick" })
                    Text("ID ${s.uid}", fontSize = 11.sp, color = c.onSurface.copy(alpha = .6f))
                    Text("${"%.2f".format(s.total)} \$T", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c.primary) } }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            Button({ dlg = "dep" }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(t("dep")) }
            OutlinedButton({ dlg = "wd" }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(t("wd")) } }
        OutlinedButton({ dlg = "ref" }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("🎁 ${t("ref")}") }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Config.TIERS.forEachIndexed { i, tr -> TierIcon(i, tr, s.tier >= i) } }

        if (online && Config.SHOW_DEMO) {
            // =====  BLOCK 1: график с выдуманными данными — замени на данные сервера или выключи Config.SHOW_DEMO =====
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(c.surface)) { Column(Modifier.padding(14.dp)) {
                Text("${t("new")}  (Online)", fontWeight = FontWeight.Bold)
                FakeChart(c.primary) } }
            // ===== BLOCK 2: топ 10 с выдуманными именами — замени на данные сервера или выключи Config.SHOW_DEMO =====
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(c.surface)) { Column(Modifier.padding(14.dp)) {
                Text("🏆 ${t("top")}  (Online)", fontWeight = FontWeight.Bold)
                val names = remember { listOf("Omar", "Timur", "Nora", "Leo", "Ivan", "Alex", "Kai", "Sofia", "Max", "Ivan").shuffled().take(10) }
                names.forEachIndexed { i, n -> Text("${i + 1}. ${n}_${Random.nextInt(10, 99)}***") } } }
            // =====  DEMO =====
        }
        if (s.blocked) Text("⚠ Security check failed (modified app, root or patcher tool). Deposits and withdrawals are disabled.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Button({ dlg = "ai" }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("✨ ${t("ai")}", fontSize = 18.sp) }
        Text(t("rules"), fontSize = 11.sp, color = c.onSurface.copy(alpha = .6f))
    }

    when (dlg) {
        "set" -> Dlg(t("set"), { dlg = "" }) {
            Text(t("theme"), fontWeight = FontWeight.Bold)
            THEME_NAMES.forEachIndexed { i, n -> Text((if (s.theme == i) "● " else "○ ") + n, Modifier.clickable { s.theme = i; s.save() }.padding(4.dp)) }
            Text(t("lang"), fontWeight = FontWeight.Bold)
            L.names.forEach { (k, n) -> Text((if (s.lang == k) "● " else "○ ") + n, Modifier.clickable { s.lang = k; s.save() }.padding(4.dp)) }
            Text(t("pin"), fontWeight = FontWeight.Bold)
            var np by remember { mutableStateOf(s.pin) }
            OutlinedTextField(np, { np = it.filter(Char::isDigit).take(6) }, singleLine = true, placeholder = { Text("empty = off") })
            Button({ s.pin = np; s.save(); dlg = "" }) { Text(t("save")) } }
        "nick" -> Dlg(t("nick"), { dlg = "" }) {
            var n by remember { mutableStateOf(s.nick) }
            OutlinedTextField(n, { n = it.take(20) }, singleLine = true)
            Button({ if (n.isNotBlank()) { s.nick = n; s.save() }; dlg = "" }) { Text(t("save")) } }
        "dep" -> Dlg(t("dep"), { dlg = "" }) {
            var hash by remember { mutableStateOf("") }; var net by remember { mutableStateOf(0) }
            var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf("") }
            var copied by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val n = Config.NETWORKS[net]

            // 1. Выбор сети
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                Config.NETWORKS.forEachIndexed { i, x ->
                    val sel = i == net
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) c.primary else c.background)
                        .border(1.dp, c.primary.copy(alpha = .5f), RoundedCornerShape(50))
                        .clickable { net = i; copied = false; msg = "" }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(x.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (sel) c.background else c.onSurface) } } }

            // 2. QR + адрес
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                QrCode(n.address, Modifier.size(180.dp))
                Spacer(Modifier.height(8.dp))
                Text(n.address, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black, textAlign = TextAlign.Center) }
            Button({
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("a", n.address))
                copied = true },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(if (copied) t("copied") else t("copy")) }

            // 3. Предупреждение
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33FFB300)).padding(12.dp)) {
                Text("⚠  " + t("warn").replace("%s", n.name), fontSize = 12.sp) }
            Text("${Config.MIN_DEPOSIT}–${Config.MAX_DEPOSIT} \$", fontSize = 12.sp, color = c.onSurface.copy(alpha = .7f))

            // 4. Как это работает
            Text(t("how"), fontWeight = FontWeight.Bold)
            listOf("s1", "s2", "s3", "s4").forEachIndexed { i, k ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(c.primary), Alignment.Center) {
                        Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.background) }
                    Spacer(Modifier.width(10.dp))
                    Text(t(k), fontSize = 13.sp) } }

            // 5. Проверка перевода
            OutlinedTextField(hash, { hash = it.trim() }, label = { Text("TX hash") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button({
                if (s.usedTx.contains(hash.lowercase())) { msg = "Hash already used"; return@Button }
                busy = true; msg = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) { ChainVerifier.verify(n, hash) }
                    busy = false
                    val usd = Math.floor(r.usd * 100) / 100
                    when {
                        r.error != null -> msg = r.error
                        usd < Config.MIN_DEPOSIT || usd > Config.MAX_DEPOSIT -> msg = "Amount $usd out of range"
                        else -> { s.deposit(usd, hash, n.name); dlg = "" } } } },
                Modifier.fillMaxWidth().height(50.dp), enabled = !busy && !s.blocked && hash.length >= 40, shape = RoundedCornerShape(14.dp)) { Text(if (busy) "…" else t("verify")) }
            if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Text(Config.PAY_APPS, fontSize = 11.sp, color = c.onSurface.copy(alpha = .6f)) }
        "wd" -> Dlg(t("wd"), { dlg = "" }) {
            var now by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
            val left = (s.lastDep + Config.WITHDRAW_LOCK_HOURS * 3_600_000 - now).coerceAtLeast(0) / 1000
            var amt by remember { mutableStateOf("") }; var addr by remember { mutableStateOf("") }
            Text("ID ${s.uid}  •  max ${"%.2f".format(s.real)} \$T", fontSize = 12.sp)
            OutlinedTextField(amt, { amt = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("\$T") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(addr, { addr = it }, label = { Text("Wallet") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val a = amt.toDoubleOrNull() ?: 0.0
            Button({ s.withdraw(a, addr); dlg = "" }, Modifier.fillMaxWidth().height(50.dp), enabled = !s.blocked && left == 0L && a > 0 && a <= s.real && addr.length > 10, shape = RoundedCornerShape(14.dp)) {
                Text(if (left > 0) "${t("wait")} %02d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60) else t("wd")) }
            HistSection(t("pend"), s.hist.filter { it.startsWith("P:") }, true, t("empty"))
            HistSection(t("done"), s.hist.filter { it.startsWith("D:") }, false, t("empty")) }
        "ref" -> Dlg(t("ref"), { dlg = "" }) {
            val link = "quantumai://ref/${s.uid}"
            Text("+${Config.REFERRAL_BONUS.toInt()} \$T"); Text(link)
            Button({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null)) }) { Text("Share") } }
        "ai" -> Dlg(t("ai"), { dlg = "" }) {
            var q by remember { mutableStateOf("") }; var r by remember { mutableStateOf("") }
            OutlinedTextField(q, { q = it })
            Button({ r = AiEngine.ask(q) }) { Text("→") }; Text(r) }
    }
}

@Composable fun TierIcon(i: Int, t: Tier, unlocked: Boolean) {
    val tr = rememberInfiniteTransition(label = "t")
    val g by tr.animateFloat(0.25f, 1f, infiniteRepeatable(tween(1000 + i * 300), RepeatMode.Reverse), label = "g")
    val sc by tr.animateFloat(1f, 1.1f, infiniteRepeatable(tween(900 + i * 200), RepeatMode.Reverse), label = "s")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(62.dp).graphicsLayer { alpha = if (unlocked) 1f else .45f; if (unlocked) { scaleX = sc; scaleY = sc } }
            .border(3.dp, GOLD.copy(alpha = if (unlocked) g else .3f), RoundedCornerShape(16.dp))
            .padding(3.dp).clip(RoundedCornerShape(13.dp)), Alignment.Center) {
            Image(painterResource(listOf(R.drawable.tier_1, R.drawable.tier_2, R.drawable.tier_3, R.drawable.tier_4)[i]), t.name, Modifier.fillMaxSize()) }
        Text(t.name, fontSize = 11.sp)
        if (t.pct > 0) Text("+${t.pct}%", color = GOLD, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("≥$${t.min}", fontSize = 10.sp)
    }
}

@Composable fun FakeChart(col: Color) {
    val pts = remember { mutableStateListOf<Float>().apply { repeat(30) { add(0.5f) } } }
    LaunchedEffect(Unit) { while (true) { delay(700); val l = pts.last(); pts.removeAt(0); pts.add((l + Random.nextFloat() * .5f - .25f).coerceIn(.05f, .95f)) } }
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val p = Path()
        pts.forEachIndexed { i, v -> val x = size.width * i / (pts.size - 1); val y = size.height * (1 - v); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
        drawPath(p, col, style = Stroke(4f)) }
}
