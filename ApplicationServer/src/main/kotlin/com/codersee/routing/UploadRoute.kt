package com.codersee.routing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import kotlin.io.path.Path
import kotlin.io.path.div
import io.ktor.utils.io.*
import java.nio.file.Files.createDirectories

private val UPLOAD_ROOT: String = System.getenv("UPLOAD_ROOT") ?: "uploads"

val uploadDir = Path(UPLOAD_ROOT)


private fun sanitize(name: String): String {
    val cleaned = buildString {
        for (ch in name) {
            if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-') append(ch)
        }
    }.take(128)
    return cleaned
}

private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp")

private fun contentTypeForExtension(ext: String): ContentType =
    when (ext.lowercase()) {
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "png"         -> ContentType.Image.PNG
        "webp" -> ContentType.parse("image/webp")
        else          -> ContentType.Application.OctetStream
    }

fun Route.uploadRoute(){

    get {
        val username = extractPrincipalUsername(call) ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val dir = (uploadDir / username).toFile().apply { mkdirs() }
        val names = dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
        call.respond(names)
    }

    get("{filename}") {
        val username = extractPrincipalUsername(call) ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val raw = call.parameters["filename"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val filename = sanitize(raw)
        if (filename.isBlank()) return@get call.respond(HttpStatusCode.BadRequest, "Invalid filename.")
        val file = (uploadDir / username / filename).toFile()
        if (!file.exists() || file.isDirectory) return@get call.respond(HttpStatusCode.NotFound)
        val ext = file.extension
        val ct  = contentTypeForExtension(ext)
        call.respondBytes(file.readBytes(), contentType = ct)
    }

    post("{filename}") {
        val username = extractPrincipalUsername(call) ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val raw = call.parameters["filename"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val filename = sanitize(raw)
        if (filename.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Invalid filename.")
        val ext = filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isEmpty() || ext !in allowedExtensions) {
            return@post call.respond(HttpStatusCode.UnsupportedMediaType, "Only ${allowedExtensions.joinToString()} allowed.")
        }

        // ensure user folder exists
        createDirectories(uploadDir / username)
        val target = (uploadDir / username / filename).toFile()
        if (target.exists()) return@post call.respond(HttpStatusCode.Conflict, "File already exists.")

        var wrote = false
        val multipart = call.receiveMultipart(formFieldLimit = 100 * 1024 * 1024) // 100MB
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    if (part.name == "image") {
                        part.provider().copyAndClose(target.writeChannel())
                        wrote = true
                    }
                }
                else -> Unit
            }
            part.dispose()
        }

        if (!wrote) return@post call.respond(HttpStatusCode.BadRequest, "No file part (expected 'image').")

        call.respond(HttpStatusCode.Created, target.name)
    }
}