import com.sksamuel.hoplite.ConfigLoader
import infrastructure.*
import logic.TweetPicker
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

val config = ConfigLoader().loadConfigOrThrow<Config>("/config.yaml")

fun main() {
    DataBaseSingleton.db

    transaction {
        SchemaUtils.create(Tweets)
        SchemaUtils.create(MonitoringSearches)
        SchemaUtils.create(Users)
    }

    val tweetPicker = TweetPicker(config.schedules)

    tweetPicker.initScheduler()

}