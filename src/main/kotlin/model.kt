import org.json.JSONObject
import org.sqlite.util.StringUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class TweetObject(
    val twitterIdRaw: String,
    val text: String,
    val createdAtRaw: String,
    val lang: String,
    val authorIdRaw: String) {

    val createdAt: LocalDateTime = LocalDateTime.parse(createdAtRaw, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
    val twitterId: Long = twitterIdRaw.toLong()
    val authorId: Long = authorIdRaw.toLong()

    companion object {
        fun of(jsonObject: JSONObject): TweetObject {
            return TweetObject(
                jsonObject.getString("id"),
                jsonObject.getString("text"),
                jsonObject.getString("created_at"),
                jsonObject.getString("lang"),
                jsonObject.getString("author_id"),
            )
        }
    }

}

data class TwitterUser(val userIdRaw: String, val name: String, val handle: String) {
    val userId: Long = userIdRaw.toLong()

    constructor(userId: Long, name: String, handle: String) : this(userId.toString(), name, handle) {}
}

data class Monitoring(
    val newestId: Long,
    val oldestId: Long,
    val resultCount: Int,
    val nextToken: String,
    val searchedUser: String
) {
    companion object {
        fun of(jsonObject: JSONObject, searchedUser: String): Monitoring {
            return Monitoring(
                jsonObject.getLong("newest_id"),
                jsonObject.getLong("oldest_id"),
                jsonObject.getInt("result_count"),
                if (jsonObject.has("next_token")) jsonObject.getString("next_token") else "",
                searchedUser
            )
        }
    }
}

data class UserSearchConfig(var nextToken: String, var newestId: Long, var oldestId: Long) {

    constructor(newestId: Long, oldestId: Long) : this("", newestId, oldestId) {}

    companion object {
        fun empty(): UserSearchConfig {
            return UserSearchConfig("", 0, 0)
        }
    }
}

class UrlUserTimeline private constructor(
    baseUrl: String,
    userId: String,
    maxResult: Int,
    tweetFields: List<String>?,
    lastParam: Pair<UrlBuildingMethod, String>?
) {
    val urlString: String
    val endpoint: String = "2/users/$userId/tweets"
    init {
        val maxResultPart = "max_results=$maxResult"
        val tweetFieldsPart = if (tweetFields!=null) "tweet.fields=${StringUtils.join(tweetFields, ",")}" else null
        val lastParamPart = if (lastParam!=null) {
            val policy = lastParam.first
            val valueParam = lastParam.second
            when(policy) {
                UrlBuildingMethod.NONE -> null
                UrlBuildingMethod.BEFORE -> "until_id=$valueParam"
                UrlBuildingMethod.NEXT_TOKEN -> "pagination_token=$valueParam"
                UrlBuildingMethod.SINCE -> "since_id=$valueParam"
            }
        } else {
            null
        }
        urlString = baseUrl + endpoint + "?" + listOfNotNull(maxResultPart, tweetFieldsPart, lastParamPart).joinToString("&")
    }

    data class Builder(
        var baseUrl: String,
        var userId: Long,
        var maxResult: Int = 100,
        var tweetFields: List<String>? = null,
        var lastParam: Pair<UrlBuildingMethod, String>? = null
    ) {
        fun maxResult(maxResult: Int) = apply { this.maxResult = maxResult }
        fun tweetFields(tweetFields: List<String>?  ) = apply { this.tweetFields = tweetFields }
        fun lastParam(lastParam: Pair<UrlBuildingMethod, String>? ) = apply { this.lastParam = lastParam }

        fun build() = UrlUserTimeline(baseUrl, userId.toString(), maxResult, tweetFields, lastParam)
    }
}

enum class UrlBuildingMethod {
    NEXT_TOKEN,
    SINCE,
    BEFORE,
    NONE
}