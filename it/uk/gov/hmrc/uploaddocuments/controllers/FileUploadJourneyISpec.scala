package uk.gov.hmrc.uploaddocuments.controllers

import akka.actor.ActorSystem
import com.typesafe.config.Config
import play.api.libs.json.{Format, JsObject, JsValue, Json}
import play.api.libs.ws.{DefaultWSCookie, StandaloneWSRequest}
import play.api.mvc.{AnyContent, Call, Cookie, Request, Session}
import play.api.test.FakeRequest
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyStateFormats
import uk.gov.hmrc.uploaddocuments.models._
import uk.gov.hmrc.uploaddocuments.repository.CacheRepository
import uk.gov.hmrc.uploaddocuments.services.{FileUploadJourneyService, KeyProvider, MongoDBCachedJourneyService}
import uk.gov.hmrc.uploaddocuments.stubs.{ExternalApiStubs, UpscanInitiateStubs}
import uk.gov.hmrc.uploaddocuments.support.{SHA256, ServerISpec, StateMatchers, TestData, TestSessionStateService}

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class FileUploadJourneyISpec extends FileUploadJourneyISpecSetup with ExternalApiStubs with UpscanInitiateStubs {

  import sessionStateService.model.State._

  implicit val journeyId: JourneyId = JourneyId("sadasdjkasdhuqyhwa326176318346674e764764")

  val hostUserAgent: String = HostService.Any.userAgent

  val fileUploadSessionConfig =
    FileUploadSessionConfig(
      nonce = Nonce.random,
      continueUrl = s"$wireMockBaseUrlAsString/continue-url",
      backlinkUrl = s"$wireMockBaseUrlAsString/backlink-url",
      callbackUrl = s"$wireMockBaseUrlAsString/result-post-url"
    )

  val FILES_LIMIT = fileUploadSessionConfig.maximumNumberOfFiles

  "FileUploadJourneyController" when {

    "preferUploadMultipleFiles" should {
      "return false when jsenabled cookie NOT set" in {
        controller.preferUploadMultipleFiles(FakeRequest()) shouldBe false
      }

      "return true when jsenabled cookie set" in {
        controller.preferUploadMultipleFiles(
          FakeRequest().withCookies(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) shouldBe true
      }
    }

    "successRedirect" should {
      "return /file-verification when jsenabled cookie NOT set" in {
        controller.successRedirect(FakeRequest()) should endWith(
          "/upload-documents/file-verification"
        )
      }

      "return /journey/:journeyId/file-verification when jsenabled cookie set" in {
        controller.successRedirect(
          fakeRequest(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) should endWith(
          s"/upload-documents/journey/${SHA256.compute(journeyId.value)}/file-verification"
        )
      }
    }

    "errorRedirect" should {
      "return /file-rejected when jsenabled cookie NOT set" in {
        controller.errorRedirect(FakeRequest()) should endWith(
          "/upload-documents/file-rejected"
        )
      }

      "return /journey/:journeyId/file-rejected when jsenabled cookie set" in {
        controller.errorRedirect(
          fakeRequest(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) should endWith(
          s"/upload-documents/journey/${SHA256.compute(journeyId.value)}/file-rejected"
        )
      }
    }

    "POST /continue-to-host" should {
      "redirect to the continueUrl if answer is no and non empty file uploads" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full")
            )
        )
        val state = ContinueToHost(
          context,
          fileUploads = nonEmptyFileUploads
        )
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url")

        val result = await(request("/continue-to-host").post(Map("choice" -> "no")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe ContinueToHost(
          context,
          fileUploads = nonEmptyFileUploads
        )
      }

      "redirect to the continueUrl if answer is no and empty file uploads and no continueWhenEmptyUrl" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full")
            )
        )
        val state = ContinueToHost(context, fileUploads = FileUploads())
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url")

        val result = await(request("/continue-to-host").post(Map("choice" -> "no")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe ContinueToHost(context, fileUploads = FileUploads())
      }

      "redirect to the continueWhenEmptyUrl if answer is no and empty file uploads" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full")
            )
        )
        val state = ContinueToHost(context, fileUploads = FileUploads())
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url-if-empty")

        val result = await(request("/continue-to-host").post(Map("choice" -> "no")))

        sessionStateService.getState shouldBe ContinueToHost(context, fileUploads = FileUploads())

        result.status shouldBe 200
        result.body shouldBe expected
      }

      "redirect to the continueUrl if answer is no and full file uploads and no continueWhenFullUrl" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty")
            )
        )
        val state = ContinueToHost(context, fileUploads = nFileUploads(context.config.maximumNumberOfFiles))
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url")

        val result = await(request("/continue-to-host").post(Map("choice" -> "no")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe ContinueToHost(
          context,
          fileUploads = nFileUploads(context.config.maximumNumberOfFiles)
        )
      }

      "redirect to the continueWhenFullUrl if answer is no and full file uploads" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full")
            )
        )
        val state = ContinueToHost(context, fileUploads = nFileUploads(context.config.maximumNumberOfFiles))
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url-if-full")

        val result = await(request("/continue-to-host").post(Map("choice" -> "no")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe ContinueToHost(
          context,
          fileUploads = nFileUploads(context.config.maximumNumberOfFiles)
        )
      }

      "redirect to the backlinkUrl if answer is yes and non empty file uploads" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full")
            )
        )
        val state = ContinueToHost(
          context,
          fileUploads = nonEmptyFileUploads
        )
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/backlink-url")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe Initialized(
          context,
          fileUploads = nonEmptyFileUploads
        )
      }

      "redirect to the backlinkUrl if answer is yes and empty file uploads and no continueWhenEmptyUrl" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full")
            )
        )
        val state = ContinueToHost(context, fileUploads = FileUploads())
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/backlink-url")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe Initialized(context, fileUploads = FileUploads())
      }

      "redirect to the backlinkUrl if answer is yes and empty file uploads" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full")
            )
        )
        val state = ContinueToHost(context, fileUploads = FileUploads())
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/backlink-url")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        sessionStateService.getState shouldBe Initialized(context, fileUploads = FileUploads())

        result.status shouldBe 200
        result.body shouldBe expected
      }

      "redirect to the backlinkUrl if answer is yes and full file uploads and no continueWhenFullUrl" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty")
            )
        )
        val state = ContinueToHost(context, fileUploads = nFileUploads(context.config.maximumNumberOfFiles))
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/backlink-url")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe Initialized(
          context,
          fileUploads = nFileUploads(context.config.maximumNumberOfFiles)
        )
      }

      "redirect to the backlinkUrl if answer is yes and full file uploads" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full")
            )
        )
        val state = ContinueToHost(context, fileUploads = nFileUploads(context.config.maximumNumberOfFiles))
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/backlink-url")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe Initialized(
          context,
          fileUploads = nFileUploads(context.config.maximumNumberOfFiles)
        )
      }

      "redirect to the continueAfterYesAnswerUrl if answer is yes and non empty file uploads" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full"),
              continueAfterYesAnswerUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-yes")
            )
        )
        val state = ContinueToHost(
          context,
          fileUploads = nonEmptyFileUploads
        )
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url-if-yes")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe Initialized(
          context,
          fileUploads = nonEmptyFileUploads
        )
      }

      "redirect to the continueAfterYesAnswerUrl if answer is yes and empty file uploads and no continueWhenEmptyUrl" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full"),
              continueAfterYesAnswerUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-yes")
            )
        )
        val state = ContinueToHost(context, fileUploads = FileUploads())
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url-if-yes")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe Initialized(context, fileUploads = FileUploads())
      }

      "redirect to the continueAfterYesAnswerUrl if answer is yes and empty file uploads" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full"),
              continueAfterYesAnswerUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-yes")
            )
        )
        val state = ContinueToHost(context, fileUploads = FileUploads())
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url-if-yes")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        sessionStateService.getState shouldBe Initialized(context, fileUploads = FileUploads())

        result.status shouldBe 200
        result.body shouldBe expected
      }

      "redirect to the continueAfterYesAnswerUrl if answer is yes and full file uploads and no continueWhenFullUrl" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueAfterYesAnswerUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-yes")
            )
        )
        val state = ContinueToHost(context, fileUploads = nFileUploads(context.config.maximumNumberOfFiles))
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url-if-yes")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe Initialized(
          context,
          fileUploads = nFileUploads(context.config.maximumNumberOfFiles)
        )
      }

      "redirect to the continueAfterYesAnswerUrl if answer is yes and full file uploads" in {
        val context = FileUploadContext(
          fileUploadSessionConfig
            .copy(
              continueWhenEmptyUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-empty"),
              continueWhenFullUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-full"),
              continueAfterYesAnswerUrl = Some(s"$wireMockBaseUrlAsString/continue-url-if-yes")
            )
        )
        val state = ContinueToHost(context, fileUploads = nFileUploads(context.config.maximumNumberOfFiles))
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val expected = givenSomePage(200, "/continue-url-if-yes")

        val result = await(request("/continue-to-host").post(Map("choice" -> "yes")))

        result.status shouldBe 200
        result.body shouldBe expected

        sessionStateService.getState shouldBe Initialized(
          context,
          fileUploads = nFileUploads(context.config.maximumNumberOfFiles)
        )
      }
    }

    "POST /initiate-upscan/:uploadId" should {
      "initialise first file upload" in {
        val state = UploadMultipleFiles(
          FileUploadContext(fileUploadSessionConfig),
          fileUploads = FileUploads()
        )
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/journey/${SHA256.compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl, hostUserAgent)

        val result = await(request("/initiate-upscan/001").post(""))

        result.status shouldBe 200
        val json = result.body[JsValue]
        (json \ "upscanReference").as[String] shouldBe "11370e18-6e24-453e-b45a-76d3e32ea33d"
        (json \ "uploadId").as[String] shouldBe "001"
        (json \ "uploadRequest").as[JsObject] shouldBe Json.obj(
          "href" -> "https://bucketName.s3.eu-west-2.amazonaws.com",
          "fields" -> Json.obj(
            "Content-Type"            -> "application/xml",
            "acl"                     -> "private",
            "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            "policy"                  -> "xxxxxxxx==",
            "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
            "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "x-amz-date"              -> "yyyyMMddThhmmssZ",
            "x-amz-meta-callback-url" -> callbackUrl,
            "x-amz-signature"         -> "xxxx",
            "success_action_redirect" -> "https://myservice.com/nextPage",
            "error_action_redirect"   -> "https://myservice.com/errorPage"
          )
        )

        sessionStateService.getState shouldBe
          UploadMultipleFiles(
            FileUploadContext(fileUploadSessionConfig),
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Initiated(
                  Nonce.Any,
                  Timestamp.Any,
                  "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  uploadId = Some("001"),
                  uploadRequest = Some(
                    UploadRequest(
                      href = "https://bucketName.s3.eu-west-2.amazonaws.com",
                      fields = Map(
                        "Content-Type"            -> "application/xml",
                        "acl"                     -> "private",
                        "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                        "policy"                  -> "xxxxxxxx==",
                        "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
                        "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
                        "x-amz-date"              -> "yyyyMMddThhmmssZ",
                        "x-amz-meta-callback-url" -> callbackUrl,
                        "x-amz-signature"         -> "xxxx",
                        "success_action_redirect" -> "https://myservice.com/nextPage",
                        "error_action_redirect"   -> "https://myservice.com/errorPage"
                      )
                    )
                  )
                )
              )
            )
          )
      }

      "initialise next file upload" in {
        val state = UploadMultipleFiles(
          FileUploadContext(fileUploadSessionConfig),
          fileUploads = FileUploads(
            Seq(FileUpload.Posted(Nonce.Any, Timestamp.Any, "23370e18-6e24-453e-b45a-76d3e32ea389"))
          )
        )
        sessionStateService.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/journey/${SHA256.compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl, hostUserAgent)

        val result = await(request("/initiate-upscan/002").post(""))

        result.status shouldBe 200
        val json = result.body[JsValue]
        (json \ "upscanReference").as[String] shouldBe "11370e18-6e24-453e-b45a-76d3e32ea33d"
        (json \ "uploadId").as[String] shouldBe "002"
        (json \ "uploadRequest").as[JsObject] shouldBe Json.obj(
          "href" -> "https://bucketName.s3.eu-west-2.amazonaws.com",
          "fields" -> Json.obj(
            "Content-Type"            -> "application/xml",
            "acl"                     -> "private",
            "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            "policy"                  -> "xxxxxxxx==",
            "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
            "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "x-amz-date"              -> "yyyyMMddThhmmssZ",
            "x-amz-meta-callback-url" -> callbackUrl,
            "x-amz-signature"         -> "xxxx",
            "success_action_redirect" -> "https://myservice.com/nextPage",
            "error_action_redirect"   -> "https://myservice.com/errorPage"
          )
        )

        sessionStateService.getState shouldBe
          UploadMultipleFiles(
            FileUploadContext(fileUploadSessionConfig),
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "23370e18-6e24-453e-b45a-76d3e32ea389"),
                FileUpload.Initiated(
                  Nonce.Any,
                  Timestamp.Any,
                  "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  uploadId = Some("002"),
                  uploadRequest = Some(
                    UploadRequest(
                      href = "https://bucketName.s3.eu-west-2.amazonaws.com",
                      fields = Map(
                        "Content-Type"            -> "application/xml",
                        "acl"                     -> "private",
                        "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                        "policy"                  -> "xxxxxxxx==",
                        "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
                        "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
                        "x-amz-date"              -> "yyyyMMddThhmmssZ",
                        "x-amz-meta-callback-url" -> callbackUrl,
                        "x-amz-signature"         -> "xxxx",
                        "success_action_redirect" -> "https://myservice.com/nextPage",
                        "error_action_redirect"   -> "https://myservice.com/errorPage"
                      )
                    )
                  )
                )
              )
            )
          )
      }
    }

    "GET /preview/:reference/:fileName" should {
      "stream the uploaded file content back if it exists" in {
        val bytes = Array.ofDim[Byte](1024 * 1024)
        Random.nextBytes(bytes)
        val upscanUrl = stubForFileDownload(200, bytes, "test.pdf")

        val state = Summary(
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

        val state = Summary(
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

    "GET /foo" should {
      "return an error page not found" in {
        val state = sessionStateService.getState
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/foo").get())

        result.status shouldBe 404
        result.body should include("Page not found")
        sessionStateService.getState shouldBe state
      }
    }
  }
}

trait FileUploadJourneyISpecSetup extends ServerISpec with StateMatchers {

  val dateTime = LocalDateTime.now()
  val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)

  import play.api.i18n._
  implicit val messages: Messages = MessagesImpl(Lang("en"), app.injector.instanceOf[MessagesApi])

  val today = LocalDate.now
  val (y, m, d) = (today.getYear(), today.getMonthValue(), today.getDayOfMonth())

  lazy val controller = app.injector.instanceOf[FileUploadJourneyController]

  lazy val sessionStateService = new TestSessionStateService[JourneyId]
    with FileUploadJourneyService[JourneyId] with MongoDBCachedJourneyService[JourneyId] {

    override lazy val actorSystem: ActorSystem = app.injector.instanceOf[ActorSystem]
    override lazy val cacheRepository = app.injector.instanceOf[CacheRepository]
    lazy val keyProvider: KeyProvider = KeyProvider(app.injector.instanceOf[Config])

    override lazy val keyProviderFromContext: JourneyId => KeyProvider =
      hc => KeyProvider(keyProvider, None)

    override val stateFormats: Format[model.State] =
      FileUploadJourneyStateFormats.formats

    override def getJourneyId(journeyId: JourneyId): Option[String] =
      Some(SHA256.compute(journeyId.value))
  }

  final def fakeRequest(cookies: Cookie*)(implicit
    journeyId: JourneyId
  ): Request[AnyContent] =
    fakeRequest("GET", "/", cookies: _*)

  final def fakeRequest(method: String, path: String, cookies: Cookie*)(implicit
    journeyId: JourneyId
  ): Request[AnyContent] =
    FakeRequest(Call(method, path))
      .withCookies(cookies: _*)
      .withSession(SessionKeys.sessionId -> journeyId.value)

  final def request(path: String)(implicit journeyId: JourneyId): StandaloneWSRequest = {
    val sessionCookie =
      sessionCookieBaker
        .encodeAsCookie(Session(Map(SessionKeys.sessionId -> journeyId.value)))
    wsClient
      .url(s"$baseUrl$path")
      .withCookies(
        DefaultWSCookie(
          sessionCookie.name,
          sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
        )
      )
      .addHttpHeaders(play.api.http.HeaderNames.USER_AGENT -> "it-test")
  }

  final def backchannelRequest(path: String)(implicit journeyId: JourneyId): StandaloneWSRequest = {
    val sessionCookie =
      sessionCookieBaker
        .encodeAsCookie(Session(Map(SessionKeys.sessionId -> journeyId.value)))
    wsClient
      .url(s"$backchannelBaseUrl$path")
      .withCookies(
        DefaultWSCookie(
          sessionCookie.name,
          sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
        )
      )
      .addHttpHeaders(play.api.http.HeaderNames.USER_AGENT -> "it-test")
  }

  final def requestWithCookies(path: String, cookies: (String, String)*)(implicit
    journeyId: JourneyId
  ): StandaloneWSRequest = {
    val sessionCookie =
      sessionCookieBaker
        .encodeAsCookie(Session(Map(SessionKeys.sessionId -> journeyId.value)))

    wsClient
      .url(s"$baseUrl$path")
      .withCookies(
        (cookies.map(c => DefaultWSCookie(c._1, c._2)) :+ DefaultWSCookie(
          sessionCookie.name,
          sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
        )): _*
      )
      .addHttpHeaders(play.api.http.HeaderNames.USER_AGENT -> "it-test")
  }

  val nonEmptyFileUploads = FileUploads(files =
    Seq(
      FileUpload.Accepted(
        Nonce.Any,
        Timestamp.Any,
        "f029444f-415c-4dec-9cf2-36774ec63ab8",
        "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
        ZonedDateTime.parse("2018-04-24T09:30:00Z"),
        "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        "test.pdf",
        "application/pdf",
        4567890
      )
    )
  )

  def nFileUploads(n: Int): FileUploads =
    FileUploads(files = for (i <- 1 to n) yield TestData.acceptedFileUpload)

}
