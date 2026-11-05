# Script to run CareQueue Spring Boot backend with clean classpath resolution
$ErrorActionPreference = "Stop"

if (Test-Path "$PSScriptRoot\.env") {
    Get-Content "$PSScriptRoot\.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $name = $parts[0].Trim()
            $value = $parts[1].Trim()
            [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

$repo = "C:\Users\sanja\.m2\repository"
$classes = Resolve-Path "$PSScriptRoot\target\classes"

$dependencies = @(
    "org\springframework\boot\spring-boot\3.3.2\spring-boot-3.3.2.jar",
    "org\springframework\boot\spring-boot-autoconfigure\3.3.2\spring-boot-autoconfigure-3.3.2.jar",
    "org\springframework\boot\spring-boot-starter\3.3.2\spring-boot-starter-3.3.2.jar",
    "org\springframework\boot\spring-boot-starter-web\3.3.2\spring-boot-starter-web-3.3.2.jar",
    "org\springframework\boot\spring-boot-starter-tomcat\3.3.2\spring-boot-starter-tomcat-3.3.2.jar",
    "org\springframework\boot\spring-boot-starter-json\3.3.2\spring-boot-starter-json-3.3.2.jar",
    "org\springframework\boot\spring-boot-starter-logging\3.3.2\spring-boot-starter-logging-3.3.2.jar",
    "org\springframework\boot\spring-boot-starter-jdbc\3.3.2\spring-boot-starter-jdbc-3.3.2.jar",
    "org\springframework\spring-core\6.1.11\spring-core-6.1.11.jar",
    "org\springframework\spring-beans\6.1.11\spring-beans-6.1.11.jar",
    "org\springframework\spring-context\6.1.11\spring-context-6.1.11.jar",
    "org\springframework\spring-web\6.1.11\spring-web-6.1.11.jar",
    "org\springframework\spring-webmvc\6.1.11\spring-webmvc-6.1.11.jar",
    "org\springframework\spring-expression\6.1.11\spring-expression-6.1.11.jar",
    "org\springframework\spring-aop\6.1.11\spring-aop-6.1.11.jar",
    "org\springframework\spring-jcl\6.1.11\spring-jcl-6.1.11.jar",
    "org\springframework\spring-jdbc\6.1.11\spring-jdbc-6.1.11.jar",
    "org\springframework\spring-tx\6.1.11\spring-tx-6.1.11.jar",
    "org\apache\tomcat\embed\tomcat-embed-core\10.1.26\tomcat-embed-core-10.1.26.jar",
    "org\apache\tomcat\embed\tomcat-embed-el\10.1.26\tomcat-embed-el-10.1.26.jar",
    "org\apache\tomcat\embed\tomcat-embed-websocket\10.1.26\tomcat-embed-websocket-10.1.26.jar",
    "com\fasterxml\jackson\core\jackson-databind\2.17.2\jackson-databind-2.17.2.jar",
    "com\fasterxml\jackson\core\jackson-core\2.17.2\jackson-core-2.17.2.jar",
    "com\fasterxml\jackson\core\jackson-annotations\2.17.2\jackson-annotations-2.17.2.jar",
    "com\fasterxml\jackson\datatype\jackson-datatype-jdk8\2.17.2\jackson-datatype-jdk8-2.17.2.jar",
    "com\fasterxml\jackson\datatype\jackson-datatype-jsr310\2.17.2\jackson-datatype-jsr310-2.17.2.jar",
    "com\fasterxml\jackson\module\jackson-module-parameter-names\2.17.2\jackson-module-parameter-names-2.17.2.jar",
    "ch\qos\logback\logback-classic\1.5.6\logback-classic-1.5.6.jar",
    "ch\qos\logback\logback-core\1.5.6\logback-core-1.5.6.jar",
    "org\slf4j\slf4j-api\2.0.13\slf4j-api-2.0.13.jar",
    "org\slf4j\jul-to-slf4j\2.0.13\jul-to-slf4j-2.0.13.jar",
    "org\apache\logging\log4j\log4j-to-slf4j\2.23.1\log4j-to-slf4j-2.23.1.jar",
    "org\apache\logging\log4j\log4j-api\2.23.1\log4j-api-2.23.1.jar",
    "org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar",
    "jakarta\annotation\jakarta.annotation-api\2.1.1\jakarta.annotation-api-2.1.1.jar",
    "io\micrometer\micrometer-observation\1.13.2\micrometer-observation-1.13.2.jar",
    "io\micrometer\micrometer-commons\1.13.2\micrometer-commons-1.13.2.jar",
    "com\zaxxer\HikariCP\5.1.0\HikariCP-5.1.0.jar",
    "com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar"
)

$jarPaths = $dependencies | ForEach-Object { Join-Path $repo $_ }
$classpath = ($classes.Path + ";" + ($jarPaths -join ";"))

Write-Host "Starting CareQueue Spring Boot Backend on http://localhost:8080..."
& java -cp "$classpath" com.savemom.appointmentqueue.AppointmentQueueApplication
