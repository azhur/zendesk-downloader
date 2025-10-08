package com.azhur.zendesk.downloader

import cats.effect.{ExitCode, IO, IOApp}
import com.azhur.zendesk.downloader.api.ApiServer
import scribe.cats.io
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    HttpClientCatsBackend.resource[IO]().use {
      backend =>
        for {
          _                <- io.info("Starting Zendesk Downloader")
          ticketStateStore <- InMemoryTicketStateStore.make
          ticketOrchestrator = TicketDownloadOrchestrator(ticketStateStore, backend)

          // Start ticket orchestrator in background
          ticketOrchestratorFiber <- ticketOrchestrator.run.start
          _                       <- io.info("Ticket Download Orchestrator started")

          // Start API server
          _ <- ApiServer.make(ticketStateStore).use(_ => ticketOrchestratorFiber.join)
        } yield ExitCode.Success
    }
}
