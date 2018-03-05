CHANGES
=======

0.04-SNAPSHOT (2018-03-10)
--------------------------

* Add a new build option (`addFiles`) allowing to specify a sequence of binary files to be written directly into a packaged D64 disk image that may be needed by a compiled program during its runtime execution
* `CommTest` library dependency version updated to 0.04-SNAPSHOT (fixes mocking of multiple labels pointing to the same target memory address)

0.03 (2018-02-28)
-----------------

* `CommTest` library dependency version updated to 0.03 (enables capturing screenshots of a presently displayed screen and saving them as _PNG_ files)

0.02 (2018-02-10)
-----------------

* `CommTest` library dependency version updated to 0.02 (enables a +60k RAM extension simulation, and allows mocking of individual subroutine calls)

0.01 (2017-10-29)
-----------------

* Initial version (provides an sbt plugin for running CommTest, packaging executable programs, creating D64 disk image files, and running them in a VICE emulator)
