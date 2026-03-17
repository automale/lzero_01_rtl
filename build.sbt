lazy val commonSettings = Seq(
  organization := "tech.l-zero",
  version := "0.1.0",
  scalaVersion := "2.13.10", // Rocket Chip 권장 Scala 버전 확인 필요
  addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "5.0.0" cross CrossVersion.full),
  libraryDependencies ++= Seq(
    "org.chipsalliance" %% "chisel" % "5.0.0",
  )
)

// Rocket Chip 프로젝트를 정의하고 프로젝트의 의존성으로 추가
lazy val rocketchip = RootProject(file("rocket-chip"))

lazy val lzero_pim = (project in file("."))
  .dependsOn(rocketchip) // 우리 프로젝트가 Rocket Chip에 의존함을 명시
  .settings(commonSettings)