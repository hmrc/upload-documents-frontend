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

import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel
import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel.State
import uk.gov.hmrc.uploaddocuments.services.SessionStateService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ContinueToHostController @Inject() (
  sessionStateService: SessionStateService,
  router: Router,
  renderer: Renderer,
  components: BaseControllerComponents
)(implicit ec: ExecutionContext)
    extends BaseController(components) {

  // GET /continue-to-host
  final val continueToHost: Action[AnyContent] =
    Action.async { implicit request =>
      whenActiveSession {
        whenAuthenticated {
          val sessionStateUpdate =
            FileUploadJourneyModel.Transitions.continueToHost
          sessionStateService
            .getOrApply[State.ContinueToHost](sessionStateUpdate)
            .map {
              case (continueToHost: State.ContinueToHost, breadcrumbs) =>
                renderer.display(continueToHost, breadcrumbs, None)

              case other =>
                router.redirectTo(other)
            }
            .andThen { case _ => sessionStateService.cleanBreadcrumbs() }
        }
      }
    }

  // POST /continue-to-host
  final val continueWithYesNo: Action[AnyContent] =
    Action.async { implicit request =>
      whenActiveSession {
        whenAuthenticated {
          FileUploadJourneyController.YesNoChoiceForm.bindFromRequest
            .fold(
              formWithErrors => sessionStateService.currentState.map(router.redirectWithForm(formWithErrors)),
              choice => {
                val sessionStateUpdate =
                  FileUploadJourneyModel.Transitions.continueWithYesNo(choice)
                sessionStateService
                  .apply(sessionStateUpdate)
                  .map(router.redirectTo)
              }
            )
        }
      }
    }
}
