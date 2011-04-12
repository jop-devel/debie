#!/bin/sh
#
# Script to compile tp_debie1_tr on the host Intel/Linux system.
#
# Any arguments go into the CCOPT (eg. -Wpadded).
#
# Copyright (c) 2008 Tidorum Ltd.
#
# $Id: build.sh,v 1.4 2008/04/04 06:21:49 niklas Exp $


# Set tpd to the Test Program Directory:
tpd=../..

# Set hnd to the "harness" directory:

hnd=${tpd}/harness

# Native gcc and options:

export CC="gcc"
export CCOPT="-g -O2 -I. -I${hnd} -Wall $*"
export LD="gcc"
export LDOPT=

${CC} ${CCOPT} -c ${tpd}/class.c
${CC} ${CCOPT} -c ${tpd}/classtab.c
${CC} ${CCOPT} -c ${tpd}/debie.c
${CC} ${CCOPT} -c -I${tpd} ${hnd}/harness.c -DTRACE_HARNESS
${CC} ${CCOPT} -c ${tpd}/health.c
${CC} ${CCOPT} -c ${tpd}/hw_if.c
${CC} ${CCOPT} -c ${tpd}/measure.c
${CC} ${CCOPT} -c -I${tpd} target.c -DTRACE_TARGET
${CC} ${CCOPT} -c ${tpd}/tc_hand.c
${CC} ${CCOPT} -c ${tpd}/telem.c


${CC} ${LDOPT}           \
    -o tp_debie1_tr      \
    class.o              \
    classtab.o           \
    debie.o              \
    harness.o            \
    health.o             \
    hw_if.o              \
    measure.o            \
    target.o             \
    tc_hand.o            \
    telem.o

