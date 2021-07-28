package me.appr.booking.trip

import akka.Done
import akka.pattern.StatusReply
import akka.persistence.typed.javadsl.ReplyEffect

internal fun TripEntity.onTripCreated(command: CreateTrip) =
    command.persistThenReply(TripCreated(id, command.name, command.capacity)) {
        toSummary()
    }

internal fun TripEntity.onNameChanged(state: Trip, command: ChangeName) =
    command.persistThenReply(NameChanged(id, state.name, command.name)) {
        toSummary()
    }

internal fun TripEntity.onCapacityChanged(state: Trip, command: ChangeCapacity) = when {
    command.capacity <= 0 -> {
        command.replyWithError(BadRequestException("Capacity must be greater than zero"))
    }
    state.getReservedCapacity() > command.capacity -> {
        command.replyWithError(
            ConflictException(
                "Can not reduce total trip capacity beyond already reserved capacity." +
                    " You need to cancel some reservations first."
            )
        )
    }
    else -> {
        Effect()
            .persist(CapacityChanged(id, state.capacity, command.capacity))
            .thenReply(command.replyTo) { updated: Trip ->
                StatusReply.success(updated.toSummary())
            }
    }
}

internal fun TripEntity.onReservationCreated(state: Trip, command: CreateReservation): ReplyEffect<Event, Trip> {
    val reservedCapacity = state.getReservedCapacity()

    return when {
        command.capacity <= 0 -> {
            command.replyWithError(BadRequestException("Capacity must greater than zero"))
        }
        reservedCapacity >= state.capacity -> {
            command.replyWithError(SoldOutException())
        }
        reservedCapacity + command.capacity > state.capacity -> {
            command.replyWithError(NotEnoughCapacityException())
        }
        else -> {
            val randomId = state.randomId()
            command.persistThenReplyConstant(
                ReservationCreated(id, randomId, command.name, command.capacity),
                randomId
            )
        }
    }
}

internal fun TripEntity.onPassengerNameChanged(state: Trip, command: ChangePassengerName): ReplyEffect<Event, Trip> {
    val existingReservation = state.getReservation(command.reservationId)

    return when {
        existingReservation == null -> {
            command.replyWithError(NotFoundException("Reservation not found"))
        }
        existingReservation.name == command.name -> {
            Effect().reply(command.replyTo, StatusReply.success(existingReservation))
        }
        else -> {
            val event = PassengerNameChanged(id, command.reservationId, existingReservation.name, command.name)
            command.persistThenReplyConstant(event, Trip.Reservation(command.name, existingReservation.capacity))
        }
    }
}

internal fun TripEntity.onReservedCapacityChanged(
    state: Trip,
    command: ChangeReservedCapacity
): ReplyEffect<Event, Trip> {
    val reservedCapacity = state.getReservedCapacity()
    val existingReservation = state.getReservation(command.reservationId)

    return when {
        existingReservation == null -> {
            command.replyWithError(NotFoundException("Reservation not found"))
        }
        command.capacity <= 0 -> {
            command.replyWithError(BadRequestException("Number of reserved spots should be greater than zero"))
        }
        command.capacity > existingReservation.capacity && reservedCapacity >= state.capacity -> {
            command.replyWithError(SoldOutException())
        }
        reservedCapacity - existingReservation.capacity + command.capacity > state.capacity -> {
            command.replyWithError(NotEnoughCapacityException())
        }
        existingReservation.capacity == command.capacity -> {
            Effect().reply(command.replyTo, StatusReply.success(existingReservation))
        }
        else -> {
            val event = ReservedCapacityChanged(
                tripId = id,
                reservationId = command.reservationId,
                oldCapacity = existingReservation.capacity,
                newCapacity = command.capacity
            )
            command.persistThenReplyConstant(event, Trip.Reservation(existingReservation.name, command.capacity))
        }
    }
}

internal fun TripEntity.onReservationCancelled(state: Trip, command: CancelReservation) =
    when (val reservedCapacity = state.getReservedCapacity()) {
        0 -> command.replyWithError(NotFoundException("Reservation not found"))
        else -> {
            val event = ReservedCapacityChanged(
                tripId = id,
                reservationId = command.reservationId,
                oldCapacity = reservedCapacity,
                newCapacity = 0
            )
            command.persistThenReplyConstant(event, Done.done())
        }
    }

internal fun TripEntity.onGet(state: Trip, command: GetTrip) =
    Effect().reply(command.replyTo, StatusReply.success(state.toSummary()))

internal fun TripEntity.onListReservations(state: Trip, command: ListReservations) =
    Effect().reply(command.replyTo, StatusReply.success(state.listReservations()))
