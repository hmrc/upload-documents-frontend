package uk.gov.hmrc.uploaddocuments.controllers

import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel.State
import uk.gov.hmrc.uploaddocuments.models._
import uk.gov.hmrc.uploaddocuments.stubs.ExternalApiStubs

import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class PreviewControllerISpec extends ControllerISpecBase with ExternalApiStubs {

  "PreviewController" when {

    "GET /preview/:reference/:fileName" should {
      "stream the uploaded file content back if it exists" in {
        val bytes = Array.ofDim[Byte](1024 * 1024)
        Random.nextBytes(bytes)
        val upscanUrl = stubForFileDownload(200, bytes, "test.pdf")

        val state = State.Summary(
          FileUploadContext(fileUploadSessionConfig),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                upscanUrl,
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                4567890
              ),
              FileUpload.Failed(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              )
            )
          ),
          acknowledged = false
        )
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(
            request("/preview/f029444f-415c-4dec-9cf2-36774ec63ab8/test.pdf")
              .get()
          )
        result.status shouldBe 200
        result.header("Content-Type") shouldBe Some("application/pdf")
        result.header("Content-Length") shouldBe Some(s"${bytes.length}")
        result.header("Content-Disposition") shouldBe Some("""inline; filename="test.pdf"; filename*=utf-8''test.pdf""")
        result.bodyAsBytes.toArray[Byte] shouldBe bytes
        sessionStateService.getState shouldBe state
      }

      "return error page if file does not exist" in {
        val upscanUrl = stubForFileDownloadFailure(404, "test.pdf")

        val state = State.Summary(
          FileUploadContext(fileUploadSessionConfig),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                upscanUrl,
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                4567890
              ),
              FileUpload.Failed(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              )
            )
          ),
          acknowledged = false
        )
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(
            request("/preview/f029444f-415c-4dec-9cf2-36774ec63ab8/test.pdf")
              .get()
          )
        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("global.error.500.title"))
        result.body should include(htmlEscapedMessage("global.error.500.heading"))
        sessionStateService.getState shouldBe state
      }
    }
  }
}
