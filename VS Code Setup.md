## VS Code Setup for Tron Common API

### Recommended Extensions

*(Paste this into a command prompt to install them all)*
```
code --install-extension --force ckolkman.vscode-postgres
code --install-extension --force DotJoshJohnson.xml
code --install-extension --force eamodio.gitlens
code --install-extension --force ecmel.vscode-html-css
code --install-extension --force GabrielBB.vscode-lombok
code --install-extension --force humao.rest-client
code --install-extension --force ms-azuretools.vscode-docker
code --install-extension --force ms-vscode-remote.remote-wsl
code --install-extension --force msjsdiag.debugger-for-chrome
code --install-extension --force msjsdiag.debugger-for-edge
code --install-extension --force Pivotal.vscode-boot-dev-pack
code --install-extension --force Pivotal.vscode-concourse
code --install-extension --force Pivotal.vscode-manifest-yaml
code --install-extension --force Pivotal.vscode-spring-boot
code --install-extension --force redhat.java
code --install-extension --force redhat.vscode-yaml
code --install-extension --force VisualStudioExptTeam.vscodeintellicode
code --install-extension --force vscjava.vscode-java-debug
code --install-extension --force vscjava.vscode-java-dependency
code --install-extension --force vscjava.vscode-java-pack
code --install-extension --force vscjava.vscode-java-test
code --install-extension --force vscjava.vscode-maven
code --install-extension --force vscjava.vscode-spring-boot-dashboard
code --install-extension --force vscjava.vscode-spring-initializr
code --install-extension --force yzhang.markdown-all-in-one
code --install-extension --force Zignd.html-css-class-completion
```

### Project Setup

1. Generate a token for npm to access the P1 GitLab registry:
   1. Navigate to: https://code.il2.dso.mil/profile/personal_access_tokens
   2. Select scopes: `api` and `read_registry`
   3. Copy the generated token to use in the next step
   4. Add a `.npmrc` file to your home directory with the following contents:

```
@tron:registry=https://code.il2.dso.mil/api/v4/packages/npm/
'//code.il2.dso.mil/api/v4/packages/npm/:_authToken'="<your auth token here>"
```

2. Create the file `.vscode/launch.json` with the following contents (replace with correct postgres user/pass, or remove and set passwords as environment vars)
```
{
    // Use IntelliSense to learn about possible attributes.
    // Hover to view descriptions of existing attributes.
    // For more information, visit: https://go.microsoft.com/fwlink/?linkid=830387
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Common API | 8088 | Postgres",
            "request": "launch",
            "mainClass": "mil.tron.commonapi.CommonApiApplication",
            "projectName": "commonapi",
            "args": "--spring.profiles.active=production --server.port=8088 --debug=true --security.enabled=false --spring.liquibase.contexts=dev --puckboard-url=http://localhost:8080/puckboard-api/v1",
            "env": {
                "PGHOST": "localhost",
                "PGPORT": "5432",
                "PG_DATABASE": "common",
                "APP_DB_ADMIN_PASSWORD": "**********",
                "PG_USER": "postgres"
            }
        },
        {
            "type": "java",
            "name": "Common API | 8088 | Postgres | Security (8089)",
            "request": "launch",
            "mainClass": "mil.tron.commonapi.CommonApiApplication",
            "projectName": "commonapi",
            "preLaunchTask": "run-jwt-admin-background",
            "postDebugTask": "stop-jwt-utility",
            "args": "--spring.profiles.active=production --server.port=8089 --debug=true --security.enabled=true --spring.liquibase.contexts=dev --puckboard-url=http://localhost:8080/puckboard-api/v1",
            "env": {
                "PGHOST": "localhost",
                "PGPORT": "5432",
                "PG_DATABASE": "common",
                "APP_DB_ADMIN_PASSWORD": "**********",
                "PG_USER": "postgres"
            }
        },
        {
            "type": "java",
            "name": "Common API | 8088 | H2",
            "request": "launch",
            "mainClass": "mil.tron.commonapi.CommonApiApplication",
            "projectName": "commonapi",
            "args": "--server.port=8088 --debug=true --security.enabled=false --spring.liquibase.contexts=dev --spring.datasource.driverClassName=org.h2.Driver --spring.h2.console.enabled=true --spring.datasource.username=sa --puckboard-url=http://localhost:8080/puckboard-api/v1",
        },
        {
            "type": "java",
            "name": "Common API | 8088 | H2 | Security (8089)",
            "request": "launch",
            "mainClass": "mil.tron.commonapi.CommonApiApplication",
            "projectName": "commonapi",
            "preLaunchTask": "run-jwt-admin-background",
            "postDebugTask": "stop-jwt-utility",
            "args": "--server.port=8089 --debug=true --security.enabled=true --spring.liquibase.contexts=dev --spring.datasource.driverClassName=org.h2.Driver --spring.h2.console.enabled=true --spring.datasource.username=sa --puckboard-url=http://localhost:8080/puckboard-api/v1",
        }
    ]
}
```

3. Create the file`.vscode/tasks.json` with the following contents

```
{
    // See https://go.microsoft.com/fwlink/?LinkId=733558
    // for the documentation about the tasks.json format
    "version": "2.0.0",
    "tasks": [
        {
            "label": "run-jwt-admin-background",
            "type": "shell",
            "command": "npx jwt-proxy 8088 8089 --silent --jwt admin.jwt --namespace istio-system",
            "isBackground": true,
            "problemMatcher": [
                {
                    "pattern": [
                        {
                            "regexp": ".",
                            "file": 1,
                            "location": 2,
                            "message": 3
                        }
                    ],
                    "background": {
                        "activeOnStart": true,
                        "beginsPattern": ".",
                        "endsPattern": "."
                    }
                }
            ]
        },
        {
            "label": "stop-jwt-utility",
            "type": "shell",
            "command": "npx kill-port 8088"
        },
        {
            "label": "run-jwt-utility",
            "type": "shell",
            "command": "npx jwt-proxy 8088 8089",
            "problemMatcher": []
        },
        {
            "label": "install-jwt-utility",
            "type": "shell",
            "command": "npm i @tron/jwt-cli-utility",
            "problemMatcher": []
        },
    ]
}
```
4. Go to `Terminal` > `Run Task` and select `install-jwt-utility`
5. Press <kbd>F5</kbd>

### Notes

You can select from the various configurations on the blue bottom bar near the left side

You can use the above as examples to create additional configurations (e.g. using other namespaces or jwt users), the `launch.json` and `task.json` files are not tracked by version control, so customize them as much as you like.

In the security enabled configurations, VS Code will run the JWT utility automatically in the background and proxy from 8088 to 8089. It will also automatically kill it when you stop debugging. In the other configurations the API itself runs on 8088, so the port to use from a client perspective is always 8088.

You can use the following `launch.json` to debug the Dashboard UI (separate repo) in either Chrome or Edge:
```
{
    // Use IntelliSense to learn about possible attributes.
    // Hover to view descriptions of existing attributes.
    // For more information, visit: https://go.microsoft.com/fwlink/?linkid=830387
    "version": "0.2.0",
    "configurations": [
        {
            "type": "edge",
            "request": "launch",
            "name": "Debug in Edge",
            "url": "http://localhost:3000",
            "webRoot": "${workspaceFolder}",
        },
        {
            "type": "chrome",
            "request": "launch",
            "name": "Debug in Chrome",
            "url": "http://localhost:3000",
            "webRoot": "${workspaceFolder}",
        }
    ]
}
```
