package ru.tecon.uploaderService.model

import ru.tecon.uploaderService.ejb.das.ListenerType
import java.io.Serializable
import java.util.Properties

/**
 * Информация по слушателям для системы сбора данных
 *
 * @author Maksim Shchelkonogov
 * 17.01.2024
 */
data class Listener(val dasName: String,
                    val type: ListenerType,
                    val jndiProperties: Properties,
                    val lookupName: String,
                    val counterNameSet: Set<String>) : Serializable