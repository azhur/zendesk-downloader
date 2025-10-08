package com.azhur.zendesk.downloader

import cats.effect.IO
import eu.timepit.refined.types.numeric.NonNegLong
import eu.timepit.refined.types.string.NonEmptyString
import scribe.cats.io

import java.time.Instant

/**
 * Handles downloading and processing ticket pages for a single customer.
 */
class TicketDownloader(
  stateStore: TicketStateStore,
  sourceApi: ZendeskApi,
  sink: TicketDownloadSink
):
  /**
   * Runs the download process for a customer.
   * - Streams ticket pages starting from the given start time
   * - Updates state after each page
   * - Processes each page (running the sink)
   * - terminates when streamTicketPages stream ends (end_of_stream = true)
   */
  def run(state: DownloadState): IO[Unit] =
    for {
      _ <- sourceApi
        .streamTicketPages(state.startPoint)
        .evalTap(page => sink.run(page.tickets.map(t => SinkTicket(state.subdomain, t.id, t.createdAt, t.updatedAt))))
        .evalTap(
          page =>
            page.afterUrl match {
              case Some(value) =>
                val maxTimestamp =
                  page.tickets.view.map(_.generatedTimestamp.value).maxOption.getOrElse(Instant.EPOCH.getEpochSecond)

                stateStore.upsertDownloadState(
                  DownloadState(
                    state.subdomain,
                    state.oauthToken,
                    StartPoint.NextUrl(value),
                    lastSeenTimestamp = NonNegLong.unsafeFrom(maxTimestamp)
                  )
                )
              case None => IO.unit
            }
        )
        .compile
        .drain

      _ <- io.info(
        s"[${state.subdomain.value}] All tickets downloaded (end_of_stream reached). Will restart in 5 seconds..."
      )
    } yield ()

object TicketDownloader:
  def apply(stateStore: TicketStateStore, api: ZendeskApi, sink: TicketDownloadSink): TicketDownloader =
    new TicketDownloader(stateStore, api, sink)
