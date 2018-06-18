pipeline {
  agent any
  environment {
    CI = 'true'
  }
  stages {
    stage('Build Help Set') {
      steps {
        sh 'build.sh'
      }
    }
    stage('Deploy to Latest') {
      when {
        branch 'master'
      }
      steps {
        sh 'cp -r ./dist/* /opt/termahelp/latest'
      }
    }
    stage('Deploy named version') {
      when {
        branch '^v*'
      }
      steps {
       sh 'cp -r ./dist/* /opt/termahelp/${GIT_BRANCH}'
      }
    }
  }
}
