/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.uploaddocuments.services

import akka.actor.ActorSystem
import play.api.Logger
import play.api.libs.json.{Format, JsString, JsValue, Json}
import uk.gov.hmrc.play.fsm.{PersistentJourneyService, PlayFsmUtils}
import uk.gov.hmrc.uploaddocuments.repository.CacheRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.io.AnsiColor

/** Journey persistence service mixin, stores encrypted serialized state using [[JourneyCache]].
  */
trait MongoDBCachedJourneyService[RequestContext] extends PersistentJourneyService[RequestContext] {

  val actorSystem: ActorSystem
  val cacheRepository: CacheRepository
  val stateFormats: Format[model.State]
  def getJourneyId(context: RequestContext): Option[String]
  val traceFSM: Boolean = false
  val keyProvider: KeyProvider

  private val self = this

  case class PersistentState(state: model.State, breadcrumbs: List[model.State])

  implicit lazy val formats1: Format[model.State] = stateFormats
  implicit lazy val formats2: Format[PersistentState] = Json.format[PersistentState]

  private val logger = Logger.apply(this.getClass)

  final val cache = new JourneyCache[String, RequestContext] {

    override lazy val actorSystem: ActorSystem = self.actorSystem
    override lazy val journeyKey: String = self.journeyKey
    override lazy val cacheRepository: CacheRepository = self.cacheRepository
    override lazy val format: Format[String] = implicitly[Format[String]]

    override def getJourneyId(implicit requestContext: RequestContext): Option[String] =
      self.getJourneyId(requestContext)
  }

  def encrypt(state: model.State, breadcrumbs: List[model.State]): JsValue =
    JsString(Encryption.encrypt(PersistentState(state, breadcrumbs), keyProvider))

  final override def apply(
    transition: model.Transition
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs] =
    cache
      .modify(Encryption.encrypt(PersistentState(model.root, Nil), keyProvider)) { encrypted =>
        val entry = Encryption.decrypt[PersistentState](encrypted, keyProvider)
        val (state, breadcrumbs) = (entry.state, entry.breadcrumbs)
        transition.apply
          .applyOrElse(
            state,
            (_: model.State) => model.fail(model.TransitionNotAllowed(state, breadcrumbs, transition))
          )
          .map { endState =>
            Encryption.encrypt(
              PersistentState(
                endState,
                updateBreadcrumbs(endState, state, breadcrumbs)
              ),
              keyProvider
            )
          }
      }
      .map { encrypted =>
        val entry = Encryption.decrypt[PersistentState](encrypted, keyProvider)
        val stateAndBreadcrumbs = (entry.state, entry.breadcrumbs)
        if (traceFSM) {
          logger.debug("-" + stateAndBreadcrumbs._2.length + "-" * 32)
          logger.debug(
            s"${AnsiColor.CYAN}Current state: ${Json
              .prettyPrint(Json.toJson(stateAndBreadcrumbs._1.asInstanceOf[model.State]))}${AnsiColor.RESET}"
          )
          logger.debug(
            s"${AnsiColor.BLUE}Breadcrumbs: ${stateAndBreadcrumbs._2.map(PlayFsmUtils.identityOf)}${AnsiColor.RESET}"
          )
        }
        stateAndBreadcrumbs
      }

  final override protected def fetch(implicit
    requestContext: RequestContext,
    ec: ExecutionContext
  ): Future[Option[StateAndBreadcrumbs]] =
    cache.fetch
      .map(_.map { encrypted =>
        val entry = Encryption.decrypt[PersistentState](encrypted, keyProvider)
        (entry.state, entry.breadcrumbs)
      })

  final override protected def save(
    stateAndBreadcrumbs: StateAndBreadcrumbs
  )(implicit requestContext: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs] = {
    val entry = PersistentState(stateAndBreadcrumbs._1, stateAndBreadcrumbs._2)
    val encrypted = Encryption.encrypt(entry, keyProvider)
    cache
      .save(encrypted)
      .map { _ =>
        if (traceFSM) {
          logger.debug("-" + stateAndBreadcrumbs._2.length + "-" * 32)
          logger.debug(s"${AnsiColor.CYAN}Current state: ${Json
            .prettyPrint(Json.toJson(stateAndBreadcrumbs._1.asInstanceOf[model.State]))}${AnsiColor.RESET}")
          logger.debug(
            s"${AnsiColor.BLUE}Breadcrumbs: ${stateAndBreadcrumbs._2.map(PlayFsmUtils.identityOf)}${AnsiColor.RESET}"
          )
        }
        stateAndBreadcrumbs
      }
  }

  final override def clear(implicit requestContext: RequestContext, ec: ExecutionContext): Future[Unit] =
    cache.clear()

}
