package com.augmentedreality.server.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.augmentedreality.server.model.User
import com.augmentedreality.server.routing.request.LoginRequest
import java.util.*
import io.ktor.server.application.Application
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal

class JwtService(
    private val application: Application,
    private val userService: UserService,
) {

    private val secret = getConfigProperty("jwt.secret")
    private val issuer = getConfigProperty("jwt.issuer")
    private val audience = getConfigProperty("jwt.audience")

    val realm = getConfigProperty("jwt.realm")

    val jwtVerifier: JWTVerifier =
        JWT
            .require(Algorithm.HMAC256(secret))
            .withAudience(audience)
            .withIssuer(issuer)
            .build()

    fun createJwtToken(loginRequest: LoginRequest): String? {
        println("creating JWT for ${loginRequest.username}")
        val principal = userService.ldapAuth(loginRequest)

        return principal?.let {
            println("LDAP auth succeeded")
            JWT.create()
                .withAudience(audience)
                .withIssuer(issuer)
                .withClaim("username", loginRequest.username)
                .withExpiresAt(Date(System.currentTimeMillis() + 24 * 3_600_000))
                .sign(Algorithm.HMAC256(secret))
        }

    }

    fun customValidator(
        credential: JWTCredential,
    ): JWTPrincipal? {
        val username: String? = extractUsername(credential)
        println("Checking jwt for $username")
        val foundUser: User? = username?.let(userService::findByUsername)

        return foundUser?.let {
            if (audienceMatches(credential))
                JWTPrincipal(credential.payload)
            else
                null
        }
    }

    private fun audienceMatches(
        credential: JWTCredential,
    ): Boolean =
        credential.payload.audience.contains(audience)

    private fun getConfigProperty(path: String) =
        application.environment.config.property(path).getString()

    private fun extractUsername(credential: JWTCredential): String? =
        credential.payload.getClaim("username").asString()
}
