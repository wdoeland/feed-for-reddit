package io.github.legosteen11.feedforreddit.database

import com.mashape.unirest.http.Unirest
import io.github.legosteen11.feedforreddit.config.IConfig
import io.github.legosteen11.feedforreddit.config.MyConfig
import io.github.legosteen11.feedforreddit.config.reddit_url_http
import io.github.legosteen11.feedforreddit.getRequest
import io.github.legosteen11.feedforreddit.telegram.Bot
import io.github.legosteen11.simpledbclient.IDatabaseClient
import org.intellij.lang.annotations.Language
import org.json.JSONException
import org.json.JSONObject
import org.telegram.telegrambots.api.methods.send.SendMessage
import java.sql.Statement

fun createSqlParameterString(size: Int): String {
    var result = ""
    (1..size).forEach {
        if(it==1)
            result = "?"
        else
            result += ", ?"
    }

    return result
}

fun createSqlInsertParameterString(vararg parameters: Pair<String, Any?>): Pair<String, Array<Any?>> {
    var result = ""

    parameters.forEachIndexed { index, parameter ->
        if(index==0)
            result = parameter.first
        else
            result += ", ${parameter.first}"
    }

    return Pair(result, parameters.map { it.second }.toTypedArray())
}

fun createSqlAndOrOrString(vararg parameters: Pair<String, Any?>, and: Boolean = true): Pair<String, Array<Any?>> {
    var result = ""

    parameters.forEachIndexed { index, parameter ->
        if(index==0)
            result = "${parameter.first} = ?"
        else
            result += " ${if(and) "AND" else "OR"} ${parameter.first} = ?"
    }

    return Pair(result, parameters.map { it.second }.toTypedArray())
}

fun parameterMapToArray(vararg parameters: Pair<String, Any?>): Array<Any?> {
    val list = arrayListOf<Any?>()
    parameters.forEach {
        list.add(it.first)
        list.add(it.second)
    }

    return list.toTypedArray()
}

interface IEntity {
    /**
     * The identifier of the entity
     */
    var id: Int

    /**
     * Check whether the entity exists
     *
     * @return Returns true if the entity exists
     */
    fun exists(database: IDatabaseClient): Boolean = database.getSingleValueQuery("SELECT id FROM ${getTableName()} WHERE ${createSqlAndOrOrString(*getParameters()).first}", *createSqlAndOrOrString(*getParameters()).second) != null

    /**
     * Create the entity in the database.
     *
     * @return Returns true if new
     */
    fun sync(database: IDatabaseClient): Boolean {
        if(!exists(database)) {
            id = database.executeUpdate("INSERT INTO ${getTableName()} (${createSqlInsertParameterString(*getParameters()).first}) VALUES(${createSqlParameterString(getParameters().size)})", *createSqlInsertParameterString(*getParameters()).second, returnGeneratedKeys = Statement.RETURN_GENERATED_KEYS)

            return true
        }
        //language=MySQL
        id = database.getSingleValueQuery("SELECT id FROM ${getTableName()} WHERE ${createSqlAndOrOrString(*getParameters()).first}", *createSqlAndOrOrString(*getParameters()).second) as Int

        return false
    }

    /**
     * Remove the entity from the database
     *
     * @param database The database client
     *
     * @return Returns true if the object existed and is removed and false if it didn't exist and wasn't removed.
     */
    fun remove(database: IDatabaseClient): Boolean {
        if(exists(database)) {
            database.executeUpdate("DELETE FROM ${getTableName()} WHERE id = ?", id)
            return true
        } else {
            return false
        }
    }

    fun getParameters(): Array<Pair<String, Any?>>

    //fun getParameterValue(name: String?): Any? = this.javaClass.getField(name)[name]

    /**
     * Get the name of this table
     *
     * @return The name of this table
     */
    fun getTableName(): String = this::class.simpleName!!

    //fun getCustomParameters() = this::class.primaryConstructor?.parameters?.map { it.name }?.toMutableList()?.apply { remove("id") }?.toTypedArray() ?: arrayOf()
}

data class User(val chatId: Long,
                override var id: Int = -1): IEntity {
    override fun getParameters(): Array<Pair<String, Any?>> = arrayOf(Pair("chatId", chatId))

    /**
     * Subscribe a user to a [subreddit]
     *
     * @param database The database client
     * @param subreddit The subreddit to subscribe to
     *
     * @return Returns true if the subscriptions was new, returns false if the user was already subscribed
     */
    fun subscribeTo(database: IDatabaseClient, subreddit: Subreddit): Boolean = Subscription(id, subreddit.id).sync(database)

    /**
     * Unsubscribe from a [subreddit]
     *
     * @param database The database client
     * @param subreddit The subreddit to unsubscribe from
     *
     * @return Returns true if successfully unsubscribed, returns false if the user was never subscribed to this subreddit
     *
     */
    fun unsubscribeFrom(database: IDatabaseClient, subreddit: Subreddit): Boolean = Subscription(id, subreddit.id).apply { sync(database) }.remove(database)

    /**
     * Send a [message] to this user on Telegram
     *
     * @param message The message
     */
    fun sendMessage(message: String) {
        Bot.sendMessage(SendMessage(chatId, message))
    }

    /**
     * Check whether the user is admin or not
     *
     * @return Returns true if the user is admin
     */
    fun isAdmin(): Boolean = chatId == MyConfig.telegramAdminChatId

    /**
     * Get the subreddits that this user is subscribed to
     *
     * @param database The database client
     *
     * @return An array with the subreddits that  the user is subscribed to.
     */
    @Language("MySQL")
    fun getSubscriptions(database: IDatabaseClient): Array<Subreddit> {
        Database.client.executeQuery("SELECT Subreddit.id, Subreddit.name FROM Subreddit INNER JOIN Subscription ON Subreddit.id = Subscription.subredditId WHERE Subscription.userId = ?", id).let { resultSet ->
            arrayListOf<Subreddit>().let {
                while (resultSet.next()) {
                    it.add(Subreddit(resultSet.getString("Subreddit.name"), resultSet.getInt("Subreddit.id")))
                }

                return it.toTypedArray()
            }
        }
    }
}

data class Subreddit(val name: String,
                     override var id: Int = -1): IEntity {
    override fun getParameters(): Array<Pair<String, Any?>> = arrayOf(Pair("name", name))

    /**
     * Get the subreddit json url
     *
     * @return The url to the subreddit json.
     */
    fun getSubRedditJsonUrl(): String = "$reddit_url_http/r/$name.json"

    /**
     * Check whether the subreddit exists on Reddit
     *
     * @return Returns true if the subreddit exists, false if it doesn't
     */
    fun existsOnReddit(): Boolean {
        try {
            getRequest(getSubRedditJsonUrl()).getJSONObject("data")?.getJSONArray("children")?.forEach { child ->
                if (child is JSONObject) {
                    child.getJSONObject("data").let { data ->
                        data.getString("id").let { postId ->
                            if (postId != null) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (e: JSONException) {
            return false
        }

        return false
    }

    /**
     * Get all new posts for this subreddit
     *
     * @param database The database client
     *
     * @return An array with new posts in this subreddit.
     */
    fun getNewPosts(database: IDatabaseClient): Array<RichPost>? {
        val posts = arrayListOf<RichPost>()
        getRequest("$reddit_url_http/r/$name.json").getJSONObject("data")?.getJSONArray("children")?.forEach { child ->
            if(child is JSONObject) {
                child.getJSONObject("data").let { data ->
                    data.getString("id").let { postId ->
                        if(postId != null) {
                            posts.add(RichPost(Post(postId, id), data.getString("title"), "$reddit_url_http${data.getString("permalink")}"))
                        }
                    }
                }
            } else
                return null
        }

        //al oldPosts = posts.toTypedArray().filterExistsAndSync(database)

        //val newPosts = posts.filter { !oldPosts.contains(Post(it.postId, it.subredditId, it.id)) }

        return posts.toTypedArray().filterNotExists(database)
    }

    fun getSubScribers(database: IDatabaseClient): Array<User> {
        val subs = arrayListOf<User>()

        database.executeQuery("SELECT User.id, User.chatId FROM User INNER JOIN Subscription ON User.id = Subscription.userId WHERE Subscription.subredditId = ?", id).let {
            while (it.next())
                subs.add(User(it.getLong("chatId"), it.getInt("id")))
        }

        return subs.toTypedArray()
    }

    fun notifySubscribersOfNewPosts(database: IDatabaseClient) {
        val newPosts = getNewPosts(database)?.apply { syncBatch(database) }
        val subscribers = getSubScribers(database)

        newPosts?.forEach { post ->
            subscribers.forEach { user ->
                Bot.sendMessage(SendMessage(user.chatId, "${post.title}\n${post.url}"))
            }
        }
    }

    companion object {
        /**
         * Get all subreddits
         *
         * @param database The database client
         *
         * @return Returns an array of all Subreddits that are in the database
         */
        fun getAll(database: IDatabaseClient): Array<Subreddit> {
            val subs = arrayListOf<Subreddit>()

            database.executeQuery("SELECT id, name FROM Subreddit").let {
                while (it.next())
                    subs.add(Subreddit(it.getString("name"), it.getInt("id")))
            }

            return subs.toTypedArray()
        }
    }
}

open class Post(val postId: String,
                val subredditId: Int,
                override var id: Int = -1): IEntity {
    override fun getParameters(): Array<Pair<String, Any?>> = arrayOf(Pair("postId", postId), Pair("subredditId", subredditId))

    /**
     * Get rich post data and return a rich post object
     *
     * @return A rich post object
     */
    fun toRichPost(): RichPost? {
        val subName = getSubreddit()?.name
        if(subName != null) {
            val url = "$reddit_url_http/r/$subName/$postId.json"

            Unirest.get(url).header("accept", "application/json").asJson().let {
                if(it.body.isArray) {
                    it.body.array.getJSONObject(0)?.getJSONObject("data")?.getJSONArray("children")?.getJSONObject(0)?.getJSONObject("data")?.getString("title").let {
                        title -> return RichPost(this, title ?: "Could not load title", url)
                    }
                }
            }
        }
        return null
    }

    /**
     * Get the subreddit that this post was posted in
     *
     * @return Return the subreddit or null if it was not found for some reason.
     */
    @Language("MySQL")
    fun getSubreddit(): Subreddit? = Database.client.executeQuery("SELECT name, id FROM Subreddit WHERE id = ?", subredditId).let {
        if(it.next()) {
            Subreddit(it.getString("name"), it.getInt("id"))
        } else {
            null
        }
    }
}

class RichPost(post: Post,
               val title: String,
               val url: String): Post(post.postId, post.subredditId, post.id) {
    override fun getTableName(): String = "Post"
}

data class Subscription(val userId: Int,
                         val subredditId: Int,
                         override var id: Int = -1): IEntity {
    override fun getParameters(): Array<Pair<String, Any?>> = arrayOf(Pair("userId", userId), Pair("subredditId", subredditId))
}

object Database {
    val config: IConfig = MyConfig

    val client: IDatabaseClient = config.databaseClient

    fun createTables() {
        // sync users table
        //language=MySQL
        client.executeUpdate(
                """
CREATE TABLE IF NOT EXISTS User (
  id INT(11) PRIMARY KEY AUTO_INCREMENT,
  chatId BIGINT UNIQUE
)
"""
        )

        // sync subreddits table
        //language=MySQL
        client.executeUpdate(
                """
CREATE TABLE IF NOT EXISTS Subreddit (
  id INT(11) PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(191) UNIQUE
)
"""
        )

        // sync posts table
        //language=MySQL
        client.executeUpdate(
                """
CREATE TABLE IF NOT EXISTS Post (
  id INT(11) PRIMARY KEY AUTO_INCREMENT,
  postId VARCHAR(20) UNIQUE,
  subredditId INT(11),
  FOREIGN KEY (subredditId)
    REFERENCES Subreddit(id)
)
"""
        )

        // sync subscriptions table
        //language=MySQL
        client.executeUpdate(
                """
CREATE TABLE IF NOT EXISTS Subscription (
  id INT(11) PRIMARY KEY AUTO_INCREMENT,
  userId INT(11),
  subredditId INT(11),
  FOREIGN KEY (userId)
    REFERENCES User(id),
  FOREIGN KEY (subredditId)
    REFERENCES Subreddit(id)
)
"""
        )
    }
}

fun Array<out IEntity>.syncBatch(database: IDatabaseClient) {
    if(isNotEmpty()) {
        val firstObject = first()

        val sql = "INSERT INTO ${firstObject.getTableName()} (${createSqlInsertParameterString(*firstObject.getParameters()).first}) VALUES(${createSqlParameterString(firstObject.getParameters().size)})"

        val ids = database.executeBatchUpdate(sql, *this.map { it.getParameters().map { it.second }.toTypedArray() }.toTypedArray())

        forEachIndexed { index, any ->
            any.id = ids[index]
        }
    }
}

/**
 * Get all the existing posts of an array of posts and sync their ID's with id from the database
 *
 * @param database The database client
 *
 * @return An array with all the posts from the array of posts that are in the database
 */
fun Array<RichPost>.filterNotExists(database: IDatabaseClient): Array<RichPost> {
    val nonExisting = this.toMutableList()

    createSqlAndOrOrString(*map { Pair("postId", it.postId) }.toTypedArray(), and = false).let {
        val sql = "SELECT postId, id FROM Post WHERE ${it.first}"
        val params = it.second


        database.executeQuery(sql, *params).let {
            while (it.next()) {
                nonExisting.remove(nonExisting.first { post -> post.postId == it.getString("postId") })
            }
        }
    }

    return nonExisting.toTypedArray()
}