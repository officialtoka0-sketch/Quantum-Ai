package com.quantumai.app

data class Tier(val name: String, val min: Int, val pct: Int, val emoji: String)
data class Net(val name: String, val address: String, val kind: String)

/** ВСЕ настройки бизнеса в одном месте. */
object Config {
    // >>> ТАЙМЕР ВЫВОДА: поставь 0, когда автоматизируешь вывод <<<
    const val WITHDRAW_LOCK_HOURS = 1445L
    const val MIN_DEPOSIT = 30
    const val MAX_DEPOSIT = 50_000
    const val REFERRAL_BONUS = 6.0
    const val REAL_LABEL = "Realtime"   // метка у онлайн блоков; убери вместе с блоком

    val TIERS = listOf(
        Tier("Basic", 100, 15, "⚙️"), Tier("Advanced", 300, 20, "🚀"),
        Tier("Pro", 500, 25, "🖥️"), Tier("Ultra", 1000, 40, "⚡"))

    const val PAY_APPS = "Binance • OKX • Bybit • Trust Wallet • any wallet/exchange"
    val NETWORKS = listOf(
        Net("Bitcoin", "bc1qyhrt2rdy3yz0qulme95hv05kw3knmcg0w26wl9", "BTC"),
        Net("Solana", "4quFfVtBRobQpLAVLQZ4GhdCQnEwfr423AfjCDMkzerR", "SOL"),
        Net("USDT TRC20", "TCFLHinoHNRcowyj774xhUqTX1kX6yBGvS", "TRC20"),
        Net("USDT BEP20", "0xb38Ceb8Aec33D6345B89063CB06e48f996801f46", "BEP20"))

    val RULES_EN = "You sleep while the AI works for you! Deposit bonuses: " +
        "+$100 gets +15%, +$300 gets +20%, +$500 gets +25%, and +$1000 gets +40% to your balance. " +
        "Your total amount multiplies! Minimum deposit: $30, maximum: $50,000. " +
        "Withdrawals are available after a 60-day period. Quantum AI LLC."

}
