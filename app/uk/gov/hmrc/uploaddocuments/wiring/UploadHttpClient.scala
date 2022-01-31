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

package uk.gov.hmrc.play.http.ws

import akka.actor.ActorSystem
import play.api.Configuration
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadHttpClient @Inject() (
  config: Configuration,
  httpAuditing: HttpAuditing,
  wsClient: WSClient,
  actorSystem: ActorSystem
) extends DefaultHttpClient(config, httpAuditing, wsClient, actorSystem) {

  override def doPost[A](
    url: String,
    body: A,
    headers: Seq[(String, String)]
  )(implicit
    rds: Writes[A],
    ec: ExecutionContext
  ): Future[HttpResponse] =
    execute(buildRequest(url, deduplicate(headers)).withBody(Json.toJson(body)), "POST")
      .map(WSHttpResponse.apply)

  private def deduplicate(headers: Seq[(String, String)]): Seq[(String, String)] =
    headers.groupBy(_._1).map { case (k, vs) => (k, vs.last) }.values.toSeq
}
