#!/bin/sh
# docker-entrypoint.sh

set -e

export SILO_VERSION="$(cat resources/VERSION.txt)"

_print_header() {
    header_text="$(cat resources/BANNER.txt)

Environment:
$(env -0 | sort -z | tr '\0  ' '\n' | grep SILO | awk '{print "  ", $0}')"
    printf '%s\n' "$header_text"
}

_print_help_input() {
    help_text_input="
Error: The input directory $SILO_INPUT is empty.
Use the volume flag to bind a directory with the SILO
input files from the host to the conatiner.

Exiting."
    printf '%s\n' "$help_text_input"
}

#_print_help_output() {
#    help_text_output="
#Error: The output directory $SILO_OUTPUT is not
#empty. Mount an empty folder or pass the environment
#variable SILO_OUTPUT_OVERWRITE=true to the container.
#
#Exiting."
#    printf '%s\n' "$help_text_output"
#}

_check_input_directory() {
    if [ -d "$SILO_INPUT" ] && files=$(ls -qAH -- "$SILO_INPUT") && [ -z "$files" ]; then
        _print_help_input
        exit 1
    fi
}

#_check_output_directory() {
#    if [ -d "$SILO_OUTPUT" ] && files=$(ls -qAH -- "$SILO_OUTPUT") && [ -z "$files" ]; then
#        :
#    else
#        if [ $SILO_OUTPUT_OVERWRITE ]; then
#            printf '\n%s\n' "Note: Overwriting the (not empty) output directory."
#            rm -rf $SILO_OUTPUT/*
#        else
#            _print_help_output
#            exit 1
#        fi
#    fi
#}

_print_header
_check_input_directory
#_check_output_directory
printf '%s\n' ""
exec "$@"
