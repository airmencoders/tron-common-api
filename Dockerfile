# Load the hardened jdk image we use to run common
FROM registry.il2.dso.mil/platform-one/devops/pipeline-templates/base-image/harden-openjdk11-jre:11.0.11

ENV CONTEXTS DEV

# Run as a unprivileged user
USER appuser

# Copy the binaries
VOLUME /tmp
COPY target/common-api.jar /app/commonapi.jar

ENTRYPOINT ["java","-Xms512m","-Xmx1024m","-XX:+HeapDumpOnOutOfMemoryError","-XX:HeapDumpPath=/tmp/heap-dump.core","-jar","/app/commonapi.jar"]