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

package uk.gov.hmrc.uploaddocuments.controllers.internal

import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel
import uk.gov.hmrc.uploaddocuments.services.SessionStateService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import uk.gov.hmrc.uploaddocuments.controllers.Router
import uk.gov.hmrc.uploaddocuments.controllers.BaseController
import uk.gov.hmrc.uploaddocuments.controllers.BaseControllerComponents
import uk.gov.hmrc.uploaddocuments.controllers.Renderer
import play.api.mvc.{Action, AnyContent}

@Singleton
class WipeOutController @Inject() (
  sessionStateService: SessionStateService,
  router: Router,
  results: Renderer,
  components: BaseControllerComponents
)(implicit ec: ExecutionContext)
    extends BaseController(components) {

  // POST /internal/wipe-out
  final val wipeOut: Action[AnyContent] =
    Action.async { implicit request =>
      whenJourneyIdAvailable {
        val hc = HeaderCarrierConverter.fromRequest(request) // required to process Session-ID from the cookie
        whenAuthorisedWithoutEnrolmentReturningForbidden { _ =>
          val sessionStateUpdate = FileUploadJourneyModel.Transitions.wipeOut
          sessionStateService
            .apply(sessionStateUpdate)
            .map(_ => NoContent)
            .andThen { case _ => sessionStateService.cleanBreadcrumbs() }
        }(hc, ec)
      }
    }

}
