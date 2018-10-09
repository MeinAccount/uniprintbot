package heiko

import HEIKO_TOKEN
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

class HeikoNotificationBot : TelegramLongPollingBot() {
    override fun onUpdateReceived(update: Update) {
        TODO("blub")
    }

    override fun getBotUsername() = "HeikoNotificationBot"

    override fun getBotToken() = HEIKO_TOKEN
}
