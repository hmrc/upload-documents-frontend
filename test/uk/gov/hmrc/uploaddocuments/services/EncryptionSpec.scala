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
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.uploaddocuments.support.UnitSpec

import java.nio.charset.StandardCharsets
import scala.util.Random

class EncryptionSpec extends UnitSpec with ScalaCheckPropertyChecks {

  def testKey: String = {
    val bytes = new Array[Byte](32)
    Random.nextBytes(bytes)
    new String(Base64.encodeBase64(bytes), StandardCharsets.UTF_8)
  }

  case class Wrapper(data1: String, data2: Int)
  object Wrapper {
    implicit val format: Format[Wrapper] = Json.format[Wrapper]
  }

  "Encryption" should {
    "encrypt and decrypt values using provided key" in {
      val keyProvider = KeyProvider(testKey)
      forAll { (data1: String, data2: Int) =>
        val original = Wrapper(data1, data2)
        val encrypted: String = Encryption.encrypt(original, keyProvider)
        val decrypted: Wrapper = Encryption.decrypt[Wrapper](encrypted, keyProvider)
        encrypted should not be decrypted
        decrypted shouldBe original
      }
    }

    "encrypt and decrypt values using provided key sequence" in {
      forAll { (data1: String, data2: Int) =>
        val keyUsedForEncryption = testKey
        val keyProvider1 = KeyProvider(Seq(keyUsedForEncryption, testKey, testKey))
        val keyProvider2 = KeyProvider(Seq(testKey, testKey, testKey, keyUsedForEncryption, testKey, testKey))
        val original = Wrapper(data1, data2)
        val encrypted: String = Encryption.encrypt(original, keyProvider1)
        val decrypted: Wrapper = Encryption.decrypt[Wrapper](encrypted, keyProvider2)
        encrypted should not be decrypted
        decrypted shouldBe original
      }
    }
  }
}
