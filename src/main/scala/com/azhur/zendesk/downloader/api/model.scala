package com.azhur.zendesk.downloader.api

import eu.timepit.refined.types.all.PosInt
import eu.timepit.refined.types.numeric.NonNegLong
import eu.timepit.refined.types.string.NonEmptyString
import com.azhur.zendesk.downloader.*
import io.circe.refined.*
import io.circe.{Decoder, Encoder}

import java.time.Instant

object model {

  /**
   * Request to add a new customer download job.
   */
  final case class AddCustomerRequest(
    subdomain: NonEmptyString,
    oauthToken: NonEmptyString, // todo use a secret type to not expose in logs
    startTimestamp: Option[NonNegLong]
  )

  object AddCustomerRequest:
    given Decoder[AddCustomerRequest] = Decoder.forProduct3(
      "subdomain",
      "oauth_token",
      "start_timestamp"
    )(AddCustomerRequest.apply)

    given Encoder[AddCustomerRequest] = Encoder.forProduct3(
      "subdomain",
      "oauth_token",
      "start_timestamp"
    )(r => (r.subdomain, r.oauthToken, r.startTimestamp))

  /**
   * Response for get state endpoint.
   */
  final case class GetStateResponse(
    subdomain: NonEmptyString,
    lastSeenTimestamp: NonNegLong,
    secondsBehindRealtime: NonNegLong
  )

  object GetStateResponse:
    given Decoder[GetStateResponse] =
      Decoder.forProduct3("subdomain", "last_seen_timestamp", "seconds_behind_realtime")(GetStateResponse.apply)

    given Encoder[GetStateResponse] =
      Encoder.forProduct3("subdomain", "last_seen_timestamp", "seconds_behind_realtime")(
        r => (r.subdomain, r.lastSeenTimestamp, r.secondsBehindRealtime)
      )

  /**
   * Error response for not found.
   */
  final case class ErrorResponse(
    code: PosInt,
    message: NonEmptyString
  )

  object ErrorResponse:
    given Decoder[ErrorResponse] = Decoder.forProduct2("code", "message")(ErrorResponse.apply)

    given Encoder[ErrorResponse] = Encoder.forProduct2("code", "message")(e => (e.code, e.message))
}
