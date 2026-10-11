package com.quantumai.app

object L {
    val names = mapOf("ru" to "Русский", "en" to "English", "tk" to "Türkmen", "pt" to "Português (BR)", "de" to "Deutsch", "ur" to "اردو")
    private val raw = mapOf(
        "en" to "dep=Deposit;wd=Withdraw;set=Settings;theme=Theme;lang=Language;pin=PIN code;ref=Referrals;ai=AI;new=new users today;top=Top 10 richest;wait=Withdrawal available in;pend=Pending;done=Completed;nick=Nickname;save=Save",
        "ru" to "dep=Ввести средства;wd=Вывести средства;set=Настройки;theme=Тема;lang=Язык;pin=Пин-код;ref=Рефералы;ai=ИИ;new=новых пользователей сегодня;top=Топ 10 богачей;wait=Вывод будет доступен через;pend=В обработке;done=Выполненные;nick=Ник;save=Сохранить",
        "tk" to "dep=Serişde goş;wd=Serişde çykar;set=Sazlamalar;theme=Tema;lang=Dil;pin=PIN kod;ref=Referallar;ai=AI;new=şu gün täze ulanyjylar;top=Iň baý 10;wait=Çykarmak elýeterli bolar;pend=Garaşylýar;done=Tamamlanan;nick=Lakam;save=Ýatda sakla",
        "pt" to "dep=Depositar;wd=Sacar;set=Configurações;theme=Tema;lang=Idioma;pin=Código PIN;ref=Indicações;ai=IA;new=novos usuários hoje;top=Top 10 mais ricos;wait=Saque disponível em;pend=Pendentes;done=Concluídos;nick=Apelido;save=Salvar",
        "de" to "dep=Einzahlen;wd=Auszahlen;set=Einstellungen;theme=Design;lang=Sprache;pin=PIN-Code;ref=Empfehlungen;ai=KI;new=neue Nutzer heute;top=Top 10 Reichste;wait=Auszahlung möglich in;pend=Ausstehend;done=Abgeschlossen;nick=Nickname;save=Speichern",
        "ur" to "dep=رقم جمع کریں;wd=رقم نکالیں;set=ترتیبات;theme=تھیم;lang=زبان;pin=پن کوڈ;ref=ریفرلز;ai=اے آئی;new=آج نئے صارفین;top=10 امیر ترین;wait=نکالنا دستیاب ہوگا;pend=زیر التواء;done=مکمل;nick=نک نیم;save=محفوظ کریں")
    private val m = raw.mapValues { e -> e.value.split(";").associate { it.substringBefore("=") to it.substringAfter("=") } }
    fun t(l: String, k: String) = m[l]?.get(k) ?: m["en"]!![k] ?: k
}
