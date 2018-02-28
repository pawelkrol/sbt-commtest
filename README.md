sbt-commtest
============

[sbt] plugin for running [CommTest]. This plugin requires [sbt 0.13].

VERSION
-------

Version 0.04-SNAPSHOT (2018-03-10)

INSTALLATION
------------

Add plugin to `project/plugins.sbt`:

    resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/groups/public/"

    addSbtPlugin("com.github.pawelkrol" % "sbt-commtest" % "0.04-SNAPSHOT")

EXAMPLE
-------

A sample `build.sbt` with settings to configure `sbt-commtest`:

    lazy val root = (project in file(".")).
      settings(
        Defaults.coreDefaultSettings ++ Seq(
          addFiles := Seq("01", "02", "03"),
          mainProgram := "program.src",
          packagerOptions := "-m64 -t64 -x1",
          scalaVersion := "2.12.4",
          startAddress := 0x1000
        )
      )

PREREQUISITES
-------------

`sbt-commtest` assumes the following set of conventions is respected:

1. Directory structure of a project reflects an example layout:

    ```
    .
    ├── ...
    ├── src
    │   ├── main   # Source code files
    │   └── test   # CommTest unit tests
    └── ...
    ```

2. `src/main` subdirectory consists of source program files (recognised by a `.src` extension) as well as supplementary include files (identified with an `.inc` extension). Include files will not be compiled as standalone binaries that become subjects to test, and they will be ignored by a test engine. They are not even expected to compile successfully without a context of their parent program files. On the other hand, all source code files (`.src`) are compiled and their result binaries may be included in unit tests.

3. `src/test` subdirectory consists of [Scala] unit tests conforming to documented [CommTest] requirements. There is one exception to those requirements though. Due to the fact that `sbt-commtest` plugin puts all result files in a `target` subdirectory, you must refer them from `target` and not from your source code directory in unit tests configuration. For example, if your `src/main` subdirectory contains a single source code file to test named `program.src`, all your test classes will need to specify appropriate references to both output binary and label log files (see [CommTest documentation] for more details on defining your unit tests) that will be written into `target` subdirectory during compilation process:

    ```
    outputPrg = "target/program.prg" // rather than "program.prg"

    labelLog = "target/program.log"  // rather than "program.log"
    ```

Please note that `src/main/program.src` compiles to `target/program.prg`, while label log is written to `target/program.log`. Future versions of this plugin will improve this aspect of setting up your tests in a way that instead of specifying targets explicitly you will only need to indicate a source code file under test.

USAGE
-----

Start [sbt] in an interactive mode:

    $ sbt

Compile the project:

    sbt> compile

Run the tests:

    sbt> test

Define the main program file (as a relative path to a directory containing all of your source code files) and specify an initial start address in your `build.sbt`:

    mainProgram := "program.src"

    startAddress := 0x1000

In an above example `target/program` would be created by packaging `target/program.prg`. Note that the main program file does not need to end with a `.src` extension (you may not necessarily want to unit test it before packaging), it will be successfully compiled and packaged nonetheless.

Package the program:

    sbt> package

`mainProgram` setting will also determine a file name of a target D64 disk image (e.g. `target/program.d64`).

Execute the program within an emulator (optionally with an additional hardware simulated):

    sbt> run
    sbt> run-ide64
    sbt> run-plus60k

Delete files produced by the build:

    sbt> clean

LOGGING
-------

[CommTest] is built on top of [CPU 6502 Simulator], which defaults to a very extensive logging configuration. Running tests without applying any modifications to these settings will result in dumping a very detailed processing output to a `cpu-6502-simulator.log` file.

In order to avoid running out of available disk space quickly it is recommended to minimise the logging output or even turn off logging completely. This is accomplished by creating an appropriately configured `src/test/resources/logback-test.xml` file. Should you lack an inspiration how to do it, see an example [logback-test.xml](example/src/test/resources/logback-test.xml).

COPYRIGHT AND LICENCE
---------------------

Copyright (C) 2017, 2018 by Pawel Krol.

This library is free open source software; you can redistribute it and/or modify it under [the same terms](https://github.com/pawelkrol/sbt-commtest/blob/master/LICENSE.md) as Scala itself, either Scala version 2.12.4 or, at your option, any later version of Scala you may have available.


[sbt]: http://www.scala-sbt.org/
[CommTest]: https://github.com/pawelkrol/Scala-CommTest
[sbt 0.13]: http://www.scala-sbt.org/1.0/docs/ChangeSummary_0.13.0.html
[Scala]: http://scala-lang.org/
[CommTest documentation]: https://github.com/pawelkrol/Scala-CommTest#initial-setup
[CPU 6502 Simulator]: https://github.com/pawelkrol/cpu-6502-simulator
