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

package uk.gov.hmrc.uploaddocuments.support

import org.jsoup.safety.Safelist
import org.jsoup.Jsoup

object HtmlCleaner {

  final val blockSafelist = new Safelist()
    .addTags(
      "div",
      "p",
      "span",
      "br",
      "ol",
      "ul",
      "li",
      "dd",
      "dl",
      "dt",
      "i",
      "b",
      "em",
      "strong",
      "summary",
      "details"
    )
    .addAttributes(":all", "class")
    .addAttributes(":all", "id")
    .addAttributes(":all", "data-module")

  final val simpleTextSafelist =
    Safelist.simpleText
      .addTags("span")
      .addAttributes(":all", "class")
      .addAttributes(":all", "id")

  final def cleanBlock(text: String): String = Jsoup.clean(text, blockSafelist)

  final def cleanSimpleText(text: String): String = Jsoup.clean(text, simpleTextSafelist)

}
