package com.augmentedreality.server.repository

import com.augmentedreality.server.model.User
import com.augmentedreality.server.routing.request.LoginRequest
import com.augmentedreality.server.routing.request.UserRequest
import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.ldap.ldapAuthenticate
import java.util.*
import javax.naming.Context
import javax.naming.directory.BasicAttribute
import javax.naming.directory.BasicAttributes
import javax.naming.directory.InitialDirContext
import javax.naming.ldap.LdapName
import javax.naming.ldap.Rdn

class UserRepository(application: Application) {

    private val config = application.environment.config
    private val ldapURL = config.propertyOrNull("ldap.url")?.getString()
        ?: System.getenv("LDAP_URL")
        ?: "ldap://localhost:1389"
    private val baseDn = config.propertyOrNull("ldap.baseDn")?.getString()
        ?: System.getenv("LDAP_BASE_DN")
        ?: "dc=ldap,dc=asd,dc=msd,dc=localhost"
    private val bindDn = config.propertyOrNull("ldap.bindDn")?.getString()
        ?: System.getenv("LDAP_BIND_DN")
        ?: "cn=admin,dc=ldap,dc=asd,dc=msd,dc=localhost"
    private val bindPw = config.propertyOrNull("ldap.bindPw")?.getString()
        ?: System.getenv("LDAP_BIND_PW")
        ?: "adminpassword"


    fun findByUsername(username: String): User? {
        println("userRepo.findByUsername: $username")
        val dc = InitialDirContext(Hashtable<String, Any?>().apply {
            this[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
            this[Context.PROVIDER_URL] = ldapURL
            //The new openLDAP container doesn't allow anonymous searches
            //so we must log in as admin here, just to do a query
            this[Context.SECURITY_CREDENTIALS] = bindPw
            this[Context.SECURITY_PRINCIPAL] = bindDn
        })

        val answer = dc.search(
            baseDn,
            BasicAttributes(true).apply {
                put(BasicAttribute("cn", username))
            },
            arrayOf("cn")
        )

        println("answer: $answer")
        val answerList = answer.toList()

        println("LDAP answerList: $answerList")

        return if (answerList.size == 1)
            User(answerList.first().attributes["cn"].toString())
        else null

    }

    fun save(user: UserRequest): Boolean {
        //create a new user here
        try {
            //admin context
            //log in as admin
            val env = Hashtable<String?, Any?>()
            env[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
            env[Context.PROVIDER_URL] = ldapURL
            env[Context.SECURITY_CREDENTIALS] = bindPw
            env[Context.SECURITY_PRINCIPAL] = bindDn

            val dirContext = InitialDirContext(env)

            //create info about the new user
            val cn = user.username
            //TODO: return error if username has special chars in it
            val username = LdapName("cn=${Rdn.escapeValue(cn)},$baseDn")
            val attributes = BasicAttributes(true).apply {
                put(BasicAttribute("cn", cn))
                put(BasicAttribute("sn", "lastNameIsUnusedButRequired"))
                put("userPassword", user.password)
                put(BasicAttribute("objectClass").apply {
                    add("inetOrgPerson")
                })
            }


            //add that user info to LDAP
            val result = dirContext.createSubcontext(username, attributes)
            return true

        } catch (ex: Exception) {
            println("Create user failed: ${ex.message}")
            println("stack trace: ${ex.stackTraceToString()}")
            return false
        }
    }

    private fun nameToDN(name: String) = LdapName("cn=${Rdn.escapeValue(name)},$baseDn")

    fun ldapAuth(loginRequest: LoginRequest): UserIdPrincipal? {
        val pwdCred = UserPasswordCredential(loginRequest.username, loginRequest.password)
        return ldapAuthenticate(
            pwdCred,
            ldapURL,
            // possible injection attack here, should really sanitize the username
            nameToDN(loginRequest.username).toString()
        )
    }
}
