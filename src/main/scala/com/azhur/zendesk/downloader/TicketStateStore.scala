package com.azhur.zendesk.downloader

import cats.effect.{IO, Ref}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import eu.timepit.refined.types.numeric.NonNegLong
import eu.timepit.refined.types.string.NonEmptyString

/**
 * Represents the download state for a customer.
 * @param subdomain Zendesk subdomain (unique per customer)
 * @param startPoint the last end_time processed from TicketsPage
 */
final case class DownloadState(
  subdomain: NonEmptyString,
  oauthToken: NonEmptyString,
  startPoint: StartPoint,
  lastSeenTimestamp: NonNegLong
)

sealed trait StartPoint

object StartPoint {
  final case class Time(time: NonNegLong)           extends StartPoint
  final case class NextUrl(url: String Refined Url) extends StartPoint
}

/**
 * Store for managing download progress state across restarts.
 */
trait TicketStateStore:
  /**
   * Get the current state for a customer, if it exists.
   */
  def getDownloadState(subdomain: NonEmptyString): IO[Option[DownloadState]]

  /**
   * Insert or update the state for a customer.
   */
  def upsertDownloadState(state: DownloadState): IO[Unit]

  /**
   * Get all customers that have not been started yet (i.e., not in active downloads).
   */
  def getNonStartedDownloads: IO[List[DownloadState]]

  /**
   * Mark a customer as actively downloading.
   */
  def markAsActive(subdomain: NonEmptyString): IO[Unit]

  /**
   * Mark a customer as inactive (no longer downloading).
   */
  def markAsInactive(subdomain: NonEmptyString): IO[Unit]

/**
 * In-memory implementation of StateStore using Ref for thread-safe state management.
 * Note: This state will be lost if the application restarts. It also does not scale beyond a single instance.
 *
 * For production use, consider a persistent storage solution.
 */
class InMemoryTicketStateStore private (
  stateRef: Ref[IO, Map[NonEmptyString, DownloadState]],
  activeDownloadsRef: Ref[IO, Set[NonEmptyString]]
) extends TicketStateStore:

  override def getDownloadState(subdomain: NonEmptyString): IO[Option[DownloadState]] =
    stateRef.get.map(_.get(subdomain))

  override def upsertDownloadState(state: DownloadState): IO[Unit] =
    stateRef.update(_.updated(state.subdomain, state))

  override def getNonStartedDownloads: IO[List[DownloadState]] =
    for {
      states          <- stateRef.get
      activeDownloads <- activeDownloadsRef.get
    } yield states.values.filterNot(state => activeDownloads.contains(state.subdomain)).toList

  override def markAsActive(subdomain: NonEmptyString): IO[Unit] =
    activeDownloadsRef.update(_ + subdomain)

  override def markAsInactive(subdomain: NonEmptyString): IO[Unit] =
    activeDownloadsRef.update(_ - subdomain)

object InMemoryTicketStateStore:
  /**
   * Creates a new InMemoryStateStore with empty initial state.
   */
  def make: IO[InMemoryTicketStateStore] =
    for {
      stateRef           <- Ref.of[IO, Map[NonEmptyString, DownloadState]](Map.empty)
      activeDownloadsRef <- Ref.of[IO, Set[NonEmptyString]](Set.empty)
    } yield new InMemoryTicketStateStore(stateRef, activeDownloadsRef)
