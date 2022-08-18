package logic

import Monitoring
import TweetObject
import TwitterUser
import UrlUserTimeline
import UrlBuildingMethod
import UserSearchConfig
import com.sksamuel.hoplite.ConfigLoader
import infrastructure.Config
import infrastructure.DatabaseConnector
import infrastructure.Schedule
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val config = ConfigLoader().loadConfigOrThrow<Config>("/config.yaml")

class TweetPicker(private val schedules: List<Schedule>) {

    private val logger = KotlinLogging.logger {}
    val BASE_URL_SEARCH_TWEETS: String = "https://api.twitter.com/"

    val headers =
        mapOf("Authorization" to "Bearer ${config.auth.bearerToken}")

    var userSearchConfigs: LinkedHashMap<String, UserSearchConfig> = LinkedHashMap()

    private val scheduler = Executors.newScheduledThreadPool(schedules.size)

    fun initScheduler() {
        var initalDelay = 0L
        for (scheduleConfig in this.schedules) {
            logger.info { "Setting scheduler for userId ${scheduleConfig.twitterId} " +
                    "running each ${scheduleConfig.frequency / (60 * 60)}h, quota ${scheduleConfig.quota}, " +
                    "starting in ${initalDelay / 60} minutes" }
            scheduler.scheduleWithFixedDelay(
                { this.handleTweetRetrievalForUser(
                    scheduleConfig.twitterId,
                    scheduleConfig.quota
                ) },
                initalDelay,
                scheduleConfig.frequency,
                TimeUnit.SECONDS)
            initalDelay += 60 * 2 // each task will start tot minutes apart
        }
    }

    fun getUrlResponse(url: String) : JSONObject? {
        val response = khttp.get(
            url = url,
            headers = headers
        )
        val jsonRes = response.jsonObject
        if (response.statusCode == 200)
            return jsonRes
        logger.warn { "Failed fetching user info from $url, status response ${response.statusCode}\nresponse: {$jsonRes" }
        return null
    }

    fun createUrlTweetResearch(twitterUserId: Long, urlBuildMethod: UrlBuildingMethod, lastParamValue: String) : String {

        val urlUserTimeline = UrlUserTimeline.Builder(
            BASE_URL_SEARCH_TWEETS, twitterUserId
        )
            .tweetFields(listOf("created_at", "lang", "author_id"))
            .lastParam(urlBuildMethod to lastParamValue)
            .build()

        return urlUserTimeline.urlString

    }

    fun performGetUser(twitterUserId: Long) : JSONObject? {
        val url = "https://api.twitter.com/2/users/$twitterUserId"

        val response = khttp.get(
            url = url,
            headers = headers
        )
        val jsonRes = response.jsonObject
        if (response.statusCode == 200)
            return jsonRes
        logger.warn { "Failed fetching user info from $url, status response ${response.statusCode}\nresponse: {$jsonRes" }
        return null
    }

    fun handleTweetRetrievalForUser(twitterUserId: Long, quota: Int) {

        logger.info { "START retrieval for user $twitterUserId - quota $quota" }

        var userHandle = ""

        val userDoesNotExist = transaction {
            !DatabaseConnector.existsUser(twitterUserId)
        }

        if (userDoesNotExist) {
            logger.info { "User $twitterUserId not found in db, fetching info from API" }

            val jsonObject: JSONObject =
                performGetUser(twitterUserId) ?: throw Exception("couldn't get user having id $twitterUserId")

            if (!jsonObject.has("data")) throw Exception("couldn't get data from response for id $twitterUserId")

            val data = jsonObject.getJSONObject("data")

            transaction {
                val handle = data.getString("username")
                DatabaseConnector.insertUserOrIgnore(
                    TwitterUser(
                        data.getString("id"),
                        data.getString("name"),
                        handle
                    )
                )
                userSearchConfigs.getOrPut(handle) { UserSearchConfig.empty() }
                userHandle = handle
            }

        } else {
            logger.info { "User $twitterUserId found in db, retrieving info" }
            transaction {
                val user: TwitterUser = DatabaseConnector.getUser(twitterUserId)
                userHandle = user.handle
                val mostRecentIdForUser = DatabaseConnector.getMostRecentIdForUser(userHandle)
                val leastRecentIdForUser = DatabaseConnector.getLeastRecentIdForUser(userHandle)
                val nextTokenForUser = DatabaseConnector.getNextTokenForUser(userHandle)

                userSearchConfigs.getOrPut(userHandle) { UserSearchConfig(nextTokenForUser, mostRecentIdForUser, leastRecentIdForUser) }

                logger.info { "Retrieved configs ${userSearchConfigs[userHandle]} for user $user" }

            }
        }

        var internalQuota: Int = quota
        var insertedTweets: Int = -1
        var urlResponse: JSONObject?

        while (internalQuota > 0) {
            logger.info { "Still $internalQuota tweets to collect" }
            try {

                val userConfig = userSearchConfigs[userHandle] ?: throw Exception("User config for $twitterUserId are null, can't progress")
                var adoptedPolicy: String

                urlResponse = tryFetchFromLastToken(twitterUserId, userConfig)
                adoptedPolicy = "from last token"
                if (urlResponse == null) {
                    urlResponse = tryFetchNew(twitterUserId, userConfig)
                    adoptedPolicy = "fetched new"
                }
                if (urlResponse == null) {
                    urlResponse = tryFetchOld(twitterUserId, userConfig)
                    adoptedPolicy = "fetched old"
                }
                if (urlResponse == null && insertedTweets == -1) {
                    urlResponse = tryFetchBlank(twitterUserId)
                    adoptedPolicy = "fetched blank"
                }
                if (urlResponse == null) {
                    logger.info { "No more tweets to fetch, goodbye!" }
                    internalQuota = 0
                    break
                }

                logger.info { "Adopted '$adoptedPolicy' policy" }

                transaction {

                    insertedTweets = extractAndInsertTweets(userHandle, urlResponse)
                    internalQuota -= insertedTweets
                    if (insertedTweets > 0) {
                        logger.info { "Inserted $insertedTweets tweets" }
                        userSearchConfigs[userHandle] = DatabaseConnector.generateUserConfig(userHandle)
                    } else {
                        logger.info { "No tweets inserted." }
                        userSearchConfigs[userHandle] = DatabaseConnector.generateUserConfigNoNextToken(userHandle)
                    }
                }
            } catch (exception: Exception) {
                logger.error { "Error while fetching tweets: $exception" }
                userSearchConfigs[userHandle] = DatabaseConnector.generateUserConfigNoNextToken(userHandle)
            }
        }
        logger.info { "END retrieval for user $twitterUserId" }
    }

    fun tryFetchFromLastToken(twitterUserId: Long, userConfig: UserSearchConfig) : JSONObject? {
        if (userConfig.nextToken == "") return null

        val url = createUrlTweetResearch(twitterUserId, UrlBuildingMethod.NEXT_TOKEN, userConfig.nextToken)
        val urlResponse = getUrlResponse(url)
        if (urlResponse == null || urlResponse.getJSONObject("meta").getInt("result_count") == 0) return null
        return urlResponse
    }

    fun tryFetchNew(twitterUserId: Long, userConfig: UserSearchConfig) : JSONObject? {
        if (userConfig.newestId == 0L) return null

        val url = createUrlTweetResearch(twitterUserId, UrlBuildingMethod.SINCE, (userConfig.newestId+1).toString())
        val urlResponse = getUrlResponse(url)
        if (urlResponse == null || urlResponse.getJSONObject("meta").getInt("result_count") == 0) return null
        return urlResponse
    }

    fun tryFetchOld(twitterUserId: Long, userConfig: UserSearchConfig) : JSONObject? {
        if (userConfig.oldestId == 0L) return null

        val url = createUrlTweetResearch(twitterUserId, UrlBuildingMethod.BEFORE, (userConfig.oldestId-1).toString())
        val urlResponse = getUrlResponse(url)
        if (urlResponse == null || urlResponse.getJSONObject("meta").getInt("result_count") == 0) return null
        return urlResponse
    }

    fun tryFetchBlank(twitterUserId: Long) : JSONObject? {

        val url = createUrlTweetResearch(twitterUserId, UrlBuildingMethod.NONE, "")
        val urlResponse = getUrlResponse(url)
        if (urlResponse == null || urlResponse.getJSONObject("meta").getInt("result_count") == 0) return null
        return urlResponse
    }

    fun extractAndInsertTweets(userHandle: String, jsonResTweets: JSONObject) : Int {
        var totInserted = 0

        if (!jsonResTweets.has("meta")) throw Exception("Tweets json response missing required meta fields: $jsonResTweets")

        val data = jsonResTweets.getJSONArray("data")
        val meta = jsonResTweets.getJSONObject("meta")

        transaction {
            for (idx in 0 until data.length()) {
                try {
                    val tweetObject = TweetObject.of(data.getJSONObject(idx))
                    totInserted += DatabaseConnector.insertTweet(tweetObject)
                } catch (exception: Exception) {
                    logger.error { "Error inserting tweet: $exception" }
                }
            }
            val monitoring = Monitoring.of(meta, userHandle)
            logger.info { "Inserting in db monitoring data $monitoring" }
            DatabaseConnector.insertMonitoring(monitoring)

        }
        return totInserted
    }

}