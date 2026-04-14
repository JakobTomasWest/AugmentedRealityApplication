package com.codersee.util

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.util.*
import kotlin.text.toCharArray


fun hashPassword(password: String) : String =
    BCrypt.withDefaults().hashToString(14, password.toCharArray())