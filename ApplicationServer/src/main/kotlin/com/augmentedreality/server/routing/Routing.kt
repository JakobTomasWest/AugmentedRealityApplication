package com.augmentedreality.server.routing

import com.augmentedreality.server.service.JwtService
import com.augmentedreality.server.service.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*

fun Application.configureRouting(
  jwtService: JwtService,
  userService: UserService,
) {
  routing {
    // Public routes
    route("/api/auth") { authRoute(jwtService, userService) }
    route("/api/user") { userRoute(userService) }

    authenticate {
      route("/api/upload") { uploadRoute() }
      aiRoute()
    }
  }
}

fun extractPrincipalUsername(call: ApplicationCall): String? =
  call.principal<JWTPrincipal>()
    ?.payload
    ?.getClaim("username")
    ?.asString()
