package com.quantumai.app

import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

class VerifyResult(val usd: Double, val error: String?)

/** Проверка платежа напрямую в публичных нодах/API, без своего сервера. 1 USDT = 1 $T. */
object ChainVerifier {
    // >>> Эндпоинты и подтверждения — здесь. Для надёжности поставь свои ключи Alchemy/Infura/Helius. <<<
    private const val ETH_RPC = "https://ethereum-rpc.publicnode.com"
    private const val BSC_RPC = "https://bsc-rpc.publicnode.com"
    private const val SOL_RPC = "https://api.mainnet-beta.solana.com"
    private const val ETH_USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"   // 6 decimals
    private const val BSC_USDT = "0x55d398326f99059fF775485246999027B3197955"   // 18 decimals
    private const val TRON_USDT_HEX = "41a614f803b6fd780986a42c78ec9c7f77e6ded13c"
    private const val TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

    private fun http(url: String, body: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 15000
        c.setRequestProperty("Content-Type", "application/json")
        if (body != null) { c.requestMethod = "POST"; c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        return (if (c.responseCode < 400) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
    }
    private fun rpc(url: String, method: String, params: String) =
        JSONObject(http(url, """{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}"""))
    private fun price(id: String) = JSONObject(http("https://api.coingecko.com/api/v3/simple/price?ids=$id&vs_currencies=usd")).getJSONObject(id).getDouble("usd")
    private fun err(m: String) = VerifyResult(0.0, m)
    private fun hexNum(s: String) = BigInteger(s.removePrefix("0x"), 16)
    private fun scaled(v: BigInteger, dec: Int) = v.toBigDecimal().divide(BigDecimal.TEN.pow(dec)).toDouble()

    fun verify(net: Net, hash: String): VerifyResult = try {
        when (net.kind) {
            "ETH" -> evm(ETH_RPC, ETH_USDT, 6, "ethereum", 12, net.address, hash)
            "BEP20" -> evm(BSC_RPC, BSC_USDT, 18, null, 15, net.address, hash)
            "BTC" -> btc(net.address, hash)
            "SOL" -> sol(net.address, hash)
            "TRC20" -> tron(net.address, hash)
            else -> err("Unknown network")
        }
    } catch (e: Exception) { err("Check failed: ${e.message}") }

    private fun evm(url: String, usdt: String, dec: Int, nativeId: String?, minConf: Int, addr: String, h: String): VerifyResult {
        if (!Regex("^0x[0-9a-fA-F]{64}$").matches(h)) return err("Bad hash format")
        val r = rpc(url, "eth_getTransactionReceipt", """["$h"]""").optJSONObject("result") ?: return err("Transaction not found or not mined yet")
        if (r.optString("status") != "0x1") return err("Transaction failed")
        val tip = hexNum(rpc(url, "eth_blockNumber", "[]").getString("result"))
        if (tip - hexNum(r.getString("blockNumber")) < BigInteger.valueOf(minConf.toLong())) return err("Wait for more confirmations ($minConf)")
        val me = addr.lowercase().removePrefix("0x"); var sum = 0.0
        val logs = r.getJSONArray("logs")
        for (i in 0 until logs.length()) {
            val l = logs.getJSONObject(i); val t = l.getJSONArray("topics")
            if (l.getString("address").equals(usdt, true) && t.length() >= 3 && t.getString(0) == TRANSFER && t.getString(2).lowercase().endsWith(me))
                sum += scaled(hexNum(l.getString("data")), dec)
        }
        if (sum > 0) return VerifyResult(sum, null)
        if (nativeId != null) {
            val t = rpc(url, "eth_getTransactionByHash", """["$h"]""").getJSONObject("result")
            if (t.optString("to").equals(addr, true)) return VerifyResult(scaled(hexNum(t.getString("value")), 18) * price(nativeId), null)
        }
        return err("No transfer to our address in this transaction")
    }

    private fun btc(addr: String, h: String): VerifyResult {
        val t = JSONObject(http("https://mempool.space/api/tx/$h"))
        val st = t.getJSONObject("status")
        if (!st.getBoolean("confirmed")) return err("Not confirmed yet")
        if (http("https://mempool.space/api/blocks/tip/height").trim().toInt() - st.getInt("block_height") + 1 < 2) return err("Wait for 2 confirmations")
        var sats = 0L; val o = t.getJSONArray("vout")
        for (i in 0 until o.length()) if (o.getJSONObject(i).optString("scriptpubkey_address") == addr) sats += o.getJSONObject(i).getLong("value")
        return if (sats == 0L) err("No transfer to our address") else VerifyResult(sats / 1e8 * price("bitcoin"), null)
    }

    private fun sol(addr: String, h: String): VerifyResult {
        val body = """{"jsonrpc":"2.0","id":1,"method":"getTransaction","params":["$h",{"encoding":"jsonParsed","commitment":"finalized","maxSupportedTransactionVersion":0}]}"""
        val r = JSONObject(http(SOL_RPC, body)).optJSONObject("result") ?: return err("Not found or not finalized yet")
        val meta = r.getJSONObject("meta")
        if (!meta.isNull("err")) return err("Transaction failed")
        val keys = r.getJSONObject("transaction").getJSONObject("message").getJSONArray("accountKeys")
        var idx = -1
        for (i in 0 until keys.length()) if (keys.getJSONObject(i).getString("pubkey") == addr) idx = i
        if (idx < 0) return err("No transfer to our address")
        val d = meta.getJSONArray("postBalances").getLong(idx) - meta.getJSONArray("preBalances").getLong(idx)
        return if (d <= 0) err("No incoming amount") else VerifyResult(d / 1e9 * price("solana"), null)
    }

    private fun tronHex(a: String): String {
        val al = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var n = BigInteger.ZERO
        for (ch in a) n = n.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(al.indexOf(ch).toLong()))
        return n.toString(16).padStart(50, '0').substring(2, 42)
    }

    private fun tron(addr: String, h: String): VerifyResult {
        if (!Regex("^[0-9a-fA-F]{64}$").matches(h)) return err("Bad hash format")
        val t = JSONObject(http("https://api.trongrid.io/walletsolidity/gettransactionbyid", """{"value":"$h"}"""))
        if (!t.has("raw_data")) return err("Not found or not confirmed yet")
        if (t.getJSONArray("ret").getJSONObject(0).optString("contractRet") != "SUCCESS") return err("Transaction failed")
        val v = t.getJSONObject("raw_data").getJSONArray("contract").getJSONObject(0).getJSONObject("parameter").getJSONObject("value")
        if (!v.optString("contract_address").equals(TRON_USDT_HEX, true)) return err("Not a USDT TRC20 transfer")
        val d = v.optString("data")
        if (!d.startsWith("a9059cbb") || d.length < 136) return err("Not a transfer call")
        if (d.substring(32, 72).lowercase() != tronHex(addr)) return err("Recipient is not our address")
        return VerifyResult(scaled(BigInteger(d.substring(72, 136), 16), 6), null)
    }
}
