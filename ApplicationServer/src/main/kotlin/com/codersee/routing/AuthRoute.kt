package com.codersee.routing

import com.codersee.routing.request.LoginRequest
import com.codersee.service.JwtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoute(jwtService: JwtService) {

  post {
    val loginRequest = call.receive<LoginRequest>()

    val token: String? = jwtService.createJwtToken(loginRequest)

    if (token != null) {
        call.respond(
            status = HttpStatusCode.OK,
            message = mapOf("token" to token)
        )
    } else {
        call.respond(HttpStatusCode.Unauthorized)
    }
  }

}