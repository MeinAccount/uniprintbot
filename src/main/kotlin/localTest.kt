import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.meta.TelegramBotsApi
import print.UniPrintBot

fun main(args: Array<String>) {
    ApiContextInitializer.init()

    val api = TelegramBotsApi()
    val printBot = UniPrintBot()
    printBot.clearOverrideWebhook()
    api.registerBot(printBot)
}
