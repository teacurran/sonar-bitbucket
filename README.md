# SonarQube BitBucket Plugin

This is a fork of the SonarQube GitHub Plugin to offer the same functionality to BitBucket.

https://github.com/SonarCommunity/sonar-github

For BitBucket API calls, I have put together a client library here:
https://github.com/teacurran/wirelust-bitbucket-api

# Usage

1. Install plugin in Sonarqube 
2. Run sonar via maven in preview mode 

    ```
    mvn clean sonar:sonar 
        -Dsonar.analysis.mode=preview
        -Dsonar.bitbucket.login={YOUR_BITBUCKET_USER}
        -Dsonar.bitbucket.password={YOUR_PASSWORD}
        -Dsonar.bitbucket.pullRequest={PULL_REQUEST_NUMBER}
        -Dsonar.bitbucket.client.id={BITBUCKET_CLIENT_ID}
        -Dsonar.bitbucket.client.secret={BITBUCKET_CLIENT_SECRET}
    ```

3. You may have to disable some other plugins in order to get it to work, here are the settings I have to use:

    ```
    -Dissueassignplugin.enabeld=false
    -Dsonar.scm-stats.enabled=false
    -Dsonar.scm.enabled=false
    -Dsonar.bitbucket.repository=teacurran/intl-litpro
    -Dsonar.preview.excludePlugins=buildstability,devcockpit,pdfreport,report,views,jira,buildbreaker,issueassign,scm,scm-stats 
    -Dsonar.issuesReport.console.enable=true
    ```

# Parameters

Most of these options will appear in SonarQube in your global or project settings. 

The only two settings that need to be passed in via maven are:

* sonar.bitbucket.repository
* sonar.bitbucket.pullRequest


| Parameter name                               | Description                                                                                                                           |
|----------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| sonar.bitbucket.login                        | Username for logging into BitBucket                                                                                                   |
| sonar.bitbucket.password                     | Password for logging into BitBucket                                                                                                   |
| sonar.bitbucket.apiKey                       | If you want to create pull request comments for Sonar issues under your team account, provide the API key for your team account here. |
| sonar.bitbucket.client.id                    | Bitbucket client id, required in addition to login                                                                                    |
| sonar.bitbucket.client.secret                | Bitbucket client secret, required in addition to login                                                                                |
| sonar.bitbucket.pullRequest                  | Pull request ID you wish to analyze                                                                                                   |
| sonar.bitbucket.threshold                  | Minimum issue severity in which a pull request can be approved. [BLOCKER, CRITICAL, MAJOR, MINOR, INFO]                                 |


