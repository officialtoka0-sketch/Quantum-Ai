package com.quantumai.app

data class Tier(val name: String, val min: Int, val pct: Int, val emoji: String)
data class Net(val name: String, val address: String, val kind: String)

/** ВСЕ настройки бизнеса в одном месте. */
object Config {
    // >>> ТАЙМЕР ВЫВОДА: поставь 0, когда автоматизируешь вывод <<<
    const val WITHDRAW_LOCK_HOURS = 1445L
    const val MIN_DEPOSIT = 30
    const val MAX_DEPOSIT = 50_000
    const val REFERRAL_BONUS = 6.0      // начисляется только на траты в ИИ, не выводится

    // >>> false = скрыть блоки "новые пользователи" и "топ 10" (они с выдуманными данными). Выключи ДО приёма настоящих денег <<<
    const val SHOW_DEMO = true

    // Уровни: бонус % начисляется на общий счёт при пополнении
    val TIERS = listOf(
        Tier("Basic", 100, 15, "⚙️"),
        Tier("Advanced", 300, 20, "🚀"),
        Tier("Pro", 500, 40, "🖥️"),
        Tier("Ultra", 1000, 40, "⚡"))

    const val PAY_APPS = "Binance • OKX • Bybit • Trust Wallet • any wallet/exchange"

    private const val EVM = "0xb38Ceb8Aec33D6345B89063CB06e48f996801f46"
    val NETWORKS = listOf(
        Net("Bitcoin", "bc1qyhrt2rdy3yz0qulme95hv05kw3knmcg0w26wl9", "BTC"),
        Net("Ethereum", EVM, "ETH"),
        Net("USDT BEP20", EVM, "BEP20"),
        Net("USDT TRC20", "TCFLHinoHNRcowyj774xhUqTX1kX6yBGvS", "TRC20"),
        Net("Arbitrum", EVM, "ARB"))
}
