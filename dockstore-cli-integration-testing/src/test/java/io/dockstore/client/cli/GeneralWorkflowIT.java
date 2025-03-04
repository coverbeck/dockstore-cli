/*
 *    Copyright 2018 OICR
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

import java.util.List;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.WorkflowTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static io.dockstore.webservice.resources.WorkflowResource.FROZEN_VERSION_REQUIRED;
import static io.dockstore.webservice.resources.WorkflowResource.NO_ZENDO_USER_TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This test suite will have tests for the workflow mode of the Dockstore Client.
 * Created by aduncan on 05/04/16.
 */
@Category({ ConfidentialTest.class, WorkflowTest.class })
public class GeneralWorkflowIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    public void refreshAll() {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // get userid
        final long userid = testingPostgres.runSelectStatement(String.format("SELECT id FROM user_profile WHERE username='%s';", USER_2_USERNAME), long.class);

        // Delete all entries associated with the userid
        testingPostgres.runDeleteStatement(String.format("DELETE FROM user_entry ue WHERE ue.userid = %d", userid));

        // Count number of entries after running the delete statement
        final long entryCountAfterDelete = testingPostgres.runSelectStatement(String.format("SELECT COUNT(*) FROM user_entry WHERE userid = %d;", userid), long.class);
        assertEquals("After deletion, there should be 0 entries remaining associated with this user", 0, entryCountAfterDelete);

        // run CLI refresh command to refresh all workflows
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh"});

        // final count of workflows associated with this user
        final long entryCountAfterRefresh = testingPostgres.runSelectStatement(String.format("SELECT COUNT(*) FROM user_entry WHERE userid = %d;", userid), long.class);
        assertTrue("User should be associated with >= 40 workflows", entryCountAfterRefresh >= 40);
    }

    /**
     * This test checks that refresh all workflows (with a mix of stub and full) and refresh individual.  It then tries to publish them
     */
    @Test
    public void testRefreshAndPublish() {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // refresh individual that is valid
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // check that valid is valid and full
        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals("there should be 0 published entries, there are " + count, 0, count);
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 2 valid versions, there are " + count2, 2, count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals("there should be 1 full workflows, there are " + count3, 1, count3);
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertTrue("there should be at least 4 versions, there are " + count4, 4 <= count4);

        // attempt to publish it
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        final long count5 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals("there should be 1 published entry, there are " + count5, 1, count5);

        // unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

        final long count6 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", long.class);
        assertEquals("there should be 0 published entries, there are " + count6, 0, count6);

        testPublishList();
    }

    /**
     * Test the "dockstore workflow publish" command
     */
    private void testPublishList() {
        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--script" });
        assertTrue("Should contain a FULL workflow belonging to the user",
            systemOutRule.getLog().contains("github.com/DockstoreTestUser2/hello-dockstore-workflow"));
        assertFalse("Should not contain a STUB workflow belonging to the user",
            systemOutRule.getLog().contains("gitlab.com/dockstore.test.user2/dockstore-workflow-md5sum-unified "));
    }


    /**
     * This tests attempting to publish a workflow with no valid versions
     */
    @Test
    public void testRefreshAndPublishInvalid() {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // refresh individual
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_empty_repo", "--script" });

        // check that no valid versions
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 0 valid versions, there are " + count, 0, count);

        // try and publish
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_empty_repo", "--script" });
    }





    /**
     * This tests that a restub will work on an unpublished, full workflow
     */
    @Test
    public void testRestub() {
        // Refresh and then restub
        refreshByOrganizationReplacement(USER_2_USERNAME);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        assertEquals("there should be 0 workflow versions, there are " + count, 0, count);
    }

    /**
     * This tests that a restub will not work on an published, full workflow
     */
    @Test
    public void testRestubError() {
        // Refresh and then restub
        refreshByOrganizationReplacement(USER_2_USERNAME);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
    }

    /**
     * Tests updating workflow descriptor type when a workflow is FULL and when it is a STUB
     */
    @Test
    public void testDescriptorTypes() {
        refreshByOrganizationReplacement(USER_2_USERNAME);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--descriptor-type", "wdl", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from workflow where descriptortype = 'wdl'", long.class);
        assertEquals("there should be 1 wdl workflow, there are " + count, 1, count);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--descriptor-type", "cwl", "--script" });
    }

    /**
     * Tests updating a workflow tag with invalid workflow descriptor path
     */
    @Test
    public void testWorkflowVersionIncorrectPath() {
        refreshByOrganizationReplacement(USER_2_USERNAME);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path",
            "/newdescriptor.cwl", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where name = 'master' and workflowpath = '/newdescriptor.cwl'",
                long.class);
        assertEquals("the workflow version should now have a new descriptor path", 1, count);

        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path",
            "/Dockstore.wdl", "--script" });

    }

    /**
     * Tests that convert with valid imports will work, but convert without valid imports will throw an error (for CWL)
     */
    @Test
    @Category(ToilCompatibleTest.class)
    public void testRefreshAndConvertWithImportsCWL() {
        refreshByOrganizationReplacement(USER_2_USERNAME);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow:testBoth", "--script" });

        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow:testCWL", "--script" });
    }




    /**
     * This test tests a bunch of different assumptions for how refresh should work for workflows
     */
    @Test
    public void testRefreshRelatedConcepts() {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // refresh individual that is valid
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // check that workflow is valid and full
        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 2 valid versions, there are " + count2, 2, count2);
        final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", long.class);
        assertEquals("there should be 1 full workflows, there are " + count3, 1, count3);

        // Change path for each version so that it is invalid
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.cwl', dirtybit=true");
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Workflow has no valid versions so you cannot publish

        // check that invalid
        final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertTrue("there should be at least 4 invalid versions, there are " + count4, 4 <= count4);

        // Restub
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Update workflow to WDL
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--workflow-path", "Dockstore.wdl",
                "--descriptor-type", "wdl", "--script" });

        // Can now publish workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

        // Set paths to invalid
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.wdl', dirtybit=true");
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Check that versions are invalid
        final long count5 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", long.class);
        assertTrue("there should be at least 4 invalid versions, there are " + count5, 4 <= count5);

        // should now not be able to publish
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });
    }

    /**
     * This tests the dirty bit attribute for workflow versions with github
     */
    @Test
    public void testGithubDirtyBit() {
        // refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // refresh individual that is valid
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--script" });

        // Check that no versions have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be no versions with dirty bit, there are " + count, 0, count);

        // Edit workflow path for a version
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path",
            "/Dockstoredirty.cwl", "--script" });

        // There should be on dirty bit
        final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be 1 versions with dirty bit, there are " + count1, 1, count1);

        // Update default cwl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/hello-dockstore-workflow", "--workflow-path", "/Dockstoreclean.cwl",
                "--script" });

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", long.class);
        assertTrue("there should be at least 3 versions with workflow path /Dockstoreclean.cwl, there are " + count2, 3 <= count2);

    }

    /**
     * This is a high level test to ensure that gitlab basics are working for gitlab as a workflow repo
     */
    @Test
    @Ignore("Ignoring for 1.8.6, enable for 1.9.0")
    public void testGitLab() {
        // Refresh workflow
        refreshByOrganizationReplacement(USER_2_USERNAME);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--script" });
        final long nullLastModifiedWorkflowVersions = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All GitLab workflow versions should have last modified populated after refreshing", 0,
            nullLastModifiedWorkflowVersions);

        // Check a few things
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals("there should be 1 workflow, there are " + count, 1, count);

        final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", long.class);
        assertEquals("there should be 2 valid version, there are " + count2, 2, count2);

        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals("there should be 1 workflow, there are " + count3, 1, count3);

        // publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--script" });
        final long count4 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
            long.class);
        assertEquals("there should be 1 published workflow, there are " + count4, 1, count4);

        // Should be able to get info since it is published
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry",
            SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--script" });

        // Should be able to grab descriptor
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "cwl", "--entry",
            SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example:master", "--script" });

        // unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry",
            SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--unpub", "--script" });
        final long count5 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='FULL' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and ispublished='t'",
            long.class);
        assertEquals("there should be 0 published workflows, there are " + count5, 0, count5);

        // change default branch
        final long count6 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example' and author is null and email is null and description is null",
            long.class);
        assertEquals("The given workflow shouldn't have any contact info", 1, count6);

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--default-version", "test",
                "--script" });

        final long count7 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where actualdefaultversion = 'test' and author is null and email is null and description is null",
            long.class);
        assertEquals("The given workflow should now have contact info and description", 0, count7);

        // restub
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
            SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--script" });
        final long count8 = testingPostgres.runSelectStatement(
            "select count(*) from workflow where mode='STUB' and sourcecontrol = '" + SourceControl.GITLAB.toString()
                + "' and organization = 'dockstore.test.user2' and repository = 'dockstore-workflow-example'", long.class);
        assertEquals("The workflow should now be a stub", 1, count8);

        // Convert to WDL workflow
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITLAB.toString() + "/dockstore.test.user2/dockstore-workflow-example", "--descriptor-type", "wdl",
                "--script" });

        // Should now be a WDL workflow
        final long count9 = testingPostgres.runSelectStatement("select count(*) from workflow where descriptortype='wdl'", long.class);
        assertEquals("there should be no 1 wdl workflow" + count9, 1, count9);

    }




    /**
     * This tests that WDL files are properly parsed for secondary WDL files
     */
    @Test
    public void testWDLWithImports() {
        // Refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // Update workflow to be WDL with correct path
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/test_workflow_wdl", "--descriptor-type", "wdl", "--workflow-path",
                "/hello.wdl", "--script" });

        // Check for WDL files
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where path='helper.wdl'", long.class);
        assertEquals("there should be 1 secondary file named helper.wdl, there are " + count, 1, count);

    }

    /**
     * This tests basic concepts with workflow test parameter files
     */
    @Test
    public void testTestParameterFile() {
        // Refresh all
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // Refresh specific
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--script" });

        // There should be no sourcefiles
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be no source files that are test parameter files, there are " + count, 0, count);

        // Update version master with test parameters
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version", "master", "--add",
                // Trying to remove a non-existent test parameter file now causes an error. It didn't use to and test was relying
                // on that behavior.
                "test.cwl.json", "--add", "test2.cwl.json", "--add", "fake.cwl.json", /*"--remove", "notreal.cwl.json",*/ "--script" });
        final long count2 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be two sourcefiles that are test parameter files, there are " + count2, 2, count2);

        // Update version with test parameters
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version", "master", "--add",
                "test.cwl.json", "--remove", "test2.cwl.json", "--script" });
        final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be one sourcefile that is a test parameter file, there are " + count3, 1, count3);

        // Update other version with test parameters
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version", "wdltest", "--add",
                "test.wdl.json", "--script" });
        final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='CWL_TEST_JSON'", long.class);
        assertEquals("there should be two sourcefiles that are cwl test parameter files, there are " + count4, 2, count4);

        // Restub
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--script" });

        // Change to WDL
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--descriptor-type", "wdl",
                "--workflow-path", "Dockstore.wdl", "--script" });

        // Should be no sourcefiles
        final long count5 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        assertEquals("there should be no source files that are test parameter files, there are " + count5, 0, count5);

        // Update version wdltest with test parameters
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "test_parameter", "--entry",
                SourceControl.GITHUB.toString() + "/DockstoreTestUser2/parameter_test_workflow", "--version", "wdltest", "--add",
                "test.wdl.json", "--script" });
        final long count6 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='WDL_TEST_JSON'", long.class);
        assertEquals("there should be one sourcefile that is a wdl test parameter file, there are " + count6, 1, count6);
    }


    /**
     * This tests that you can refresh user data by refreshing a workflow
     * ONLY WORKS if the current user in the database dump has no metadata, and on Github there is metadata (bio, location)
     * If the user has metadata, test will pass as long as the user's metadata isn't the same as Github already
     *
     * Ignoring this one for 1.9, since we don't have the refresh endpoint any more
     */
    @Ignore
    @Test
    public void testRefreshingUserMetadata() {
        // Refresh all workflows
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // Check that user has been updated
        // TODO: bizarrely, the new GitHub Java API library doesn't seem to handle bio
        // final long count = testingPostgres.runSelectStatement("select count(*) from enduser where location='Toronto' and bio='I am a test user'", long.class);
        final long count = testingPostgres.runSelectStatement("select count(*) from user_profile where location='Toronto'", long.class);
        assertEquals("One user should have this info now, there are  " + count, 1, count);
    }

    @Test
    public void testGenerateDOIFrozenVersion() throws ApiException {
        // Set up webservice
        ApiClient webClient = WorkflowIT.getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        //register workflow
        Workflow githubWorkflow = workflowApi
            .manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/hello.wdl", "test-update-workflow", "wdl", "/test.json");

        Workflow workflowBeforeFreezing = workflowApi.refresh(githubWorkflow.getId(), true);
        WorkflowVersion master = workflowBeforeFreezing.getWorkflowVersions().stream().filter(v -> v.getName().equals("master")).findFirst()
            .get();

        //try issuing DOI for workflow version that is not frozen.
        try {
            workflowApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail("This line should never execute if version is mutable. DOI should only be generated for frozen versions of workflows.");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(FROZEN_VERSION_REQUIRED));
        }

        //freeze version 'master'
        master.setFrozen(true);
        final List<WorkflowVersion> workflowVersions1 = workflowApi
            .updateWorkflowVersion(workflowBeforeFreezing.getId(), Lists.newArrayList(master));
        master = workflowVersions1.stream().filter(v -> v.getName().equals("master")).findFirst().get();
        assertTrue(master.isFrozen());

        //TODO: For now just checking for next failure (no Zenodo token), but should replace with when DOI registration tests are written
        try {
            workflowApi.requestDOIForWorkflowVersion(workflowBeforeFreezing.getId(), master.getId(), "");
            fail("This line should never execute without valid Zenodo token");
        } catch (ApiException ex) {
            assertTrue(ex.getResponseBody().contains(NO_ZENDO_USER_TOKEN));

        }
    }

}
