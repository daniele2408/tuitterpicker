import infrastructure.DataBaseSingleton
import infrastructure.DatabaseConnector
import infrastructure.Tweets
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test

internal class SampleTest {

    @Test
    fun insert() {
        val tweetObject = TweetObject(
            "123",
            "hello there I'm a tweet",
            "2022-08-09T12:55:54.000Z",
            "en",
            "456"
        )

        DataBaseSingleton.dbMem

        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.create(Tweets)

            DatabaseConnector.insertTweet(tweetObject)

            val res = Tweets.selectAll().map {
                it[Tweets.tweetId] to it[Tweets.text]
            }

            println("Tweets: $res")
        }

    }

}