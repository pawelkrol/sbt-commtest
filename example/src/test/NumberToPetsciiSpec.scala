import com.github.pawelkrol.CommTest.FunSpec

class NumberToPetsciiSpec extends FunSpec {

  outputPrg = "target/number-to-petscii.prg"

  labelLog = "target/number-to-petscii.log"

  describe("byte_to_hex") {
    it("converts an 8-bit number to a sequence of digits") {
      AC = 0xd5 // number to convert
      call
      assert(readByteAt("sign") === 0x00)                     // plus sign
      assert(readBytesAt("number", XR()) === Seq(0x0d, 0x05)) // digits
      assert(XR === 0x02)                                     // number of digits
    }
  }

  describe("number_to_petscii") {
    it("converts a number to a PETSCII string") {
      writeByteAt("sign", 0x00)                      // sign to convert
      XR = 0x04                                      // number of digits
      writeBytesAt("number", 0x00, 0x03, 0x0e, 0x08) // digits to convert
      call
      assert(readByteAt("sign") === 0x2b)                                 // "+"
      assert(readBytesAt("number", 0x04) === Seq(0x30, 0x33, 0x45, 0x38)) // "03e8"
    }
  }

  describe("word_to_hex") {
    it("converts a 16-bit number to a sequence of digits") {
      AC = 0x40 // number to convert (lo byte)
      XR = 0x3f // number to convert (hi byte)
      call
      assert(readByteAt("sign") === 0x00)                                 // plus sign
      assert(readBytesAt("number", XR()) === Seq(0x03, 0x0f, 0x04, 0x00)) // digits
      assert(XR === 0x04)                                                 // number of digits
    }
  }
}
