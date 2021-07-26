package me.appr.booking.common

open class ApiException(val code: String, val intCode: Int, message: String? = null) :
    Throwable(message), JacksonSerializable
