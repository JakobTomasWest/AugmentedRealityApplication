package codersee

import ch.qos.logback.classic.Logger
import com.codersee.module
import com.codersee.routing.request.LoginRequest
import io.ktor.client.call.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import kotlinx.serialization.Serializable



class AuthTest {
    @Test
    fun newUserTest() = testApplication {
        //load the application module
        application {
            module()
        }
        environment {
            config = ApplicationConfig("testing.conf")
            println(config)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }



        val credentials = LoginRequest("user", "pass")

        val response = client.post("/api/user"){
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val authResponse = client.post("/api/auth"){
            contentType(ContentType.Application.Json)
            setBody(credentials)
        }
        assertEquals(HttpStatusCode.OK, authResponse.status)

        @Serializable
        data class AuthResponse(val token: String)
        val token: AuthResponse = authResponse.body()
        assertTrue(token.token.isNotEmpty())


    }

}