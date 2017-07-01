package io.github.legosteen11.feedforreddit.config

import io.github.legosteen11.simpledbclient.IDatabaseClient

/**
 * Created by wouter on 6/28/17.
 */
interface IConfig {
    val telegramToken: String
    val telegramUsername: String

    val telegramAdminChatId: Long

    val dbUsername: String
    val dbPassword: String
    val dbDatabase: String
    val dbHostname: String
    val dbPort: String

    val databaseClient: IDatabaseClient
}


/*
Example config object:

object MyConfig : IConfig {
    override val telegramToken: String = ""
    override val telegramUsername: String = ""

    override val telegramAdminChatId: Long = -1L

    override val dbUsername: String = ""
    override val dbPassword: String = ""
    override val dbDatabase: String = ""
    override val dbHostname: String = ""
    override val dbPort: String = "3306"

    override val databaseClient: IDatabaseClient = ConnectionPoolDatabaseClient(dbHostname, dbUsername, dbPassword, dbDatabase)
}
*/