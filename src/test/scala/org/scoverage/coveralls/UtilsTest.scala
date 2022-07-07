package org.scoverage.coveralls

import java.io.File

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class UtilsTest extends AnyWordSpec with Matchers {
  "mkFileFromPath" when {
    "getting the right params" should {

      "return the right path" in {
        val path = Seq(".", "a", "b")
        Utils.mkFileFromPath(path).getPath shouldEqual path.mkString(
          File.separator
        )
      }
    }
  }
}
