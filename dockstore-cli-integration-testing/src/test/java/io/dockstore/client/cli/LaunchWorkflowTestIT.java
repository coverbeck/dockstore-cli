/*
 *    Copyright 2022 OICR and UCSC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */


package io.dockstore.client.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

import com.google.gson.Gson;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LaunchWorkflowTestIT  {

    //create tests that will call client.checkEntryFile for workflow launch with different files and descriptor

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };


    @Test
    public void runWorkflowConvert() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("smcFusionQuant-INTEGRATE-workflow.cwl"));

        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("convert");
        args.add("cwl2json");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());

        runClientCommand(args);
        final String log = systemOutRule.getLog();
        Gson gson = new Gson();
        final Map<String, Map<String, Object>> map = gson.fromJson(log, Map.class);
        assertEquals(4, map.size());
        assertTrue(
            map.containsKey("TUMOR_FASTQ_1") && map.containsKey("TUMOR_FASTQ_2") && map.containsKey("index") && map.containsKey("OUTPUT"));
    }

    @Test
    @Ignore("Detection code is not robust enough for biowardrobe wdl using --local-entry")
    public void toolAsWorkflow() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
            () -> assertTrue("Out should suggest to run as tool instead", systemErrRule.getLog().contains("Expected a workflow but the")));
        runClientCommand(args);
    }

    @Test
    public void malJsonWorkflowWdlLocal() {
        //checks if json input has broken syntax for workflows

        File helloWdl = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("testInvalidJSON.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(helloWdl.getAbsolutePath());
        args.add("--json");
        args.add(jsonFile.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message",
            systemErrRule.getLog().contains("Could not launch, syntax error in json file: " + jsonFile)));
        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);
    }

    @Test
    public void provisionInputWithPathSpaces() {
        //Tests if file provisioning can handle a json parameter that specifies a file path containing spaces
        File helloWDL = new File(ResourceHelpers.resourceFilePath("helloSpaces.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("helloSpaces.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(helloWDL.getAbsolutePath());
        args.add("--json");
        args.add(helloJSON.getPath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));
    }

    @Test
    public void missingTestParameterFileWDLFailure() {
        // Run a workflow without specifying test parameter files, defer errors to the
        // Cromwell workflow engine.
        File file = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        exit.expectSystemExitWithStatus(IO_ERROR);
        runClientCommandConfig(args, config);
        assertTrue("This workflow cannot run without test files, it should raise an exception from the workflow engine",
            systemOutRule.getLog().contains("problems running command:"));
    }

    @Test
    public void missingTestParameterFileWDLSuccess() {
        // Run a workflow without specifying test parameter files, defer errors to the
        // Cromwell workflow engine.
        File file = new File(ResourceHelpers.resourceFilePath("no-input-echo.wdl"));
        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        runClientCommandConfig(args, config);
        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));

    }

    @Test
    public void missingTestParameterFileCWL() {
        // Tests that the CWLrunner is able to handle workflows that do not specify
        // test parameter files.
        File file = new File(ResourceHelpers.resourceFilePath("no-input-echo.cwl"));
        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        exit.expectSystemExitWithStatus(1);
        runClientCommandConfig(args, config);
        // FIXME: The CWLTool should be able to execute this workflow, there is an
        //        issue with how outputs are handled.
        //        https://github.com/dockstore/dockstore/issues/4922
        if (false) {
            assertTrue("CWLTool should be able to run this workflow without any problems",
                systemOutRule.getLog().contains("[job no-input-echo.cwl] completed success"));
        }
    }


    @Test
    public void duplicateTestParameterFile() {
        // Return client failure if both --json and --yaml are passed
        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("helloSpaces.json"));
        File helloYAML = new File(ResourceHelpers.resourceFilePath("hello.yaml"));

        ArrayList<String> args = new ArrayList<>();
        args.add("workflow");
        args.add("launch");
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(helloJSON.getPath());
        args.add("--yaml");
        args.add(helloYAML.getPath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        exit.expectSystemExitWithStatus(CLIENT_ERROR);
        exit.checkAssertionAfterwards(() -> assertTrue("Client error should be returned",
            systemErrRule.getLog().contains(AbstractEntryClient.MULTIPLE_TEST_FILE_ERROR_MESSAGE)));
        runClientCommandConfig(args, config);
    }

    private void runClientCommand(ArrayList<String> args) {
        args.add(0, ResourceHelpers.resourceFilePath("config"));
        args.add(0, "--config");
        args.add(0, "--script");
        Client.main(args.toArray(new String[0]));
    }

    private void runClientCommandConfig(ArrayList<String> args, File config) {
        //used to run client with a specified config file
        args.add(0, config.getPath());
        args.add(0, "--config");
        args.add(0, "--script");
        Client.main(args.toArray(new String[0]));
    }


}
