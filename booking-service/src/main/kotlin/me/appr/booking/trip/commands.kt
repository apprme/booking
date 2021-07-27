package me.appr.booking.trip

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import com.fasterxml.jackson.annotation.JsonCreator
import me.appr.booking.common.JacksonSerializable

abstract class Command<T>(val replyTo: ActorRef<StatusReply<T>>) : JacksonSerializable

class CreateTrip(
    val name: String,
    val capacity: Int,
    replyTo: ActorRef<StatusReply<Trip.Summary>>
) : Command<Trip.Summary>(replyTo)

class ChangeName(
    val name: String,
    replyTo: ActorRef<StatusReply<Trip.Summary>>
) : Command<Trip.Summary>(replyTo)

class ChangeCapacity(
    val capacity: Int,
    replyTo: ActorRef<StatusReply<Trip.Summary>>
) : Command<Trip.Summary>(replyTo)

class GetTrip @JsonCreator constructor(replyTo: ActorRef<StatusReply<Trip.Summary>>) : Command<Trip.Summary>(replyTo)

class CreateReservation(
    val name: String,
    val capacity: Int,
    replyTo: ActorRef<StatusReply<String>>
) : Command<String>(replyTo)

class ChangeReservedCapacity(
    val reservationId: String,
    val capacity: Int,
    replyTo: ActorRef<StatusReply<Trip.Reservation>>
) : Command<Trip.Reservation>(replyTo)

class ChangePassengerName(
    val reservationId: String,
    val name: String,
    replyTo: ActorRef<StatusReply<Trip.Reservation>>
) : Command<Trip.Reservation>(replyTo)

class CancelReservation(
    val reservationId: String,
    replyTo: ActorRef<StatusReply<Done>>
) : Command<Done>(replyTo)

class ListReservations @JsonCreator constructor(
    replyTo: ActorRef<StatusReply<Trip.Reservations>>
) : Command<Trip.Reservations>(replyTo)
