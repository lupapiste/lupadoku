#!/bin/bash

source ~/.bash_profile

set -eu

echo "Running JVM tests"
lein test

echo "Making uberjar"
lein uberjar
