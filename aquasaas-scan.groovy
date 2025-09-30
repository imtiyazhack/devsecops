/*******************************************************************************
 * Function : call
 * Parameter:
 *  - scan_image_name_tag : string - name and tag of the docker image you want to scan in the format 'image-name:tag'
 *
 * Description:
 *   convenience method for CSEC - Aquasec scan abstraction
 *
 * Pre-requisite:
 *  - artifactory username and token to download the aqua-scanner cli binary file.
 *  - Aquasec login to upload the scan result
 *  - docker image to be scanned shall be present in local registry on the agent.
 *  - jq shall be present, required for local results analysis.
 *  - Application Name should be onboarded to Aqua Console as Aqua Label
 *
 * Usage:
 *    aquasaas(Application Name,Imagename:tag,dc_id)
 *
 *******************************************************************************/
def call(appname, scan_image_name_tag) {
  withCredentials([
        usernamePassword(credentialsId: 'KEY IN ARTIFACTORY CREDENTIAL', usernameVariable: 'RITS_ARTIUSERNAME', passwordVariable: 'RITS_ARTIPASSWORD'),
        usernamePassword(credentialsId: 'KEY IN AQUA CONSOLE CREDS', usernameVariable: 'USER_EMAIL', passwordVariable: 'USER_PASSWORD'), // Aqua Console PROD Creds
        string(credentialsId: 'KEY IN SAAS TOKEN', variable: 'SAAS_TOKEN')
    ]) {
        env.PARAM_ARTI_BASE_URL = 'ARTIFACTORY URL'
        env.PARAM_ARTI_SCANNER_NAME = 'AQUA SCANNER CLI PATH'
        env.PARAM_AQUA_SERVER_URL2 = 'SERVER URL'
        env.AQUA_API_URL = 'AQUA API URL'
        env.VAR_IMAGE_NAME_TAG = "${scan_image_name_tag}"
        env.APP_NAME = "${appname}"

        sh '''
            set -x
            export VAR_REGISTRY_NAME=$(echo $VAR_IMAGE_NAME_TAG | cut -d "." -f 1)
            echo VAR_REGISTRY_NAME= ${VAR_REGISTRY_NAME}
            export LBU_NAME=$(echo $VAR_REGISTRY_NAME | cut -d'-' -f2)
            echo LBU_NAME=${LBU_NAME}
            echo APP_NAME= ${APP_NAME}
            export New_VAR_IMAGE_NAME_TAG=$(echo $VAR_IMAGE_NAME_TAG | cut -d "/" -f2-)
            echo VAR_IMAGE_NAME_TAG= ${VAR_IMAGE_NAME_TAG}
            curl -u ${RITS_ARTIUSERNAME}:${RITS_ARTIPASSWORD} -O ${PARAM_ARTI_BASE_URL}/${PARAM_ARTI_SCANNER_NAME}
            chmod +x scannercli-2410.6.26

            ### jq plugin
            curl -u ${RITS_ARTIUSERNAME}:${RITS_ARTIPASSWORD} -O 'Artifactory path to jq'

            if [ $? -ne 0 ]; then
                echo "Failed to download jq."
                exit 1
            fi

            # Make jq executable
            chmod +x jq
            if [ $? -ne 0 ]; then
                echo "Failed to make jq executable."
                exit 1
            fi

            # Verify jq is present
            ls -al | grep jq
            if [ $? -ne 0 ]; then
                echo "jq not found."
                exit 1
            fi

            echo "jq downloaded and verified successfully."
            chmod +x jq
            ls -al | grep jq

            echo "------------------------------------------" AUTHENTICATION "-----------------------------------------------------------------------"
            # Define the auth request payload
            export payload="{\\"email\\":\\"$USER_EMAIL\\",\\"password\\":\\"$USER_PASSWORD\\"}"

            # Send the POST request and store the response in a variable
            export response=$(curl -s -v -X POST -H "Content-Type: application/json" -d "$payload" "$AQUA_API_URL")

            # Parse the response to get the Bearer Token
            export aquatoken=$(echo "$response" | jq -r ".data.token")

            # Print the Bearer Token
            [ "$DEBUG_MODE" = true ] && echo "[DEBUG]: Bearer Token: $aquatoken"

            echo '===================================================================================================================='
            echo '===============================================   VERIFYING APPLICATION EXISIT ====================================='
            echo '===================================================================================================================='
            export labelname=$(curl -v -X GET \
                -H "Content-Type: application/json" \
                -H "Authorization: Bearer $aquatoken" \
                $PARAM_AQUA_SERVER_URL2/api/v1/settings/labels/$APP_NAME | jq -r '.name')

            echo $labelname

            if [ "$labelname" = "$APP_NAME" ]; then

                echo "-----------------------------------------------------------------------------------------------------------"
                echo "Executing Aquasec scans"
                echo "-----------------------------------------------------------------------------------------------------------"
                ./scannercli-2410.6.26 scan                  \
                    -H ${PARAM_AQUA_SERVER_URL2}                 \
                    -A $SAAS_TOKEN                              \
                    --no-verify                                 \
                    --dockerless                                \
                    --checkonly                                 \
                    --collect-sensitive                         \
                    --scan-malware                              \
                    --registry ${VAR_REGISTRY_NAME}             \
                    --register                                   \
                    --local ${VAR_IMAGE_NAME_TAG} >result2.json
                    echo $?

                # Path to the JSON file
                json_file="./result2.json"

                # Read the JSON file, append LBU_NAME and APP_NAME, and write back to the file
                jq --arg lbu_name "$LBU_NAME" --arg app_name "$APP_NAME" '. + {LBU_NAME: $lbu_name, APP_NAME: $app_name}' "$json_file" > tmp.$$.json && mv tmp.$$.json "$json_file"

                cat result2.json >aquasec_results.json

                echo '=============================================================================================================='
                echo '===============================================   MAPPING APPLICATION TO IMAGE ==============================='
                echo '=============================================================================================================='
                export labelname=$(curl -v -X POST \
                    -H "Content-Type: application/json" \
                    -H "Authorization: Bearer $aquatoken" \
                    -d '{"id": ["'${VAR_REGISTRY_NAME}'","'${New_VAR_IMAGE_NAME_TAG}'"],"type": "image"}' \
                    $PARAM_AQUA_SERVER_URL2/api/v1/settings/labels/$labelname/attach)

                echo '================================================================================================================'
                echo '===============================================   SCAN COMPLETED ==============================================='
                echo "Kindly login to aquasec SAAS portal to check further details : https://2441398e6f.cloud.aquasec.com"
                echo '================================================================================================================'

                exit 0

            else
                echo '================================================================================================================'
                echo '===============================================   SCAN FAILED ==============================================='
                echo " Incorrect application name provided in scan snippet or Application not onboarded to Aqua console"
                echo '================================================================================================================'
            fi

            exit 1

        '''
    }
}
