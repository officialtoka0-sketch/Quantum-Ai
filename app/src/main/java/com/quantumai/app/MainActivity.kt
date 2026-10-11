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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

class AppState(ctx: Context) {
    private val p = ctx.getSharedPreferences("q", 0)
    var nick by mutableStateOf(p.getString("nick", "Quantum User")!!)
    var uid by mutableStateOf(p.getString("uid", null) ?: ("" + (1..9).random() + (1..14).map { (0..9).random() }.joinToString("")).also { p.edit().putString("uid", it).apply() })
    var theme by mutableStateOf(p.getInt("theme", 0))
    var lang by mutableStateOf(p.getString("lang", "en")!!)
    var pin by mutableStateOf(p.getString("pin", "")!!)
    var real by mutableStateOf(p.getFloat("real", 0f).toDouble())     // выводится
    var bonus by mutableStateOf(p.getFloat("bonus", 0f).toDouble())   // тоже выводится
    var lastDep by mutableStateOf(p.getLong("lastDep", 0L))
    var tier by mutableStateOf(p.getInt("tier", -1))
    var hist by mutableStateOf(p.getString("hist", "")!!.split("~").filter { it.isNotBlank() }) // "P:" в обработке, "D:" выполнен
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
        .putString("hist", hist.joinToString("~")).putString("pref", pendingRef).putBoolean("refUsed", refUsed).putString("used", usedTx.joinToString("~")).putString("mac", Guard.mac("${real.toFloat()}|${bonus.toFloat()}|$lastDep|$tier")).apply()
    // Вызывается ТОЛЬКО после успешной проверки в ChainVerifier
    fun deposit(a: Double, hash: String) {
        val i = Config.TIERS.indexOfLast { a >= it.min }
        if (i >= 0) { bonus += a * Config.TIERS[i].pct / 100.0; tier = maxOf(tier, i) }
        real += a; lastDep = System.currentTimeMillis(); usedTx = usedTx + hash.lowercase(); save()
    }
    // TODO(SERVER): заявка уходит тебе на сервер; статус "D:" ставишь после ручной выплаты
    fun withdraw(a: Double, addr: String) {
    // Списываем сначала real, потом bonus — можно вывести весь баланс
    var left = a
    val fromReal = minOf(real, left)
    real -= fromReal
    left -= fromReal
    if (left > 0) bonus -= left
        hist = listOf("P:${java.text.SimpleDateFormat("dd.MM HH:mm").format(java.util.Date())}  - ${"%.2f".format(a)} \$T → $addr") + hist
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
        Text("QUANTUM Ai", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(v, { v = it.filter(Char::isDigit); if (v == s.pin) ok() }, visualTransformation = PasswordVisualTransformation(), label = { Text("PIN") })
    }
}

@Composable fun Dlg(title: String, close: () -> Unit, body: @Composable ColumnScope.() -> Unit) = AlertDialog(
    onDismissRequest = close, confirmButton = { TextButton(close) { Text("✕") } }, title = { Text(title) },
    text = { Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp), content = body) })

@Composable fun Home(s: AppState, online: Boolean) {
    val ctx = LocalContext.current; val c = MaterialTheme.colorScheme
    fun t(k: String) = L.t(s.lang, k)
    var dlg by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { ctx.contentResolver.openInputStream(u)?.use { i -> File(ctx.filesDir, "avatar.jpg").outputStream().use { o -> i.copyTo(o) } }; s.av++ } }
    val bmp = remember(s.av) { File(ctx.filesDir, "avatar.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("QUANTUM Ai", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.primary)
            Text("⋮", fontSize = 30.sp, modifier = Modifier.clickable { dlg = "set" }.padding(8.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.primary).clickable { pick.launch("image/*") }, Alignment.Center) {
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) else Text(s.nick.take(1), fontSize = 26.sp, color = c.background) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(s.nick, fontSize = 13.sp, modifier = Modifier.clickable { dlg = "nick" })
                Text("ID ${s.uid}", fontSize = 11.sp, color = c.onSurface.copy(alpha = .6f))
                Text("${"%.2f".format(s.total)} \$T", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.primary) } }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            Button({ dlg = "dep" }, Modifier.weight(1f)) { Text(t("dep")) }
            OutlinedButton({ dlg = "wd" }, Modifier.weight(1f)) { Text(t("wd")) } }
        OutlinedButton({ dlg = "ref" }, Modifier.fillMaxWidth()) { Text("🎁 ${t("ref")}") }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Config.TIERS.forEachIndexed { i, tr -> TierIcon(i, tr, s.tier >= i) } }

        if (online) {
            // ===== BLOCK 1: график новых пользователей — замени на данные сервера =====
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(c.surface)) { Column(Modifier.padding(14.dp)) {
                Text("${t("new")}  (real)", fontWeight = FontWeight.Bold)
                FakeChart(c.primary) } }
            // ===== FAKE BLOCK 2: топ 10 богачей — замени на данные сервера =====
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(c.surface)) { Column(Modifier.padding(14.dp)) {
                Text("🏆 ${t("top")}  (real)", fontWeight = FontWeight.Bold)
                val names = remember { listOf("Omar", "Timur", "Nora", "Leo", "Ivan", "Alex", "Kai", "Sofia", "Max", "Ivan").shuffled().take(10) }
                names.forEachIndexed { i, n -> Text("${i + 1}. ${n}_${Random.nextInt(10, 99)}***") } } }
            // ===== END =====
        }
        if (s.blocked) Text("⚠ Security check failed (modified app, root or patcher tool). Deposits and withdrawals are disabled.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Button({ dlg = "ai" }, Modifier.fillMaxWidth().height(56.dp)) { Text("✨ ${t("ai")}", fontSize = 18.sp) }
        Text(Config.RULES_EN, fontSize = 11.sp, color = c.onSurface.copy(alpha = .6f))
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
            val scope = rememberCoroutineScope()
            Config.TIERS.forEach { Text("${it.emoji} ≥ ${it.min}: +${it.pct}%", fontSize = 12.sp) }
            Text("${Config.MIN_DEPOSIT}–${Config.MAX_DEPOSIT} \$ → TX hash", fontSize = 12.sp)
            Config.NETWORKS.forEachIndexed { i, n -> Text((if (i == net) "● " else "○ ") + n.name, Modifier.clickable { net = i }.padding(4.dp)) }
            Text(Config.NETWORKS[net].address, fontSize = 12.sp, modifier = Modifier.clickable {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("a", Config.NETWORKS[net].address)) })
            Text(Config.PAY_APPS, fontSize = 11.sp)
            OutlinedTextField(hash, { hash = it.trim() }, label = { Text("TX hash") }, singleLine = true)
            Button({
                if (s.usedTx.contains(hash.lowercase())) { msg = "Hash already used"; return@Button }
                busy = true; msg = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) { ChainVerifier.verify(Config.NETWORKS[net], hash) }
                    busy = false
                    val usd = Math.floor(r.usd * 100) / 100
                    when {
                        r.error != null -> msg = r.error
                        usd < Config.MIN_DEPOSIT || usd > Config.MAX_DEPOSIT -> msg = "Amount $usd out of range"
                        else -> { s.deposit(usd, hash); dlg = "" } } } },
                enabled = !busy && !s.blocked && hash.length >= 40) { Text(if (busy) "…" else "Verify & credit") }
            if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        "wd" -> Dlg(t("wd"), { dlg = "" }) {
            var now by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
            val left = (s.lastDep + Config.WITHDRAW_LOCK_HOURS * 3_600_000 - now).coerceAtLeast(0) / 1000
            var amt by remember { mutableStateOf("") }; var addr by remember { mutableStateOf("") }
            Text("ID ${s.uid}  •  max ${"%.2f".format(s.total)} \$T", fontSize = 12.sp)
            OutlinedTextField(amt, { amt = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("\$T") }, singleLine = true)
            OutlinedTextField(addr, { addr = it }, label = { Text("Wallet") }, singleLine = true)
            val a = amt.toDoubleOrNull() ?: 0.0
            Button({ s.withdraw(a, addr); dlg = "" }, enabled = !s.blocked && left == 0L && a > 0 && a <= s.total && addr.length > 10) {
                Text(if (left > 0) "${t("wait")} %02d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60) else t("wd")) }
            Text(t("pend"), fontWeight = FontWeight.Bold); s.hist.filter { it.startsWith("P:") }.take(10).forEach { Text(it.drop(2), fontSize = 12.sp) }
            Text(t("done"), fontWeight = FontWeight.Bold); s.hist.filter { it.startsWith("D:") }.take(10).forEach { Text(it.drop(2), fontSize = 12.sp) } }
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
        Text(t.name, fontSize = 11.sp); Text("+${t.pct}%", color = GOLD, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text("≥$${t.min}", fontSize = 10.sp)
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
