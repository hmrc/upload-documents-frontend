package uk.gov.hmrc.uploaddocuments.controllers

import play.api.libs.json.Json
import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel.State
import uk.gov.hmrc.uploaddocuments.models._

import scala.concurrent.ExecutionContext.Implicits.global

class FileRejectedControllerISpec extends ControllerISpecBase {

  "FileRejectedController" when {

    "GET /file-rejected" should {
      "show upload document again" in {
        sessionStateService.setState(
          State.UploadSingleFile(
            FileUploadContext(fileUploadSessionConfig),
            "2b72fe99-8adf-4edb-865e-622ae710f77c",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request(
            "/file-rejected?key=2b72fe99-8adf-4edb-865e-622ae710f77c&errorCode=EntityTooLarge&errorMessage=Entity+Too+Large"
          ).get()
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))
        sessionStateService.getState shouldBe State.UploadSingleFile(
          FileUploadContext(fileUploadSessionConfig),
          "2b72fe99-8adf-4edb-865e-622ae710f77c",
          UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          ),
          Some(
            FileTransmissionFailed(
              S3UploadError("2b72fe99-8adf-4edb-865e-622ae710f77c", "EntityTooLarge", "Entity Too Large")
            )
          )
        )
      }
    }

    "POST /file-rejected" should {
      "mark file upload as rejected" in {
        sessionStateService.setState(
          State.UploadMultipleFiles(
            FileUploadContext(fileUploadSessionConfig),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/file-rejected").post(
            Json.obj(
              "key"          -> "2b72fe99-8adf-4edb-865e-622ae710f77c",
              "errorCode"    -> "EntityTooLarge",
              "errorMessage" -> "Entity Too Large"
            )
          )
        )

        result.status shouldBe 201

        sessionStateService.getState shouldBe State.UploadMultipleFiles(
          FileUploadContext(fileUploadSessionConfig),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Rejected(
                Nonce.Any,
                Timestamp.Any,
                "2b72fe99-8adf-4edb-865e-622ae710f77c",
                S3UploadError("2b72fe99-8adf-4edb-865e-622ae710f77c", "EntityTooLarge", "Entity Too Large")
              )
            )
          )
        )
      }
    }
  }
}
