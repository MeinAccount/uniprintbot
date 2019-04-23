import com.google.cloud.datastore.Entity
import remote.UserStorage
import remote.datastore

fun main() {
    datastore.update(*UserStorage.listUsers().asSequence().map {
        Entity.newBuilder(it)
                .remove("notifyBlub")
                .set("notifyBlub", false)
                .build()
    }.toList().toTypedArray())
}
