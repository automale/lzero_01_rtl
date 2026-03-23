// 1. 공통 빌드 설정 (Scala 2.13 환경)
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "tech.l-zero"

val chiselVersion = "6.7.0"

// 2. 메인 프로젝트 정의
lazy val lzero_pim = (project in file("."))
  .settings(
    name := "lzero_pim",
    
    // 3. 라이브러리 및 의존성 주입 (Chisel + 로컬 캐시의 로켓칩)
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel" % chiselVersion,
      "org.chipsalliance" %% "rocketchip-6.7.0" % "1.6-SNAPSHOT"
    ),
    
    // 4. Chisel 컴파일러 플러그인 추가
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
  )