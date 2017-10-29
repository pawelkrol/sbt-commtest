import com.github.pawelkrol.CommTest.FunSpec

class BitmapAnalyserSpec extends FunSpec {

  outputPrg = "target/bitmap-analyser/bitmap-analyser.prg"

  labelLog = "target/bitmap-analyser/bitmap-analyser.log"

  describe("bitmap_analyser") {
    it("identifies pixel colour on a multicolour bitmap") {
      call
      assert(AC == 0x04) // purple
    }
  }
}
