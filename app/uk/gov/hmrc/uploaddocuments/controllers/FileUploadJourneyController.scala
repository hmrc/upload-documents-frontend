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

import akka.actor.{ActorSystem, Scheduler}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.{Configuration, Environment}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.uploaddocuments.connectors._
import uk.gov.hmrc.uploaddocuments.models._
import uk.gov.hmrc.uploaddocuments.services.SessionStateService
import uk.gov.hmrc.uploaddocuments.views.UploadFileViewHelper
import uk.gov.hmrc.uploaddocuments.wiring.AppConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class FileUploadJourneyController @Inject() (
  fileUploadJourneyService: SessionStateService,
  views: uk.gov.hmrc.uploaddocuments.views.FileUploadViews,
  upscanInitiateConnector: UpscanInitiateConnector,
  fileUploadResultPushConnector: FileUploadResultPushConnector,
  uploadFileViewHelper: UploadFileViewHelper,
  appConfig: AppConfig,
  authConnector: FrontendAuthConnector,
  environment: Environment,
  configuration: Configuration,
  val controllerComponents: MessagesControllerComponents,
  val actorSystem: ActorSystem
) extends BaseJourneyController(
      fileUploadJourneyService,
      appConfig,
      authConnector,
      environment,
      configuration
    ) with FileStream {

  final val controller = routes.FileUploadJourneyController
  final val callbackFromUpscanController =
    uk.gov.hmrc.uploaddocuments.controllers.internal.routes.CallbackFromUpscanController

  import FileUploadJourneyController._
  import uk.gov.hmrc.uploaddocuments.journeys.FileUploadJourneyModel._

  implicit val scheduler: Scheduler = actorSystem.scheduler

  /** Initial time to wait for callback arrival. */
  final val INITIAL_CALLBACK_WAIT_TIME_SECONDS = 2

  /** This cookie is set by the script on each request coming from one of our own pages open in the browser.
    */
  final val COOKIE_JSENABLED = "jsenabled"

  final def preferUploadMultipleFiles(implicit rh: RequestHeader): Boolean =
    rh.cookies.get(COOKIE_JSENABLED).isDefined

  final def successRedirect(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => routes.FileVerificationController.asyncWaitingForFileVerification(journeyId.get)
      case None    => routes.FileVerificationController.showWaitingForFileVerification
    })

  final def successRedirectWhenUploadingMultipleFiles(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + routes.FilePostedController.asyncMarkFileUploadAsPosted(journeyId.get)

  final def errorRedirect(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => routes.FileRejectedController.asyncMarkFileUploadAsRejected(journeyId.get)
      case None    => routes.FileRejectedController.markFileUploadAsRejected
    })

  final def upscanRequest(nonce: String, maximumFileSizeBytes: Long)(implicit
    rh: RequestHeader
  ) =
    UpscanInitiateRequest(
      callbackUrl = appConfig.baseInternalCallbackUrl +
        callbackFromUpscanController.callbackFromUpscan(currentJourneyId, nonce).url,
      successRedirect = Some(successRedirect),
      errorRedirect = Some(errorRedirect),
      minimumFileSize = Some(1),
      maximumFileSize = Some(maximumFileSizeBytes.toInt)
    )

  final def upscanRequestWhenUploadingMultipleFiles(
    nonce: String,
    maximumFileSizeBytes: Long
  )(implicit
    rh: RequestHeader
  ) =
    UpscanInitiateRequest(
      callbackUrl = appConfig.baseInternalCallbackUrl +
        callbackFromUpscanController.callbackFromUpscan(currentJourneyId, nonce).url,
      successRedirect = Some(successRedirectWhenUploadingMultipleFiles),
      errorRedirect = Some(errorRedirect),
      minimumFileSize = Some(1),
      maximumFileSize = Some(maximumFileSizeBytes.toInt)
    )

  // --------------------------------------------- //
  //                    ACTIONS                    //
  // --------------------------------------------- //

  // POST /initiate-upscan/:uploadId
  final def initiateNextFileUpload(uploadId: String): Action[AnyContent] =
    whenAuthenticated
      .applyWithRequest { implicit request =>
        Transitions
          .initiateNextFileUpload(uploadId)(upscanRequestWhenUploadingMultipleFiles)(
            upscanInitiateConnector.initiate(_, _)
          )
      }
      .displayUsing(renderUploadRequestJson(uploadId))

  // GET /uploaded/:reference/remove
  final def removeFileUploadByReference(reference: String): Action[AnyContent] =
    whenAuthenticated
      .applyWithRequest { implicit request =>
        Transitions.removeFileUploadByReference(reference)(upscanRequest)(
          upscanInitiateConnector.initiate(_, _)
        )(fileUploadResultPushConnector.push(_))
      }

  // POST /uploaded/:reference/remove
  final def removeFileUploadByReferenceAsync(reference: String): Action[AnyContent] =
    whenAuthenticated
      .applyWithRequest { implicit request =>
        Transitions.removeFileUploadByReference(reference)(upscanRequest)(
          upscanInitiateConnector.initiate(_, _)
        )(fileUploadResultPushConnector.push(_))
      }
      .displayUsing(renderFileRemovalStatus)

  // GET /preview/:reference/:fileName
  final def previewFileUploadByReference(reference: String, fileName: String): Action[AnyContent] =
    whenAuthenticated.showCurrentState
      .displayAsyncUsing(streamFileFromUspcan(reference))

  /** Function from the `State` to the `Call` (route), used by play-fsm internally to create redirects.
    */
  final override def getCallFor(state: State)(implicit request: Request[_]): Call =
    state match {
      case State.Initialized(context, fileUploads) =>
        Call(
          "GET",
          context.config.continueAfterYesAnswerUrl
            .getOrElse(context.config.backlinkUrl)
        )

      case State.ContinueToHost(context, fileUploads) =>
        routes.ContinueToHostController.continueToHost

      case _: State.UploadMultipleFiles =>
        routes.ChooseMultipleFilesController.showChooseMultipleFiles

      case _: State.UploadSingleFile =>
        routes.ChooseSingleFileController.showChooseFile

      case _: State.WaitingForFileVerification =>
        routes.FileVerificationController.showWaitingForFileVerification

      case _: State.Summary =>
        routes.SummaryController.showSummary

      case _: State.SwitchToUploadSingleFile =>
        routes.ChooseSingleFileController.showChooseFile

      case _ =>
        Call("GET", appConfig.govukStartUrl)
    }

  import uk.gov.hmrc.play.fsm.OptionalFormOps._

  /** Function from the `State` to the `Result`, used by play-fsm internally to render the actual content.
    */
  final override def renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result =
    state match {
      case State.Uninitialized =>
        Redirect(appConfig.govukStartUrl)

      case State.Initialized(config, fileUploads) =>
        if (preferUploadMultipleFiles)
          Redirect(routes.ChooseMultipleFilesController.showChooseMultipleFiles)
        else
          Redirect(routes.ChooseSingleFileController.showChooseFile)

      case State.ContinueToHost(context, fileUploads) =>
        if (fileUploads.acceptedCount == 0)
          Redirect(context.config.getContinueWhenEmptyUrl)
        else if (fileUploads.acceptedCount >= context.config.maximumNumberOfFiles)
          Redirect(context.config.getContinueWhenFullUrl)
        else
          Redirect(context.config.continueUrl)

      case State.UploadMultipleFiles(context, fileUploads) =>
        Ok(
          views.uploadMultipleFilesView(
            minimumNumberOfFiles = context.config.minimumNumberOfFiles,
            maximumNumberOfFiles = context.config.maximumNumberOfFiles,
            initialNumberOfEmptyRows = context.config.initialNumberOfEmptyRows,
            maximumFileSizeBytes = context.config.maximumFileSizeBytes,
            filePickerAcceptFilter = context.config.getFilePickerAcceptFilter,
            allowedFileTypesHint = context.config.content.allowedFilesTypesHint
              .orElse(context.config.allowedFileExtensions)
              .getOrElse(context.config.allowedContentTypes),
            context.config.newFileDescription,
            initialFileUploads = fileUploads.files,
            initiateNextFileUpload = controller.initiateNextFileUpload,
            checkFileVerificationStatus = routes.FileVerificationController.checkFileVerificationStatus,
            removeFile = controller.removeFileUploadByReferenceAsync,
            previewFile = controller.previewFileUploadByReference,
            markFileRejected = routes.FileRejectedController.markFileUploadAsRejectedAsync,
            continueAction =
              if (context.config.features.showYesNoQuestionBeforeContinue)
                routes.ContinueToHostController.continueWithYesNo
              else routes.ContinueToHostController.continueToHost,
            backLink = Call("GET", context.config.backlinkUrl),
            context.config.features.showYesNoQuestionBeforeContinue,
            context.config.content.yesNoQuestionText,
            formWithErrors.or(YesNoChoiceForm)
          )(implicitly[Request[_]], context.messages, context.config.features, context.config.content)
        )

      case State.UploadSingleFile(context, reference, uploadRequest, fileUploads, maybeUploadError) =>
        import context._
        Ok(
          views.uploadSingleFileView(
            maxFileUploadsNumber = context.config.maximumNumberOfFiles,
            maximumFileSizeBytes = context.config.maximumFileSizeBytes,
            filePickerAcceptFilter = context.config.getFilePickerAcceptFilter,
            allowedFileTypesHint = context.config.content.allowedFilesTypesHint
              .orElse(context.config.allowedFileExtensions)
              .getOrElse(context.config.allowedContentTypes),
            context.config.newFileDescription,
            uploadRequest = uploadRequest,
            fileUploads = fileUploads,
            maybeUploadError = maybeUploadError,
            successAction = routes.SummaryController.showSummary,
            failureAction = routes.ChooseSingleFileController.showChooseFile,
            checkStatusAction = routes.FileVerificationController.checkFileVerificationStatus(reference),
            backLink = backLinkFor(breadcrumbs)
          )
        )

      case State.WaitingForFileVerification(context, reference, _, _, _) =>
        import context._
        Ok(
          views.waitingForFileVerificationView(
            successAction = routes.SummaryController.showSummary,
            failureAction = routes.ChooseSingleFileController.showChooseFile,
            checkStatusAction = routes.FileVerificationController.checkFileVerificationStatus(reference),
            backLink = backLinkFor(breadcrumbs)
          )
        )

      case State.Summary(context, fileUploads, _) =>
        Ok(
          if (fileUploads.acceptedCount < context.config.maximumNumberOfFiles)
            views.summaryView(
              maxFileUploadsNumber = context.config.maximumNumberOfFiles,
              maximumFileSizeBytes = context.config.maximumFileSizeBytes,
              formWithErrors.or(YesNoChoiceForm),
              fileUploads,
              routes.SummaryController.submitUploadAnotherFileChoice,
              controller.previewFileUploadByReference,
              controller.removeFileUploadByReference,
              backLinkFor(breadcrumbs)
            )(implicitly[Request[_]], context.messages, context.config.features, context.config.content)
          else
            views.summaryNoChoiceView(
              context.config.maximumNumberOfFiles,
              fileUploads,
              routes.ContinueToHostController.continueToHost,
              controller.previewFileUploadByReference,
              controller.removeFileUploadByReference,
              Call("GET", context.config.backlinkUrl)
            )(implicitly[Request[_]], context.messages, context.config.content)
        )

      case _ => NotImplemented
    }

  private def renderUploadRequestJson(
    uploadId: String
  ) =
    Renderer.simple {
      case s: State.UploadMultipleFiles =>
        s.fileUploads
          .findReferenceAndUploadRequestForUploadId(uploadId) match {
          case Some((reference, uploadRequest)) =>
            val json =
              Json.obj(
                "upscanReference" -> reference,
                "uploadId"        -> uploadId,
                "uploadRequest"   -> UploadRequest.formats.writes(uploadRequest)
              )
            Ok(json)

          case None => NotFound
        }

      case _ => Forbidden
    }

  private def renderFileRemovalStatus =
    Renderer.simple {
      case s: State => NoContent
      case _        => BadRequest
    }

  private def streamFileFromUspcan(
    reference: String
  ) =
    AsyncRenderer.simple {
      case s: HasFileUploads =>
        s.fileUploads.files.find(_.reference == reference) match {
          case Some(file: FileUpload.Accepted) =>
            getFileStream(
              file.url,
              file.fileName,
              file.fileMimeType,
              file.fileSize,
              (fileName, fileMimeType) =>
                fileMimeType match {
                  case _ =>
                    HeaderNames.CONTENT_DISPOSITION ->
                      s"""inline; filename="${fileName.filter(_.toInt < 128)}"; filename*=utf-8''${RFC3986Encoder
                        .encode(fileName)}"""
                }
            )

          case _ => Future.successful(NotFound)
        }
      case _ => Future.successful(NotFound)

    }

}

object FileUploadJourneyController {

  import FormFieldMappings._

  val YesNoChoiceForm = Form[Boolean](
    mapping("choice" -> yesNoMapping)(identity)(Option.apply)
  )

  val UpscanUploadSuccessForm = Form[S3UploadSuccess](
    mapping(
      "key"    -> nonEmptyText,
      "bucket" -> optional(nonEmptyText)
    )(S3UploadSuccess.apply)(S3UploadSuccess.unapply)
  )

  val UpscanUploadErrorForm = Form[S3UploadError](
    mapping(
      "key"            -> nonEmptyText,
      "errorCode"      -> text,
      "errorMessage"   -> text,
      "errorRequestId" -> optional(text),
      "errorResource"  -> optional(text)
    )(S3UploadError.apply)(S3UploadError.unapply)
  )

}
