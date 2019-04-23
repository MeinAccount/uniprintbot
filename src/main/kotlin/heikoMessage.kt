import heiko.HeikoNotificationBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

fun main(args: Array<String>) {
    val bot = HeikoNotificationBot()
//    bot.execute(SendSticker().setSticker("CAADAgADdAYAApb6EgVv0K8Liwm_rQI").setChatId(HEIKO_GROUP))

    val text = readLine()
    if (text != null) {
        bot.execute(SendMessage(HEIKO_GROUP, text).setParseMode("Markdown"))
    }
}
