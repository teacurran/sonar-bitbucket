# SonarQube BitBucket Plugin

This is a fork of the SonarQube GitHub Plugin to offer the same functionality to BitBucket.

https://github.com/SonarCommunity/sonar-github

I have started with the source for SonarQube GitHub Plugin and am changing the API calls to call BitBucket.

For BitBucket API calls, it is using a library here:
https://github.com/teacurran/wirelust-bitbucket-api

# Status
Currently the plugin is authenticating, getting the pull request, and properly commenting with both in-line comments
and a global pull request comment. It is still buggy, I will update the documentation on how to use when it is more 
complete.

# Todo

* Fix issues where old comments don't always get deleted leading to duplicates
* clean up the code 
* remove references to github and github API
* add pull request approval when all Sonar checks pass
* remove any previous approvals when Sonar checks don't pass

