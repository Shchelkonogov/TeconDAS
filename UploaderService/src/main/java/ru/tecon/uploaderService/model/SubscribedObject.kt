package ru.tecon.uploaderService.model

import java.io.Serializable

/**
 * Информация о подписанных объектах системы
 *
 * @author Maksim Shchelkonogov
 * 15.11.2023
 */
data class SubscribedObject(val id: String,
                            val objectName: String,
                            val serverName: String) : Serializable
