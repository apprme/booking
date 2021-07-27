package me.appr.booking.trip

import com.fasterxml.jackson.annotation.JsonCreator
import me.appr.booking.common.JacksonSerializable
import java.util.*

class Trip(var capacity: Int = 0, var name: String = "") : JacksonSerializable {
    private val reservations = mutableMapOf<String, Reservation>()

    fun getReservation(reservationId: String) =
        reservations[reservationId]

    fun getReservedCapacity() =
        reservations.values.sumOf(Reservation::capacity)

    fun createReservation(reservationId: String, name: String, capacity: Int) {
        reservations[reservationId] = Reservation(name, capacity)
    }

    fun updateReservedCapacity(reservationId: String, capacity: Int) {
        if (capacity == 0) {
            reservations.remove(reservationId)
        } else {
            val existing = reservations[reservationId]!!
            reservations[reservationId] = Reservation(existing.name, capacity)
        }
    }

    fun updatePassengerName(reservationId: String, name: String) {
        val existing = reservations[reservationId]!!
        reservations[reservationId] = Reservation(name, existing.capacity)
    }

    fun toSummary() = Summary(name, capacity, capacity - getReservedCapacity())

    // creates a snapshot copy (safe to send to other actors)
    fun listReservations() = Reservations(reservations.values.toList())

    fun randomId(): String {
        while (true) {
            val id = UUID.randomUUID().toString()
            if (!reservations.containsKey(id)) {
                return id
            }
        }
    }

    data class Reservation(val name: String, val capacity: Int) : JacksonSerializable

    data class Summary(val name: String, val totalCapacity: Int, val remainingCapacity: Int) : JacksonSerializable

    data class Reservations @JsonCreator constructor(val list: List<Reservation>) : JacksonSerializable
}
