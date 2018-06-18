pipeline {
  agent any
  stages {
    stage('Build Help Set') {
      steps {
        build 'terma-help-docs'
        writeFile(file: 'build_info', text: '${WORKSPACE} ${GIT_BRANCH}')
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

cp -r /var/jenkins_home/workspace/terma-help-docs/dist /opt/termahelp/${BRANCH}'''
      }
    }
  }
}