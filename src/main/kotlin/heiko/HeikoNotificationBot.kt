package heiko

import HEIKO_TOKEN
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Pattern

const val DATE_REGEX = ".*(?:([0-9]{4})-)?([0-9]{2})-([0-9]{2}).*"
private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. MMMM YYYY", Locale.GERMANY)

class HeikoNotificationBot : TelegramLongPollingBot() {
    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            if (update.message.hasText() && !update.message.chat.isUserChat) {
                processMessage(update.message)
            } else if (update.message.hasSticker() && update.message.chat.isUserChat) {
                execute(
                    SendMessage(update.message.chatId, update.message.sticker.fileId)
                        .setReplyToMessageId(update.message.messageId)
                )
            }
        }
    }

    private fun processMessage(message: Message) {
        if (message.text.startsWith("/next", true)) {
            val matcher = Pattern.compile(DATE_REGEX).matcher(message.text)
            if (matcher.matches()) {
                try {
                    val date = LocalDate.of(
                        matcher.group(1)?.toInt() ?: Year.now().value,
                        matcher.group(2).toInt(), matcher.group(3).toInt()
                    )
                    if (date >= LocalDate.now()) {
                        val pinned = execute(
                            SendMessage(message.chatId, "Nächster Spieleabend am ${date.format(DATE_FORMAT)}!")
                        )
                        execute(PinChatMessage(pinned.chatId, pinned.messageId))
                        storeSDate(date, message)
                    }
                } catch (e: DateTimeException) {
                    execute(
                        SendMessage(message.chatId, "Datum konnte nicht geparst werden")
                            .setReplyToMessageId(message.messageId)
                    )
                } catch (e: TelegramApiException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun getBotUsername() = "HeikoNotificationBot"

    override fun getBotToken() = HEIKO_TOKEN
}
