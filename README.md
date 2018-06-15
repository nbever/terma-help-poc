# terma-help-poc
POC for building a homegrown DITA help system

# Overall Architecture

This proof of concept is built around the concept of having Docker containers responsible for executing all pieces of the solution, backed by a GIT repository.  The overall pipeline flow is like this:

## Setup
* Build the jenkins container so that it contains the ditac tool (DITA compiler):
  `docker build -f jenkins-dockerfile -t <some readable name> .`
* Build the help web server which lives in teh "helpServer" directory
  `docker build -t help-web-server .`
* Run the jenkins server and configure it to point to the GIT repository.  
  `docker run -u root -d --rm -p 8080:8080 -v <path to shared data dir on host>:/var/jenkins_home <container name>`
* The configuration for pipelines is already in this repository but you'll need to create a job called 'terma-help-docs' which builds the help source.  Obviously it needs to pull the code from the GIT branch, but then the build step is this:
  ```/opt/ditac/bin/ditac -images images -p xsl-resources-directory resources -f html -p chain-topics yes -p chain-pages both /var/jenkins_home/${GIT_BRANCH}/help.html ${WORKSPACE}/src/main.ditamap```

* Run the web help docker container
  `docker run -u root --rm -d -p 8083:8083 -v <path to shared data dir on host>:/opt/termahelp help-web-server`

## Workflow
* User pulls source code from GIT and navigates to teh appropriate branch
* Changes are made to the document on a branch and a PR is created for review.
* After review, the code is merged into the "master" version branch

## Automated Workflow
* When code is merged into master or a version branch it will trigger jenkins to compiler the code
* When compilation is complete, jenkins will move on to step 2 in the pipeline which copies the built items into a version directory under /opt/termahelp causing it be immediately available.
