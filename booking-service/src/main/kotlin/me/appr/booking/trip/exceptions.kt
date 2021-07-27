package me.appr.booking.trip

import me.appr.booking.common.ApiException

object ErrorCodes {
    const val conflict = "conflict"
    const val not_found = "not_found"
    const val sold_out = "sold_out"
    const val not_enough_capacity = "not_enough_capacity"
    const val bad_request = "bad_request"
}

class SoldOutException : ApiException(ErrorCodes.sold_out, 403)
class NotEnoughCapacityException : ApiException(ErrorCodes.not_enough_capacity, 403)
class BadRequestException(message: String) : ApiException(ErrorCodes.bad_request, 400, message)
class NotFoundException(message: String) : ApiException(ErrorCodes.not_found, 404, message)
class ConflictException(message: String) : ApiException(ErrorCodes.conflict, 409, message)
