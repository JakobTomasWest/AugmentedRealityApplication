package com.codersee.service

import com.codersee.model.User
import com.codersee.repository.UserRepository
import com.codersee.routing.request.LoginRequest
import com.codersee.routing.request.UserRequest
import io.ktor.server.auth.Principal
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.ldap.ldapAuthenticate
import java.util.*
import javax.naming.ldap.LdapName

class UserService(
    private val userRepository: UserRepository
) {


//  fun findAll(): List<User> =
//    userRepository.findAll()

//  fun findById(id: String): User? =
//    userRepository.findById(
//      id = UUID.fromString(id)
//    )


    fun findByUsername(username: String): User? =
        userRepository.findByUsername(username)

    fun save(user: UserRequest): User? {
        return if(userRepository.save(user)) User(user.username) else null

//        val foundUser = userRepository.findByUsername(user.username)
//
//        return if (foundUser == null) {
//            userRepository.save(user)
//            User(user.username)
//        } else null
    }



    fun ldapAuth(loginRequest: LoginRequest) = userRepository.ldapAuth(loginRequest)

}