package bot

import SSH_HOST
import SSH_PASSWORD
import SSH_USER
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.io.Serializable

data class AvailableFile(val path: String, val name: String, var selected: Boolean)

class UniPrintBot {
    private var useArchive = false
    private var files = emptyList<AvailableFile>()

    fun onUpdateReceived(update: Update): BotApiMethod<*>? {
        if (update.hasMessage() && update.message.hasText()) {
            if (update.message.from.id != 639133737) {
                return SendMessage(update.message.chatId, "Permission Denied for user ${update.message.from.id}")
            }

            if (update.message.text == "/start") {
                useArchive = false
                loadRemoteFiles()

                val message = SendMessage(update.message.chatId, "Welche Dateien sollen gedruckt werden?")
                message.replyMarkup = getFilesKeyboard()
                return message
            }
        } else if (update.hasCallbackQuery() && update.callbackQuery.from.id == 639133737) {
            return handleCallbackQuery(update.callbackQuery)
        }

        return null
    }

    private fun handleCallbackQuery(callbackQuery: CallbackQuery): BotApiMethod<out Serializable> {
        if (callbackQuery.data == "print") {
            val message = EditMessageText()
            val selected = files.filter { it.selected }
            if (selected.isEmpty()) {
                return AnswerCallbackQuery().also {
                    it.callbackQueryId = callbackQuery.id
                }
            } else if (selected.size == 1) {
                message.text = "${selected.single().name} wird gedruckt..."
            } else {
                message.text = "${selected.dropLast(1).joinToString(", ", transform = AvailableFile::name)} und ${selected.last().name} werden gedruckt..."
            }

            message.messageId = callbackQuery.message.messageId
            message.chatId = callbackQuery.message.chatId.toString()
            message.inlineMessageId = callbackQuery.inlineMessageId
            return message
        } else if (callbackQuery.data == "archive" || callbackQuery.data == "main") {
            useArchive = !useArchive
            loadRemoteFiles()
        } else {
            val search = callbackQuery.data.takeWhile { it != '|' }
            files.firstOrNull { it.path == search }?.let {
                it.selected = !it.selected
            }
        }

        // update keyboard
        val message = EditMessageReplyMarkup()
        message.messageId = callbackQuery.message.messageId
        message.chatId = callbackQuery.message.chatId.toString()
        message.inlineMessageId = callbackQuery.inlineMessageId
        message.replyMarkup = getFilesKeyboard()
        return message
    }


    private fun loadRemoteFiles() {
        files = SSHClient().use { sshClient ->
            sshClient.addHostKeyVerifier(PromiscuousVerifier())
            sshClient.connect(SSH_HOST)
            sshClient.authPassword(SSH_USER, SSH_PASSWORD)

            sshClient.newSFTPClient().use { sftpClient ->
                sftpClient.ls(if (useArchive) "Documents/print/archive" else "Documents/print")
            }
        }.filter { it.isRegularFile }
                .map { AvailableFile(it.name, it.name.takeWhile { it != '.' }, true) }
    }

    private fun getFilesKeyboard(): InlineKeyboardMarkup {
        val markup = InlineKeyboardMarkup()
        markup.keyboard = files.map {
            InlineKeyboardButton("${if (it.selected) "☒" else "☐"} ${it.name}")
                    .setCallbackData("${it.path}|${it.selected}")
        }.plus(if (useArchive) InlineKeyboardButton("Aktiv").setCallbackData("main") else
            InlineKeyboardButton("Archiv").setCallbackData("archive"))
                .plus(InlineKeyboardButton("Drucken!").setCallbackData("print"))
                .chunked(3)

        return markup
    }
}
