package uk.gov.hmrc.uploaddocuments.connectors

import play.api.Application
import uk.gov.hmrc.http._
import uk.gov.hmrc.uploaddocuments.models._
import uk.gov.hmrc.uploaddocuments.stubs.ExternalApiStubs
import uk.gov.hmrc.uploaddocuments.support.AppISpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.util.Success
import java.time.ZonedDateTime
import play.api.libs.json.Json
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString
import java.util.UUID

class FileUploadResultPushConnectorISpec extends FileUploadResultPushConnectorISpecSetup {

  override implicit val defaultTimeout: FiniteDuration = 10 seconds

  import FileUploadResultPushConnector._

  "FileUploadResultPushConnector" when {
    "push" should {
      "retry when applicable" in {
        shouldRetry(Success(Right(()))) shouldBe false
        shouldRetry(Success(Left(Error(501, "")))) shouldBe true
        shouldRetry(Success(Left(Error(500, "")))) shouldBe true
        shouldRetry(Success(Left(Error(499, "")))) shouldBe true
        shouldRetry(Success(Left(Error(498, "")))) shouldBe false
        shouldRetry(Success(Left(Error(404, "")))) shouldBe false
        shouldRetry(Success(Left(Error(403, "")))) shouldBe false
        shouldRetry(Success(Left(Error(400, "")))) shouldBe false
      }

      def request(url: String): Request =
        Request(
          url,
          Nonce(123),
          Seq(
            UploadedFile(
              upscanReference = "jjSJKjksjJSJ",
              downloadUrl = "https://aws.amzon.com/dummy.jpg",
              uploadTimestamp = ZonedDateTime.parse("2007-12-03T10:15:30+01:00"),
              checksum = "akskakslaklskalkskalksl",
              fileName = "dummy.jpg",
              fileMimeType = "image/jpg",
              fileSize = 1024
            )
          ),
          Some(Json.obj("foo" -> Json.obj("bar" -> JsNumber(123), "url" -> JsString(url))))
        )

      "accept valid request and return success when response 204" in {
        val path = s"/dummy-host-endpoint"
        val url = s"$wireMockBaseUrlAsString$path"
        givenResultPushEndpoint(path, Payload.from(request(url), "http://base.external.callback"), 204)
        val result: Response = await(connector.push(request(url)))
        result.isRight shouldBe true
        verifyResultPushHasHappened(path, 1)
      }

      "accept valid request and return an error without retrying" in {
        (200 to 498).filterNot(Set(204, 301, 302, 303, 307, 308).contains).foreach { status =>
          val path = s"/dummy-host-endpoint-$status"
          val url = s"$wireMockBaseUrlAsString$path"
          givenResultPushEndpoint(path, Payload.from(request(url), "http://base.external.callback"), status)
          val result: Response = await(connector.push(request(url)))
          result shouldBe Left(Error(status, s"Failure pushing uploaded files to $url:  HostService.Any"))
          verifyResultPushHasHappened(path, 1)
        }
      }

      "accept valid request and return an error without retrying if 3xx" in {
        Set(301, 302, 303, 307, 308).foreach { status =>
          val path = s"/dummy-host-endpoint-$status"
          val url = s"$wireMockBaseUrlAsString$path"
          givenResultPushEndpoint(path, Payload.from(request(url), "http://base.external.callback"), status)
          val result: Response = await(connector.push(request(url)))
          result shouldBe Left(Error(0, "originalUrl"))
          verifyResultPushHasHappened(path, 1)
        }
      }

      "accept valid request and return an error after retrying" in {
        (499 to 599).foreach { status =>
          val path = s"/dummy-host-endpoint-$status"
          val url = s"$wireMockBaseUrlAsString$path"
          givenResultPushEndpoint(path, Payload.from(request(url), "http://base.external.callback"), status)
          val result: Response = await(connector.push(request(url)))
          result shouldBe Left(Error(status, s"Failure pushing uploaded files to $url:  HostService.Any"))
          verifyResultPushHasHappened(path, 3)
        }
      }
    }
  }

}

trait FileUploadResultPushConnectorISpecSetup extends AppISpec with ExternalApiStubs {

  implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(UUID.randomUUID.toString)))

  override def fakeApplication: Application = appBuilder.build()

  lazy val connector: FileUploadResultPushConnector =
    app.injector.instanceOf[FileUploadResultPushConnector]

}
