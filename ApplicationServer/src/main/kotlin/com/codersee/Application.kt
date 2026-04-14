package com.codersee

import com.codersee.plugins.configureSecurity
import com.codersee.plugins.configureSerialization
import com.codersee.repository.NotesRepository
import com.codersee.repository.UserRepository
import com.codersee.routing.configureRouting
import com.codersee.service.JwtService
import com.codersee.service.UserService
import io.ktor.server.application.*

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  val userRepository = UserRepository(this)
  val userService = UserService(userRepository)
  val jwtService = JwtService(this, userService)

  val notesRepository = NotesRepository()

  configureSerialization()
  configureSecurity(jwtService)
  configureRouting(jwtService, userService, notesRepository)
}
