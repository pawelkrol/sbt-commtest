emulator := "x64sc"

emulatorOptions := "-cartrr /home/djgruby/.vice/Carts/rr38q_cnet.bin -model c64c -truedrive"

imageBuilderOptions := "-n \"DJ GRUBY / TRIAD\" -i \"2019 \""

mainProgram := "bitmap-analyser/bitmap-analyser.src"

packagerOptions := "-m64 -t64 -x1"

scalaVersion := "2.12.8"

startAddress := 0x1000
