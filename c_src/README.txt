This is the DEBIE-1 DPU SW, version c, for benchmark use

This software is provided by courtesy of its owner, Space Systems
Finland Ltd (SSF). SSF allows you to use this version of the DEBIE-1 DPU
software for the specific purpose and under the specific conditions set
forth in the Terms Of Use document enclosed with or attached to this
software. In particular, the software remains the property of SSF and
you must not distribute the software to third parties without written
and signed authorization from SSF.

The Terms Of Use document should be in a file called terms_of_use.pdf;
if you do not find that file near this README file, ask for it from the
source from where you got this README. Do not use the SW before you have
read the Terms Of Use. Note that the Terms Of Use (section 8) let SSF
terminate your use of this software at any time and for any reason.

This version of the DEBIE-1 DPU SW has been modified by Tidorum Ltd to
adapt it for use as a benchmark (in particular in the WCET Tool
Challenge). SSF has granted Tidorum the right to distribute this version
as a benchmark for embedded programming, but this right does not extend
to recipients, who are not allowed to distribute the SW further, without
permission from SSF (see the attached Terms Of Use).

For more information about the DEBIE-1 benchmark, please refer to
the WCC'08 Wiki at (login needed):

http://www.mrtc.mdh.se/projects/WCC08/doku.php?id=bench:debie1

(For the WCC'08 Wiki am earlier and slightly different version of the
Terms Of Use applied, a version limited to WCET analysis benchmarking.
For users who receive the DEBIE-1 benchmark after 2009-02-01, the
current, attached Terms Of Use apply.)


CONTENTS

The folder "code" contains the 'C' source-code of the debie1 benchmark.
The main "code" folder contains the target-independent (portable)
parts of the source-code, without the test-cases, too.

The subfolder code/harness contains the source-code parts that
simulate the DEBIE1 peripherals and run the test-cases. This code
is portable, too, but it is not part of the original DEBIE1 flight
software. The main module is harness.c; the rest of the files
in code/harness are headers that introduce the harness interface
into the DEBIE1 header files. For example, the header file
code/harness/target_ad_conv.h extends code/ad_conv.h and
makes the software use the simulated A/D conversion functions
in harness.c.

There are subfolders for each target and cross-compiler as follows:

code/intel/linux

   Compilation for an Intel/Linux system using the native
   GCC compiler. The compiled program (debie1, not included in
   this archive) runs the test cases currently written in the
   harness module. The code should be fully portable to other
   32-bit computers, eg Intel/Windows, but the build script may
   need adjustments.

code/arm7-lpc2138-mam/gcc-if07

   Cross-compilation for the NXP LPC2138 using the GNU ARM
   compiler that comes with the IF-DEV-LPC development kit,
   updated to Build 118 from www.isystem.com. The build script
   needs files (start-up and run-time code) from the folder
   arm7-lpc2138-mam/gcc-if07, see below.

   The LPC2138 is configured with MAM enabled (mode 2) and
   the PLL set to generate a 60 MHz clock frequency.

   The compiled program (debie1.elf, included in this archive)
   repeatedly runs the test cases in harness.c, and blinks the
   LED now and then (about every 25 sec).

Each target subfolder contains the following target- and
compiler-specific files:

   keyword.h

      Type and macro definitions for this target and cross-compiler.

   problems.h

      Macro definitions for marking the start and end of specific
      WCC'08 "analysis problems" in the test part of harness.c.
      May need other header files, eg. for RapiTime.

   target.c

      Target-specific test-harness operations.

   target_xxx.h

      Target-specific parts of the DEBIE-1 header files, if it
      is necessary to override those in code/harness.

   build.sh or build-bat

      A script to compile and link the benchmark.

The folder "arm7-lpc218-mam/gcc-if07" (note, this is not the one under
"code") contains files needed by "code/arm7-lpc2183-mam/gcc-if07": a setup
script, a linker script, and run-time support files: crt0.s, crt_asyst.c,
intvec.s, cpulib.c.

To generate the binary executable for a given target and cross-compiler,
"cd" to the corresponding subfolder of "code", check that the path
definitions in the build script (build.sh or build.bat and the
corresponding setup.sh or setup.bat) are suitable for your workstation, and
execute the build script.

For running and debugging the program on the iF-DEV-LPC, the winIDEA
IDE from iSYSTEM is convenient.

--
Tidorum/NH
2009-02-02
