package infrastructure

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Users : IntIdTable() {
    val twitterUserId: Column<Long> = long("twitter_user_id")
    val name: Column<String> = varchar("username", 50)
    val twitterHandle: Column<String> = varchar("twitter_handle", 50)
    val updateTime: Column<LocalDateTime> = datetime("update_time")
}

object Tweets : IntIdTable() {
    val tweetId: Column<Long> = long("tweetId").uniqueIndex()
    val text: Column<String> = varchar("text", 500)
    val lang: Column<String> = varchar("language", 50)
    val createdAt: Column<LocalDateTime> = datetime("created_at")
    val authorId: Column<Long> = long("author_id")
}

object MonitoringSearches: IntIdTable() {
    val newestId: Column<Long> = long("newest_id")
    val oldestId: Column<Long> = long("oldest_id")
    val resultCount: Column<Int> = integer("result_count")
    val nextToken: Column<String> = varchar("next_token", 50)
    val searchedUserTwitterId:  Column<String> = varchar("searched_user", 50)
}