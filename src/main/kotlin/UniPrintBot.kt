import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton

data class AvailableFile(val path: String, val name: String, var selected: Boolean)

class UniPrintBot {
    private val files = listOf(AvailableFile("algebra", "Algebra", false),
            AvailableFile("analysis", "Analysis", false),
            AvailableFile("stat", "Statistik", false))

    fun onUpdateReceived(update: Update): BotApiMethod<*>? {
        if (update.hasMessage() && update.message.hasText()) {
            if (update.message.text == "/start") {
                val message = SendMessage(update.message.chatId, "Welche Dateien sollen gedruckt werden?")
                message.replyMarkup = getFilesKeyboard()
                return message
            }
        } else if (update.hasCallbackQuery()) {
            val search = update.callbackQuery.data.takeWhile { it != '|' }
            files.firstOrNull { it.path == search }?.let {
                it.selected = !it.selected
            }

            // update keyboard
            val message = EditMessageReplyMarkup()
            message.messageId = update.callbackQuery.message.messageId
            message.chatId = update.callbackQuery.message.chatId.toString()
            message.inlineMessageId = update.callbackQuery.inlineMessageId
            message.replyMarkup = getFilesKeyboard()
            return message
        }

        return null
    }

    private fun getFilesKeyboard(): InlineKeyboardMarkup {
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard.add(files.map {
            InlineKeyboardButton("${if (it.selected) "☒" else "☐"} ${it.name}")
                    .setCallbackData("${it.path}|${it.selected}")
        })

        return keyboard
    }
}
