import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import heiko.HeikoNotificationBot

fun main(args: Array<String>) {
    val text = readLine()
    if (text != null) {
        val bot = HeikoNotificationBot()
        bot.execute(SendMessage(HEIKO_GROUP, text).setParseMode("Markdown"))
    }
}
