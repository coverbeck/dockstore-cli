/*
 *    Copyright 2017 OICR
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Assert;
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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class LaunchTestIT {
    public static final long LAST_MODIFIED_TIME_100 = 100L;
    public static final long LAST_MODIFIED_TIME_1000 = 1000L;

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
    public void wdlCorrect() {
        //Test when content and extension are wdl  --> no need descriptor
        File helloWDL = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(helloJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(helloWDL.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));
    }


    @Test
    public void wdlMetadataNoopPluginTest() {
        //Test when content and extension are wdl  --> no need descriptor
        File helloWDL = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("hello.metadata.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(helloJSON.getAbsolutePath());
        args.add("--wdl-output-target");
        args.add("noop://nowhere.test");

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withTestPlugin"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(helloWDL.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));
        assertTrue("output should include a noop plugin run with metadata", systemOutRule.getLog().contains("really cool metadata"));
    }


    @Test
    public void wdlWorkflowCorrectFlags() {
        wdlEntryCorrectFlags("workflow");
    }

    @Test
    public void wdlToolCorrectFlags() {
        wdlEntryCorrectFlags("tool");
    }

    private void wdlEntryCorrectFlags(String entryType) {
        File yamlTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.yaml"));
        File jsonTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.json"));

        List<String> yamlFileWithJSONFlag = getLaunchStringList(entryType);
        yamlFileWithJSONFlag.add("--json");
        yamlFileWithJSONFlag.add(yamlTestParameterFile.getAbsolutePath());

        List<String> yamlFileWithYAMLFlag = getLaunchStringList(entryType);
        yamlFileWithYAMLFlag.add("--yaml");
        yamlFileWithYAMLFlag.add(yamlTestParameterFile.getAbsolutePath());

        List<String> jsonFileWithJSONFlag = getLaunchStringList(entryType);
        jsonFileWithJSONFlag.add("--json");
        jsonFileWithJSONFlag.add(jsonTestParameterFile.getAbsolutePath());

        List<String> jsonFileWithYAMLFlag = getLaunchStringList(entryType);
        jsonFileWithYAMLFlag.add("--yaml");
        jsonFileWithYAMLFlag.add(jsonTestParameterFile.getAbsolutePath());

        Client.main(yamlFileWithJSONFlag.toArray(new String[0]));
        Client.main(yamlFileWithYAMLFlag.toArray(new String[0]));
        Client.main(jsonFileWithJSONFlag.toArray(new String[0]));
        Client.main(jsonFileWithYAMLFlag.toArray(new String[0]));
    }

    @Test
    public void yamlAndJsonWorkflowCorrect() {
        yamlAndJsonEntryCorrect("workflow");
    }

    @Test
    public void yamlAndJsonToolCorrect() {
        yamlAndJsonEntryCorrect("tool");
    }

    private void yamlAndJsonEntryCorrect(String entryType) {
        File yamlTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.yaml"));
        File jsonTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.json"));

        List<String> args = getLaunchStringList(entryType);
        args.add("--yaml");
        args.add(yamlTestParameterFile.getAbsolutePath());
        args.add("--json");
        args.add(jsonTestParameterFile.getAbsolutePath());
        exit.expectSystemExitWithStatus(CLIENT_ERROR);
        exit.checkAssertionAfterwards(() -> Assert.assertTrue(systemErrRule.getLog().contains(AbstractEntryClient.MULTIPLE_TEST_FILE_ERROR_MESSAGE)));
        Client.main(args.toArray(new String[0]));
    }

    @Test
    public void testMaliciousParameterYaml() {
        File yamlTestParameterFile = new File(ResourceHelpers.resourceFilePath("malicious.input.yaml"));

        List<String> args = getLaunchStringList("workflow");
        args.add("--yaml");
        args.add(yamlTestParameterFile.getAbsolutePath());
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> Assert.assertTrue(systemErrRule.getLog().contains("could not determine a constructor for the tag")));
        Client.main(args.toArray(new String[0]));
    }

    private List<String> getLaunchStringList(String entryType) {
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        final List<String> strings = new ArrayList<>();
        strings.add("--script");
        strings.add("--config");
        strings.add(ResourceHelpers.resourceFilePath("config"));
        strings.add(entryType);
        strings.add("launch");
        strings.add("--local-entry");
        strings.add(descriptorFile.getAbsolutePath());

        return strings;
    }



    @Test
    public void randomExtWdl() {
        //Test when content is random, but ext = wdl
        File file = new File(ResourceHelpers.resourceFilePath("random.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }


    @Test
    public void wdlNoExt() {
        //Test when content = wdl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("wdlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run",
            systemOutRule.getLog().contains("This is a WDL file.. Please put an extension to the entry file name."));

    }

    @Test
    public void randomNoExt() {
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("random"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message of invalid file", systemErrRule.getLog()
            .contains("Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name.")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void randomWithExt() {
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("hello.txt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message of invalid file", systemErrRule.getLog()
            .contains("Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name.")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void wdlNoTask() {
        //Test when content is missing 'task'

        File file = new File(ResourceHelpers.resourceFilePath("noTask.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message and exit",
            systemErrRule.getLog().contains("Required fields that are missing from WDL file : 'task'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void wdlNoCommand() {
        //Test when content is missing 'command'

        File file = new File(ResourceHelpers.resourceFilePath("noCommand.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message and exit",
            systemErrRule.getLog().contains("Required fields that are missing from WDL file : 'command'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void wdlNoWfCall() {
        //Test when content is missing 'workflow' and 'call'

        File file = new File(ResourceHelpers.resourceFilePath("noWfCall.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include an error message and exit",
            systemErrRule.getLog().contains("Required fields that are missing from WDL file : 'workflow' 'call'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }


    @Test
    public void entry2jsonNoVersion() throws IOException {
        /*
         * Make a runtime JSON template for input to the workflow
         * but don't provide a version at the end of the entry
         * E.g dockstore workflow convert entry2json --entry quay.io/collaboratory/dockstore-tool-linux-sort
         * Dockstore will try to use the 'master' version, however the 'master' version
         * is not valid so Dockstore should print an error message and exit
         * */
        WorkflowVersion aWorkflowVersion1 = new WorkflowVersion();
        aWorkflowVersion1.setName("master");
        aWorkflowVersion1.setValid(false);
        aWorkflowVersion1.setLastModified(LAST_MODIFIED_TIME_100);

        List<WorkflowVersion> listWorkflowVersions = new ArrayList<>();
        listWorkflowVersions.add(aWorkflowVersion1);

        Workflow workflow = new Workflow();
        workflow.setWorkflowVersions(listWorkflowVersions);
        workflow.setLastModified(1);

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        doReturn(workflow).when(api).getPublishedWorkflowByPath(anyString(), eq(WorkflowSubClass.BIOWORKFLOW.toString()), eq("versions"), eq(null));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include error message",
            systemErrRule.getLog().contains("Cannot use workflow version 'master'")));

        workflowClient.downloadTargetEntry("quay.io/collaboratory/dockstore-tool-linux-sort", ToolDescriptor.TypeEnum.WDL, false);
    }

    @Test
    public void entry2jsonBadVersion() throws IOException {
        /*
         * Make a runtime JSON template for input to the workflow
         * but provide a non existent version at the end of the entry
         * E.g dockstore workflow convert entry2json --entry quay.io/collaboratory/dockstore-tool-linux-sort:2.0.0
         * Dockstore will try to use the last modified version (1.0.0) and print an explanation message.
         * The last modified version is not valid so Dockstore should print an error message and exit
         * */

        WorkflowVersion aWorkflowVersion1 = new WorkflowVersion();
        aWorkflowVersion1.setName("1.0.0");
        aWorkflowVersion1.setValid(false);
        aWorkflowVersion1.setLastModified(LAST_MODIFIED_TIME_1000);

        List<WorkflowVersion> listWorkflowVersions = new ArrayList<>();
        listWorkflowVersions.add(aWorkflowVersion1);

        Workflow workflow = new Workflow();
        workflow.setWorkflowVersions(listWorkflowVersions);
        workflow.setLastModified(1);

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        doReturn(workflow).when(api).getPublishedWorkflowByPath(anyString(), eq(WorkflowSubClass.BIOWORKFLOW.toString()), eq("versions"), eq(null));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue("output should include error messages",
            (systemOutRule.getLog().contains("Could not locate workflow with version '2.0.0'") && systemErrRule.getLog()
                .contains("Cannot use workflow version '1.0.0'"))));

        workflowClient.downloadTargetEntry("quay.io/collaboratory/dockstore-tool-linux-sort:2.0.0", ToolDescriptor.TypeEnum.WDL, false);
    }


}
