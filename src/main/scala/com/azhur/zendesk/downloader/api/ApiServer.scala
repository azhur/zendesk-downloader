package com.azhur.zendesk.downloader.api

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import com.azhur.zendesk.downloader.TicketStateStore
import com.azhur.zendesk.downloader.api.*
import com.comcast.ip4s.*
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.{Router, Server}
import scribe.cats.io
import sttp.tapir.server.http4s.Http4sServerInterpreter

object ApiServer:
  // todo make host and port configurable via env vars or config file
  def make(
    stateStore: TicketStateStore,
    host: String = "0.0.0.0",
    port: Int = 8080
  ): Resource[IO, Server] =
    for {
      _ <- Resource.eval(io.info(s"Initializing API server on $host:$port"))

      httpApp = Router("/" -> http4sRoutes(stateStore)).orNotFound
      hostAddress <- Resource.eval(
        IO.fromOption(Host.fromString(host))(
          new IllegalArgumentException(s"Invalid host: $host")
        )
      )
      portNumber <- Resource.eval(
        IO.fromOption(Port.fromInt(port))(
          new IllegalArgumentException(s"Invalid port: $port")
        )
      )

      // Build and start server
      server <- EmberServerBuilder.default[IO].withHost(hostAddress).withPort(portNumber).withHttpApp(httpApp).build
      _      <- io.info(s"Server started at ${server.address}").toResource
    } yield server

  private def http4sRoutes(stateStore: TicketStateStore): HttpRoutes[IO] =
    val apiRoutes = new ApiRoutes(stateStore)

    val addCustomerRoute = Http4sServerInterpreter[IO]().toRoutes(
      ApiEndpoints.addCustomerEndpoint.serverLogic(apiRoutes.addCustomer)
    )
    val getStateRoute = Http4sServerInterpreter[IO]().toRoutes(
      ApiEndpoints.getStateEndpoint.serverLogic(apiRoutes.getState)
    )

    addCustomerRoute <+> getStateRoute
