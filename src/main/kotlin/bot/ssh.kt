package bot

import SSH_HOST
import SSH_PASSWORD
import SSH_USER
import com.google.cloud.datastore.Entity
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.util.*

fun <T> sshClient(run: (SSHClient) -> T) = SSHClient().use { sshClient ->
    sshClient.addHostKeyVerifier(PromiscuousVerifier())
    sshClient.connect(SSH_HOST)
    sshClient.authPassword(SSH_USER, SSH_PASSWORD)
    run(sshClient)
}

fun printCommand(path: String, user: Entity) =
        "echo \"${path} von ${user.getString("name")} am ${Date()}\" >> log"
