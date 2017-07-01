package io.github.legosteen11.feedforreddit.telegram

import io.github.legosteen11.feedforreddit.config.MyConfig
import io.github.legosteen11.feedforreddit.getFreeMemSize
import io.github.legosteen11.feedforreddit.getMb
import io.github.legosteen11.feedforreddit.getHeapSize
import io.github.legosteen11.feedforreddit.getMaxMemSize
import io.github.legosteen11.feedforreddit.database.Database
import io.github.legosteen11.feedforreddit.database.Subreddit
import io.github.legosteen11.feedforreddit.database.User
import io.github.legosteen11.feedforreddit.database.syncBatch
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot

/**
 * Created by wouter on 6/28/17.
 */
object Bot : TelegramLongPollingBot() {
    override fun getBotUsername(): String = MyConfig.telegramUsername

    override fun getBotToken(): String = MyConfig.telegramToken

    override fun onUpdateReceived(update: Update?) {
        if(update != null && update.hasMessage() && update.message.text.isNotBlank()) {
            val user = User(update.message.chatId)
            if(user.sync(Database.client)) {
                // new user
                user.sendMessage("Welcome! Start by subscribing to your favorite subreddits with /subscribe <subreddit>.")
            } else {

                update.message.text.split(" ").let {
                    val parameters = it.subList(1, it.size).toTypedArray()
                    val commandText = it.first().replace("/", "")

                    var reply: SendMessage

                    Command.getCommand(commandText)?.let {
                        if (it.adminOnly) {
                            if (user.isAdmin()) {
                                reply = it.getMessage(user, parameters)
                            } else {
                                reply = SendMessage(user.chatId, "Sorry, only admins can use this command.")
                            }
                        } else {
                            reply = it.getMessage(user, parameters)
                        }

                        sendMessage(reply)
                    }
                }
            }
        }
    }
}

enum class Command(val adminOnly: Boolean = false,
                   val execute: (User, Array<String>) -> SendMessage) {
    SUBSCRIBE(false, { user, options ->
        SendMessage(user.chatId,
                options.isEmpty().let { empty ->
                    if(empty) {
                        "Please specify a subreddit."
                    } else {
                        options.first().toLowerCase().let { subName ->
                            Subreddit(subName).let { subreddit ->
                                if(subreddit.existsOnReddit()) {
                                    user.subscribeTo(Database.client, subreddit.apply { sync(Database.client).let { if(it) getNewPosts(Database.client)?.syncBatch(Database.client) } } /* sync posts so that user won't get spammed with a lot of posts */).let { success ->
                                        if(success) {
                                            "Successfully subscribed to $subName."
                                        } else {
                                            "You are already subscribed to $subName."
                                        }
                                    }
                                } else {
                                    "$subName is not a subreddit."
                                }
                            }
                        }
                    }
                }
        )
    }),

    UNSUBSCRIBE(false, {user, options ->
        SendMessage(user.chatId,
                options.isEmpty().let { empty ->
                    if(empty) {
                        "Please specify a subreddit."
                    } else {
                        options.first().toLowerCase().let { subName ->
                            io.github.legosteen11.feedforreddit.database.Subreddit(subName).apply { sync(Database.client) }.let { subreddit ->
                                if(user.unsubscribeFrom(Database.client, subreddit)) {
                                    "Successfully unsubscribed from $subName."
                                } else {
                                    "You were never subscribed to $subName."
                                }
                            }
                        }
                    }
                }
        )
    }),

    STATS(true, {user, options ->
        SendMessage(user.chatId, "Sent posts: ${Database.client.getSingleValueQuery("SELECT count(*) FROM Post")}\n" +
                "Users: ${Database.client.getSingleValueQuery("SELECT count(*) FROM User")}\n" +
                "Subscriptions: ${Database.client.getSingleValueQuery("SELECT count(*) FROM Subscription")}\n" +
                "Subreddits: ${Database.client.getSingleValueQuery("SELECT count(*) FROM Subreddit")}\n" +
                "Heap size: ${getMb(getHeapSize())}mb\n" +
                "Max memory: ${getMb(getMaxMemSize())}mb\n" +
                "Free memory: ${getMb(getFreeMemSize())}mb")
    }),

    SUBSCRIPTIONS(false, {user, options ->
        SendMessage(user.chatId, "You are subscribed to: ${user.getSubscriptions(Database.client).joinToString { it.name }}")
    }),

    ABOUT(false, {user, options ->
        SendMessage(user.chatId, "Thank you for using this bot.\n\nThis bot was developed by Wouter Doeland and contributors. Check the source code here: https://github.com/Legosteen11/FeedForReddit")
    }),

    HELP(false, {user, strings ->
        SendMessage(user.chatId, "To get updates from your favorite Subreddits, subscribe to them with /subscribe <subreddit>.\nFor example: /subscribe pics\nUnsubscribe from a Subreddit with /unsubscribe <subreddit>.\nGet a list of all your subscriptions with /subscriptions.\nGet help (show this message): /help\nGet some info about this bot: /about.")
    })

    ;


    fun getMessage(user: User, parameters: Array<String>): SendMessage = execute(user, parameters)

    companion object {
        fun getCommand(name: String): Command? = values().firstOrNull { it.name.toLowerCase() == name.toLowerCase() }
    }
}

/*
Commands:
subscribe - Subscribe to a Subreddit
unsubscribe - Unsubscribe from a Subreddit
subscriptions - List your subscriptions
help - Get help for the commands
about - About this bot
 */