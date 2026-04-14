package com.codersee.routing

import com.codersee.model.User
import com.codersee.routing.request.UserRequest
import com.codersee.routing.response.UserResponse
import com.codersee.service.UserService
import com.codersee.util.hashPassword
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.userRoute(userService: UserService) {

    // create a new user
    post {
        val userRequest = call.receive<UserRequest>()
        println("user request: ${userRequest}")
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

private fun UserRequest.toModel(): User =
    User(
        //id = UUID.randomUUID(),
        username = this.username,
        //hashedPassword = hashPassword(this.password)
    )

private fun User.toResponse(): UserResponse =
    UserResponse(
        //id = this.id,
        username = this.username
    )

