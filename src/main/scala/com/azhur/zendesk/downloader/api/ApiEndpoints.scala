package com.azhur.zendesk.downloader.api

import com.azhur.zendesk.downloader.*
import model.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.codec.refined.TapirCodecRefined
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

/**
 * Tapir endpoint definitions
 */
object ApiEndpoints extends TapirCodecRefined:
  val addCustomerEndpoint: PublicEndpoint[AddCustomerRequest, ErrorResponse, Unit, Any] =
    endpoint
      .post
      .in("api" / "v1" / "customers")
      .in(jsonBody[AddCustomerRequest])
      .out(statusCode(StatusCode.Accepted))
      .errorOut(statusCode(StatusCode.BadRequest).and(jsonBody[ErrorResponse]))
      .description("Add a new customer download job")

  val getStateEndpoint: PublicEndpoint[String, ErrorResponse, GetStateResponse, Any] =
    endpoint
      .get
      .in("api" / "v1" / "customers" / path[String]("subdomain"))
      .out(jsonBody[GetStateResponse])
      .errorOut(statusCode(StatusCode.NotFound).and(jsonBody[ErrorResponse]))
      .description("Get the download state for a customer")
