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

package uk.gov.hmrc.uploaddocuments.controllers

import akka.actor.Scheduler
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel
import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel.State
import uk.gov.hmrc.uploaddocuments.services.SessionStateService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem

@Singleton
class FileVerificationController @Inject() (
  sessionStateService: SessionStateService,
  router: Router,
  renderer: Renderer,
  components: BaseControllerComponents,
  actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends BaseController(components) {

  implicit val scheduler: Scheduler = actorSystem.scheduler

  /** Initial time to wait for callback arrival. */
  final val INITIAL_CALLBACK_WAIT_TIME_SECONDS = 2
  final val intervalInMiliseconds: Long = 500

  // GET /file-verification
  final val showWaitingForFileVerification: Action[AnyContent] =
    Action.async { implicit request =>
      whenActiveSession {
        whenAuthenticated {
          val timeoutNanoTime: Long =
            System.nanoTime() + INITIAL_CALLBACK_WAIT_TIME_SECONDS * 1000000000L
          sessionStateService
            .waitFor[State.Summary](intervalInMiliseconds, timeoutNanoTime) {
              val sessionStateUpdate =
                FileUploadJourneyModel.Transitions.waitForFileVerification
              sessionStateService
                .apply(sessionStateUpdate)
            }
            .map {
              case (waitingForFileVerification: State.WaitingForFileVerification, breadcrumbs) =>
                renderer.display(waitingForFileVerification, breadcrumbs, None)

              case other =>
                router.redirectTo(other)
            }
        }
      }
    }

}
