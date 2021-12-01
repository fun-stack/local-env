import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(Seq(
  organization := "io.github.fun-stack",
  scalaVersion := "2.13.7",

  licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT")),

  homepage := Some(url("https://github.com/fun-stack/lambda-server")),

  scmInfo := Some(ScmInfo(
    url("https://github.com/fun-stack/lambda-server"),
    "scm:git:git@github.com:fun-stack/lambda-server.git",
    Some("scm:git:git@github.com:fun-stack/lambda-server.git"))
  ),

  pomExtra :=
    <developers>
      <developer>
        <id>jkaroff</id>
        <name>Johannes Karoff</name>
        <url>https://github.com/cornerman</url>
      </developer>
    </developers>,

  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
))

lazy val commonSettings = Seq(
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
  libraryDependencies ++=
    Deps.scalatest.value % Test ::
      Nil,
  scalacOptions --= Seq("-Xfatal-warnings", "-Wconf:any&src=src_managed/.*:i"),
)

lazy val jsSettings = Seq(
  useYarn := true,
  scalacOptions += {
    val githubRepo    = "fun-stack/lambda-server"
    val local         = baseDirectory.value.toURI
    val subProjectDir = baseDirectory.value.getName
    val remote        = s"https://raw.githubusercontent.com/${githubRepo}/${git.gitHeadCommit.value.get}"
    s"-P:scalajs:mapSourceURI:$local->$remote/${subProjectDir}/"
  },
)

lazy val lambdaServer = project.in(file("lambda-server"))
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin, ScalablyTypedConverterGenSourcePlugin)
  .settings(commonSettings, jsSettings)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    stOutputPackage := "funstack.lambdaserver.facades",
    stUseScalaJsDom := true,
    /* say we want to minimize all */
    stMinimize := Selection.All,
    /* but keep these very specific things*/
    stMinimizeKeep ++= List(
      "node.httpMod.^",
      "node.httpMod.createServer",
      "node.httpMod.IncomingMessage",
      "node.httpMod.ServerResponse",
      "ws.mod.WebSocketServer",
      "ws.mod.ServerOptions",
      "ws.wsStrings",
      "jwtDecode.mod.^",
      "jwtDecode.mod.default",
      "jwtDecode.mod.JwtPayload",
    ),
    name := "lambda-server",
    libraryDependencies ++=
      Deps.cats.effect.value ::
        Deps.awsSdkJS.lambda.value ::
        Deps.awsLambdaJS.value ::
        /* Deps.sttp.openApi.value :: */
        /* Deps.sttp.circeOpenApi.value :: */
        Nil,

    Compile / npmDependencies ++= Seq(
      "@types/node" -> "14.14.31",
      "ws"            -> "8.2.3",
      "@types/ws"     -> "8.2.0",
      "jwt-decode"    -> "3.1.2",
    ),
  )

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true,
  )
  .aggregate(lambdaServer)
