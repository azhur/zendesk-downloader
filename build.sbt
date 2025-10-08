ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.3"

lazy val root = (project in file(".")).settings(
  name := "zendesk-downloader",
  libraryDependencies ++= Seq(
    "com.softwaremill.sttp.client4" %% "core"                % "4.0.12",
    "com.softwaremill.sttp.client4" %% "cats"                % "4.0.12",
    "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server" % "1.11.48",
    "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"    % "1.11.48",
    "com.softwaremill.sttp.tapir"   %% "tapir-refined"       % "1.11.48",
    "org.http4s"                    %% "http4s-ember-server" % "0.23.32",
    "org.http4s"                    %% "http4s-ember-client" % "0.23.32",
    "org.typelevel"                 %% "cats-effect"         % "3.6.3",
    "co.fs2"                        %% "fs2-core"            % "3.12.2",
    "io.circe"                      %% "circe-core"          % "0.14.15",
    "io.circe"                      %% "circe-generic"       % "0.14.15",
    "io.circe"                      %% "circe-parser"        % "0.14.15",
    "io.circe"                      %% "circe-refined"       % "0.15.1",
    "com.softwaremill.sttp.client4" %% "circe"               % "4.0.12",
    "eu.timepit"                    %% "refined"             % "0.11.3",
    "com.outr"                      %% "scribe"              % "3.15.2",
    "com.outr"                      %% "scribe-cats"         % "3.15.2"
  )
)
