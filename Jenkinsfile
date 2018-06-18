pipeline {
  agent any
  stages {
    stage('Build Help Set') {
      steps {
        sh './build.sh'
      }
    }
    stage('Deploy to Latest') {
      when {
        branch 'master'
      }
      steps {
        sh 'mkdir -p /opt/termahelp/v5.7.3'
        sh 'cp -r ./dist/* /opt/termahelp/v5.7.3'
      }
    }
    stage('Deploy named version') {
      when {
        branch 'v*'
      }
      steps {
        sh 'mkdir -p /opt/termahelp/${GIT_BRANCH}'
        sh 'cp -r ./dist/* /opt/termahelp/${GIT_BRANCH}'
      }
    }
  }
  environment {
    CI = 'true'
  }
}
