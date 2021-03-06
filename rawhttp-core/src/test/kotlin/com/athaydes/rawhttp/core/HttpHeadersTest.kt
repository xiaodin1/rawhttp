package com.athaydes.rawhttp.core

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec

class HttpHeadersTest : StringSpec({

    "Headers are multi-value, case-insensitive" {
        RawHttpHeaders.Builder.newBuilder()
                .with("hi", "aaa")
                .with("hi", "bbb")
                .with("ho", "ccc")
                .build().run {
            get("hi") shouldEqual listOf("aaa", "bbb")
            get("ho") shouldEqual listOf("ccc")
            get("Hi") shouldEqual get("hi")
            get("Hi") shouldEqual get("HI")
            get("Ho") shouldEqual get("ho")
            get("Ho") shouldEqual get("hO")
            getFirst("hi") should bePresent { it shouldEqual "aaa" }
            getFirst("HI") should bePresent { it shouldEqual "aaa" }
            getFirst("ho") should bePresent { it shouldEqual "ccc" }
            getFirst("Ho") should bePresent { it shouldEqual "ccc" }
            getFirst("HI") shouldEqual getFirst("hi")
            getFirst("HO") shouldEqual getFirst("ho")
            get("blah") should beEmpty()
            getFirst("blah") should notBePresent()

            contains("hi") shouldBe true
            contains("Hi") shouldBe true
            contains("hI") shouldBe true
            contains("HI") shouldBe true
            contains("Ho") shouldBe true
            contains("Blah") shouldBe false

            headerNames shouldEqual listOf("hi", "hi", "ho")
            uniqueHeaderNames shouldEqual setOf("HI", "HO")
        }
    }

    "Headers can be re-constructed exactly" {
        RawHttpHeaders.Builder.newBuilder()
                .with("Content-Type", "33")
                .with("Accept", "application/json")
                .with("Accept", "text/html")
                .with("Server", "nginx")
                .with("Accept", "text/plain")
                .with("Date", "22 March 2012")
                .build().toString() shouldEqual "" +
                "Content-Type: 33\r\n" +
                "Accept: application/json\r\n" +
                "Accept: text/html\r\n" +
                "Accept: text/plain\r\n" + // headers are grouped together, so the order may change
                "Server: nginx\r\n" +
                "Date: 22 March 2012\r\n" +
                "\r\n"
    }

})