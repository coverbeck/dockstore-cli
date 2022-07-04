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

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dockstore.common.Registry;
import io.dockstore.common.SlowTest;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;

@Category({ ConfidentialTest.class, ToolTest.class })
public class ManualPublishIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
    }

    /**
     * Tests manually adding, updating, and removing a dockerhub tool
     */
    @Test
    public void testVersionTagDockerhub() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        // Add a tag
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "add", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--name", "masterTest", "--image-id",
            "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        Assert.assertEquals("there should be one tag", 1, count);

        // Update tag
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "update", "--entry",
                "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--name", "masterTest", "--hidden", "true",
                "--script" });

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag t, version_metadata vm where name = 'masterTest' and vm.hidden='t' and t.id = vm.id", long.class);
        Assert.assertEquals("there should be one tag", 1, count2);

        // Remove tag
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "remove", "--entry",
                "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--name", "masterTest", "--script" });

        final long count3 = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        Assert.assertEquals("there should be no tags", 0, count3);

    }

    /**
     * Tests the case where a manually registered quay tool matching an automated build should be treated as a separate auto build (see issue 106)
     */
    @Test
    public void testManualQuaySameAsAutoQuay() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'regular'", long.class);
        Assert.assertEquals("the tool should be Auto", 1, count);
    }

    /**
     * Tests the case where a manually registered quay tool has the same path as an auto build but different git repo
     */
    @Test
    public void testManualQuayToAutoSamePathDifferentGitRepo() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname", "alternate",
            "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode = 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'alternate'", long.class);
        Assert.assertEquals("the tool should be Manual still", 1, count);
    }

    /**
     * Tests that a manually published tool still becomes manual even after the existing similar auto tools all have toolnames (see issue 120)
     */
    @Test
    public void testManualQuayToAutoNoAutoWithoutToolname() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--toolname", "testToolname", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "testtool", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.getDockerPath()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'testtool'", long.class);
        Assert.assertEquals("the tool should be Auto", 1, count);
    }

    /**
     * Tests the case where a manually registered quay tool does not have any automated builds set up, though a manual build was run (see issue 107)
     * UPDATE: Should fail because you can't publish a tool with no valid tags
     */
    @Test
    public void testManualQuayManualBuild() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "noautobuild", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
            "--script" });

    }

    /**
     * Tests the case where a manually registered quay tool does not have any tags
     */
    @Test
    public void testManualQuayNoTags() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "nobuildsatall",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
            "--script" });
    }

    /**
     * Tests a user trying to add a quay tool that they do not own and are not in the owning organization
     */
    @Test
    public void testAddQuayRepoOfNonOwnedOrg() {
        // Repo user isn't part of org
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstore2", "--name", "testrepo2", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "testOrg", "--cwl-path",
            "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });

    }

    /**
     * Check that refreshing an existing tool will not throw an error
     * Todo: Update test to check the outcome of a refresh
     */
    @Test
    public void testRefreshCorrectTool() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:dockstoretestuser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:dockstoretestuser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Github tool with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    public void testQuayGithubManualPublishAndUnpublishAlternateStructure() {
        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithubalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);
    }

    /**
     * Ensures that one cannot register an existing Quay/Github entry if you don't give it an alternate toolname
     */
    @Test
    public void testQuayGithubManuallyRegisterDuplicate() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--script" });
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Bitbucket entry with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    public void testQuayBitbucketManualPublishAndUnpublishAlternateStructure() {
        // Manual Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandbitbucketalternate",
            "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucketalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);

    }

    /**
     * Ensures that one cannot register an existing Quay/Bitbucket entry if you don't give it an alternate toolname
     */
    @Test
    public void testQuayBitbucketManuallyRegisterDuplicate() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandbitbucket",
            "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--script" });
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Gitlab entry with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    @Category(SlowTest.class)
    public void testQuayGitlabManualPublishAndUnpublishAlternateStructure() {
        // Manual Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgitlabalternate",
            "--git-url", "git@gitlab.com:dockstore.test.user/quayandgitlabalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgitlabalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);

    }

    /**
     * Ensures that one cannot register an existing Quay/Gitlab entry if you don't give it an alternate toolname
     */
    @Test
    public void testQuayGitlabManuallyRegisterDuplicate() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgitlab",
            "--git-url", "git@gitlab.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--script" });
    }

    /*
     * Test dockerhub and github -
     * These tests are focused on testing entrys created from Dockerhub and Github repositories
     */

    /**
     * Tests manual registration and unpublishing of a Dockerhub/Github entry
     */
    @Test
    public void testDockerhubGithubManualRegistration() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries", 0, count2);

    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Github entry with an alternate structure
     */
    @Test
    public void testDockerhubGithubAlternateStructure() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count2);
    }

    /**
     * Will test attempting to manually publish a Dockerhub/Github entry using incorrect CWL and/or dockerfile locations
     */
    @Ignore
    public void testDockerhubGithubWrongStructure() {
        // Todo : Manual publish entry with wrong cwl and dockerfile locations, should not be able to manual publish
        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git",
            "--git-reference", "master", "--toolname", "regular", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile",
            "--script" });
    }

    /**
     * Checks that you can manually publish and unpublish a Dockerhub/Github duplicate if different toolnames are set (but same Path)
     */
    @Test
    public void testDockerhubGithubManualRegistrationDuplicates() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count);

        // Add duplicate entry with different toolname
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular2",
            "--script" });

        // Unpublish the duplicate entrys
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 2 entries", 2, count2);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count3);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular2", "--script" });
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries", 0, count4);

    }


    /*
     * Test dockerhub and bitbucket -
     * These tests are focused on testing entrys created from Dockerhub and Bitbucket repositories
     */

    /**
     * Tests manual registration and unpublishing of a Dockerhub/Bitbucket entry
     */
    @Test
    public void testDockerhubBitbucketManualRegistration() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);
    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Bitbucket entry with an alternate structure
     */
    @Test
    public void testDockerhubBitbucketAlternateStructure() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference",
            "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/alternate", "--script" });

        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count3);

    }

    /**
     * Will test attempting to manually publish a Dockerhub/Bitbucket entry using incorrect CWL and/or dockerfile locations
     */
    @Ignore
    public void testDockerhubBitbucketWrongStructure() {
        // Todo : Manual publish entry with wrong cwl and dockerfile locations, should not be able to manual publish
        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucketalternate", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalterante.git",
            "--git-reference", "master", "--toolname", "alternate", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile",
            "--script" });
    }

    /**
     * Checks that you can manually publish and unpublish a Dockerhub/Bitbucket duplicate if different toolnames are set (but same Path)
     */
    @Test
    public void testDockerhubBitbucketManualRegistrationDuplicates() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count);

        // Add duplicate entry with different toolname
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular2", "--script" });

        // Unpublish the duplicate entrys
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 2 entries", 2, count2);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count3);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular2", "--script" });
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries", 0, count4);

    }

    /**
     * Tests manual registration and unpublishing of a Dockerhub/Gitlab entry
     */
    @Test
    @Category(SlowTest.class)
    public void testDockerhubGitlabManualRegistration() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);
    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Gitlab entry with an alternate structure
     */
    @Test
    @Category(SlowTest.class)
    public void testDockerhubGitlabAlternateStructure() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/quayandgitlabalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/alternate", "--script" });

        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count3);

    }

    /**
     * Checks that you can manually publish and unpublish a Dockerhub/Gitlab duplicate if different toolnames are set (but same Path)
     */
    @Test
    @Category(SlowTest.class)
    public void testDockerhubGitlabManualRegistrationDuplicates() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count);

        // Add duplicate entry with different toolname
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular2",
            "--script" });

        // Unpublish the duplicate entries
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 2 entries", 2, count2);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular", "--script" });
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count3);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular2", "--script" });
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries", 0, count4);

    }

    /**
     * This tests that a tool cannot be manually published if it has no default descriptor paths
     */
    @Test
    public void testManualPublishToolNoDescriptorPaths() {
        // Manual publish, should fail
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "", "--wdl-path", "", "--dockerfile-path", "/testDir/Dockerfile", "--script" });
    }

    /**
     * This tests that a tool cannot be manually published if it has an incorrect registry
     */
    @Test
    public void testManualPublishToolIncorrectRegistry() {
        // Manual publish, should fail
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            "thisisafakeregistry", "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname", "alternate",
            "--script" });
    }

    /**
     * This tests some cases for private tools
     */
    @Test
    public void testPrivateManualPublish() {
        // Setup DB

        // Manual publish private repo with tool maintainer email
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), "--namespace", "dockstoretestuser", "--name", "private_test_repo", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "tool1",
            "--tool-maintainer-email", "testemail@domain.com", "--private", "true", "--script" });

        // The tool should be private, published and have the correct email
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count, 1, count);

        // Manual publish public repo
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), "--namespace", "dockstoretestuser", "--name", "private_test_repo", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "tool2", "--script" });

        // NOTE: The tool should not have an associated email

        // Should not be able to convert to a private repo since it is published and has no email
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool2", "--private", "true", "--script" });

        // Give the tool a tool maintainer email
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool2", "--private", "true", "--tool-maintainer-email",
                "testemail@domain.com", "--script" });
    }

    /**
     * This tests that you can convert a published public tool to private if it has a tool maintainer email set
     */
    @Test
    public void testPublicToPrivateToPublicTool() {
        // Setup DB

        // Manual publish public repo
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "private_test_repo",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "tool1",
            "--script" });

        // NOTE: The tool should not have an associated email

        // Give the tool a tool maintainer email and make private
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "true", "--tool-maintainer-email",
                "testemail@domain.com", "--script" });

        // The tool should be private, published and have the correct email
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count, 1, count);

        // Convert the tool back to public
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "false", "--script" });

        // Check that the tool is no longer private
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        Assert.assertEquals("no tool should be private, but there are " + count2, 0, count2);

    }

    /**
     * This tests that you can change a tool from public to private without a tool maintainer email, as long as an email is found in the descriptor
     */
    @Test
    public void testDefaultToEmailInDescriptorForPrivateRepos() {
        // Setup DB

        // Manual publish public repo
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "private_test_repo",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-2.git", "--git-reference", "master", "--toolname", "tool1",
            "--script" });

        // NOTE: The tool should have an associated email

        // Make the tool private
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "true", "--script" });

        // The tool should be private, published and not have a maintainer email
        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail=''",
                long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count, 1, count);

        // Convert the tool back to public
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "false", "--script" });

        // Check that the tool is no longer private
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        Assert.assertEquals("no tool should be private, but there are " + count2, 0, count2);

        // Make the tool private but this time define a tool maintainer
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "true", "--tool-maintainer-email",
                "testemail2@domain.com", "--script" });

        // Check that the tool is no longer private
        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail2@domain.com'",
            long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count3, 1, count3);
    }

    /**
     * This tests that you cannot manually publish a private tool unless it has a tool maintainer email
     */
    @Test
    public void testPrivateManualPublishNoToolMaintainerEmail() {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);

        // Manual publish private repo without tool maintainer email
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "private_test_repo",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--private", "true",
            "--script" });

    }


    /**
     * This tests that you can manually publish a gitlab registry image
     */
    @Test
    @Category(SlowTest.class)
    public void testManualPublishGitlabDocker() {
        // Setup database

        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.GITLAB.name(), Registry.GITLAB.toString(), "--namespace", "dockstore.test.user", "--name", "dockstore-whalesay",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname",
            "alternate", "--private", "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--script" });

        // Check that tool exists and is published
        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where ispublished='true' and privateaccess='true'", long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count, 1, count);

    }

    /**
     * This tests that you can manually publish a private only registry (Amazon ECR), but you can't change the tool to public
     */
    @Test
    public void testManualPublishPrivateOnlyRegistry() {
        // Setup database

        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.AMAZON_ECR.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "test.dkr.ecr.test.amazonaws.com",
            "--script" });

        // Check that tool is published and has correct values
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and registry='test.dkr.ecr.test.amazonaws.com' and namespace = 'notarealnamespace' and name = 'notarealname'",
            long.class);
        Assert.assertEquals("one tool should be private, published and from amazon, there are " + count, 1, count);

        // Update tool to public (shouldn't work)
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "update_tool", "--entry",
            "test.dkr.ecr.test.amazonaws.com/notarealnamespace/notarealname/alternate", "--private", "false", "--script" });
    }

    /**
     * This tests that you can manually publish a private only registry (Seven Bridges), but you can't change the tool to public
     */
    @Test
    public void testManualPublishSevenBridgesTool() {
        // Setup database

        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.SEVEN_BRIDGES.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "images.sbgenomics.com", "--script" });

        // Check that tool is published and has correct values
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and registry='images.sbgenomics.com' and namespace = 'notarealnamespace' and name = 'notarealname'",
            long.class);
        assertEquals("one tool should be private, published and from seven bridges, there are " + count, 1, count);

        // Update tool to public (shouldn't work)
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "update_tool", "--entry",
            "images.sbgenomics.com/notarealnamespace/notarealname/alternate", "--private", "false", "--script" });
    }

    /**
     * This tests that you can't manually publish a private only registry (Seven Bridges) with an incorrect registry path
     */
    @Test
    public void testManualPublishSevenBridgesToolIncorrectRegistryPath() {
        // Setup database

        // Manual publish correct path
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.SEVEN_BRIDGES.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "test-images.sbgenomics.com",
            "--script" });

        // Check that tool is published and has correct values
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and registry='test-images.sbgenomics.com' and namespace = 'notarealnamespace' and name = 'notarealname'",
            long.class);
        assertEquals("one tool should be private, published and from seven bridges, there are " + count, 1, count);

        // Manual publish incorrect path
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.SEVEN_BRIDGES.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "testimages.sbgenomics.com",
            "--script" });

    }

    /**
     * This tests that you can't manually publish a private only registry as public
     */
    @Test
    public void testManualPublishPrivateOnlyRegistryAsPublic() {
        // Manual publish
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.AMAZON_ECR.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
            "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "amazon.registry", "--script" });

    }

    /**
     * This tests that you can't manually publish a tool from a registry that requires a custom docker path without specifying the path
     */
    @Test
    public void testManualPublishCustomDockerPathRegistry() {
        // Manual publish
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.AMAZON_ECR.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--script" });

    }


}
