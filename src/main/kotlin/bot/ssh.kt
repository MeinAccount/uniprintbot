package bot

import SSH_HOST
import SSH_PASSWORD
import SSH_USER
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

fun <T> sshClient(run: (SSHClient) -> T) = SSHClient().use { sshClient ->
    sshClient.addHostKeyVerifier(PromiscuousVerifier())
    sshClient.connect(SSH_HOST)
    sshClient.authPassword(SSH_USER, SSH_PASSWORD)
    run(sshClient)
}

