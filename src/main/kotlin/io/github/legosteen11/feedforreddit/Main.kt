package io.github.legosteen11.feedforreddit

import io.github.legosteen11.feedforreddit.database.Database
import io.github.legosteen11.feedforreddit.database.Subreddit
import io.github.legosteen11.feedforreddit.telegram.Bot
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.TelegramBotsApi

/**
 * Created by wouter on 6/28/17.
 */

fun main(args: Array<String>) {
    // sync tables
    Database.createTables()

    ApiContextInitializer.init()

    TelegramBotsApi().registerBot(Bot)

    sendNewPosts()

    while (true) {
        Thread.sleep(60000)
        sendNewPosts()
    }
}

fun sendNewPosts() {
    println("Sending new posts.")
    Subreddit.getAll(Database.client).forEach {
        it.notifySubscribersOfNewPosts(Database.client)
    }

    println("Heap size: ${getMb(getHeapSize())}mb\n" +
            "Max memory: ${getMb(getMaxMemSize())}mb\n" +
            "Free memory: ${getMb(getFreeMemSize())}mb")
}