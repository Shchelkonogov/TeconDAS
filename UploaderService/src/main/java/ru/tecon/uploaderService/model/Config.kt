package ru.tecon.uploaderService.model

import java.io.Serializable

/**
 * Информация о конфигурации
 *
 * @author Maksim Shchelkonogov
 * 04.10.2024
 */
data class Config(val name: String, val sysInfo: String) : Serializable {

    constructor(name: String): this(name, "")
}
