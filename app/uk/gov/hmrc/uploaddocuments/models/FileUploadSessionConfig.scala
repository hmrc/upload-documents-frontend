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

package uk.gov.hmrc.uploaddocuments.models

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, JsValue}
import uk.gov.hmrc.uploaddocuments.models.FileUploadSessionConfig._

import java.net.URL
import scala.util.Try

final case class FileUploadSessionConfig(
  nonce: Nonce, // unique secret shared by the host and upload microservices
  continueUrl: String, // url to continue after uploading the files
  backlinkUrl: String, // backlink url
  callbackUrl: String, // url where to post uploaded files
  continueAfterYesAnswerUrl: Option[String] =
    None, // optional url to continue after user selects YES answer in the form
  continueWhenFullUrl: Option[String] = None, // optional url to continue after all possible files has been uploaded
  continueWhenEmptyUrl: Option[String] = None, // optional url to continue after none file uploaded
  minimumNumberOfFiles: Int = defaultMinimumNumberOfFiles,
  maximumNumberOfFiles: Int = defaultMaximumNumberOfFiles,
  initialNumberOfEmptyRows: Int = defaultInitialNumberOfEmptyRows,
  maximumFileSizeBytes: Long = defaultMaximumFileSizeBytes,
  allowedContentTypes: String = defaultAllowedContentTypes,
  allowedFileExtensions: Option[String] = None,
  cargo: Option[JsValue] = None, // opaque data carried through, from and to the host service,
  newFileDescription: Option[String] = None, // description of the new file added,
  features: Features = Features(), // upload feature switches
  content: CustomizedServiceContent = CustomizedServiceContent() // page content customizations
) {

  final def getContinueWhenFullUrl: String = continueWhenFullUrl.getOrElse(continueUrl)
  final def getContinueWhenEmptyUrl: String = continueWhenEmptyUrl.getOrElse(continueUrl)
  final def getFilePickerAcceptFilter: String = allowedContentTypes + allowedFileExtensions.map("," + _).getOrElse("")

  final def isValid: Boolean =
    isValidCallbackUrl(callbackUrl) &&
      isValidFrontendUrl(continueUrl) &&
      isValidFrontendUrl(backlinkUrl) &&
      continueWhenFullUrl.map(isValidFrontendUrl).getOrElse(true) &&
      continueWhenEmptyUrl.map(isValidFrontendUrl).getOrElse(true) &&
      continueAfterYesAnswerUrl.map(isValidFrontendUrl).getOrElse(true) &&
      allowedContentTypes.nonEmpty &&
      minimumNumberOfFiles >= 0 &&
      maximumNumberOfFiles >= 1 &&
      maximumNumberOfFiles >= minimumNumberOfFiles &&
      maximumFileSizeBytes > 0
}

object FileUploadSessionConfig {

  final val defaultMinimumNumberOfFiles: Int = 1
  final val defaultMaximumNumberOfFiles: Int = 10
  final val defaultInitialNumberOfEmptyRows: Int = 3
  final val defaultMaximumFileSizeBytes = 10 * 1024 * 1024
  final val defaultAllowedContentTypes = "image/jpeg,image/png,application/pdf,text/plain"

  implicit val format: Format[FileUploadSessionConfig] =
    Format(
      ((JsPath \ "nonce").read[Nonce]
        and (JsPath \ "continueUrl").read[String]
        and (JsPath \ "backlinkUrl").read[String]
        and (JsPath \ "callbackUrl").read[String]
        and (JsPath \ "continueAfterYesAnswerUrl").readNullable[String]
        and (JsPath \ "continueWhenFullUrl").readNullable[String]
        and (JsPath \ "continueWhenEmptyUrl").readNullable[String]
        and (JsPath \ "minimumNumberOfFiles").readWithDefault[Int](defaultMinimumNumberOfFiles)
        and (JsPath \ "maximumNumberOfFiles").readWithDefault[Int](defaultMaximumNumberOfFiles)
        and (JsPath \ "initialNumberOfEmptyRows").readWithDefault[Int](defaultInitialNumberOfEmptyRows)
        and (JsPath \ "maximumFileSizeBytes").readWithDefault[Long](defaultMaximumFileSizeBytes)
        and (JsPath \ "allowedContentTypes").readWithDefault[String](defaultAllowedContentTypes)
        and (JsPath \ "allowedFileExtensions").readNullable[String]
        and (JsPath \ "cargo").readNullable[JsValue]
        and (JsPath \ "newFileDescription").readNullable[String]
        and (JsPath \ "features").readWithDefault[Features](Features())
        and (JsPath \ "content")
          .readWithDefault[CustomizedServiceContent](CustomizedServiceContent()))(FileUploadSessionConfig.apply _),
      ((JsPath \ "nonce").write[Nonce]
        and (JsPath \ "continueUrl").write[String]
        and (JsPath \ "backlinkUrl").write[String]
        and (JsPath \ "callbackUrl").write[String]
        and (JsPath \ "continueAfterYesAnswerUrl").writeNullable[String]
        and (JsPath \ "continueWhenFullUrl").writeNullable[String]
        and (JsPath \ "continueWhenEmptyUrl").writeNullable[String]
        and (JsPath \ "minimumNumberOfFiles").write[Int]
        and (JsPath \ "maximumNumberOfFiles").write[Int]
        and (JsPath \ "initialNumberOfEmptyRows").write[Int]
        and (JsPath \ "maximumFileSizeBytes").write[Long]
        and (JsPath \ "allowedContentTypes").write[String]
        and (JsPath \ "allowedFileExtensions").writeNullable[String]
        and (JsPath \ "cargo").writeNullable[JsValue]
        and (JsPath \ "newFileDescription").writeNullable[String]
        and (JsPath \ "features").write[Features]
        and (JsPath \ "content").write[CustomizedServiceContent])(unlift(FileUploadSessionConfig.unapply(_)))
    )

  final def isValidFrontendUrl(url: String): Boolean =
    url.nonEmpty && Try(new URL(url))
      .map { url =>
        val isHttps = url.getProtocol() == "https"
        val host = url.getHost()
        (host == "localhost") || (isHttps && host.endsWith(".gov.uk"))
      }
      .getOrElse(false)

  final def isValidCallbackUrl(url: String): Boolean =
    url.nonEmpty && Try(new URL(url))
      .map { url =>
        val isHttps = url.getProtocol() == "https"
        val host = url.getHost()
        (host == "localhost") || (isHttps && host.endsWith(".mdtp"))
      }
      .getOrElse(false)
}
