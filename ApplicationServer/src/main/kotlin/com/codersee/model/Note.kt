package com.codersee.model

import kotlinx.serialization.Serializable


@Serializable
data class Note(val message: String, val public: Boolean)