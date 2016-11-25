import sbt.inc.Analysis

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayRootProject)
  .aggregate(core)
  .settings(
    name := "play-ebean-root",
    releaseCrossBuild := false
  )

lazy val core = project
  .in(file("play-ebean"))
  .enablePlugins(Playdoc, PlayLibrary)
  .settings(jacoco.settings: _*)
  .settings(
    name := "play-ebean",
    libraryDependencies ++= playEbeanDeps,
    compile in Compile := enhanceEbeanClasses(
      (dependencyClasspath in Compile).value,
      (compile in Compile).value,
      (classDirectory in Compile).value,
      "play/db/ebean/**"
    )
  )

lazy val plugin = project
  .in(file("sbt-play-ebean"))
  .enablePlugins(PlaySbtPlugin)
  .settings(
    name := "sbt-play-ebean",
    organization := "com.typesafe.sbt",
    libraryDependencies ++= sbtPlayEbeanDeps,
    addSbtPlugin("com.typesafe.sbt" % "sbt-play-enhancer" % PlayEnhancerVersion),
    addSbtPlugin("com.typesafe.play" % "sbt-plugin" % PlayVersion),
    resourceGenerators in Compile <+= generateVersionFile,
    scriptedLaunchOpts ++= Seq("-Dplay-ebean.version=" + version.value),
    scriptedDependencies := {
      val () = publishLocal.value
      val () = (publishLocal in core).value
    }
  )
val PlayVersion = playVersion(sys.props.getOrElse("play.version", "2.5.10"))
val PlayEnhancerVersion = "1.1.0"
val EbeanVersion = "9.1.2"
val EbeanORMAgentVersion = "8.1.1"

playBuildRepoName in ThisBuild := "play-ebean"
// playBuildExtraTests := {
//  (scripted in plugin).toTask("").value
// }
playBuildExtraPublish := {
  (PgpKeys.publishSigned in plugin).value
}

// Dependencies
def playEbeanDeps = Seq(
  "com.typesafe.play" %% "play-java-jdbc" % PlayVersion,
  "com.typesafe.play" %% "play-jdbc-evolutions" % PlayVersion,
  "org.avaje.ebean" % "ebean" % EbeanVersion,
  "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % EbeanORMAgentVersion,
  "com.typesafe.play" %% "play-test" % PlayVersion % Test
)

def sbtPlayEbeanDeps = Seq(
  "org.avaje.ebeanorm" % "avaje-ebeanorm-agent" % EbeanORMAgentVersion,
  "com.typesafe" % "config" % "1.3.0"
)

// Ebean enhancement
def enhanceEbeanClasses(classpath: Classpath, analysis: Analysis, classDirectory: File, pkg: String): Analysis = {
  // Ebean (really hacky sorry)
  val cp = classpath.map(_.data.toURI.toURL).toArray :+ classDirectory.toURI.toURL
  val cl = new java.net.URLClassLoader(cp)
  val t = cl.loadClass("com.avaje.ebean.enhance.agent.Transformer").getConstructor(classOf[Array[URL]], classOf[String]).newInstance(cp, "debug=0").asInstanceOf[AnyRef]
  val ft = cl.loadClass("com.avaje.ebean.enhance.ant.OfflineFileTransform").getConstructor(
    t.getClass, classOf[ClassLoader], classOf[String]
  ).newInstance(t, ClassLoader.getSystemClassLoader, classDirectory.getAbsolutePath).asInstanceOf[AnyRef]
  ft.getClass.getDeclaredMethod("process", classOf[String]).invoke(ft, pkg)
  analysis
}

// Version file
def generateVersionFile = Def.task {
  val version = (Keys.version in core).value
  val file = (resourceManaged in Compile).value / "play-ebean.version.properties"
  val content = s"play-ebean.version=$version"
  IO.write(file, content)
  Seq(file)
}
