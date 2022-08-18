package infrastructure

import Monitoring
import TweetObject
import TwitterUser
import UserSearchConfig
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

object DatabaseConnector {

    fun insertTweet(tweetObject: TweetObject) : Int {

        return Tweets.insertIgnore {
            it[tweetId] = tweetObject.twitterId
            it[authorId] = tweetObject.authorId
            it[text] = tweetObject.text
            it[lang] = tweetObject.lang
            it[createdAt] = tweetObject.createdAt
        }.insertedCount

    }

    fun insertUserOrIgnore(twitterUser: TwitterUser) {
        Users.insertIgnore {
            it[twitterUserId] = twitterUser.userId
            it[name] = twitterUser.name
            it[twitterHandle] = twitterUser.handle
            it[updateTime] = LocalDateTime.now()
        }
    }

    fun insertMonitoring(monitoring: Monitoring) {
        MonitoringSearches.insertIgnore {
            it[newestId] = monitoring.newestId
            it[oldestId] = monitoring.oldestId
            it[resultCount] = monitoring.resultCount
            it[nextToken] = monitoring.nextToken
            it[searchedUserTwitterId] = monitoring.searchedUser
        }
    }

    fun getUser(twitterUserId: Long) : TwitterUser {
        return Users.select(Users.twitterUserId eq twitterUserId )
            .map { TwitterUser(it[Users.twitterUserId], it[Users.name], it[Users.twitterHandle]) }.first()
    }

    fun existsUser(twitterUserId: Long) : Boolean {
        return Users.select( Users.twitterUserId eq twitterUserId ).count() > 0
    }

    fun getMostRecentIdForUser(handle: String) : Long {
        return MonitoringSearches
            .select(MonitoringSearches.searchedUserTwitterId eq handle)
            .orderBy(MonitoringSearches.newestId to SortOrder.DESC)
            .limit(1).map { it[MonitoringSearches.newestId] }.first()

    }

    fun getLeastRecentIdForUser(handle: String) : Long {
        return MonitoringSearches
            .select(MonitoringSearches.searchedUserTwitterId eq handle)
            .orderBy(MonitoringSearches.oldestId to SortOrder.ASC)
            .limit(1).map { it[MonitoringSearches.oldestId] }.first()
    }

    fun getNextTokenForUser(handle: String) : String {
        return MonitoringSearches
            .select(MonitoringSearches.searchedUserTwitterId eq handle)
            .orderBy(MonitoringSearches.id to SortOrder.DESC)
            .limit(1).map { it[MonitoringSearches.nextToken] }.first()
    }

    fun generateUserConfig(handle: String) : UserSearchConfig {
        val mostRecentIdForUser = getMostRecentIdForUser(handle)
        val leastRecentIdForUser = getLeastRecentIdForUser(handle)
        val nextTokenForUser = getNextTokenForUser(handle)
        return UserSearchConfig(nextTokenForUser, mostRecentIdForUser, leastRecentIdForUser)
    }

    fun generateUserConfigNoNextToken(handle: String) : UserSearchConfig {
        val mostRecentIdForUser = getMostRecentIdForUser(handle)
        val leastRecentIdForUser = getLeastRecentIdForUser(handle)
        return UserSearchConfig(mostRecentIdForUser, leastRecentIdForUser)
    }

}