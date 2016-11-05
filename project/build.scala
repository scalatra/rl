import sbt._
import Keys._
import scala.xml._
import sbtbuildinfo.Plugin._
import com.typesafe.sbt.pgp.PgpKeys._

// Shell prompt which show the current project, git branch and build version
// git magic from Daniel Sobral, adapted by Ivan Porto Carrero to also work with git flow branches
object ShellPrompt {

  object devnull extends ProcessLogger {
    def info (s: => String) {}
    def error (s: => String) { }
    def buffer[T] (f: => T): T = f
  }

  val current = """\*\s+([^\s]+)""".r

  def gitBranches = ("git branch --no-color" lines_! devnull mkString)

  val buildShellPrompt = {
    (state: State) => {
      val currBranch = current findFirstMatchIn gitBranches map (_ group(1)) getOrElse "-"
      val currProject = Project.extract (state).currentProject.id
      "%s:%s> ".format (currBranch, currProject)
    }
  }

}

object RlSettings {
  val buildOrganization = "org.scalatra.rl"
  val buildScalaVersion = "2.11.0"

  val description = SettingKey[String]("description")

  val buildSettings = Defaults.defaultSettings ++ Seq(
      organization := buildOrganization,
      scalaVersion := buildScalaVersion,
      crossScalaVersions := Seq("2.11.0", "2.12.0"),
      javacOptions ++= Seq("-Xlint:unchecked"),
      scalacOptions ++= Seq(
        "-optimize",
        "-deprecation",
        "-unchecked",
        "-Xcheckinit",
        "-encoding", "utf8"),
      libraryDependencies += "org.specs2" %% "specs2-core" % "3.8.6" % "test",
      libraryDependencies += "junit" % "junit" % "4.10" % "test",
      crossVersion := CrossVersion.binary,
      resolvers ++= Seq(
        "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
        "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
        "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
      ),
      artifact in (Compile, packageBin) ~= { (art: Artifact) =>
        if (sys.props("java.version") startsWith "1.7") art.copy(classifier = Some("jdk17")) else art
      },
      autoCompilerPlugins := true,
      parallelExecution in Test := false,
      shellPrompt  := ShellPrompt.buildShellPrompt
  )

  val packageSettings = Seq (
    packageOptions <<= (packageOptions, name, version, organization) map {
      (opts, title, version, vendor) =>
         opts :+ Package.ManifestAttributes(
          "Created-By" -> "Simple Build Tool",
          "Built-By" -> System.getProperty("user.name"),
          "Build-Jdk" -> System.getProperty("java.version"),
          "Specification-Title" -> title,
          "Specification-Vendor" -> "Mojolly Ltd.",
          "Specification-Version" -> version,
          "Implementation-Title" -> title,
          "Implementation-Version" -> version,
          "Implementation-Vendor-Id" -> vendor,
          "Implementation-Vendor" -> "Mojolly Ltd.",
          "Implementation-Url" -> "https://backchat.io"
         )
    },
    homepage := Some(url("https://backchat.io")),
    startYear := Some(2010),
    licenses := Seq(("MIT", url("http://github.com/mojolly/rl/raw/HEAD/LICENSE"))),
    pomExtra <<= (pomExtra, name, description) {(pom, name, desc) => pom ++ Group(
      <scm>
        <connection>scm:git:git://github.com/mojolly/rl.git</connection>
        <developerConnection>scm:git:git@github.com:mojolly/rl.git</developerConnection>
        <url>https://github.com/mojolly/rl</url>
      </scm>
      <developers>
        <developer>
          <id>casualjim</id>
          <name>Ivan Porto Carrero</name>
          <url>http://flanders.co.nz/</url>
        </developer>
        <developer>
          <id>ben-biddington</id>
          <name>Ben Biddington</name>
          <url>http://benbiddington.wordpress.com/</url>
        </developer>
      </developers>
    )},
    publishMavenStyle := true,
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { x => false })

  val projectSettings = buildSettings ++ packageSettings
}

object RlBuild extends Build {

  import RlSettings._
  val buildShellPrompt =  ShellPrompt.buildShellPrompt

  val unpublished = Seq(
    // no artifacts in this project
    publishArtifact := false,
    // make-pom has a more specific publishArtifact setting already
    // so needs specific override
    publishArtifact in makePom := false,
    // can't seem to get rid of ivy files except by no-op'ing the entire publish task
    publish := {},
    publishSigned := {},
    publishLocalSigned := {},
    publishLocal := {},
    publishLocalSignedConfiguration := null,
    publishSignedConfiguration := null
  )

  lazy val root = Project ("rl-project", file("."),
                          settings = Project.defaultSettings ++ unpublished ++ Seq(
                            name := "rl-project",
                            scalaVersion := buildScalaVersion,
                            crossScalaVersions := Seq("2.11.0", "2.12.0")
                          )) aggregate(core, followRedirects)

  lazy val core = Project ("rl", file("core"), settings = projectSettings ++ buildInfoSettings ++ Seq(
    name := "rl",
    (resourceGenerators in Compile) += task{
      val rlResource = (resourceManaged in Compile).value / "rl"
      val f = rlResource / "tld_names.dat"
      IO.download(url("https://publicsuffix.org/list/effective_tld_names.dat"), f)
      Seq(f)
    },
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "rl",
    description := "An RFC-3986 compliant URI library."))

  lazy val followRedirects = Project("rl-expand", file("expand"), settings = projectSettings ++ Seq(
    name := "rl-expander",
    description := "Expands urls when they appear shortened",
    libraryDependencies += "com.ning" % "async-http-client" % "1.8.9",
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.7",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2" % "provided",
    initialCommands in console :=
      """
        |import rl._
        |import expand._
      """.stripMargin
  )) dependsOn (core)

}

// vim: set ts=2 sw=2 et:
