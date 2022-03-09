package funstack.lambdaserver.auth

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.JSConverters._

@js.native
@JSImport("../../../../../oidc-server/", JSImport.Namespace)
private object OidcServer extends js.Object {
  def start(port: Int): Unit = js.native
}

object DevServer {
  def start(port: Int): Unit = OidcServer.start(port)
}
