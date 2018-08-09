package remote

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.Entity
import java.io.File
import java.io.FileInputStream

internal val datastore = DatastoreOptions.newBuilder()
        .setCredentials(GoogleCredentials.fromStream(
                if (File("WEB-INF/uniprintbot.json").exists())
                    FileInputStream("WEB-INF/uniprintbot.json")
                else FileInputStream("src/main/webapp/WEB-INF/uniprintbot.json")))
        .build().service

data class IliasResource(val type: String, val name: String, val url: String,
                         var selected: Boolean = false, var entity: Entity? = null)
