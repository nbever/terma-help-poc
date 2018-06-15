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
        sh 'echo ${WORKSPACE}: ${GIT_BRANCH}'
      }
    }
  }
}