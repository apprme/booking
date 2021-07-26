package me.appr.booking.trip

import me.appr.booking.common.JacksonSerializable

abstract class Event(val tripId: String) : JacksonSerializable

class TripCreated(
    tripId: String,
    val name: String,
    val capacity: Int
) : Event(tripId)

class NameChanged(
    tripId: String,
    val oldName: String,
    val newName: String
) : Event(tripId)

class CapacityChanged(
    tripId: String,
    val oldCapacity: Int,
    val newCapacity: Int
) : Event(tripId)

class ReservationCreated(
    tripId: String,
    val reservationId: String,
    val name: String,
    val capacity: Int
) : Event(tripId)

class PassengerNameChanged(
    tripId: String,
    val reservationId: String,
    val oldName: String,
    val newName: String
) : Event(tripId)

class ReservedCapacityChanged(
    tripId: String,
    val reservationId: String,
    val oldCapacity: Int,
    val newCapacity: Int
) : Event(tripId)
