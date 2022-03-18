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

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.FutureTimeoutSupport
import com.typesafe.config.Config
import play.api.libs.json.Format
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.fsm.PersistentJourneyService
import uk.gov.hmrc.uploaddocuments.journeys.{FileUploadJourneyModel, FileUploadJourneyStateFormats}
import uk.gov.hmrc.uploaddocuments.repository.CacheRepository
import uk.gov.hmrc.uploaddocuments.wiring.AppConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait FileUploadJourneyService[RequestContext] extends PersistentJourneyService[RequestContext] {

  val journeyKey = "FileUploadJourney"

  override val model = FileUploadJourneyModel

  // do not keep errors or transient states in the journey history
  override val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs =
    _.filter { case s: model.IsTransient => false; case _ => true }
      .take(3) // retain last three states as a breadcrumbs

  override def updateBreadcrumbs(
    newState: model.State,
    currentState: model.State,
    currentBreadcrumbs: Breadcrumbs
  ): Breadcrumbs =
    if (currentState.isInstanceOf[model.IsTransient])
      currentBreadcrumbs
    else if (newState.getClass == currentState.getClass)
      currentBreadcrumbs
    else if (currentBreadcrumbs.nonEmpty && currentBreadcrumbs.head.getClass() == newState.getClass())
      currentBreadcrumbs.tail
    else currentState :: breadcrumbsRetentionStrategy(currentBreadcrumbs)

  /** Return the current state if matches expected type or apply the transition. */
  final def getOrApply[S <: model.State: ClassTag](
    transition: model.Transition
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[(model.State, List[model.State])] =
    currentState.flatMap {
      case Some(sb @ (state, breadcrumbs)) if is[S](state) => Future.successful(sb)
      case _                                               => apply(transition)
    }

  /** Wait for state until timeout. */
  final def waitFor[S <: model.State: ClassTag](intervalInMiliseconds: Long, timeoutNanoTime: Long)(
    ifTimeout: => Future[(model.State, List[model.State])]
  )(implicit rc: RequestContext, scheduler: Scheduler, ec: ExecutionContext): Future[(model.State, List[model.State])] =
    currentState.flatMap {
      case Some(sb @ (state: model.State, _)) if is[S](state) =>
        Future.successful(sb)
      case _ =>
        if (System.nanoTime() > timeoutNanoTime) {
          ifTimeout
        } else
          ScheduleAfter(intervalInMiliseconds) {
            waitFor[S](intervalInMiliseconds * 2, timeoutNanoTime)(ifTimeout)
          }
    }

  private def is[S <: model.State: ClassTag](state: model.State): Boolean =
    implicitly[ClassTag[S]].runtimeClass.isAssignableFrom(state.getClass)
}

trait SessionStateService extends FileUploadJourneyService[HeaderCarrier]

@Singleton
case class MongoDBCachedFileUploadJourneyService @Inject() (
  cacheRepository: CacheRepository,
  config: Config,
  appConfig: AppConfig,
  actorSystem: ActorSystem
) extends MongoDBCachedJourneyService[HeaderCarrier] with SessionStateService {

  override final val stateFormats: Format[model.State] =
    FileUploadJourneyStateFormats.formats

  override def getJourneyId(hc: HeaderCarrier): Option[String] =
    hc.extraHeaders.find(_._1 == journeyKey).map(_._2)

  final val baseKeyProvider: KeyProvider = KeyProvider(config)

  override final val keyProviderFromContext: HeaderCarrier => KeyProvider =
    hc => KeyProvider(baseKeyProvider, None)

  override val traceFSM: Boolean = appConfig.traceFSM
}

object ScheduleAfter extends FutureTimeoutSupport {

  /** Delay execution of the future by given miliseconds */
  def apply[T](
    delayInMiliseconds: Long
  )(body: => Future[T])(implicit scheduler: Scheduler, ec: ExecutionContext): Future[T] =
    after(duration = FiniteDuration(delayInMiliseconds, "ms"), using = scheduler)(body)
}
