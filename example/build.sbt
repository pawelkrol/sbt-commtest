emulatorOptions += "-cartrr /home/djgruby/.vice/Carts/rr38q_cnet.bin"

imageBuilderOptions := "-n \"DJ GRUBY / TRIAD\" -i \"2019 \""

mainProgram := "bitmap-analyser/bitmap-analyser.src"

packagerOptions := "-m64 -t64 -x1"

scalaVersion := "2.13.0"

startAddress := 0x1000

updateOptions := updateOptions.value.withLatestSnapshots(true)
