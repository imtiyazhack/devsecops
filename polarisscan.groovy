/*******************************************************************************
 * Function : call
 * Parameter:
 *  - polarisscan(projectname,projecttype,projectdirectory) : string - name of the project to perform the scan
 *  - projectype : string - specify type of project
 *  - projectdirectory : string - specify project directory
 * Description:
 *   master groovy file - Polaris
 *
 * Pre-requisite:
 *  - Project should be on-boarded into Polaris
 *
 *
 * Usage:
 *   `polarisscan("project_name",PROJECT_TYPE,PROJECT_DIRECTORY)`
 *
 *******************************************************************************/
// Printing Scan report in CICD pipeline
def createJsonReport(projectName, critical, high, medium, low, LBUNAME) {
  String reportName = 'polaris_results.json'
  sh """
        echo '{' > ${reportName}
        echo '  "toolName": "Polaris",' >> ${reportName}
        echo '  "lbuName": "${LBUNAME}",' >> ${reportName}
        echo '  "projectName": "${projectName}",' >> ${reportName}
        echo '  "critical": ${critical},' >> ${reportName}
        echo '  "high": ${high},' >> ${reportName}
        echo '  "medium": ${medium},' >> ${reportName}
        echo '  "low": ${low}' >> ${reportName}
        echo '}' >> ${reportName}
    """
}

 // Declaration of Envrionment Variables
withCredentials([
        usernamePassword(credentialsId: 'KEY IN ARTIFACTORY CREDENTIAL', usernameVariable: 'RITS_ARTIUSERNAME', passwordVariable: 'RITS_ARTIPASSWORD'),
        string(credentialsId: 'KEY IN POLORIS API KEY', variable: 'POLARIS_TOKEN')
    ]) {
        echo '-----------------------------------------------------------------------------------------------------------------'
        echo '-------------------------- POLARIS LIB STARTING -----------------------------------------------------'
        echo '-----------------------------------------------------------------------------------------------------------------'
        env.P_CLI = 'polaris'
        env.P_CLI_ZIP = "${P_CLI}.zip"
        env.P_CLI_PATH = "POLARIS CLI PATH"
        env.P_URL = 'POLARIS ENVIONMENT URL'
        env.SCAN_OUTFILE = 'OUTPUT LOCATION TO STORE THE FILE'
        env.PROJECTNAME = "${project_name}"
        env.LBUNAME = PROJECTNAME.substring(0, PROJECTNAME.indexOf('-'))
        env.PROJECTTYPE = "${project_type}"
        env.PROJECTDIR = "${project_directory}"
        env.PROJID = 'pid.txt'
        env.POLARIS_HOME = 'PATH TO COVERITY FILE'


  

        sh '''
            echo '====================================================================================================================================='
            echo '===============================================   Authentication API Token =========================================================='
            echo '====================================================================================================================================='
            echo ptoken=$POLARIS_API_TOKEN
            curl -u ${RITS_ARTIUSERNAME}:${RITS_ARTIPASSWORD} -O 'PATH TO DOWNLOAD JQ'
            chmod +x jq
            ls -al | grep jq
            export token=$(curl -k -v \
                -X POST -H "Accept:application/json" -H "Content-Type:application/x-www-form-urlencoded" \
                --data "accesstoken=${POLARIS_TOKEN}" \
                ${P_URL}/api/auth/v1/authenticate | ./jq -r '.jwt')
            echo ${token}
            echo '====================================================================================================================================='
            echo '===============================================   Retrieve Project Details ============================================================'
            echo '====================================================================================================================================='
            export projid=$(curl -v -X GET \
                "${P_URL}/api/common/v0/projects?page%5Blimit%5D=25&amp;page%5Boffset%5D=0&amp;filter%5Bproject%5D%5Bname%5D%5B%24eq%5D=${PROJECTNAME}" \
                -H 'accept: application/vnd.api+json' \
                -H 'Authorization: Bearer '${token} > projidd.txt)
            cat projidd.txt
            export pid=$(cat projidd.txt | ./jq -r '.data.id')
            echo ${pid} > ${PROJID}
        '''

        projid = readFile("${PROJID}").trim()
        println(projid)

        if (projid != 'null') {
      if (env.PROJECTTYPE == 'WEB') {
        sh '''
                    echo '====================================================================================================================================='
                    echo '===============================================   Executing Scan with WEB CONFIG ==============================================================='
                    echo '====================================================================================================================================='
                    export POLARIS_SERVER_URL="${P_URL}"
                    export POLARIS_ACCESS_TOKEN="${POLARIS_TOKEN}"
                    echo "P_CLI= ${P_CLI}"
                    echo "P_CLI_ZIP= ${P_CLI_ZIP}"
                    echo "P_CLI_PATH= ${P_CLI_PATH}"
                    echo "PROJECTNAME= ${PROJECTNAME}"
                    echo "PROJECTDIR= ${PROJECTDIR}"
                    echo "POLARIS_SERVER_URL=$POLARIS_SERVER_URL"
                    rm -rf polaris.zip
                    rm -rf polaris
                    curl -u "${RITS_ARTIUSERNAME}":"${RITS_ARTIPASSWORD}" -O ${P_CLI_PATH}
                    unzip "${P_CLI_ZIP}"
                    chmod +x "${P_CLI}"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "Executing Polaris scans"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    cat >polaris.yml <<"FILE_END"
version: 1
project:
  name: PROJECTNAME
  branch: ${scm.git.branch}
  revision:
    name: ${scm.git.commit}
    date: ${scm.git.commit.date}
capture:
  coverity:
    buildless:
      sourceMode:
        sourceDir: PROJECTDIR
analyze:
  mode: central
  coverity:
    cov-analyze: [ "--all", "--webapp-security", "--sigma", "enable", "--analyze-node-modules", "--distrust-all", "--enable-default"]
install:
  coverity:
    version: 2024.9.1
FILE_END
                    sed -i "s/PROJECTNAME/$PROJECTNAME/" polaris.yml
                    sed -i "s#PROJECTDIR#\"\$PROJECTDIR\"#" polaris.yml
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    ls -alh | grep polaris
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    cat polaris.yml
                    ./polaris analyze -w > temp.txt
                    cat temp.txt
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "Summary of identified vulnerabilities in the scan"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "Kindly login to Polaris portal to check further details : ${P_URL}"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                '''
        String critical = sh(script: "cat temp.txt | grep '\"Critical\":' | cut -d \":\" -f 2 | tr -d ' ' | tr -d ','", returnStdout: true).trim()
        String high = sh(script: "cat temp.txt | grep '\"High\":' | cut -d \":\" -f 2 | tr -d ' ' | tr -d ','", returnStdout: true).trim()
        String medium = sh(script: "cat temp.txt | grep '\"Medium\":' | cut -d \":\" -f 2 | tr -d ' ' | tr -d ','", returnStdout: true).trim()
        String low = sh(script: "cat temp.txt | grep '\"Low\":' | cut -d \":\" -f 2 | tr -d ' ' | tr -d ','", returnStdout: true).trim()
        createJsonReport(project_name, critical, high, medium, low, env.LBUNAME)
            } else if (env.PROJECTTYPE == 'ANDROID') {
        sh '''


                    echo '====================================================================================================================================='
                    echo '===============================================   Executing Scan with ANDROID CONFIG ==============================================================='
                    echo '====================================================================================================================================='
                    export POLARIS_SERVER_URL="${P_URL}"
                    export POLARIS_ACCESS_TOKEN="${POLARIS_TOKEN}"
                    echo "P_CLI= ${P_CLI}"
                    echo "P_CLI_ZIP= ${P_CLI_ZIP}"
                    echo "P_CLI_PATH= ${P_CLI_PATH}"
                    echo "PROJECTNAME= ${PROJECTNAME}"
                    echo "PROJECTDIR= ${PROJECTDIR}"
                    echo "POLARIS_SERVER_URL=$POLARIS_SERVER_URL"
                    echo "${DC_ID}"
                    rm -rf polaris.zip
                    rm -rf polaris
                    curl -u "${RITS_ARTIUSERNAME}":"${RITS_ARTIPASSWORD}" -O ${P_CLI_PATH}
                    unzip "${P_CLI_ZIP}"
                    chmod +x "${P_CLI}"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "Executing Polaris scans"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    cat >polaris.yml <<"FILE_END"
version: 1
project:
  name: PROJECTNAME
  branch: ${scm.git.branch}
  revision:
    name: ${scm.git.commit}
    date: ${scm.git.commit.date}
capture:
  coverity:
    buildless:
      sourceMode:
        sourceDir: PROJECTDIR
analyze:
  mode: central
  coverity:
    cov-analyze: ["--disable-default", "--android-security"]
install:
  coverity:
    version: 2024.9.1
FILE_END
                    sed -i "s/PROJECTNAME/$PROJECTNAME/" polaris.yml
                    sed -i "s#PROJECTDIR#\"\$PROJECTDIR\"#" polaris.yml
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    ls -alh | grep polaris
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    cat polaris.yml
                    ./polaris analyze -w > temp.txt
                    cat temp.txt
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "Summary of identified vulnerabilities in the scan"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                    echo "Kindly login to Polaris portal to check further details : ${P_URL}"
                    echo "-----------------------------------------------------------------------------------------------------------------"
                '''
        String critical = sh(script: "cat temp.txt | grep '\"Critical\":' | cut -d \":\" -f 2 | tr -d ' ' | tr -d ','", returnStdout: true).trim()
        String high = sh(script: "cat temp.txt | grep '\"High\":' | cut -d \":\" -f 2 | tr -d ' ' | tr -d ','", returnStdout: true).trim()
        String medium = sh(script: "cat temp.txt | grep '\"Medium\":' | cut -d \":\" -f 2 | tr -d ' ' | tr -d ','", returnStdout: true).trim()
        String low = sh(script: "cat temp.txt | grep '\"Low\":' | cut -d \":\" -f 2 | tr -d ' ' | tr -d ','", returnStdout: true).trim()
        createJsonReport(project_name, critical, high, medium, low, env.LBUNAME)
            } else {
        println('Incorrect configuration defined, please refer to the integration guide')
      }
        } else {
      println("PROJECT DOESN'T EXIST IN THE POLARIS SERVER, PLEASE CHECK THAT THE CORRECT PROJECT NAME IS PROVIDED, IT SHOULD BE AS PER JIRA ONBOARDING TICKET")
        }
    }
