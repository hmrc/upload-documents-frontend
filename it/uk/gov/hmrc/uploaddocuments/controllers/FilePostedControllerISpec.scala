package uk.gov.hmrc.uploaddocuments.controllers

import play.api.http.HeaderNames
import uk.gov.hmrc.uploaddocuments.journeys.State
import uk.gov.hmrc.uploaddocuments.models._
import uk.gov.hmrc.uploaddocuments.support.SHA256

import scala.concurrent.ExecutionContext.Implicits.global

class FilePostedControllerISpec extends ControllerISpecBase {

  "FilePostedController" when {

    "GET /journey/:journeyId/file-posted" should {
      "set current file upload status as posted and return 201 Created" in {
        sessionStateService.setState(
          State.UploadMultipleFiles(
            FileUploadContext(fileUploadSessionConfig),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(
            requestWithoutSessionId(
              s"/journey/${SHA256.compute(journeyId)}/file-posted?key=11370e18-6e24-453e-b45a-76d3e32ea33d&bucket=foo"
            ).get()
          )

        result.status shouldBe 201
        result.body.isEmpty shouldBe true
        result.headerValues(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe Seq("*")
        sessionStateService.getState should beState(
          State.UploadMultipleFiles(
            FileUploadContext(fileUploadSessionConfig),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
      }
    }

    "OPTIONS /journey/:journeyId/file-posted" should {
      "return 201 with access control header" in {
        val result =
          await(
            requestWithoutSessionId(s"/journey/${SHA256.compute(journeyId)}/file-posted")
              .options()
          )
        result.status shouldBe 201
        result.body.isEmpty shouldBe true
        result.headerValues(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe Seq("*")
      }
    }
  }
}
