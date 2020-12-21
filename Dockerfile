# Multi-stage docker build.  First load CentOS and install wget
FROM centos:7 as wgetsrc
RUN yum install wget -y

# Load the hardened jdk image we use to run puckboard
FROM registry.il2.dso.mil/platform-one/devops/pipeline-templates/harden-jdk/jdk11-headless:8.2.276

# Run as a unprivileged user
USER appuser

# Copy the puckboard binaries
VOLUME /tmp
COPY target/*.jar /app/commonapi.jar

# Copy the wget binaries build in stage 1
COPY --from=wgetsrc /usr/bin/wget /usr/bin
COPY --from=wgetsrc /usr/lib64/libssl.so.10 /usr/lib64/
COPY --from=wgetsrc /usr/lib64/libidn.so.11 /usr/lib64/
COPY --from=wgetsrc /usr/lib64/libcrypto.so.10 /usr/lib64/

ENTRYPOINT ["java","-Xms512m","-Xmx1024m","-XX:+HeapDumpOnOutOfMemoryError","-XX:HeapDumpPath=/tmp/heap-dump.core","-jar","/app/commonapi.jar"]
