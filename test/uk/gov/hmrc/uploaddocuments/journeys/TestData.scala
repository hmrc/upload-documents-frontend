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

package uk.gov.hmrc.uploaddocuments.journeys

import uk.gov.hmrc.uploaddocuments.models._
import java.time._
import scala.concurrent.Future
import uk.gov.hmrc.uploaddocuments.connectors._
import uk.gov.hmrc.uploaddocuments.journeys.JourneyModel.UpscanInitiateApi

trait TestData {

  val uidAndEori = (Some("user-123"), Some("foo"))
  val eoriNumber = Some("foo")
  val correlationId = "123"
  val generatedAt = java.time.LocalDateTime.of(2018, 12, 11, 10, 20, 0)

  val reasonText = "our supplier went bankrupt"

  val nonEmptyFileUploads = FileUploads(files =
    Seq(
      FileUpload.Accepted(
        Nonce(1),
        Timestamp.Any,
        "foo-bar-ref-1",
        "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
        ZonedDateTime.parse("2018-04-24T09:30:00Z"),
        "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        "test.pdf",
        "application/pdf",
        4567890
      )
    )
  )

  val mockUpscanInitiate: UpscanInitiateApi = (serviceId, request) =>
    Future.successful(
      UpscanInitiateResponse(
        reference = "foo-bar-ref",
        uploadRequest = someUploadRequest(request)
      )
    )

  val testUpscanRequest: (String, Long) => UpscanInitiateRequest =
    (nonce, maxFileSize) =>
      UpscanInitiateRequest(
        callbackUrl = "https://foo.bar/callback",
        successRedirect = Some("https://foo.bar/success"),
        errorRedirect = Some("https://foo.bar/failure"),
        minimumFileSize = Some(0),
        maximumFileSize = Some(maxFileSize.toInt)
      )

  def someUploadRequest(request: UpscanInitiateRequest) =
    UploadRequest(
      href = "https://s3.bucket",
      fields = Map(
        "callbackUrl"     -> request.callbackUrl,
        "successRedirect" -> request.successRedirect.getOrElse(""),
        "errorRedirect"   -> request.errorRedirect.getOrElse(""),
        "minimumFileSize" -> request.minimumFileSize.getOrElse(0).toString,
        "maximumFileSize" -> request.maximumFileSize.getOrElse(0).toString
      )
    )

  val fileUploadInitiated = FileUpload.Initiated(
    Nonce(1),
    Timestamp.Any,
    "foo-bar-ref-1"
  )

  val fileUploadPosted = FileUpload.Posted(
    Nonce(2),
    Timestamp.Any,
    "foo-bar-ref-2"
  )

  val fileUploadAccepted = FileUpload.Accepted(
    Nonce(3),
    Timestamp.Any,
    "foo-bar-ref-3",
    "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
    ZonedDateTime.parse("2018-04-24T09:30:00Z"),
    "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    "test.pdf",
    "application/pdf",
    4567890
  )
  val fileUploadFailed = FileUpload.Failed(
    Nonce(4),
    Timestamp.Any,
    "foo-bar-ref-4",
    UpscanNotification.FailureDetails(
      UpscanNotification.QUARANTINE,
      "e.g. This file has a virus"
    )
  )

  val fileUploadRejected = FileUpload.Rejected(
    Nonce(5),
    Timestamp.Any,
    "foo-bar-ref-5",
    S3UploadError("a", "b", "c")
  )

  val fileUploadDuplicate = FileUpload.Duplicate(
    Nonce(6),
    Timestamp.Any,
    "foo-bar-ref-6",
    "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    "test.pdf",
    "test2.png"
  )

  final val acceptedFileUpload =
    FileUpload.Accepted(
      Nonce(1),
      Timestamp.Any,
      "foo-bar-ref-1",
      "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
      ZonedDateTime.parse("2018-04-24T09:30:00Z"),
      "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
      "test.pdf",
      "application/pdf",
      4567890
    )

}
