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

package io.dockstore.common;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.Application;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Tag;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public final class CommonTestUtilities {

    // Travis is slow, need to wait up to 1 min for webservice to return
    public static final int WAIT_TIME = 60000;
    public static final String PUBLIC_CONFIG_PATH = ResourceHelpers.resourceFilePath("dockstore.yml");
    /**
     * confidential testing config, includes keys
     */
    public static final String CONFIDENTIAL_CONFIG_PATH;
    static final String DUMMY_TOKEN_1 = "08932ab0c9ae39a880905666902f8659633ae0232e94ba9f3d2094cb928397e7";
    private static final Logger LOG = LoggerFactory.getLogger(CommonTestUtilities.class);

    static {
        String confidentialConfigPath = null;
        try {
            confidentialConfigPath = ResourceHelpers.resourceFilePath("dockstoreTest.yml");
        } catch (Exception e) {
            LOG.error("Confidential Dropwizard configuration file not found.", e);

        }
        CONFIDENTIAL_CONFIG_PATH = confidentialConfigPath;
    }

    private CommonTestUtilities() {

    }

    /**
     * Drops the database and recreates from migrations, not including any test data, using new application
     *
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void dropAndRecreateNoTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) throws Exception {
        dropAndRecreateNoTestData(support, CONFIDENTIAL_CONFIG_PATH);
    }

    public static void dropAndRecreateNoTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support,
        String dropwizardConfigurationFile) throws Exception {
        LOG.info("Dropping and Recreating the database with no test data");
        Application<DockstoreWebserviceConfiguration> application = support.newApplication();
        application.run("db", "drop-all", "--confirm-delete-everything", dropwizardConfigurationFile);
        application
            .run("db", "migrate", dropwizardConfigurationFile, "--include", "1.3.0.generated,1.3.1.consistency,1.4.0,1.5.0,"
                    + "1.6.0,1.7.0,1.8.0,1.9.0,1.10.0,1.11.0,1.12.0,1.13.0");
    }

    /**
     * Drops the database and recreates from migrations for non-confidential tests
     *
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void dropAndCreateWithTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication)
        throws Exception {
        dropAndCreateWithTestData(support, isNewApplication, CONFIDENTIAL_CONFIG_PATH);
    }

    public static void dropAndCreateWithTestData(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        String dropwizardConfigurationFile) throws Exception {
        LOG.info("Dropping and Recreating the database with non-confidential test data");
        Application<DockstoreWebserviceConfiguration> application;
        if (isNewApplication) {
            application = support.newApplication();
        } else {
            application = support.getApplication();
        }
        application.run("db", "drop-all", "--confirm-delete-everything", dropwizardConfigurationFile);

        List<String> migrationList = Arrays
            .asList("1.3.0.generated", "1.3.1.consistency", "test", "1.4.0", "1.5.0", "test_1.5.0", "1.6.0", "1.7.0",
                    "1.8.0", "1.9.0", "1.10.0", "1.11.0", "1.12.0", "1.13.0");
        runMigration(migrationList, application, dropwizardConfigurationFile);
    }

    /**
     * Shared convenience method
     *
     * @return
     */
    public static ApiClient getWebClient(boolean authenticated, String username, TestingPostgres testingPostgres) {
        File configFile = FileUtils.getFile("src", "test", "resources", "config2");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        if (authenticated) {
            client.addDefaultHeader("Authorization", "Bearer " + (testingPostgres
                .runSelectStatement("select content from token where tokensource='dockstore' and username= '" + username + "';",
                    String.class)));
        }
        return client;
    }

    /**
     * Shared convenience method for openApi Client
     *
     * @return
     */
    public static io.dockstore.openapi.client.ApiClient getOpenApiWebClient(boolean authenticated, String username, TestingPostgres testingPostgres) {
        File configFile = FileUtils.getFile("src", "test", "resources", "config2");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        io.dockstore.openapi.client.ApiClient client = new io.dockstore.openapi.client.ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        if (authenticated) {
            client.addDefaultHeader("Authorization", "Bearer " + (testingPostgres
                .runSelectStatement("select content from token where tokensource='dockstore' and username= '" + username + "';",
                    String.class)));
        }
        return client;
    }

    /**
     * Deletes BitBucket Tokens from Database
     *
     * @param testingPostgres reference to the testing instance of Postgres
     * @throws Exception
     */
    public static void deleteBitBucketToken(TestingPostgres testingPostgres)  {
        LOG.info("Deleting BitBucket Token from Database");
        testingPostgres.runUpdateStatement("delete from token where tokensource = 'bitbucket.org'");
    }

    /**
     * Wrapper for dropping and recreating database from migrations and optionally deleting bitbucket tokens
     *
     * @param support reference to testing instance of the dockstore web service
     * @param testingPostgres reference to the testing instance of Postgres
     * @param needBucketToken if false the bitbucket token will be deleted
     * @throws Exception
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support,
        TestingPostgres testingPostgres, Boolean needBucketToken) throws Exception {
        LOG.info("Dropping and Recreating the database with confidential 1 test data");
        cleanStatePrivate1(support, CONFIDENTIAL_CONFIG_PATH);
        if (!needBucketToken) {
            deleteBitBucketToken(testingPostgres);
        }
    }
    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 1
     *
     * @param support reference to testing instance of the dockstore web service
     * @param testingPostgres reference to the testing instance of Postgres
     * @throws Exception
     */
    public static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, TestingPostgres testingPostgres) throws Exception {
        cleanStatePrivate1(support, testingPostgres, false);
    }

    /**
     * Drops and recreates database from migrations for test confidential 1
     *
     * @param support    reference to testing instance of the dockstore web service
     * @param configPath
     * @throws Exception
     */
    private static void cleanStatePrivate1(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath)
        throws Exception {
        Application<DockstoreWebserviceConfiguration> application = support.getApplication();
        application.run("db", "drop-all", "--confirm-delete-everything", configPath);

        List<String> migrationList = Arrays.asList("1.3.0.generated", "1.3.1.consistency");
        runMigration(migrationList, application, configPath);

        migrationList = Collections.singletonList(
                new File("../dockstore-webservice/src/main/resources/migrations.test.confidential1.xml").getAbsolutePath());
        runExternalMigration(migrationList, application, configPath);

        migrationList = Arrays.asList("1.4.0", "1.5.0");
        runMigration(migrationList, application, configPath);

        migrationList = Collections.singletonList(
                new File("../dockstore-webservice/src/main/resources/migrations.test.confidential1_1.5.0.xml").getAbsolutePath());
        runExternalMigration(migrationList, application, configPath);

        migrationList = Arrays.asList("1.6.0", "1.7.0", "1.8.0", "1.9.0", "1.10.0", "1.11.0", "1.12.0", "1.13.0");
        runMigration(migrationList, application, configPath);
    }

    private static void runExternalMigration(List<String> migrationList, Application<DockstoreWebserviceConfiguration> application,
        String configPath) {
        migrationList.forEach(migration -> {
            try {
                application.run("db", "migrate", configPath, "--migrations", migration);
            } catch (Exception e) {
                Assert.fail();
            }
        });
    }

    public static void runMigration(List<String> migrationList, Application<DockstoreWebserviceConfiguration> application, String configPath) {
        migrationList.forEach(migration -> {
            try {
                application.run("db", "migrate", configPath, "--include", migration);
            } catch (Exception e) {
                Assert.fail();
            }
        });
    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 2 and optionally deleting BitBucket tokens
     *
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication,
        TestingPostgres testingPostgres, boolean needBucketToken) throws Exception {
        LOG.info("Dropping and Recreating the database with confidential 2 test data");
        cleanStatePrivate2(support, CONFIDENTIAL_CONFIG_PATH, isNewApplication);
        if (!needBucketToken) {
            deleteBitBucketToken(testingPostgres);
        }
    }

    /**
     * Wrapper for dropping and recreating database from migrations for test confidential 2
     *
     * @param support reference to testing instance of the dockstore web service
     * @throws Exception
     */
    public static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, boolean isNewApplication, TestingPostgres testingPostgres)
        throws Exception {
        cleanStatePrivate2(support, isNewApplication, testingPostgres, false);
        // TODO: You can uncomment the following line to disable GitLab tool and workflow discovery
        // getTestingPostgres(SUPPORT).runUpdateStatement("delete from token where tokensource = 'gitlab.com'");
    }

    /**
     * Drops and recreates database from migrations for test confidential 2
     *
     * @param support    reference to testing instance of the dockstore web service
     * @param configPath
     * @throws Exception
     */
    private static void cleanStatePrivate2(DropwizardTestSupport<DockstoreWebserviceConfiguration> support, String configPath,
        boolean isNewApplication) throws Exception {
        Application<DockstoreWebserviceConfiguration> application;
        if (isNewApplication) {
            application = support.newApplication();
        } else {
            application = support.getApplication();
        }
        application.run("db", "drop-all", "--confirm-delete-everything", configPath);

        List<String> migrationList = Arrays.asList("1.3.0.generated", "1.3.1.consistency");
        runMigration(migrationList, application, configPath);

        migrationList = Collections.singletonList(
                new File("../dockstore-webservice/src/main/resources/migrations.test.confidential2.xml").getAbsolutePath());
        runExternalMigration(migrationList, application, configPath);


        migrationList = Arrays.asList("1.4.0", "1.5.0");
        runMigration(migrationList, application, configPath);

        migrationList = Collections.singletonList(
                new File("../dockstore-webservice/src/main/resources/migrations.test.confidential2_1.5.0.xml").getAbsolutePath());
        runExternalMigration(migrationList, application, configPath);

        migrationList = Arrays.asList("1.6.0", "1.7.0", "1.8.0", "1.9.0", "1.10.0", "1.11.0", "1.12.0", "1.13.0");
        runMigration(migrationList, application, configPath);
    }

    public static void checkToolList(String log) {
        Assert.assertTrue(log.contains("NAME"));
        Assert.assertTrue(log.contains("DESCRIPTION"));
        Assert.assertTrue(log.contains("GIT REPO"));
    }

    /**
     * This method will create and register a new container for testing
     *
     * @return DockstoreTool
     * @throws ApiException comes back from a web service error
     */
    public static DockstoreTool getContainer() {
        DockstoreTool c = new DockstoreTool();
        c.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        c.setName("testUpdatePath");
        c.setGitUrl("https://github.com/DockstoreTestUser2/dockstore-tool-imports");
        c.setDefaultDockerfilePath("/Dockerfile");
        c.setDefaultCwlPath("/dockstore.cwl");
        c.setRegistryString(Registry.DOCKER_HUB.getDockerPath());
        c.setIsPublished(false);
        c.setNamespace("testPath");
        c.setToolname("test5");
        c.setPath("quay.io/dockstoretestuser2/dockstore-tool-imports");
        Tag tag = new Tag();
        tag.setName("1.0");
        tag.setReference("master");
        tag.setValid(true);
        tag.setImageId("123456");
        tag.setCwlPath(c.getDefaultCwlPath());
        tag.setWdlPath(c.getDefaultWdlPath());
        List<Tag> tags = new ArrayList<>();
        tags.add(tag);
        c.setWorkflowVersions(tags);
        return c;
    }
}
