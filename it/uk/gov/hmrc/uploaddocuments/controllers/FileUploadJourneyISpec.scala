package uk.gov.hmrc.uploaddocuments.controllers

import akka.actor.ActorSystem
import com.typesafe.config.Config
import play.api.libs.json.Format
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

class FileUploadJourneyISpec extends FileUploadJourneyISpecSetup with ExternalApiStubs with UpscanInitiateStubs {

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
