/*******************************************************************************
 * Function : call
 * Parameter:
 *  - appId   : string - application identifier in nexus
 *  - scanDir : string - relative path of the code to be scanned
 *  - stage   : string - stage of the code to be scanned (dev, uat, preprod, prod, etc), arbitrary from app-dev team
 *
 * Description:
 *   convenience method for nexus scan abstraction.
 *
 * Pre-requisite:
 *  - artifactory username and token to download the nexus-cli
 *  - nexus login to upload the scan result
 *  - nexus trust store pass for nexus cert
 *
 * Usage:
 *   `String result = nexusscan("IcommerceBFF", "$WORKSPACE", "stage-release");`
 *
 *******************************************************************************/

 {
  withCredentials([
      usernamePassword(credentialsId: 'KEY IN ARTIFACTORY CREDENTIAL', usernameVariable: 'RITS_ARTIUSERNAME', passwordVariable: 'RITS_ARTIPASSWORD'),
      usernamePassword(credentialsId: 'KEY IN NEXUS CREDENTIAL', usernameVariable: 'RITS_NEXUSLOGIN', passwordVariable: 'RITS_NEXUSPASSWORD'),
      string(credentialsId: 'KEYSTORE CREDENTIAL', variable: 'RITS_NEXUS_TRUSTSTORE_PASS') //Optional
  ]) {
    env.VAR_APP_ID = "${appId}"
    env.VAR_SCAN_DIR = "${scanDir}"
    env.VAR_STAGE = "${stage}"
    env.PARAM_ARTI_BASE_URL = 'NEXUS SCANNER CLI PATH'
    env.PARAM_ARTI_JAR_NAME = 'NAME OF CLI' //nexus-iq-cli-standaloneCLI-179
    env.PARAM_NEXUS_URL = 'NEXUS SERVER URL'
    env.PARAM_RESULT_FILE = 'nexus_results.json'

    sh '''
      unset HTTPS_PROXY
      unset HTTP_PROXY
      if ! [ -f ${PARAM_ARTI_JAR_NAME} ]; then
        curl --user ${RITS_ARTIUSERNAME}:${RITS_ARTIPASSWORD}           \
             --remote-name                                                   \
             --location ${PARAM_ARTI_BASE_URL}/${PARAM_ARTI_JAR_NAME}

      fi
      ls -al
      chmod +x "${PARAM_ARTI_JAR_NAME}"
      ls -al
      ./${PARAM_ARTI_JAR_NAME}                                         \
           -i ${VAR_APP_ID}                                                  \
           -s ${PARAM_NEXUS_URL}                                             \
           -t ${VAR_STAGE}                                                   \
           --ignore-scanning-errors \
           -r ${VAR_APP_ID}_${PARAM_RESULT_FILE}                             \
           -a ${RITS_NEXUSLOGIN}:${RITS_NEXUSPASSWORD}                       \
           ${VAR_SCAN_DIR}

    '''
  }
}
