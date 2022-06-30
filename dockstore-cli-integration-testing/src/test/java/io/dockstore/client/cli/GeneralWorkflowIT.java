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

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ToilCompatibleTest;
import io.dockstore.common.WorkflowTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
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
     * This tests the dirty bit attribute for workflow versions with bitbucket
     */
    @Test
    public void testBitbucketDirtyBit() {
        refreshByOrganizationReplacement(USER_2_USERNAME);

        // refresh individual that is valid
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry",
            SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--script" });
        final long nullLastModifiedWorkflowVersions = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where lastmodified is null", long.class);
        assertEquals("All Bitbucket workflow versions should have last modified populated after refreshing", 0,
            nullLastModifiedWorkflowVersions);
        // Check that no versions have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be no versions with dirty bit, there are " + count, 0, count);

        // Edit workflow path for a version
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry",
            SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--name", "master", "--workflow-path",
            "/Dockstoredirty.cwl", "--script" });

        // There should be on dirty bit
        final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", long.class);
        assertEquals("there should be 1 versions with dirty bit, there are " + count1, 1, count1);

        // Update default cwl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry",
                SourceControl.BITBUCKET.toString() + "/dockstore_testuser2/dockstore-workflow", "--workflow-path", "/Dockstoreclean.cwl",
                "--script" });

        // There should be 3 versions with new cwl
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", long.class);
        assertEquals("there should be 4 versions with workflow path /Dockstoreclean.cwl, there are " + count2, 4, count2);

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



    /**
    * Tests publishing/unpublishing workflows with the --new-entry-name parameter
    */
    @Test
    public void testPublishWithNewEntryName() {

        final String publishNameParameter = "--new-entry-name";

        // register workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
            "parameter_test_workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--script"});

        // count number of workflows for this user with the workflowname 'test_entryname'
        final long countInitialWorkflowPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname IS NULL;", long.class);
        assertEquals("The initial workflow should be published without a workflow name", 1, countInitialWorkflowPublish);

        // publish workflow with name 'test_entryname'
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // publish workflow with name 'test_entryname' a second time, shouldn't work
        systemOutRule.clearLog();
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});
        assertTrue("Attempting to publish a registered workflow should notify the user",
            systemOutRule.getLog().contains("The following workflow is already registered: github.com/DockstoreTestUser2/parameter_test_workflow"));

        // verify there are 2 workflows associated with the user
        final long countTotalPublishedWorkflows = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND ispublished='t';", long.class);
        assertEquals("Ensure there are 2 published workflows", 2, countTotalPublishedWorkflows);

        // verify count of number of published workflows, with the desired name, is 1
        final long countPublishedWorkflowWithCustomName = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='t';", long.class);
        assertEquals("Ensure there is a published workflow with the expected workflow name", 1, countPublishedWorkflowWithCustomName);

        // Try unpublishing with both --unpub and --entryname specified, should fail
        systemExit.expectSystemExitWithStatus(Client.COMMAND_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // unpublish workflow with name 'test_entryname'
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});

        // verify count of number of unpublish workflows with the desired name is 1
        final long countUnpublishedWorkflowWithCustomName = testingPostgres.runSelectStatement(
            "SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='f';", long.class);
        assertEquals("The workflow should exist and be unpublished", 1, countUnpublishedWorkflowWithCustomName);

        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});
        assertTrue("Attempting to publish a registered workflow should notify the user",
            systemOutRule.getLog().contains("The following workflow is already unpublished: github.com/DockstoreTestUser2/parameter_test_workflow"));
    }


    /**
     * Tests publishing/unpublishing workflows with the original --entryname parameter to ensure backwards compatibility
     */
    @Test
    public void testPublishWithEntryName() {

        final String publishNameParameter = "--entryname";

        // register workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository",
            "parameter_test_workflow", "--organization", "DockstoreTestUser2", "--git-version-control", "github", "--script"});

        // count number of workflows for this user with the workflowname 'test_entryname'
        final long countInitialWorkflowPublish = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                + "AND repository='parameter_test_workflow' AND workflowname IS NULL;", long.class);
        assertEquals("The initial workflow should be published without a workflow name", 1, countInitialWorkflowPublish);

        // publish workflow with name 'test_entryname'
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // publish workflow with name 'test_entryname' a second time, shouldn't work
        systemOutRule.clearLog();
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish",
                "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});
        assertTrue("Attempting to publish a registered workflow should notify the user",
            systemOutRule.getLog().contains("The following workflow is already registered: github.com/DockstoreTestUser2/parameter_test_workflow"));

        // verify there are 2 workflows associated with the user
        final long countTotalPublishedWorkflows = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                        + "AND repository='parameter_test_workflow' AND ispublished='t';", long.class);
        assertEquals("Ensure there are 2 published workflows", 2, countTotalPublishedWorkflows);

        // verify count of number of published workflows, with the desired name, is 1
        final long countPublishedWorkflowWithCustomName = testingPostgres
            .runSelectStatement("SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' "
                        + "AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='t';", long.class);
        assertEquals("Ensure there is a published workflow with the expected workflow name", 1, countPublishedWorkflowWithCustomName);

        // Try unpublishing with both --unpub and --entryname specified, should fail
        systemExit.expectSystemExitWithStatus(Client.COMMAND_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow", publishNameParameter, "test_entryname", "--script"});

        // unpublish workflow with name 'test_entryname'
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});

        // verify count of number of unpublish workflows with the desired name is 1
        final long countUnpublishedWorkflowWithCustomName = testingPostgres.runSelectStatement(
            "SELECT COUNT(*) FROM workflow WHERE organization='DockstoreTestUser2' AND repository='parameter_test_workflow' AND workflowname='test_entryname' AND ispublished='f';", long.class);
        assertEquals("The workflow should exist and be unpublished", 1, countUnpublishedWorkflowWithCustomName);

        systemOutRule.clearLog();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--unpub",
            "--entry", "github.com/DockstoreTestUser2/parameter_test_workflow/test_entryname", "--script"});
        assertTrue("Attempting to publish a registered workflow should notify the user",
            systemOutRule.getLog().contains("The following workflow is already unpublished: github.com/DockstoreTestUser2/parameter_test_workflow"));

    }
}
