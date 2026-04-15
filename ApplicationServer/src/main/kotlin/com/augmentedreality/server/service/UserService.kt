package com.augmentedreality.server.service

import com.augmentedreality.server.model.User
import com.augmentedreality.server.repository.UserRepository
import com.augmentedreality.server.routing.request.LoginRequest
import com.augmentedreality.server.routing.request.UserRequest

class UserService(
    private val userRepository: UserRepository
) {

    fun findByUsername(username: String): User? =
        userRepository.findByUsername(username)

    fun save(user: UserRequest): User? {
        return if(userRepository.save(user)) User(user.username) else null
    }

    fun ldapAuth(loginRequest: LoginRequest) = userRepository.ldapAuth(loginRequest)

}
