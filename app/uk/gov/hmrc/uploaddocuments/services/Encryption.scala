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

package uk.gov.hmrc.uploaddocuments.services

import org.apache.commons.codec.binary.Base64
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json, Reads, Writes}

import java.nio.charset.StandardCharsets
import java.security.Key
import javax.crypto.Cipher

trait KeyProvider {
  def key: Key
}

object Encryption {

  def encrypt[T](value: T, keyProvider: KeyProvider)(implicit wrts: Writes[T]): String =
    encrypt(Json.stringify(wrts.writes(value)), keyProvider.key)

  def decrypt[T](encrypted: String, keyProvider: KeyProvider)(implicit rds: Reads[T]): T =
    rds
      .reads(Json.parse(decrypt(encrypted, keyProvider.key))) match {
      case JsSuccess(value, path) => value
      case JsError(jsonErrors) =>
        val error =
          s"Encountered an issue with de-serialising JSON state: ${jsonErrors
            .map { case (p, s) =>
              s"${if (p.toString().isEmpty()) "" else s"$p -> "}${s.map(_.message).mkString(", ")}"
            }
            .mkString(", ")}. \nCheck if all your states have relevant entries declared in the *JourneyStateFormats.serializeStateProperties and *JourneyStateFormats.deserializeState functions."
        Logger(getClass).error(error)
        throw new Exception(error)
    }

  def encrypt(data: String, key: Key): String =
    try {
      val cipher: Cipher = Cipher.getInstance(key.getAlgorithm)
      cipher.init(Cipher.ENCRYPT_MODE, key, cipher.getParameters)
      new String(Base64.encodeBase64(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8))), StandardCharsets.UTF_8)
    } catch {
      case e: Exception => throw new SecurityException("Failed encrypting data", e)
    }

  def decrypt(data: String, key: Key): String =
    try {
      val cipher: Cipher = Cipher.getInstance(key.getAlgorithm)
      cipher.init(Cipher.DECRYPT_MODE, key, cipher.getParameters)
      new String(cipher.doFinal(Base64.decodeBase64(data.getBytes(StandardCharsets.UTF_8))), StandardCharsets.UTF_8)
    } catch {
      case e: Exception => throw new SecurityException("Failed decrypting data", e)
    }

}
