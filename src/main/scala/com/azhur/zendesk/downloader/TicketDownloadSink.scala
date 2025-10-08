package com.azhur.zendesk.downloader

import cats.Show
import cats.effect.IO
import cats.syntax.all.*
import eu.timepit.refined.types.all.*

import java.time.Instant

trait TicketDownloadSink {
  def run(page: List[SinkTicket]): IO[Unit]
}

object LoggingTicketDownloadSink extends TicketDownloadSink {
  override def run(page: List[SinkTicket]): IO[Unit] =
    scribe
      .cats
      .io
      .info(
        page.map(_.show).mkString("\n")
      )
}

final case class SinkTicket(
  domain: NonEmptyString,
  id: Long,
  createdAt: Instant,
  updatedAt: Instant
)

object SinkTicket:
  given Show[SinkTicket] = Show.show {
    t => s"Ticket(domain=${t.domain}, id=${t.id}, createdAt=${t.createdAt}, updatedAt=${t.updatedAt})"
  }
