package io.github.gciatto.python.uv.test

import io.github.gciatto.python.uv.Pep440
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class Pep440Test :
    StringSpec({
        "semantic versions should be converted into PEP 440 versions" {
            mapOf(
                "1.2.3" to "1.2.3",
                "v1.2.3" to "1.2.3",
                "1.2.4-dev05-abc1234" to "1.2.4.dev5",
                "1.2.4-dev05+abc1234" to "1.2.4.dev5",
                "1.2.4-dev05-b123456" to "1.2.4.dev5",
                "2.0.0-rc.1" to "2.0.0rc1",
                "2.0.0-beta2" to "2.0.0b2",
                "1.0.0-alpha.2-dev01-abc" to "1.0.0a2.dev1",
                "1.0.0-SNAPSHOT" to "1.0.0.dev0",
                "1.0.0-post1" to "1.0.0.post1",
                "unspecified" to "0.0.0",
            ).forEach { (version, expected) ->
                Pep440.from(version) shouldBe expected
            }
        }

        "local labels should be kept on demand" {
            Pep440.from("1.2.4-dev05-abc1234", includeLocal = true) shouldBe "1.2.4.dev5+abc1234"
        }
    })
