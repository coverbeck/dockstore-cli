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
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LaunchToolTestIT {

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
    public void runToolWithDirectoriesConversion() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));

        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("convert");
        args.add("cwl2json");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());

        runClientCommand(args);
        final String log = systemOutRule.getLog();
        Gson gson = new Gson();
        final Map<String, Map<String, Object>> map = gson.fromJson(log, Map.class);
        assertEquals(2, map.size());
        assertEquals("Directory", map.get("indir").get("class"));
    }

    @Test
    public void workflowAsTool() {
        File file = new File(ResourceHelpers.resourceFilePath("noInput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
            () -> assertTrue("Out should suggest to run as workflow instead", systemErrRule.getLog().contains("Expected a tool but the")));
        runClientCommand(args);
    }

    @Test
    public void cwlNoOutput() {
        File file = new File(ResourceHelpers.resourceFilePath("noOutput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message and exit",
            systemErrRule.getLog().contains("Required fields that are missing from CWL file : 'outputs'")));
        runClientCommand(args);
    }

    @Test
    public void cwlIncompleteOutput() {
        File file = new File(ResourceHelpers.resourceFilePath("incompleteOutput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        runClientCommand(args);

        assertTrue("output should include an error message", systemErrRule.getLog().contains("\"outputs\" section is not valid"));
    }

    @Test
    public void cwlIdContainsNonWord() {
        File file = new File(ResourceHelpers.resourceFilePath("idNonWord.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should have started provisioning",
            systemOutRule.getLog().contains("Provisioning your input files to your local machine")));
        runClientCommand(args);
    }

    @Test
    public void cwlMissingIdParameters() {
        File file = new File(ResourceHelpers.resourceFilePath("missingIdParameters.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("launch");
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        runClientCommand(args);

        assertTrue("output should include an error message",
            systemErrRule.getLog().contains("while parsing a block collection"));
    }

    @Test
    public void cwl2jsonNoOutput() {
        exit.expectSystemExit();
        File file = new File(ResourceHelpers.resourceFilePath("noOutput.cwl"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
        args.add("convert");
        args.add("cwl2json");
        args.add("--cwl");
        args.add(file.getAbsolutePath());

        runClientCommand(args);
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message",
            systemErrRule.getLog().contains("\"outputs section is not valid\"")));
    }

    @Test
    public void malJsonToolWdlLocal() {
        //checks if json input has broken syntax for tools

        File helloWdl = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("testInvalidJSON.json"));
        ArrayList<String> args = new ArrayList<>();
        args.add("tool");
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
