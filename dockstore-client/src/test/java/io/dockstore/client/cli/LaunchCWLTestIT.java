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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.Utilities;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class LaunchCWLTestIT {

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
    public void runToolWithDirectories() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void runToolWithDirectoriesThreaded() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runToolThreaded(cwlFile, args, api, usersApi, client);
    }

    @Test
    public void runToolWithGlobbedFilesOnOutput() throws IOException {

        File fileDir = new File("/tmp/provision_out_with_files");
        FileUtils.deleteDirectory(fileDir);
        FileUtils.forceMkdir(fileDir);

        File cwlFile = new File(ResourceHelpers.resourceFilePath("splitBlob.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("splitBlob.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 7, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            assertTrue("output should provision out to correct locations",
                systemOutRule.getLog().contains("/tmp/provision_out_with_files/"));
            assertTrue(new File("/tmp/provision_out_with_files/test.a" + y).exists());
        }
    }

    @Test
    public void runToolWithoutProvisionOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split_no_provision_out.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Uploading");
        assertEquals("output should include multiple provision out events, found " + countMatches, 0, countMatches);
    }

    @Test
    public void yamlToolCorrect() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-tool.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("echo-job.yml"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--yaml");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void runToolWithSecondaryFilesOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolWithSecondaryFilesRenamedOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.renamed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolWithSecondaryFilesOfVariousKinds() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.nocaret.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.renamed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 8, countMatches);
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa");
        for (char y = 'b'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.aa.a" + y + "extra";
            checkFileAndThenDeleteIt(filename);
        }
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa.funky.extra.stuff");
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa.groovyextrastuff");
    }

    @Test
    public void runToolWithSecondaryFilesOfEvenStrangerKinds() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.more.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.extra.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'e'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.txt.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.extra");
    }


    @Test
    public void runToolSecondaryFilesToDirectory() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_directory.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolSecondaryFilesToDirectoryThreaded() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_directory.json"));

        runTool(cwlFile, cwlJSON, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolSecondaryFilesToCWD() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_missing_directory.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "./test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolMalformedToCWD() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_malformed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "./test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolToMissingS3() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_s3_failed.json"));
        ByteArrayOutputStream launcherOutput = null;
        try {
            launcherOutput = new ByteArrayOutputStream();
            System.setOut(new PrintStream(launcherOutput));

            thrown.expect(AssertionError.class);
            runTool(cwlFile, cwlJSON);
            final String standardOutput = launcherOutput.toString();
            assertTrue("Error should occur, caused by Amazon S3 Exception",
                standardOutput.contains("Caused by: com.amazonaws.services.s3.model.AmazonS3Exception"));
        } finally {
            try {
                if (launcherOutput != null) {
                    launcherOutput.close();
                }
            } catch (IOException ex) {
                assertTrue("Error closing output stream.", true);
            }
        }
    }

    @Test
    public void runToolDirectoryMalformedToCWD() throws IOException {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split_dir.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_malformed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include one provision out event, found " + countMatches, 1, countMatches);
        String filename = "test1";
        checkFileAndThenDeleteIt(filename);
        FileUtils.deleteDirectory(new File(filename));
    }

    private void runTool(File cwlFile, File cwlJSON) {
        runTool(cwlFile, cwlJSON, false);
    }

    private void runTool(File cwlFile, File cwlJSON, boolean threaded) {
        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--cwl");
        args.add(cwlFile.getAbsolutePath());
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        if (threaded) {
            runToolThreaded(cwlFile, args, api, usersApi, client);
        } else {
            runTool(cwlFile, args, api, usersApi, client, true);
        }
    }

    private void runTool(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client, boolean useCache) {
        client.setConfigFile(ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));
        Client.SCRIPT.set(true);
        runToolShared(cwlFile, args, api, usersApi, client);
    }

    private void runToolThreaded(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client) {
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withThreads"));

        runToolShared(cwlFile, args, api, usersApi, client);
    }

    private void runToolShared(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client) {
        ToolClient toolClient = new ToolClient(api, null, usersApi, client, false);
        toolClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cwltool run", systemOutRule.getLog().contains("Final process status is success"));
    }



    @Test
    public void cwlCorrectWithCache() {
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, true);
    }



    @Test
    public void cwlNullInputParameter() {
        // Tests if a null input parameter is correctly handled when converting json
        File nullCWL = new File(ResourceHelpers.resourceFilePath("nullParam.cwl"));
        File nullJSON = new File(ResourceHelpers.resourceFilePath("nullParam.json"));

        // run simple echo null tool
        runTool(nullCWL, nullJSON, false);
    }

    private void checkFileAndThenDeleteIt(String filename) {
        assertTrue("output should provision out to correct locations, could not find " + filename + " in log",
            systemOutRule.getLog().contains(filename));
        assertTrue("file does not actually exist", Files.exists(Paths.get(filename)));
        // cleanup
        FileUtils.deleteQuietly(Paths.get(filename).toFile());
    }

    private void runWorkflow(File cwlFile, ArrayList<String> args, WorkflowsApi api, UsersApi usersApi, Client client, boolean useCache) {
        client.setConfigFile(ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));
        Client.SCRIPT.set(true);
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cwltool run", systemOutRule.getLog().contains("Final process status is success"));
    }

    @Test
    public void cwlCorrect() {
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void cwlMetadataNoopPluginTest() {

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("collab-cwl-noop-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add("--json");
        args.add(cwlJSON.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        Client.SCRIPT.set(true);
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withTestPlugin"));

        PluginClient.handleCommand(Lists.newArrayList("download"), Utilities.parseConfig(client.getConfigFile()));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cwltool run", systemOutRule.getLog().contains("Final process status is success"));
        assertTrue("output should include a noop plugin run with metadata", systemOutRule.getLog().contains("really cool metadata"));
    }

    @Test
    public void cwlWrongExt() {
        //Test when content = cwl but ext = wdl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should tell user to specify the descriptor", systemOutRule.getLog()
            .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }

    @Test
    public void cwlWrongExtForce() {
        //Test when content = cwl but ext = wdl, descriptor provided --> CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--local-entry");
        args.add(file.getAbsolutePath());
        args.add("--json");
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(CWL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL.getShortName());

        assertTrue("output should include a successful cromwell run",
            systemOutRule.getLog().contains("This is a CWL file.. Please put the correct extension to the entry file name."));
    }

    @Test
    public void wdlWrongExt() {
        //Test when content = wdl but ext = cwl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
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
    public void randomExtCwl() {
        //Test when content is random, but ext = cwl
        File file = new File(ResourceHelpers.resourceFilePath("random.cwl"));
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
    public void wdlWrongExtForce() {
        //Test when content = wdl but ext = cwl, descriptor provided --> WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add(file.getAbsolutePath());
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(WDL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, WDL.getShortName());

        assertTrue("output should include a successful cromwell run",
            systemOutRule.getLog().contains("This is a WDL file.. Please put the correct extension to the entry file name."));
    }

    @Test
    public void cwlWrongExtForce1() {
        //Test when content = cwl but ext = wdl, descriptor provided --> !CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add("wrongExtcwl.wdl");
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(WDL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, WDL.getShortName());
    }

    @Test
    public void wdlWrongExtForce1() {
        //Test when content = wdl but ext = cwl, descriptor provided --> !WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add("wrongExtwdl.cwl");
        args.add("--local-entry");
        args.add("--json");
        args.add(json.getAbsolutePath());
        args.add("--descriptor");
        args.add(CWL.getShortName());

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        Client.SCRIPT.set(true);

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL.getShortName());
    }

    @Test
    public void cwlNoExt() {
        //Test when content = cwl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("cwlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<>();
        args.add("--entry");
        args.add("cwlNoExt");
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

        assertTrue("output should contain a validation issue",
            systemOutRule.getLog().contains("This is a CWL file.. Please put an extension to the entry file name."));
    }


    @Test
    public void cwlNoInput() {
        //Test when content is missing 'input'

        File file = new File(ResourceHelpers.resourceFilePath("noInput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

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
            systemErrRule.getLog().contains("Required fields that are missing from CWL file : 'inputs'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }


}
