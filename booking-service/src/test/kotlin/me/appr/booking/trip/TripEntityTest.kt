package me.appr.booking.trip

import akka.Done
import akka.actor.testkit.typed.javadsl.ActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit
import com.typesafe.config.ConfigFactory
import me.appr.booking.common.JacksonSerializable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TripEntityTest {

    private val testKit: ActorTestKit = ActorTestKit.create(
        ConfigFactory.parseString(config).resolve().withFallback(EventSourcedBehaviorTestKit.config())
    )

    private val eventSourcedTestKit: EventSourcedBehaviorTestKit<Command<*>, Event, Trip> =
        EventSourcedBehaviorTestKit.create(testKit.system(), TripEntity.create(TRIP_ID))

    @BeforeEach
    fun beforeEach() {
        eventSourcedTestKit.clear()
    }

    @Test
    fun `should have no reservations when created`() {
        val result = create("A", 10)
        assertTrue(result.reply().isSuccess, "Expected command to succeed")
        val summary = result.reply().value
        assertEquals(10, summary.totalCapacity)
        assertEquals(10, summary.remainingCapacity)
    }

    @Test
    fun `should not allow reservations if not exists`() {
        val result = book("John", 10)
        assertFalse(result.reply().isSuccess)
        assertTrue(result.reply().error is NotFoundException)
    }

    @Test
    fun `should not allow capacity change if not exists`() {
        val result = updateCapacity(10)
        assertFalse(result.reply().isSuccess)
        assertTrue(result.reply().error is NotFoundException)
    }

    @Test
    fun `should not allow to reserve more spots than available`() {
        create("A", 10)
        val result = book("Ronald", 11)
        assertFalse(result.reply().isSuccess)
        assertTrue(result.reply().error is NotEnoughCapacityException)
    }

    @Test
    fun `should not allow reservations when sold-out`() {
        create("A", 10)
        book("John", 10)
        assertEquals(0, get().reply().value.remainingCapacity)

        val overbook = book("Ronald", 1)
        assertFalse(overbook.reply().isSuccess)
        assertTrue(overbook.reply().error is SoldOutException)
    }

    @Test
    fun `should release capacity when reservation is cancelled`() {
        create("A", 10)
        val result = book("John", 10)
        assertEquals(0, get().reply().value.remainingCapacity)

        cancel(result.reply().value)
        assertEquals(10, get().reply().value.remainingCapacity)
    }

    @Test
    fun `should update trip name`() {
        create("A", 10)
        updateName("B")

        assertEquals("B", get().reply().value.name)
    }

    @Test
    fun `should update trip capacity`() {
        create("A", 10)
        assertEquals(10, get().reply().value.totalCapacity)

        updateCapacity(20)
        assertEquals(20, get().reply().value.totalCapacity)
    }

    @Test
    fun `should not allow to decrease total trip capacity beyond already reserved capacity`() {
        create("A", 10)
        book("John", 8)
        val result = updateCapacity(7)
        assertFalse(result.reply().isSuccess)
        assertTrue(result.reply().error is ConflictException)
    }

    @Test
    fun `should release seats on partial cancellation`() {
        create("A", 10)
        val reservationId = book("John", 8).reply().value
        assertEquals(2, get().reply().value.remainingCapacity)

        updateReservedCapacity(reservationId, 2)
        assertEquals(8, get().reply().value.remainingCapacity)
    }

    @Test
    fun `should not allow to update reservation beyond remaining capacity`() {
        create("A", 10)
        val reservationId = book("John", 8).reply().value

        val result = updateReservedCapacity(reservationId, 11)
        assertFalse(result.reply().isSuccess)
        assertTrue(result.reply().error is NotEnoughCapacityException)
    }

    @Test
    fun `should not allow to update non-existent reservation`() {
        create("A", 10)

        val result = updateReservedCapacity("abc", 2)
        assertFalse(result.reply().isSuccess)
        assertTrue(result.reply().error is NotFoundException)
    }

    @Test
    fun `should list exising reservations`() {
        create("A", 10)

        book("p1", 8)
        book("p2", 2)

        val result = listReservations()
        assertTrue(result.reply().isSuccess)
        val value = result.reply().value.list
        assertEquals(2, value.size)

        val p1 = value.first { it.name == "p1" }
        assertEquals("p1", p1.name)
        assertEquals(8, p1.capacity)

        val p2 = value.first { it.name == "p2" }
        assertEquals("p2", p2.name)
        assertEquals(2, p2.capacity)
    }

    @Test
    fun `should not appear in reservation list when cancelled`() {
        create("A", 10)

        val reservationId = book("p1", 8).reply().value
        book("p2", 2)

        cancel(reservationId)

        val result = listReservations()
        assertTrue(result.reply().isSuccess)
        val value = result.reply().value.list
        assertEquals(1, value.size)
        assertEquals("p2", value[0].name)
        assertEquals(2, value[0].capacity)
    }

    private fun get() =
        eventSourcedTestKit.runCommand<StatusReply<Trip.Summary>> {
            GetTrip(it)
        }

    private fun create(destination: String, capacity: Int) =
        eventSourcedTestKit.runCommand<StatusReply<Trip.Summary>> {
            CreateTrip(destination, capacity, it)
        }

    private fun updateCapacity(capacity: Int) =
        eventSourcedTestKit.runCommand<StatusReply<Trip.Summary>> {
            ChangeCapacity(capacity, it)
        }

    private fun updateName(name: String) =
        eventSourcedTestKit.runCommand<StatusReply<Trip.Summary>> {
            ChangeName(name, it)
        }

    private fun book(passenger: String, capacity: Int) =
        eventSourcedTestKit.runCommand<StatusReply<String>> {
            CreateReservation(passenger, capacity, it)
        }

    private fun cancel(reservationId: String) =
        eventSourcedTestKit.runCommand<StatusReply<Done>> {
            CancelReservation(reservationId, it)
        }

    private fun updateReservedCapacity(reservationId: String, capacity: Int) =
        eventSourcedTestKit.runCommand<StatusReply<Trip.Reservation>> {
            ChangeReservedCapacity(reservationId, capacity, it)
        }

    private fun listReservations() =
        eventSourcedTestKit.runCommand<StatusReply<Trip.Reservations>> {
            ListReservations(it)
        }

    companion object {
        const val TRIP_ID = "fafa8eb9-73b2-473c-8acd-4146d639be69"

        private val config = """
            akka.actor.serialization-bindings {
                "${JacksonSerializable::class.qualifiedName}" = jackson-json
            }
        """.trimIndent()
    }
}
