package io.github.legosteen11.feedforreddit

import com.mashape.unirest.http.Unirest
import org.json.JSONObject

/**
 * Created by wouter on 6/28/17.
 */
fun getRequest(url: String): JSONObject = Unirest.get(url)
        .header("accept", "application/json")
        .header("User-Agent", "web:io.github.legosteen11.telegrambotforreddit:v0.1")
        .asJson().body.`object`

fun getHeapSize(): Long = Runtime.getRuntime().totalMemory()

fun getMaxMemSize(): Long = Runtime.getRuntime().maxMemory()

fun getFreeMemSize(): Long = Runtime.getRuntime().freeMemory()

fun getMb(bytes: Long) = bytes / (1024L * 1024L)