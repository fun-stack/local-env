package funstack.local.helper

import scala.annotation.unused

private object NativeCodec {
  import scala.scalajs.js
  import js.Dynamic.{global => g}

  @js.native
  trait BufferFactory extends js.Any {
    def from(@unused s: String, @unused tpe: String): Buffer = js.native
  }

  @js.native
  trait Buffer extends js.Any {
    def toString(@unused s: String): String = js.native
  }

  def buffer = g.Buffer.asInstanceOf[BufferFactory]

  val atob: js.Function1[String, String] =
    if (js.typeOf(g.atob) == "undefined") base64 => buffer.from(base64, "base64").toString("binary")
    else g.atob.asInstanceOf[js.Function1[String, String]]

  val btoa: js.Function1[String, String] =
    if (js.typeOf(g.btoa) == "undefined") text => buffer.from(text, "binary").toString("base64")
    else g.btoa.asInstanceOf[js.Function1[String, String]]
}

object Base64Codec {
  def encode(string: String): String =
    NativeCodec.btoa(string)

  def decode(base64Data: String): String =
    NativeCodec.atob(base64Data)
}
