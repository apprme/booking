package me.appr.booking.trip

import akka.actor.testkit.typed.javadsl.ActorTestKit
import akka.testkit.SocketUtil
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import me.appr.booking.common.JacksonSerializable

class TestNode(private val psqlHost: String, private val psqlPort: Int) {
    private val addresses = SocketUtil.temporaryServerAddresses(2, "127.0.0.1", false)
    private val httpHost = addresses.apply(0).hostName
    internal val httpPort = addresses.apply(0).port

    private val testKit = ActorTestKit.create("IntegrationTest", config())
    internal val system = testKit.system()

    private fun config(): Config? {
        val s = """
            include "integration"
            akka {
              management.http.hostname = ${addresses.apply(1).hostName}              
              management.http.port = ${addresses.apply(1).port}     
              discovery.config.services {
                "booking" {
                  endpoints = [
                    {host = "${addresses.apply(1).hostName}", port = ${addresses.apply(1).port}}
                  ]
                }
              }  
              actor.serialization-bindings {
                "${JacksonSerializable::class.qualifiedName}" = jackson-json
              }
            }
            rest {
              hostname = $httpHost
              port = $httpPort
            }            
            jdbc-connection-settings {
              url = "jdbc:postgresql://$psqlHost:$psqlPort/${ApplicationIT.dbUser}?reWriteBatchedInserts=true"
              user = "${ApplicationIT.dbUser}"
              password = "${ApplicationIT.dbPassword}"
            }
        """.trimIndent()

        return ConfigFactory.parseString(s.trimIndent()).resolve()
    }
}
