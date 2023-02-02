package funstack.local.helper

import typings.jwtDecode.mod.{default => jwt_decode, JwtPayload}

import scala.scalajs.js

object AccessToken {
  def toAuthorizer(accessToken: Option[String]): js.Dynamic = accessToken match {
    case None              =>
      js.Dynamic.literal()
    case Some(accessToken) =>
      val jwtPayload = jwt_decode[JwtPayload](accessToken)
      stringifyClaims(jwtPayload).asInstanceOf[js.Dynamic]
  }

  private def stringifyClaims(jwtPayload: JwtPayload): js.Object = {
    val stringEntries = js.Object.entries(jwtPayload).map { case js.Tuple2(key, value) =>
      if (js.typeOf(value) == "string" || value.isInstanceOf[String]) {
        js.Tuple2(key, value)
      }
      else {
        js.Tuple2(key, js.JSON.stringify(value.asInstanceOf[js.Any]))
      }
    }

    js.Object.fromEntries(stringEntries).asInstanceOf[js.Object]
  }
}
