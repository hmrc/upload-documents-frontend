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

import play.api.mvc._
import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel._
import uk.gov.hmrc.uploaddocuments.wiring.AppConfig

import javax.inject.{Inject, Singleton}

@Singleton
class Router @Inject() (appConfig: AppConfig) {

  final val controller = routes.FileUploadJourneyController

  final def routeTo(state: State): Call =
    state match {
      case State.Initialized(context, fileUploads) =>
        Call(
          "GET",
          context.config.continueAfterYesAnswerUrl
            .getOrElse(context.config.backlinkUrl)
        )

      case State.ContinueToHost(context, fileUploads) =>
        controller.continueToHost

      case _: State.UploadMultipleFiles =>
        controller.showChooseMultipleFiles

      case _: State.UploadSingleFile =>
        controller.showChooseFile

      case _: State.WaitingForFileVerification =>
        controller.showWaitingForFileVerification

      case _: State.Summary =>
        controller.showSummary

      case _: State.SwitchToUploadSingleFile =>
        controller.showChooseFile

      case _ =>
        Call("GET", appConfig.govukStartUrl)
    }

}
