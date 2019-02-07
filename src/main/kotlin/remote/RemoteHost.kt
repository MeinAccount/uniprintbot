package remote

import BOT_TOKEN
import SSH_HOST
import SSH_PASSWORD
import SSH_USER
import com.google.cloud.datastore.Entity
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.telegram.telegrambots.meta.api.objects.File
import java.util.*
import java.util.concurrent.TimeUnit

object RemoteHost {
    private fun <T> sshClient(run: (SSHClient) -> T) = SSHClient().use { sshClient ->
        sshClient.addHostKeyVerifier(PromiscuousVerifier())
        sshClient.timeout = 30
        sshClient.connectTimeout = 30000
        sshClient.connect(SSH_HOST)
        sshClient.authPassword(SSH_USER, SSH_PASSWORD)
        run(sshClient)
    }

    private fun printCommand(path: String, user: Entity? = null) =
            "echo \"$path von ${user?.getString("name")} am ${Date()}\" >> log; " +
                    "lp -d fsmathd -o sides=two-sided-long-edge $path"


    fun printTelegramFile(user: Entity, file: File) {
        sshClient { client ->
            client.startSession().use { session ->
                session.exec("""temp_file=$(mktemp);
                                |wget -O ${"$"}temp_file "${file.getFileUrl(BOT_TOKEN)}";
                                |${printCommand("\$temp_file", user)}""".trimMargin()
                ).join(30, TimeUnit.SECONDS)
            }
        }
    }

    fun cancelAll() {
        sshClient { client ->
            client.startSession().use { session ->
                session.exec("cancel -a -x").join(5, TimeUnit.SECONDS)
            }
        }
    }
}
