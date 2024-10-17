# 1 Billion Row Challenge in Clojure

This is my attempt to solve a popular [1brc challenge](https://1brc.dev/) and push Clojure to its (my) limits.

It used to be 100% Clojure solution but gradually more and more Java code was introduced.
I might come back in the future and go the other way around rewriting some of Java code in Clojure
trying to keep the performance up to its current levels.
However, for now I am more than satisfied with the project's current state.

I did my best to follow the best practices keeping code clean, modular and high-level enough while still preserving its performance.
Still, due to the nature of the challenge there might be a few cryptic hard-to-follow places.

---

Official webpage of the challenge: <https://1brc.dev/> \
Official repo for Java solutions: <https://github.com/gunnarmorling/1brc>

## 📖 Table of Contents

- [1 Billion Row Challenge in Clojure](#1-billion-row-challenge-in-clojure)
  - [📖 Table of Contents](#-table-of-contents)
  - [🏃 How to compile and run](#-how-to-compile-and-run)
    - [Generate "measurements.txt" file](#generate-measurementstxt-file)
    - [Run method #1: Uberjar (standard Java)](#run-method-1-uberjar-standard-java)
      - [Step 1. Create uberjar with Leiningen](#step-1-create-uberjar-with-leiningen)
      - [Step 2. Run uberjar with Java](#step-2-run-uberjar-with-java)
    - [Run method #2: GraalVM (native executable)](#run-method-2-graalvm-native-executable)
      - [Step 1. Install GraalVM](#step-1-install-graalvm)
      - [Step 2. Create native image with Leiningen](#step-2-create-native-image-with-leiningen)
      - [Step 3. Run native image](#step-3-run-native-image)
  - [💻 Testing environment](#-testing-environment)
  - [🎯 Results](#-results)
    - [Benchmark command for baseline Java implementation from official repo](#benchmark-command-for-baseline-java-implementation-from-official-repo)
    - [Benchmark command for uberjar](#benchmark-command-for-uberjar)
    - [Benchmark command for GraalVM](#benchmark-command-for-graalvm)
    - [Results table](#results-table)
  - [🚀 Implemented optimizations](#-implemented-optimizations)
  - [📉 Failed optimizations](#-failed-optimizations)
  - [🧑‍🔬 Benchmarking and profiling tools](#-benchmarking-and-profiling-tools)
  - [📝 TODOs](#-todos)

## 🏃 How to compile and run

***Please make sure that you have JDK installed on your system.\
During development, I used Java 21 from [Adoptium](https://adoptium.net/). Feel free to install any Java starting from version 21.***

---

This project relies heavily on [Leiningen](https://leiningen.org/) build and project management tool.
All build processes both for Clojure and Java are done with it.

Despite that, it is **not required** to have Leiningen installed on your system. All commands given in this document
use portable Leiningen scripts included in this repo and allow you to use Leiningen without installing it on your system,
pretty much like Maven Wrapper `mvnw`.

---

- ***All coding, testing and benchmarking were done solely on a Windows machine. Therefore, I have not properly tested Linux commands.***
- ***All commands provided here are expected to be run from the project's root directory.***

---

### Generate "measurements.txt" file

For the sake of simplicity and convenience, I have pre-built and included a JAR from the official Java repo.\
*Hopefully there won't be any license issues with the original project.*

The following command will generate `measurements.txt` file containing 1 billion rows and place it in
the project's root. The generation process might take a few minutes to finish.

```shell
java --class-path vendor/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc.CreateMeasurements 1000000000
```

### Run method #1: Uberjar (standard Java)

#### Step 1. Create uberjar with Leiningen

On Windows with PowerShell:

```powershell
$env:LEIN_HOME = (Get-Location).Path + "\.lein"; ./lein.bat uberjar
```

On Linux with shell:

```shell
LEIN_HOME=.lein ./lein uberjar
```

#### Step 2. Run uberjar with Java

```console
java -jar target\onebrc-0.1.0-SNAPSHOT-standalone.jar
```

### Run method #2: GraalVM (native executable)

#### Step 1. Install GraalVM

1. Get GraalVM from its official website here: <https://www.graalvm.org/>\
   *The repo uses `GraalVM JDK 21.0.4+8.1`, so you should be totally good with any distribution starting from Java 21*
2. Set `GRAALVM_HOME` environment variable

Here is a more detailed guide: <https://graalvm.github.io/native-build-tools/0.9.6/graalvm-setup.html>\
However, keep in mind that this guide is for older versions, when `native-image` required a manual
installation. Nowadays, you can completely skip this step as it already comes preinstalled.

#### Step 2. Create native image with Leiningen

[This amazing plugin](https://github.com/taylorwood/lein-native-image) for Leiningen allows us to easily build
a native image. Everything is already pre-configured, all you need is just run a single-line command and
wait a bit for it to finish as it takes longer than uberjar build method.

On Windows with PowerShell:

```powershell
$env:LEIN_HOME = (Get-Location).Path + "\.lein"; ./lein.bat native-image
```

On Linux with shell:

```shell
LEIN_HOME=.lein ./lein native-image
```

#### Step 3. Run native image

On Windows with PowerShell:

```console
./target/onebrc-0.1.0-SNAPSHOT.exe
```

On Linux with shell:

```shell
./target/onebrc-0.1.0-SNAPSHOT
```

## 💻 Testing environment

- ThinkPad E14 Gen.4 (AMD)
  - Windows 11 Home (23H2)
  - AMD Ryzen 5 5625U (6 cores / 12 threads)
  - 16 GB RAM (DDR4-3200)
  - Micron 2450 512GB MTFDKCD512TFK (PCIe Gen4 NVMe SSD)
- [OpenJDK Runtime Environment Temurin-21.0.4+7 (build 21.0.4+7-LTS)](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.4%2B7)
- GraalVM JDK 21.0.4+8.1

## 🎯 Results

*All final benchmarks were done on my personal everyday laptop ThinkPad E14 with
[`hyperfine`](https://github.com/sharkdp/hyperfine) utility the same way as described in the official Java repo here:
[Evaluating Results](https://github.com/gunnarmorling/1brc#evaluating-results).*

> The `hyperfine` program is used for measuring execution times of the launch scripts of all entries, i.e. end-to-end times are measured.
Each contender is run five times in a row.
The slowest and the fastest runs are discarded.
The mean value of the remaining three runs is the result for that contender and will be added to the results table above.
The exact same *measurements.txt* file is used for evaluating all contenders.
See the script *evaluate.sh* for the exact implementation of the evaluation steps.

---

In the official description it is said that they run the program from a RAM disk, which completely removes disk
latency from the equation. During all my benchmarks, I ran everything as is, keeping it simple. What is more, I don't
have that much RAM to dump a 13 GB text file. So no RAM disk for me.
Lastly, I completely ignored number of cores they used for benchmarking and used all available cores of my machine.

> Programs are run from  a RAM disk (i.o. the IO overhead for loading the file from disk is not relevant), using 8 cores of the machine.

---

*All benchmark commands were derived and extracted from [`evaluate.sh`](https://github.com/gunnarmorling/1brc/blob/main/evaluate.sh) script
that can be found in the official Java repo.*

### Benchmark command for baseline Java implementation from official repo

```console
hyperfine --warmup 0 --runs 10 --export-json ./bench-baseline-timing.json "java --class-path vendor/average-1.0.0-SNAPSHOT.jar dev.morling.onebrc.CalculateAverage_baseline"
```

### Benchmark command for uberjar

```console
hyperfine --warmup 0 --runs 10 --export-json ./bench-uberjar-timing.json "java -jar ./target/onebrc-0.1.0-SNAPSHOT-standalone.jar"
```

### Benchmark command for GraalVM

On Windows with PowerShell:

```powershell
hyperfine --warmup 0 --runs 10 --export-json ./bench-graalvm-timing.json ".\target\onebrc-0.1.0-SNAPSHOT.exe"
```

On Linux with shell:

```shell
hyperfine --warmup 0 --runs 10 --export-json ./bench-graalvm-timing.json "./target/onebrc-0.1.0-SNAPSHOT"
```

### Results table

| Run         | Slowest (s.ms) | Fastest (s.ms) | Average (s.ms) | Performance improvement (times baseline) |
| ----------- | -------------: | -------------: | -------------: | :--------------------------------------: |
| Baseline    |         168.67 |         149.50 |         155.91 |                    1                     |
| Uberjar     |          13.49 |          11.67 |          12.84 |                    12                    |
| **GraalVM** |      **11.48** |      **10.24** |      **11.04** |                  **14**                  |

GraalVM executable shows better performance compared to uberjar. The difference might seem not so sufficient,
only around 1-2 seconds. However, it is approximately 15% of free performance boost.
Not that bad, considering that all you have to do is simply recompile the code with GraalVM as-is and provide a few extra
compilation flags (can be found in `project.clj` file).

## 🚀 Implemented optimizations

- Parallel processing with [`pmap`](https://clojuredocs.org/clojure.core/pmap)
- Split and process file in small chunks
- Memory-mapped chunks ([`MappedByteBuffer`](https://docs.oracle.com/javase/8/docs/api/java/nio/MappedByteBuffer.html))
- Mutable aggregator objects for each chunk
- Work with strings as raw bytes and delay string decoding
- Custom hashmap optimized for raw byte strings
- Cache hashmap key hashes
- Branchless programming techniques
- Unroll temperature parsing loop
- Parse station names in chunks of 8 and 4 bytes
- Look for separators with bitwise operators and masks
- Heavy utilization of [`ByteBuffer`](https://docs.oracle.com/javase/8/docs/api/java/nio/ByteBuffer.html)
- Reduce memory allocation by reusing the same `ByteBuffer` within a thread to parse station names
- Compilation with [GraalVM](https://www.graalvm.org/)

## 📉 Failed optimizations

- Overuse of branchless techniques
- Fully branchless temperature parser
- SIMD with Java Vector API
- Hashmap with double hashing to reduce collisions
- Any simple hashing algorithm other than `djb2`
- Calculate `djb2` hash in chunks of 8 and 4 bytes

## 🧑‍🔬 Benchmarking and profiling tools

- [Criterium](https://github.com/hugoduncan/criterium)
- [VisualVM](https://visualvm.github.io/)
- [Tufte](https://github.com/taoensso/tufte)

## 📝 TODOs

- [x] Reimplement `ChunkedFile` in Clojure \
  *No noticeable performance hit expected*
- [ ] Reimplement `BitwiseHelpers` in Clojure \
  *No noticeable performance hit expected*
- [ ] ~~Reimplement `ChunkReader` in Clojure~~ \
  *Potential performance degradation. Proceed carefully* \
  **As expected, didn't work out well. 2-3 times performance hit. Keeping `ChunkReader` as is**
- [ ] [Optional] Reimplement `Result` in Clojure \
  *Might be difficult or non-obvious because of mutable nature of `Result` class*
- [ ] [Optional] Challenge myself implementing a hashmap in Clojure
