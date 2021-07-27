package me.appr.booking.trip

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.*
import java.time.Duration

class TripEntity(val id: String) :
    EventSourcedBehaviorWithEnforcedReplies<Command<*>, Event, Trip>(
        PersistenceId.of(ENTITY_KEY.name(), id),
        SupervisorStrategy.restartWithBackoff(
            Duration.ofMillis(200),
            Duration.ofSeconds(5),
            0.1
        )
    ) {

    override fun emptyState() = Trip(0, "")

    override fun retentionCriteria(): SnapshotCountRetentionCriteria =
        RetentionCriteria.snapshotEvery(5, 2)

    override fun commandHandler(): CommandHandlerWithReply<Command<*>, Event, Trip> =
        newCommandHandlerWithReplyBuilder()
            .forState { it.capacity == 0 }
            .onCommand(CreateTrip::class.java, ::onTripCreated)
            .orElse(
                newCommandHandlerWithReplyBuilder()
                    .forState { it.capacity != 0 }
                    .onCommand(ChangeName::class.java, ::onNameChanged)
                    .onCommand(ChangeCapacity::class.java, ::onCapacityChanged)
                    .onCommand(CreateReservation::class.java, ::onReservationCreated)
                    .onCommand(ChangePassengerName::class.java, ::onPassengerNameChanged)
                    .onCommand(ChangeReservedCapacity::class.java, ::onReservedCapacityChanged)
                    .onCommand(CancelReservation::class.java, ::onReservationCancelled)
                    .onCommand(GetTrip::class.java, ::onGet)
                    .onCommand(ListReservations::class.java, ::onListReservations)
            )
            .orElse(
                newCommandHandlerWithReplyBuilder()
                    .forAnyState()
                    .onCommand(Command::class.java) { command ->
                        command.replyWithError(NotFoundException("Trip not found"))
                    }
            )
            .build()

    override fun eventHandler(): EventHandler<Trip, Event> = newEventHandlerBuilder()
        .forState { it.capacity == 0 }
        .onEvent(TripCreated::class.java) { _, event ->
            Trip(event.capacity, event.name)
        }
        .orElse(
            newEventHandlerBuilder()
                .forState { it.capacity != 0 }
                .onEvent(NameChanged::class.java) { state, event ->
                    state.apply { name = event.newName }
                }
                .onEvent(CapacityChanged::class.java) { state, event ->
                    state.apply { capacity = event.newCapacity }
                }
                .onEvent(ReservationCreated::class.java) { state, event ->
                    state.apply { createReservation(event.reservationId, event.name, event.capacity) }
                }
                .onEvent(PassengerNameChanged::class.java) { state, event ->
                    state.apply { updatePassengerName(event.reservationId, event.newName) }
                }
                .onEvent(ReservedCapacityChanged::class.java) { state, event ->
                    state.apply { updateReservedCapacity(event.reservationId, event.newCapacity) }
                }
        )
        .build()

    internal fun <T> Command<T>.replyWithError(t: Throwable) =
        Effect().reply(replyTo, StatusReply.error(t))

    internal fun <T> Command<T>.persistThenReply(event: Event, message: Trip.() -> T) =
        Effect()
            .persist(event)
            .thenReply(replyTo) { updated: Trip ->
                StatusReply.success(updated.message())
            }

    internal fun <T> Command<T>.persistThenReplyConstant(event: Event, message: T) =
        Effect()
            .persist(event)
            .thenReply(replyTo) {
                StatusReply.success(message)
            }

    companion object {
        internal val ENTITY_KEY: EntityTypeKey<Command<*>> =
            EntityTypeKey.create(Command::class.java, "Trip")

        fun create(id: String): Behavior<Command<*>> = Behaviors.setup { ctx ->
            EventSourcedBehavior.start(TripEntity(id), ctx)
        }

        internal fun init(system: ActorSystem<*>?) {
            ClusterSharding.get(system)
                .init(
                    Entity.of(ENTITY_KEY) { entityContext ->
                        create(entityContext.entityId)
                    }
                )
        }
    }
}
