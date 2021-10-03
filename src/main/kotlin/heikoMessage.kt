import heiko.HeikoNotificationBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

fun main() {
    val bot = HeikoNotificationBot()

    print("Text to send: ")
    val text = readLine()
    if (text != null) {
        println(bot.execute(SendMessage(HEIKO_GROUP, text).apply {
            parseMode = "Markdown"
        }))
    }
}
