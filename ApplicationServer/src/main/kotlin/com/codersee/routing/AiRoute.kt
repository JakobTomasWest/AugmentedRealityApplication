package com.codersee.routing

import io.ktor.server.auth.jwt.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.call.*
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.streams.asInput
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream

fun Route.aiRoute() {
    val mlBase = run {
        val env = System.getenv("ML_URL")
        if (!env.isNullOrBlank()) env
        else {
            val resolves = try {
                java.net.InetAddress.getByName("ml"); true
            } catch (_: Throwable) { false }
            if (resolves) "http://ml:8000" else "http://localhost:8000"
        }
    }
    val client = HttpClient(Apache) {
        expectSuccess = false
    }

    authenticate {
        route("/api/ai") {
            post("/segment-bytes") {
                val parts = call.receiveMultipart()
                var imageBytes: ByteArray? = null
                var incomingContentType: ContentType? = null

                parts.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> if (part.name == "image") {
                            val packet = part.provider().readRemaining()
                            imageBytes = packet.readBytes()
                            incomingContentType = part.contentType
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                val payload = imageBytes
                if (payload == null) {
                    call.respond(HttpStatusCode.BadRequest, "No 'image' part")
                    return@post
                }

                val mlResp: HttpResponse = try {
                    client.submitFormWithBinaryData(
                        url = "$mlBase/segment",
                        formData = formData {
                            append(
                                key = "image",
                                value = payload,
                                headers = Headers.build {
                                    val ct = (incomingContentType ?: ContentType.Application.OctetStream).toString()
                                    append(HttpHeaders.ContentType, ct)
                                    append(HttpHeaders.ContentDisposition, "filename=upload")
                                }
                            )
                        }
                    ) {
                        expectSuccess = false
                        accept(ContentType.Image.PNG)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, "ML service error: ${e.message}")
                    return@post
                }

                val segStatus = mlResp.status
                val segType = mlResp.headers[HttpHeaders.ContentType] ?: ""
                val segBody = mlResp.body<ByteArray>()

                if (!segStatus.isSuccess()) {
                    val preview = segBody.decodeToString().take(200)
                    call.respond(segStatus, if (preview.isNotBlank()) preview else "Upstream ML error (${segStatus.value})")
                    return@post
                }

                if (!segType.contains("image/png", ignoreCase = true)) {
                    val preview = runCatching { segBody.decodeToString().take(160) }.getOrNull() ?: "<binary>"
                    call.respond(HttpStatusCode.BadGateway, "Unexpected ML Content-Type: $segType; body preview=\"$preview\"")
                    return@post
                }

                call.respondBytes(segBody, ContentType.Image.PNG)
            }
            post("/detect-bytes") {
                val parts = call.receiveMultipart()
                var imageBytes: ByteArray? = null
                var incomingContentType: ContentType? = null

                parts.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> if (part.name == "image") {
                            run { val p = part.provider().readRemaining(); imageBytes = p.readBytes() }
                            incomingContentType = part.contentType
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                val payload = imageBytes
                if (payload == null) {
                    call.respond(HttpStatusCode.BadRequest, "No 'image' part")
                    return@post
                }

                val mlResp: HttpResponse = try {
                    client.submitFormWithBinaryData(
                        url = "$mlBase/detect",
                        formData = formData {
                            append(
                                key = "image",
                                value = payload,
                                headers = Headers.build {
                                    val ct = (incomingContentType ?: ContentType.Application.OctetStream).toString()
                                    append(HttpHeaders.ContentType, ct)
                                    append(HttpHeaders.ContentDisposition, "filename=upload")
                                }
                            )
                        }
                    ) {
                        // Do not throw on non-2xx so we can forward status/body to client
                        expectSuccess = false
                        accept(ContentType.Application.Json)
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, "ML service error: ${e.message}")
                    return@post
                }

                val status = mlResp.status
                val contentType = mlResp.headers[HttpHeaders.ContentType] ?: ""
                val bodyText = mlResp.bodyAsText()

                if (!status.isSuccess()) {
                    // Bubble up ML error to the client with same status & body
                    call.respond(status, bodyText.ifBlank { "Upstream ML error (${status.value})" })
                    return@post
                }

                if (!contentType.contains("application/json", ignoreCase = true)) {
                    call.respond(
                        HttpStatusCode.BadGateway,
                        "Unexpected ML Content-Type: $contentType; body preview=\"${bodyText.take(160)}\""
                    )
                    return@post
                }

                if (bodyText.isBlank()) {
                    call.application.environment.log.warn("/api/ai/detect-bytes: upstream returned 200 with empty body")
                    call.respond(HttpStatusCode.BadGateway, "ML returned empty JSON body")
                    return@post
                }

                call.respondText(bodyText, ContentType.Application.Json)
            }
            get("/detect-name/{name}") {
                val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest, "missing name")
                val uploadRoot = System.getenv("UPLOAD_ROOT") ?: "/app/uploads"

                // pull user id from JWT (attempt common claim keys)
                val principal = call.principal<JWTPrincipal>()
                val userId =
                    principal?.getClaim("userId", String::class)
                        ?: principal?.getClaim("username", String::class)
                        ?: principal?.getClaim("name", String::class)
                        ?: principal?.getClaim("preferred_username", String::class)
                        ?: principal?.subject

                val root = java.nio.file.Path.of(uploadRoot)
                val candidates = buildList {
                    if (!userId.isNullOrBlank()) add(root.resolve(userId).resolve(name))
                    add(root.resolve(name))
                }

                val file = candidates.firstOrNull { java.nio.file.Files.exists(it) }
                if (file == null) {
                    call.application.environment.log.info(
                        "/api/ai/detect-name 404: not found in any of: " +
                            candidates.joinToString(" | ") { it.toString() }
                    )
                    return@get call.respond(HttpStatusCode.NotFound, "file not found")
                }

                val ct = when {
                    name.endsWith(".png",  true) -> ContentType.Image.PNG
                    name.endsWith(".webp", true) -> ContentType.parse("image/webp")
                    name.endsWith(".heic", true) || name.endsWith(".heif", true) -> ContentType.parse("image/heic")
                    else -> ContentType.Image.JPEG
                }

                val resp: HttpResponse = client.submitFormWithBinaryData(
                    url = "$mlBase/detect",
                    formData = formData {
                        append(
                            key = "image",
                            value = file.inputStream().asInput(),
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, ct.toString())
                                append(
                                    HttpHeaders.ContentDisposition,
                                    """form-data; name="image"; filename="${file.fileName}""""
                                )
                            }
                        )
                    }
                ) {
                    expectSuccess = false
                    accept(ContentType.Application.Json)
                }

                val status = resp.status
                val contentType = resp.headers[HttpHeaders.ContentType] ?: ""
                val text = resp.bodyAsText()

                if (!status.isSuccess()) {
                    return@get call.respond(HttpStatusCode.BadGateway, "ML error $status: $text")
                }
                if (!contentType.contains("application/json", ignoreCase = true)) {
                    return@get call.respond(
                        HttpStatusCode.BadGateway,
                        "Unexpected ML Content-Type: $contentType; body preview=\"${text.take(160)}\""
                    )
                }
                if (text.isBlank()) {
                    call.application.environment.log.warn("/api/ai/detect-name: upstream returned 200 with empty body")
                    return@get call.respond(HttpStatusCode.BadGateway, "ML returned empty JSON body")
                }

                call.respondText(text, ContentType.Application.Json)
            }
        }
    }
}