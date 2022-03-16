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
import uk.gov.hmrc.uploaddocuments.services.SessionStateService
import uk.gov.hmrc.uploaddocuments.views.FileUploadViews

import javax.inject.{Inject, Singleton}

@Singleton
class StartController @Inject() (
  sessionStateService: SessionStateService,
  router: Router,
  views: FileUploadViews,
  components: BaseControllerComponents
) extends BaseController(components) {

  // GET /
  final val start: Action[AnyContent] =
    Action { implicit request =>
      if (router.preferUploadMultipleFiles)
        Redirect(router.showChooseMultipleFiles)
      else
        Ok(views.startView(router.showChooseMultipleFiles))
    }

}
