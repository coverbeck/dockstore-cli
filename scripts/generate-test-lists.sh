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

# This functions consumes $REMAINING_TEST_FILE and determines which of those files have the listed Junit category (CATEGORY) in them
# such as BitBucketTest.class, it then removes those test from $REMAINING_TEST_FILE and adds them to $OUTPUT_TEST_FILE
# Note: Does not acknowledge comments
function generate_test_list {
  # Reset or create temp file
  : > "$PREFIX"/"$TEMP"
  grep -l "$CATEGORY" $(cat "$PREFIX"/"$REMAINING_TEST_FILE") > "$PREFIX"/"$OUTPUT_TEST_FILE"
  grep -v -x -f "$PREFIX"/"$OUTPUT_TEST_FILE" "$PREFIX"/"$REMAINING_TEST_FILE" > "$PREFIX"/"$TEMP"
  cp "$PREFIX"/"$TEMP" "$PREFIX"/"$REMAINING_TEST_FILE"

  FILE_TO_CHANGE="$PREFIX"/"$OUTPUT_TEST_FILE"
  make_file_names_fully_qualified_class_paths
}


#####################################
# Get list of all Integration Tests #
#####################################

# We are setting the Remaining test file to first be non-confidential tests, we can

# Modify prefix for integration tests
PREFIX="$BASE_PREFIX""/IT"
mkdir -p "$PREFIX"


REMAINING_TEST_FILE=non-confidential.txt

# Using same wild card patterns the Failsafe Plugin uses
# https://maven.apache.org/surefire/maven-failsafe-plugin/examples/inclusion-exclusion.html
find . -name "*IT\.java" -or -name "IT*\.java" -or -name "*ITCase\.java" > "$PREFIX"/"$REMAINING_TEST_FILE"


# Get Toil ITs
CATEGORY=ToilCompatibleTest.class\|ToilOnlyTest.class
OUTPUT_TEST_FILE=toil.txt
generate_test_list


# Get Singularity Tests
CATEGORY=SingularityTest.class
OUTPUT_TEST_FILE=singularity.txt
generate_test_list

# Get Language Parsing ITs
CATEGORY=LanguageParsingTest.class
OUTPUT_TEST_FILE=language-parsing.txt
generate_test_list

# Get ALL Confidential Tests
CATEGORY=ConfidentialTest.class
OUTPUT_TEST_FILE=workflow_confidential.txt
generate_test_list

# Convert all non-confidential test file names to fully qualified class paths
FILE_TO_CHANGE="$PREFIX"/"$REMAINING_TEST_FILE"
make_file_names_fully_qualified_class_paths

# Now set remaining test file to be confidential_workflow
REMAINING_TEST_FILE=workflow_confidential.txt

CATEGORY=ToolTest.class
OUTPUT_TEST_FILE=tool_confidential.txt
generate_test_list

# Convert all confidential workflow test file names to fully qualified class paths
FILE_TO_CHANGE="$PREFIX"/"$REMAINING_TEST_FILE"
make_file_names_fully_qualified_class_paths


