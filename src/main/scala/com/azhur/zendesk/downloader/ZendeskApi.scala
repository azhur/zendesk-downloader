package com.azhur.zendesk.downloader

import cats.effect.IO
import eu.timepit.refined.api.Refined
import eu.timepit.refined.*
import eu.timepit.refined.string.*
import eu.timepit.refined.types.numeric.NonNegLong
import eu.timepit.refined.types.string.*
import fs2.Stream
import sttp.client4.*
import sttp.client4.circe.*
import io.circe.Decoder
import io.circe.refined.*
import scribe.cats.io
import sttp.model.{StatusCode, Uri}

import java.time.Instant
import scala.concurrent.duration.*

trait ZendeskApi:
  /**
   * Returns a stream of incremental ticket export pages starting from the given Unix timestamp.
   * The stream will automatically handle pagination and rate limiting.
   */
  def streamTicketPages(start: StartPoint): Stream[IO, TicketsPage]

object ZendeskApi:
  def apply(
    config: ZendeskApiConfig,
    backend: Backend[IO]
  ): ZendeskApi = new ZendeskApi:
    private val baseUrl               = uri"https://${config.subdomain}.zendesk.com/api/v2"
    private val ticketsIncrementalUrl = baseUrl.addPath("incremental", "tickets", "cursor.json")

    override def streamTicketPages(start: StartPoint): Stream[IO, TicketsPage] = {
      val url = start match {
        case StartPoint.Time(time) =>
          ticketsIncrementalUrl.addParam("start_time", time.value.toString)
        case StartPoint.NextUrl(url) => uri"$url"
      }

      Stream.unfoldEval[IO, Option[Uri], TicketsPage](Some(url)) {
        case None => IO.pure(None)
        case Some(url) =>
          fetchWithRetry[TicketsPage](url).map {
            response =>
              val nextState = response.afterUrl.flatMap(url => Option.unless(response.endOfStream)(uri"$url"))
              Some((response, nextState))
          }
      }
    }

    private def fetchWithRetry[R: Decoder](url: Uri, attempt: Int = 0): IO[R] = IO.defer {
      val request = basicRequest.get(url).auth.bearer(config.oauthToken.value).response(asJson[R])

      backend.send(request).flatMap {
        _.body match
          case Right(data) => IO.pure(data)
          case Left(error) =>
            error.response.code match
              case StatusCode.TooManyRequests =>
                val retryAfter = error.response.header("Retry-After").flatMap(_.toIntOption).getOrElse(60)

                io.warn(
                  s"TooManyRequests for ${config.subdomain.value} customer, retrying after $retryAfter seconds..."
                ) *>
                  IO.sleep(retryAfter.seconds) *>
                  fetchWithRetry(url, attempt) // retry indefinitely for 429

              case code if code.isServerError && attempt < 3 =>
                val backoffSeconds = Math.pow(2, attempt).toLong
                IO.sleep(backoffSeconds.seconds) *>
                  fetchWithRetry(url, attempt + 1)

              case _ => IO.raiseError(error)
      }
    }

end ZendeskApi

final case class ZendeskApiConfig(
  subdomain: NonEmptyString,
  oauthToken: NonEmptyString
)

final case class Ticket(
  id: Long,
  createdAt: Instant,
  updatedAt: Instant,
  generatedTimestamp: NonNegLong
)

object Ticket:
  given Decoder[Ticket] = Decoder.forProduct4("id", "created_at", "updated_at", "generated_timestamp")(Ticket.apply)

// note those fields are required for time-based pagination only
case class TicketsPage(
  tickets: List[Ticket],
  endOfStream: Boolean,
  afterUrl: Option[String Refined Url]
)

object TicketsPage:
  given Decoder[TicketsPage] = Decoder.forProduct3(
    "tickets",
    "end_of_stream",
    "after_url"
  )(TicketsPage.apply)
