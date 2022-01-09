import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object Deps {
  import Def.{setting => s}

  val scalatest = s("org.scalatest" %%% "scalatest" % "3.2.10")

  val cats = new {
    val effect = s("org.typelevel" %%% "cats-effect" % "3.3.4")
  }

  val awsLambdaJS = s("net.exoego" %%% "aws-lambda-scalajs-facade" % "0.11.0")
}
