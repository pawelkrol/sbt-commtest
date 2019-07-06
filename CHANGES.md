CHANGES
=======

0.05-SNAPSHOT (2019-07-06)
--------------------------

* Fix bug with an incorrect parsing of executed shell commands by properly escaping quotation marks of an input string
* `CommTest` library dependency version updated to 0.05 (adds support for `callOriginal` method which executes an original subroutine implementation from within its mocked version)

0.04 (2019-01-23)
-----------------

* Add a new build option (`addFiles`) allowing to specify a sequence of binary files to be written directly into a packaged D64 disk image that may be needed by a compiled program during its runtime execution
* `CommTest` library dependency version updated to 0.04 (fixes mocking of multiple labels pointing to the same target memory address)
* `Scala` library version updated to 2.12.8
* `sbt` build tool version upgraded to 1.2.8

0.03 (2018-02-28)
-----------------

* `CommTest` library dependency version updated to 0.03 (enables capturing screenshots of a presently displayed screen and saving them as _PNG_ files)

0.02 (2018-02-10)
-----------------

* `CommTest` library dependency version updated to 0.02 (enables a +60k RAM extension simulation, and allows mocking of individual subroutine calls)

0.01 (2017-10-29)
-----------------

* Initial version (provides an sbt plugin for running CommTest, packaging executable programs, creating D64 disk image files, and running them in a VICE emulator)
