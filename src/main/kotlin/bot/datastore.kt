package bot

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.Entity
import java.io.FileInputStream

private val datastore = DatastoreOptions.newBuilder()
        .setCredentials(GoogleCredentials.fromStream(FileInputStream("WEB-INF/uniprintbot.json")))
        .build().service
private val keyFactory = datastore.newKeyFactory().setKind("whitelist-user")

fun getUser(userId: Int): Entity? = datastore.get(keyFactory.newKey(userId.toString()))
