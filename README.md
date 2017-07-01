# FeedForReddit
Telegram bot for receiving Reddit posts from your favourite subreddits!

## How to use
### Use the Telegram bot
Start using FeedForReddit right away by sending a message to [the bot](https://telegram.me/feedforredditbot).

### Run on your own server
What you'll need:
1. A MySQL or MariaDB database
2. Java JDK and JRE installed

#### Step 1): Download the source code:

`git clone https://github.com/Legosteen11/feed-for-reddit.git`

Now cd into the root directory of the repository you just cloned.

`cd feed-for-reddit`

#### Step 2): Add your configuration:

Create a file called `MyConfig.kt` in `src/main/kotlin/io/github/legosteen11/feedforreddit/config`.

With nano: `nano src/main/kotlin/io/github/legosteen11/feedforreddit/config/MyConfig.kt`

Paste this content in the file and enter your credentials:
```kotlin
object MyConfig : IConfig {
    override val telegramToken: String = "token"
    override val telegramUsername: String = "feedforredditbot"

    override val telegramAdminChatId: Long = -1L

    override val dbUsername: String = "username"
    override val dbPassword: String = "password"
    override val dbDatabase: String = "database"
    override val dbHostname: String = "hostname"
    override val dbPort: String = "3306"

    override val databaseClient: IDatabaseClient = ConnectionPoolDatabaseClient(dbHostname, dbUsername, dbPassword, dbDatabase)
}
```

#### Step 3): Compile the bot:
To compile the bot just run the gradle jar task.

`./gradlew jar`

#### Step 4): Run the bot:
To run the bot simply run the jar file that is located in `build/libs`.
For Example:

`java -jar feed-for-reddit-version.jar`

Thanks for reading :)
