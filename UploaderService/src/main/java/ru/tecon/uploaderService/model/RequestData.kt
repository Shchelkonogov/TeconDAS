package ru.tecon.uploaderService.model

import java.io.Serializable

/**
 * Информация о запросе к системе сбора данных.
 *
 * @author Maksim Shchelkonogov
 * 19.01.2024
 */
data class RequestData(val serverName: String,
                       val requestId: String,
                       val objectId: String,
                       val counter: String,
                       val objectName: String,
                       var prop: Map<String, String>) : Serializable {
   constructor(serverName: String, requestId: String, objectId: String, counter: String, objectName: String) :
           this(serverName, requestId, objectId, counter, objectName, hashMapOf());
}