package me.appr.booking

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.EntityRef
import akka.http.javadsl.Http
import akka.http.javadsl.marshallers.jackson.Jackson
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.model.headers.Location
import akka.http.javadsl.server.AllDirectives
import akka.http.javadsl.server.ExceptionHandler
import akka.http.javadsl.server.PathMatchers.uuidSegment
import akka.http.javadsl.server.RejectionHandler
import akka.http.javadsl.server.Route
import akka.http.javadsl.server.directives.RouteDirectives
import akka.management.javadsl.AkkaManagement
import akka.pattern.StatusReply
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.appr.booking.common.ApiException
import me.appr.booking.trip.*
import java.time.Duration
import java.util.*

class Application(private val sharding: ClusterSharding) : AllDirectives() {

    private val mapper = jacksonObjectMapper()

    private val exceptionHandler = ExceptionHandler.newBuilder()
        .match(ApiException::class.java) { t ->
            complete(StatusCodes.get(t.intCode), ErrorResponse(t), Jackson.marshaller(mapper))
        }
        .build()

    fun createRoute(): Route = ignoreTrailingSlash {
        concat(
            pathEndOrSingleSlash {
                post {
                    entity(Jackson.unmarshaller(mapper, TripDTO::class.java)) { trip ->
                        val id = UUID.randomUUID().toString()
                        getTripEntityById(id).onStatusResponse<Trip.Summary>({
                            CreateTrip(trip.name, trip.capacity, it)
                        }) {
                            created("/$id", it)
                        }
                    }
                }
            },
            pathPrefix(uuidSegment()) { tripId ->
                concat(
                    pathEnd {
                        get {
                            getTripEntityById(tripId.toString()).onStatusResponse<Trip.Summary>({
                                GetTrip(it)
                            })
                        }
                    },
                    path("name") {
                        patch {
                            entity(Jackson.unmarshaller(mapper, String::class.java)) { name ->
                                getTripEntityById(tripId.toString()).onStatusResponse<Trip.Summary>({
                                    ChangeName(name, it)
                                })
                            }
                        }
                    },
                    path("capacity") {
                        patch {
                            entity(Jackson.unmarshaller(mapper, Int::class.java)) { capacity ->
                                getTripEntityById(tripId.toString()).onStatusResponse<Trip.Summary>({
                                    ChangeCapacity(capacity, it)
                                })
                            }
                        }
                    },
                    pathPrefix("reservations") {
                        concat(
                            post {
                                entity(Jackson.unmarshaller(mapper, Trip.Reservation::class.java)) { reservation ->
                                    val id = tripId.toString()
                                    getTripEntityById(id).onStatusResponse<String>({
                                        CreateReservation(reservation.name, reservation.capacity, it)
                                    }) {
                                        created("/$id/reservations/$it", reservation)
                                    }
                                }
                            },
                            pathPrefix(uuidSegment()) { reservationId ->
                                concat(
                                    path("name") {
                                        patch {
                                            entity(Jackson.unmarshaller(mapper, String::class.java)) { name ->
                                                getTripEntityById(tripId.toString()).onStatusResponse<Trip.Reservation>(
                                                    {
                                                        ChangePassengerName(reservationId.toString(), name, it)
                                                    })
                                            }
                                        }
                                    },
                                    path("capacity") {
                                        patch {
                                            entity(Jackson.unmarshaller(mapper, Int::class.java)) { capacity ->
                                                getTripEntityById(tripId.toString()).onStatusResponse<Trip.Reservation>(
                                                    {
                                                        ChangeReservedCapacity(reservationId.toString(), capacity, it)
                                                    })
                                            }
                                        }
                                    },
                                    delete {
                                        getTripEntityById(tripId.toString()).onStatusResponse<Done>({
                                            CancelReservation(reservationId.toString(), it)
                                        }) {
                                            complete(StatusCodes.NO_CONTENT)
                                        }
                                    }
                                )
                            }
                        )
                    },
                )
            },
        )
    }

    private fun getTripEntityById(id: String) =
        sharding.entityRefFor(TripEntity.ENTITY_KEY, id)

    private fun <T> EntityRef<Command<*>>.onStatusResponse(
        command: (ActorRef<StatusReply<T>>) -> Command<T>,
        reply: (T) -> Route = { ok(it) }
    ) =
        onSuccess(askWithStatus(command, Duration.ofSeconds(3)), reply)
            .seal(RejectionHandler.defaultHandler(), exceptionHandler)

    private fun RouteDirectives.created(location: String, obj: Any) =
        complete(StatusCodes.CREATED, listOf(Location.create(location)), obj, Jackson.marshaller(mapper))

    private fun <T> RouteDirectives.ok(obj: T): Route =
        complete(StatusCodes.OK, obj, Jackson.marshaller(mapper))

    @Suppress("unused")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private class ErrorResponse(t: ApiException) {
        val code = t.code
        val message = t.message
    }

    private class TripDTO(val name: String, val capacity: Int)
}

fun main() {
    val system = ActorSystem.create<Unit>(Behaviors.empty(), "booking")
    AkkaManagement.get(system).start()

    SchemaUtils.createIfNotExists(system)

    TripEntity.init(system)

    val config = system.settings().config().getConfig("rest")
    Http.get(system)
        .newServerAt(config.getString("hostname"), config.getInt("port"))
        .bind(Application(ClusterSharding.get(system)).createRoute())
}
