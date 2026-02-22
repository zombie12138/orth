# ========= Runtime Stage: Minimal JRE + tini =========
FROM eclipse-temurin:17-jre-jammy
# FROM openjdk:21-jdk-slim

# Install tini and basic tools
RUN apt-get update && DEBIAN_FRONTEND=noninteractive \
    apt-get install -y --no-install-recommends tini wget curl bash && \
    rm -rf /var/lib/apt/lists/*

# Copy the JAR file
WORKDIR /app
COPY orth-admin/target/*.jar /app/orth-admin.jar

# Create log directories (including GC logs)
RUN mkdir -p /data/applogs /data/gclogs /data/err_logs /data/dumps && chmod -R 777 /data

# JVM tuning
# G1GC, MaxRAMPercentage (Xmx ratio),
# InitialRAMPercentage (Xms ratio),
# MinRAMPercentage (used for small-memory Xmx),
# G1: string deduplication,
# TieredStopAtLevel: C1 JIT,
# OOM Exit
ENV JAVA_TOOL_OPTIONS="\
    -XX:+UseG1GC \
    -XX:MaxRAMPercentage=70 \
    -XX:MinRAMPercentage=70 \
    -XX:InitialRAMPercentage=70 \
    -XX:MaxMetaspaceSize=128m \
    -XX:+UseStringDeduplication \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/data/dumps/oom-worker.hprof \
    -XX:ErrorFile=/data/err_logs/hs_err_pid%p.log \
    -Xlog:gc*,safepoint:file=/data/gclogs/gc.log:time,level,tags:filecount=3,filesize=10M \
    "

# Remote debugging on port 5005
ENV JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

EXPOSE 8080 5005

ENV JAVA_OPTS=""

# Set timezone
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# tini forwards signals, cleans up zombie processes, allows graceful shutdown
# sh -c: use shell to expand environment variables
# ENTRYPOINT ["/usr/bin/tini","--","sh","-c"]
# exec: replaces the shell with the current process, used with sh -c
# JAVA_OPTS and default JAVA_TOOL_OPTIONS both take effect
# No need for a PARAMS variable; append directly to the entrypoint
# CMD ["exec java -jar $JAVA_OPTS /app/orth-admin.jar"]

# If the JAVA_OPTS variable is not needed, you can simply:
ENTRYPOINT ["/usr/bin/tini","--"]
CMD ["java", "-jar", "/app/orth-admin.jar"]

