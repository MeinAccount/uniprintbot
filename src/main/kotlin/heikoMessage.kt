import heiko.HeikoNotificationBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import print.UniPrintBot

fun main(args: Array<String>) {
    val bot = HeikoNotificationBot()
//    bot.execute(SendSticker().setSticker("CAADAgADdAYAApb6EgVv0K8Liwm_rQI").setChatId(HEIKO_GROUP))

    print("Text to send: ")
    val text = readLine()
    if (text != null) {
        println(bot.execute(SendMessage(HEIKO_GROUP, text).setParseMode("Markdown")))
    }
}
