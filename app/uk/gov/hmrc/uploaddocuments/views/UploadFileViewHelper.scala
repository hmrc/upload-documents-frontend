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

package uk.gov.hmrc.uploaddocuments.views

import javax.inject.Singleton
import uk.gov.hmrc.uploaddocuments.models.FileUploadError
import play.api.data.FormError
import uk.gov.hmrc.uploaddocuments.models.FileTransmissionFailed
import uk.gov.hmrc.uploaddocuments.models.FileVerificationFailed
import uk.gov.hmrc.uploaddocuments.models.S3UploadError
import uk.gov.hmrc.uploaddocuments.models.UpscanNotification
import com.google.inject.Inject
import uk.gov.hmrc.uploaddocuments.wiring.AppConfig
import uk.gov.hmrc.uploaddocuments.models.DuplicateFileUpload
import uk.gov.hmrc.uploaddocuments.models.FileVerificationStatus
import uk.gov.hmrc.uploaddocuments.models.FileUpload
import play.api.mvc.Call
import play.api.libs.json.Json
import play.api.i18n.Messages

@Singleton
class UploadFileViewHelper @Inject() (appConfig: AppConfig) {

  def initialScriptStateFrom(
    initialFileUploads: Seq[FileUpload],
    previewFile: (String, String) => Call,
    maximumFileSizeBytes: Long,
    allowedFileTypesHint: String
  )(implicit
    messages: Messages
  ): String =
    Json.stringify(
      Json.toJson(
        initialFileUploads.map(file =>
          FileVerificationStatus(file, this, previewFile, (maximumFileSizeBytes / (1024 * 1024)), allowedFileTypesHint)
        )
      )
    )

  def toFormError(error: FileUploadError, maximumFileSizeBytes: Long, allowedFileTypesHint: String): FormError =
    error match {
      case FileTransmissionFailed(error) =>
        FormError("file", Seq(toMessageKey(error)), Seq((maximumFileSizeBytes / (1024 * 1024)), allowedFileTypesHint))

      case FileVerificationFailed(details) =>
        FormError("file", Seq(toMessageKey(details)), Seq((maximumFileSizeBytes / (1024 * 1024)), allowedFileTypesHint))

      case DuplicateFileUpload(checksum, existingFileName, duplicateFileName) =>
        FormError("file", Seq(duplicateFileMessageKey))
    }

  def toMessageKey(error: S3UploadError): String =
    error.errorCode match {
      case "400" | "InvalidArgument" => "error.file-upload.required"
      case "InternalError"           => "error.file-upload.try-again"
      case "EntityTooLarge"          => "error.file-upload.invalid-size-large"
      case "EntityTooSmall"          => "error.file-upload.invalid-size-small"
      case _                         => "error.file-upload.unknown"
    }

  def toMessageKey(details: UpscanNotification.FailureDetails): String =
    details.failureReason match {
      case UpscanNotification.QUARANTINE => "error.file-upload.quarantine"
      case UpscanNotification.REJECTED   => "error.file-upload.invalid-type"
      case UpscanNotification.UNKNOWN    => "error.file-upload.unknown"
    }

  val duplicateFileMessageKey: String = "error.file-upload.duplicate"
}
