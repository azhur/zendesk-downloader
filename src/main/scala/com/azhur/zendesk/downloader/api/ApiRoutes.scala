package com.azhur.zendesk.downloader.api

import cats.effect.IO
import com.azhur.zendesk.downloader.{DownloadState, StartPoint, TicketStateStore}
import eu.timepit.refined.types.all.PosInt
import eu.timepit.refined.types.numeric.NonNegLong
import eu.timepit.refined.types.string.NonEmptyString
import model.*

import java.time.Instant

class ApiRoutes(stateStore: TicketStateStore):
  def addCustomer(request: AddCustomerRequest): IO[Either[ErrorResponse, Unit]] =
    stateStore
      .upsertDownloadState(
        DownloadState(
          subdomain = request.subdomain,
          oauthToken = request.oauthToken,
          startPoint =
            StartPoint.Time(request.startTimestamp.getOrElse(NonNegLong.unsafeFrom(Instant.EPOCH.getEpochSecond))),
          lastSeenTimestamp = NonNegLong.unsafeFrom(Instant.EPOCH.getEpochSecond)
        )
      )
      .attempt
      .map(
        _.left.map(
          t =>
            ErrorResponse(
              code = PosInt.unsafeFrom(500),
              message = NonEmptyString.unsafeFrom(s"Failed to add customer")
            )
        )
      )

  def getState(subdomain: String): IO[Either[ErrorResponse, GetStateResponse]] =
    for {
      now <- IO.realTimeInstant
      subdomainRefined <- IO.fromEither(
        NonEmptyString.from(subdomain).left.map(err => new IllegalArgumentException(err))
      )
      stateOpt <- stateStore.getDownloadState(subdomainRefined)
    } yield stateOpt match
      case Some(state) =>
        Right(
          GetStateResponse(
            subdomain = state.subdomain,
            lastSeenTimestamp = state.lastSeenTimestamp,
            secondsBehindRealtime =
              NonNegLong.unsafeFrom(scala.math.max(0, now.getEpochSecond - state.lastSeenTimestamp.value))
          )
        )
      case None =>
        Left(
          ErrorResponse(
            code = PosInt.unsafeFrom(404),
            message = NonEmptyString.unsafeFrom(s"Customer with subdomain '$subdomain' not found")
          )
        )
