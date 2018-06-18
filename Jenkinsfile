pipeline {
  agent any
  stages {
    stage('Build Help Set') {
      steps {
        build 'terma-help-docs'
      }
    }
    stage('Deploy') {
      steps {
        sh '''#!/bin/sh

BRANCH = ${GIT_BRANCH}

if [ $BRANCH = "master" ]
then
    BRANCH = `cat VERSION`
fi

cp -r ${WORKSPACE}/dist /opt/termahelp/${BRANCH}'''
      }
    }
  }
}