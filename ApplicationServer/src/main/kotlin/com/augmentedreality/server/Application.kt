package com.augmentedreality.server

import com.augmentedreality.server.plugins.configureSecurity
import com.augmentedreality.server.plugins.configureSerialization
import com.augmentedreality.server.repository.UserRepository
import com.augmentedreality.server.routing.configureRouting
import com.augmentedreality.server.service.JwtService
import com.augmentedreality.server.service.UserService
import io.ktor.server.application.*

fun main(args: Array<String>) {
  io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
  val userRepository = UserRepository(this)
  val userService = UserService(userRepository)
  val jwtService = JwtService(this, userService)

  configureSerialization()
  configureSecurity(jwtService)
  configureRouting(jwtService, userService)
}
