package funstack.local.helper

import typings.jwtDecode.mod.{default => jwt_decode}
import typings.jwtDecode.mod.JwtPayload
import scala.scalajs.js

object AccessToken {
  def toAuthorizer(accessToken: Option[String]): js.Dynamic = accessToken match {
    case None              =>
      js.Dynamic.literal(
        principalId = "anon",
      )
    case Some(accessToken) =>
      js.Object
        .assign(
          js.Dynamic
            .literal(
              principalId = "user",
            )
            .asInstanceOf[js.Object],
          jwt_decode[JwtPayload](accessToken),
        )
        .asInstanceOf[js.Dynamic]
  }
}
