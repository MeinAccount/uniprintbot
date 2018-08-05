import bot.UniPrintBot
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot

fun main(args: Array<String>) {
    ApiContextInitializer.init()

    val api = TelegramBotsApi()
    api.registerBot(PollingBot())
}

private class PollingBot : TelegramLongPollingBot() {
    val bot = UniPrintBot()

    override fun onUpdateReceived(update: Update) {
        bot.onUpdateReceived(update)?.let {
            when (it) {
                is SendMessage -> execute(it)
                is EditMessageText -> execute(it)
                is EditMessageReplyMarkup -> execute(it)
                else -> TODO("not implemented")
            }
        }
    }

    override fun getBotToken() = BOT_TOKEN

    override fun getBotUsername() = "UniPrintBot"
}
