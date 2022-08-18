package infrastructure

data class AuthParams(
    val bearerToken: String
)

data class Schedule(
    val twitterId: Long,
    val frequency: Long,
    val quota: Int
)

data class Database(
    val uri: String
)

data class Config(
    val schedules: List<Schedule>,
    val auth: AuthParams,
    val database: Database
)
