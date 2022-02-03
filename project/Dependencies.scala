import sbt._

object Dependencies {
  object V {
    val CassandraJavaDriver = "4.13.0"
    val CassandraMigrator   = "2.4.0_v4"
    val cats                = "2.7.0"
    val kittens             = "2.3.2"
    val ce                  = "3.3.0"
    val fs2                 = "3.2.4"
    val test                = "3.2.9"
    val CEtest              = "1.4.0"

    val cassandraCore = "4.0.0"
    val logback       = "1.2.10"
    val scalaLogging  = "3.9.4"
    val log4cats      = "2.1.1"
    val circe         = "0.14.1"

    val config   = "1.4.1"
    val pureConf = "0.17.1"
  }

  object Libraries {
    //config
    val pureConfig = "com.github.pureconfig" %% "pureconfig" % V.pureConf

    // DB
    val javaDriverMapperRuntime   = "com.datastax.oss" % "java-driver-mapper-runtime" % V.CassandraJavaDriver
    val javaDriverMapperProcessor = "com.datastax.oss" % "java-driver-mapper-processor" % V.CassandraJavaDriver
    val cassandraCore             = ("com.datastax.cassandra" % "cassandra-driver-core" % V.cassandraCore).pomOnly()
    val cassandraMigrator         = "org.cognitor.cassandra" % "cassandra-migration" % V.CassandraMigrator
    // logging
    val logback      = "ch.qos.logback"             % "logback-classic" % V.logback      // backend
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging"  % V.scalaLogging // for ib wrapper
    val log4cats     = "org.typelevel"              %% "log4cats-slf4j" % V.log4cats     // for everything else

    // cats
    val catsCore   = "org.typelevel" %% "cats-core" % V.cats
    val kittens    = "org.typelevel" %% "kittens" % V.kittens
    val catsEffect = ("org.typelevel" %% "cats-effect" % V.ce).withSources().withJavadoc()
    val fs2core    = "co.fs2" %% "fs2-core" % V.fs2
    val fs2io      = "co.fs2" %% "fs2-io" % V.fs2
    val fs2rx      = "co.fs2" %% "fs2-reactive-streams" % V.fs2

    //circe
    val circeCore    = "io.circe" %% "circe-core"    % V.circe
    val circeGeneric = "io.circe" %% "circe-generic" % V.circe
    val circeParser  = "io.circe" %% "circe-parser"  % V.circe

    // testing
    val scalactic = "org.scalactic" %% "scalactic"                     % V.test   % Test
    val scalatest = "org.scalatest" %% "scalatest"                     % V.test   % Test
    val ceTesting = "org.typelevel" %% "cats-effect-testing-scalatest" % V.CEtest % Test
  }

}
