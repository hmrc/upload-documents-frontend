package uk.gov.hmrc.uploaddocuments.controllers

import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel.State
import uk.gov.hmrc.uploaddocuments.models._
import uk.gov.hmrc.uploaddocuments.stubs.UpscanInitiateStubs
import uk.gov.hmrc.uploaddocuments.support.SHA256

import scala.concurrent.ExecutionContext.Implicits.global

class StartControllerISpec extends ControllerISpecBase with UpscanInitiateStubs {

  "StartController" when {

    "GET /" should {
      "show the start page when no jsenabled cookie set" in {
        val state = State.Initialized(
          FileUploadContext(fileUploadSessionConfig),
          fileUploads = FileUploads()
        )
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/journey/${SHA256.compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl, hostUserAgent)

        val result = await(request("/").get())

        result.status shouldBe 200
        result.body should include("url=/upload-documents/choose-files")

        sessionStateService.getState shouldBe state
      }

    }

  }
}
