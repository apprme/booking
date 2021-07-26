package me.appr.booking.trip

import me.appr.booking.common.ApiException

class SoldOutException : ApiException("sold_out", 403)
class NotEnoughCapacityException : ApiException("not_enough_capacity", 403)
class BadRequestException(message: String) : ApiException("bad_request", 400, message)
class NotFoundException(message: String) : ApiException("not_found", 404, message)
class ConflictException(message: String) : ApiException("conflict", 409, message)
