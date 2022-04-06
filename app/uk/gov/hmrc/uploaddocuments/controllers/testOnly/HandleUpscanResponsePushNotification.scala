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

package uk.gov.hmrc.uploaddocuments.controllers.testOnly

import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.uploaddocuments.controllers.{BaseController, BaseControllerComponents}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HandleUpscanResponsePushNotification @Inject() (components: BaseControllerComponents)(implicit
  ec: ExecutionContext
) extends BaseController(components) {

  // GET /testOnly/upscanResponse
  final def upscanResponse(): Action[AnyContent] =
    Action { implicit request =>
      val json: Option[JsValue] = request.body.asJson
      Logger.apply(this.getClass.getName).debug(s"Json of File Upload: $json")
      NoContent
    }
}
