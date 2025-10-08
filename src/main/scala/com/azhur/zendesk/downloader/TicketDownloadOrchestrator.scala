package com.azhur.zendesk.downloader

import cats.effect.IO
import cats.syntax.all.*
import scribe.cats.io
import sttp.client4.Backend

/**
 * Orchestrates ticket downloads for multiple customers concurrently.
 * Automatically starts downloaders for customers as they are added to the state store.
 */
class TicketDownloadOrchestrator(
  stateStore: TicketStateStore,
  backend: Backend[IO]
):
  /**
   * Runs the orchestrator, continuously monitoring for new customers and starting downloaders.
   * This method runs indefinitely, checking for new customers or restarting the terminated jobs periodically.
   */
  def run: IO[Unit] =
    fs2
      .Stream
      .repeatEval(startTicketDownloads)
      .metered(scala.concurrent.duration.FiniteDuration(5, scala.concurrent.duration.SECONDS))
      .compile
      .drain

  /**
   * Checks for new customers in state store and starts downloaders for any that aren't already running.
   */
  private def startTicketDownloads: IO[Unit] =
    for {
      nonStartedDownloads <- stateStore.getNonStartedDownloads
      _                   <- nonStartedDownloads.parTraverse_(startDownloader)
    } yield ()

  /**
   * Starts a downloader for a specific customer in the background.
   * If the downloader fails, it logs the error and removes it from active downloads.
   * so it can be restarted on the next check cycle (within 5 seconds).
   */
  private def startDownloader(state: DownloadState): IO[Unit] =
    for {
      _ <- stateStore.markAsActive(state.subdomain)
      _ <- io.info(s"Starting downloader for customer: ${state.subdomain.value}")
      apiConfig  = ZendeskApiConfig(state.subdomain, state.oauthToken)
      api        = ZendeskApi(apiConfig, backend)
      downloader = TicketDownloader(stateStore, api, LoggingTicketDownloadSink)
      _ <- downloader
        .run(state)
        .handleErrorWith {
          error =>
            io.error(s"[${state.subdomain.value}] Download failed with error: ${error.getMessage}", error) *>
              io.warn(s"[${state.subdomain.value}] Will retry in 5 seconds...")
        }
        // todo: markAsInactive is still not guaranteed if the process crashes before guarantee is called
        .guarantee(
          stateStore.markAsInactive(state.subdomain)
        )
        .start
    } yield ()

object TicketDownloadOrchestrator:
  def apply(
    stateStore: TicketStateStore,
    backend: Backend[IO]
  ): TicketDownloadOrchestrator =
    new TicketDownloadOrchestrator(stateStore, backend)
