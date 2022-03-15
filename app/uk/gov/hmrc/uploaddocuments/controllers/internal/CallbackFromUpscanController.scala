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

import com.fasterxml.jackson.core.JsonParseException
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.uploaddocuments.connectors.FileUploadResultPushConnector
import uk.gov.hmrc.uploaddocuments.controllers.{BaseController, BaseControllerComponents}
import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel
import uk.gov.hmrc.uploaddocuments.models._
import uk.gov.hmrc.uploaddocuments.services.SessionStateService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CallbackFromUpscanController @Inject() (
  sessionStateService: SessionStateService,
  fileUploadResultPushConnector: FileUploadResultPushConnector,
  components: BaseControllerComponents
)(implicit ec: ExecutionContext)
    extends BaseController(components) {

  // POST /callback-from-upscan/journey/:journeyId/:nonce
  final def callbackFromUpscan(journeyId: String, nonce: String): Action[AnyContent] =
    Action.async { implicit request =>
      whenJourneyIdKnown {
        Future(request.body.asJson.flatMap(_.asOpt[UpscanNotification]))
          .flatMap {
            case Some(payload) =>
              val sessionStateUpdate =
                FileUploadJourneyModel.Transitions
                  .upscanCallbackArrived(fileUploadResultPushConnector.push(_))(Nonce(nonce))(payload)
              sessionStateService
                .apply(sessionStateUpdate)
                .map(_ => NoContent)

            case None => BadRequest.asFuture
          }
          .recover {
            case e: JsonParseException => BadRequest(e.getMessage())
            case e                     => InternalServerError
          }
      }
    }

}
