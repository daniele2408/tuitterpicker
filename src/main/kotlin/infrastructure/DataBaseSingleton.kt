package infrastructure

import com.sksamuel.hoplite.ConfigLoader
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

val config = ConfigLoader().loadConfigOrThrow<Config>("/config.yaml")

object DataBaseSingleton {
    private val uri: String = config.database.uri
    private const val uriMem: String = "jdbc:sqlite:file:test?mode=memory&cache=shared"

    private const val driver: String = "org.sqlite.JDBC"

    val db by lazy {
        Database.connect(uri, driver)
        TransactionManager.manager.defaultIsolationLevel =
            Connection.TRANSACTION_SERIALIZABLE
    }

    val dbMem by lazy {
        Database.connect(uriMem, driver)
        TransactionManager.manager.defaultIsolationLevel =
            Connection.TRANSACTION_SERIALIZABLE
    }

}
