import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.meta.TelegramBotsApi
import web.PollingUniPrintBot

fun main(args: Array<String>) {
    ApiContextInitializer.init()

    val api = TelegramBotsApi()
    api.registerBot(PollingUniPrintBot())
}
