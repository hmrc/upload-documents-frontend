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

import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.{Utf8MimeTypes, WithJsonBody}
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.uploaddocuments.connectors.FrontendAuthConnector
import uk.gov.hmrc.uploaddocuments.services.SessionStateService
import uk.gov.hmrc.uploaddocuments.support.SHA256
import uk.gov.hmrc.uploaddocuments.wiring.AppConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class BaseControllerComponents @Inject() (
  val sessionStateService: SessionStateService,
  val appConfig: AppConfig,
  val authConnector: FrontendAuthConnector,
  val environment: Environment,
  val configuration: Configuration,
  val messagesControllerComponents: MessagesControllerComponents
)

abstract class BaseController(
  components: BaseControllerComponents
) extends MessagesBaseController with Utf8MimeTypes with WithJsonBody with I18nSupport with AuthActions {

  final def config: Configuration = components.configuration
  final def env: Environment = components.environment
  final def authConnector: AuthConnector = components.authConnector
  final protected def controllerComponents: MessagesControllerComponents = components.messagesControllerComponents

  implicit class FutureOps[A](value: A) {
    def asFuture: Future[A] = Future.successful(value)
  }

  private val journeyIdPathParamRegex = ".*?/journey/([a-fA-F0-9]+?)/.*".r

  final def currentJourneyId(implicit rh: RequestHeader): String = journeyId.get

  final def journeyId(implicit rh: RequestHeader): Option[String] =
    journeyId(decodeHeaderCarrier(rh), rh)

  private def journeyId(hc: HeaderCarrier, rh: RequestHeader): Option[String] =
    (rh.path match {
      case journeyIdPathParamRegex(id) => Some(id)
      case _                           => None
    })
      .orElse(hc.sessionId.map(_.value).map(SHA256.compute))

  final implicit def context(implicit rh: RequestHeader): HeaderCarrier = {
    val hc = decodeHeaderCarrier(rh)
    journeyId(hc, rh)
      .map(jid => hc.withExtraHeaders(components.sessionStateService.journeyKey -> jid))
      .getOrElse(hc)
  }

  private def decodeHeaderCarrier(rh: RequestHeader): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(rh, rh.session)

  final def whenJourneyIdKnown(
    body: => Future[Result]
  )(implicit request: Request[_]): Future[Result] =
    journeyId match {
      case None => Future.successful(Redirect(components.appConfig.govukStartUrl))
      case _    => body
    }

}
