package com.augmentedreality.server.routing

import com.augmentedreality.server.routing.request.UserRequest
import com.augmentedreality.server.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoute(userService: UserService) {

    post {
        val userRequest = call.receive<UserRequest>()
        println("user request: $userRequest")
        val createdUser = userService.save(
            user = userRequest
        ) ?: return@post call.respond(HttpStatusCode.BadRequest)

        call.response.header(
            name = "username",
            value = createdUser.username.toString()
        )
        call.respond(HttpStatusCode.Created, "User created successfully")
    }


}
