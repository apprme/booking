package me.appr.booking.trip

import akka.http.javadsl.model.StatusCode
import akka.http.javadsl.model.StatusCodes
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.restassured.http.ContentType
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.response.Response
import me.appr.booking.Application
import me.appr.booking.init
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.*

@Testcontainers
class ApplicationIT {

    class KGenericContainer(imageName: DockerImageName) : GenericContainer<KGenericContainer>(imageName)

    companion object {
        const val dbPassword = "docker"
        const val dbUser = "docker"
        lateinit var node: TestNode

        @Container
        var container: GenericContainer<*> = KGenericContainer(DockerImageName.parse("postgres:13"))
            .withExposedPorts(5432)
            .withEnv("POSTGRES_PASSWORD", dbPassword)
            .withEnv("POSTGRES_USER", dbUser)

        @JvmStatic
        @BeforeAll
        fun setup() {
            node = TestNode(container.host, container.firstMappedPort)
            init(node.system)
        }
    }

    @Test
    fun `should have no reservations when created`() {
        val trip = create("A", 10)
        val summary = get(trip)
        assertEquals(10, summary.totalCapacity)
        assertEquals(10, summary.remainingCapacity)
    }

    @Test
    fun `should reply with not found when not exists`() {
        val result = Given {
            port(node.httpPort)
        } When {
            get("/${UUID.randomUUID()}")
        }

        assertErrorCode(ErrorCodes.not_found, StatusCodes.NOT_FOUND, result)
    }

    @Test
    fun `should not allow reservations if not exists`() {
        val result = book("/${UUID.randomUUID()}", "John", 10)
        assertErrorCode(ErrorCodes.not_found, StatusCodes.NOT_FOUND, result)
    }

    @Test
    fun `should not allow capacity change if not exists`() {
        val result = updateCapacity("/${UUID.randomUUID()}", 10)
        assertErrorCode(ErrorCodes.not_found, StatusCodes.NOT_FOUND, result)
    }

    @Test
    fun `should not allow to reserve more spots than available`() {
        val trip = create("A", 10)
        val result = book(trip, "Ronald", 11)
        assertErrorCode(ErrorCodes.not_enough_capacity, StatusCodes.FORBIDDEN, result)
    }

    @Test
    fun `should not allow reservations when sold-out`() {
        val trip = create("A", 10)
        book(trip, "John", 10)
        assertEquals(0, get(trip).remainingCapacity)

        val overbook = book(trip, "Ronald", 1)
        assertErrorCode(ErrorCodes.sold_out, StatusCodes.FORBIDDEN, overbook)
    }

    @Test
    fun `should release capacity when reservation is cancelled`() {
        val trip = create("A", 10)
        val reservationLocation = book(trip, "John", 10) Extract { header("Location") }
        assertEquals(0, get(trip).remainingCapacity)

        cancel(reservationLocation)
        assertEquals(10, get(trip).remainingCapacity)
    }

    @Test
    fun `should update trip name`() {
        val trip = create("A", 10)
        updateName(trip, "B")
        assertEquals("B", get(trip).name)
    }

    @Test
    fun `should update trip capacity`() {
        val trip = create("A", 10)
        assertEquals(10, get(trip).totalCapacity)

        updateCapacity(trip, 20)
        assertEquals(20, get(trip).totalCapacity)
    }

    @Test
    fun `should not allow to decrease total trip capacity beyond already reserved capacity`() {
        val trip = create("A", 10)
        book(trip, "John", 8)
        val result = updateCapacity(trip, 7)
        assertErrorCode(ErrorCodes.conflict, StatusCodes.CONFLICT, result)
    }

    @Test
    fun `should release seats on partial cancellation`() {
        val trip = create("A", 10)
        val reservation = book(trip, "John", 8) Extract { header("Location") }
        assertEquals(2, get(trip).remainingCapacity)

        updateCapacity(reservation, 2)
        assertEquals(8, get(trip).remainingCapacity)
    }

    @Test
    fun `should not allow to update reservation beyond remaining capacity`() {
        val trip = create("A", 10)
        val reservation = book(trip, "John", 8) Extract { header("Location") }

        val result = updateCapacity(reservation, 11)
        assertErrorCode(ErrorCodes.not_enough_capacity, StatusCodes.FORBIDDEN, result)
    }

    @Test
    fun `should not allow to update non-existent reservation`() {
        val trip = create("A", 10)

        val result = updateCapacity("$trip/reservations/${UUID.randomUUID()}", 2)
        assertErrorCode(ErrorCodes.not_found, StatusCodes.NOT_FOUND, result)
    }

    private fun create(name: String, capacity: Int) = Given {
        port(node.httpPort)
        contentType(ContentType.JSON)
        body(Application.TripDTO(name, capacity))
    } When {
        post("/")
    } Then {
        statusCode(StatusCodes.CREATED.intValue())
    } Extract {
        header("Location")
    }

    private fun get(path: String) = Given {
        port(node.httpPort)
    } When {
        get(path)
    } Then {
        statusCode(StatusCodes.OK.intValue())
    } Extract {
        body().`as`(Trip.Summary::class.java)
    }

    private fun book(path: String, passenger: String, capacity: Int) = Given {
        port(node.httpPort)
        contentType(ContentType.JSON)
        body(Trip.Reservation(passenger, capacity))
    } When {
        post("$path/reservations")
    }

    private fun updateName(path: String, name: String) = Given {
        port(node.httpPort)
        contentType(ContentType.JSON)
        body(jacksonObjectMapper().writeValueAsString(name))
    } When {
        patch("$path/name")
    }

    private fun updateCapacity(path: String, capacity: Int) = Given {
        port(node.httpPort)
        contentType(ContentType.JSON)
        body(capacity)
    } When {
        patch("$path/capacity")
    }

    private fun cancel(path: String) = Given {
        port(node.httpPort)
    } When {
        delete(path)
    } Then {
        statusCode(StatusCodes.NO_CONTENT.intValue())
    }

    private fun assertErrorCode(code: String, statusCode: StatusCode, r: Response) {
        val body = r Then {
            statusCode(statusCode.intValue())
        } Extract {
            body().`as`(Application.ErrorResponseDTO::class.java)
        }

        assertEquals(code, body.code)
    }
}
