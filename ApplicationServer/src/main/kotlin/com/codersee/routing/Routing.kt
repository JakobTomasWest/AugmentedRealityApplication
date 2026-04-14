package com.codersee.routing

import com.codersee.repository.NotesRepository
import com.codersee.service.JwtService
import com.codersee.service.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*

fun Application.configureRouting(
  jwtService: JwtService,
  userService: UserService,
  notesRepository: NotesRepository,
) {
  routing {
    // Public routes
    route("/api/auth") { authRoute(jwtService) }
    route("/api/user") { userRoute(userService) }

    authenticate {

      route("/api/notes") { noteRoute(notesRepository) }


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
