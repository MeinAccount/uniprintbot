package heiko

import HEIKO_FORCE_USER
import HEIKO_TOKEN
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.LanguageServiceClient
import com.google.cloud.language.v1.LanguageServiceSettings
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File
import java.io.FileInputStream
import java.util.*

private val client = LanguageServiceClient.create(LanguageServiceSettings.newBuilder()
        .setCredentialsProvider(FixedCredentialsProvider.create(GoogleCredentials.fromStream(
                if (File("WEB-INF/uniprintbot.json").exists())
                    FileInputStream("WEB-INF/uniprintbot.json")
                else FileInputStream("src/main/webapp/WEB-INF/uniprintbot.json")))).build())

private val negativeStickers = arrayOf("CAADAgADIAADyIsGAAGwI-I5pMSEdQI", "CAADAgADLwIAArrAlQXCB-MwsRsKUAI",
        "CAADAgAD6AMAAvJ-ggyV1koZSeQd7QI", "CAADAgADdQIAAsSraAthqwkz4CCMGwI", "CAADAgADKwIAAj-VzAq8_jVvbB-ZgQI",
        "CAADAgADTAUAAmMr4glGKjnwtWFTIAI", "CAADAgAD6wEAAiCBFQABCQn4d2vDOrcC", "CAADAgAD5QADNnYgDr7EklL1F-d-Ag",
        "CAADAgADuQEAAgeGFQcm74jOQU-L8wI", "CAADAgADfAUAAhhC7gjEYV0FBA_xjgI")

class HeikoNotificationBot : TelegramLongPollingBot() {
    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage() && update.message.hasText()) {
            processMessage(update.message, update.message.from.id == HEIKO_FORCE_USER)
        } else if (update.hasMessage() && update.message.hasSticker() && update.message.chatId == -256759614L) {
            execute(SendMessage(update.message.chatId, "Sticker fileId ${update.message.sticker.fileId}")
                    .setReplyToMessageId(update.message.messageId))
        }
    }

    private fun processMessage(message: Message, force: Boolean = false, score: Boolean = false) {
        when {
            message.text.startsWith("/tag@HeikoNotificationBot", true) ->
                processStripped(message, message.text.drop(25), score)
            message.text.startsWith("/tag", true) ->
                processStripped(message, message.text.drop(4), score)

            message.text.startsWith("/score@HeikoNotificationBot", true) ->
                processStripped(message, message.text.drop(27), true)
            message.text.startsWith("/score", true) ->
                processStripped(message, message.text.drop(6), true)

            force -> processStripped(message, message.text, score)
        }
    }

    private fun processStripped(message: Message, text: String, score: Boolean) {
        if (text.isNotBlank()) {
            val sentiment = client.analyzeSentiment(Document.newBuilder()
                    .setContent(text).setType(Document.Type.PLAIN_TEXT).build())
            println(sentiment)

            if (score) {
                execute(SendMessage(message.chatId,
                        "Score ${sentiment.documentSentiment.score} at magnitude ${sentiment.documentSentiment.magnitude}")
                        .setReplyToMessageId(message.messageId))
            } else if (sentiment.documentSentiment.score < 0) {
                execute(SendSticker()
                        .setChatId(message.chatId)
                        .setSticker(negativeStickers.random())
                        .setReplyToMessageId(message.messageId))
            }
        } else if (message.isReply) {
            processMessage(message.replyToMessage, true, score)
        }
    }


    override fun getBotUsername() = "HeikoNotificationBot"

    override fun getBotToken() = HEIKO_TOKEN
}


private val random = Random()
fun <T> Array<T>.random() = get(random.nextInt(size))
