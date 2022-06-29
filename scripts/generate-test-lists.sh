#!/bin/bash

#
# Copyright 2022 OICR and UCSC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#           http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This file generates a list of test files for each profile
# This file ensures that all tests are run by having a catch all statement at the end

set -o errexit
set -o nounset
set -o pipefail

# This prefix ensure that the test lists do not interfere with anything else
BASE_PREFIX=temp/test-lists

# This function changes file names in $FILE_TO_CHANGE to fully qualified class paths. For example,
# ./dockstore-integration-testing/src/test/java/io/dockstore/client/cli/BitBucketGitHubWorkflowIT.java
# becomes,
# io.dockstore.client.cli.BitBucketGitHubWorkflowIT
function make_file_names_fully_qualified_class_paths {
  sed -i 's+.*java/++g; s+/+.+g; s+\.java$++g' "$FILE_TO_CHANGE"
}

# The temporary file used in the following function
TEMP=temp_for_categorising_tests.txt

# This functions consumes $IT_LIST and determines which of those files have the listed Junit category (CATEGORY) in them
# such as BitBucketTest.class
# Note: Does not acknowledge comments
function generate_test_list {
  # Reset or create temp file
  grep -l "$CATEGORY" $(cat "$PREFIX"/"$IT_LIST") > "$PREFIX"/"$OUTPUT_TEST_FILE"
}


#####################################
# Get list of all Integration Tests #
#####################################

# We are setting the Remaining test file to first be non-confidential tests, we can

# Modify prefix for integration tests
PREFIX="$BASE_PREFIX""/IT"
mkdir -p "$PREFIX"


IT_LIST=all.txt

# Using same wild card patterns the Failsafe Plugin uses
# Get all
# https://maven.apache.org/surefire/maven-failsafe-plugin/examples/inclusion-exclusion.html
find . -name "*IT\.java" -or -name "IT*\.java" -or -name "*ITCase\.java"  > "$PREFIX"/all.txt
#FILE_TO_CHANGE="$PREFIX"/all.txt
#make_file_names_fully_qualified_class_paths


## Get Toil ITs
#CATEGORY="ToilCompatibleTest.class\|ToilOnlyTest.class"
#OUTPUT_TEST_FILE=toil.txt
#generate_test_list
#FILE_TO_CHANGE="$PREFIX"/"$OUTPUT_TEST_FILE"
#make_file_names_fully_qualified_class_paths
#
## Get confidential tests
#CATEGORY="ConfidentialTest.class\|BaseIT"
#OUTPUT_TEST_FILE=confidential.txt
#generate_test_list
#
#IT_LIST=confidential.txt
#
## Get confidential tool tests
#CATEGORY="ToolTest.class"
#OUTPUT_TEST_FILE=tool_confidential.txt
#generate_test_list
#
#
#
## Get confidential all tests that are not tool
#OUTPUT_TEST_FILE=workflow_confidential.txt
#grep -v -x -f "$PREFIX"/tool_confidential.txt "$PREFIX"/confidential.txt > "$PREFIX"/"$OUTPUT_TEST_FILE"
#
#
## Get non-confidential tests
#grep -v -x -f "$PREFIX"/confidential.txt "$PREFIX"/all.txt > "$PREFIX"/non-confidential.txt




