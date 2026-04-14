package com.codersee.routing

import com.codersee.routing.request.LoginRequest
import com.codersee.routing.request.UserRequest
import com.codersee.service.JwtService
import com.codersee.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoute(jwtService: JwtService, userService: UserService) {

  suspend fun ApplicationCall.login() {
    val loginRequest = receive<LoginRequest>()

    val token: String? = jwtService.createJwtToken(loginRequest)

    if (token != null) {
        respond(
            status = HttpStatusCode.OK,
            message = mapOf("token" to token)
        )
    } else {
        respond(HttpStatusCode.Unauthorized, "Invalid username or password")
    }
  }

  // LOGIN
  post { call.login() }
  post("/login") { call.login() }

  // SIGN UP
  post("/signup") {
    val userRequest = call.receive<UserRequest>()

    val createdUser = userService.save(userRequest)

    if (createdUser != null) {
        call.respond(HttpStatusCode.Created, "User created successfully")
    } else {
        call.respond(HttpStatusCode.BadRequest, "Sign up failed")
    }
  }

}
