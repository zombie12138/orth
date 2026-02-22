# ========= Runtime Stage: Minimal JRE + tini + Python3 =========
FROM eclipse-temurin:17-jre-jammy

# Basic tools + Python3 (supports GLUE Python / Shell, etc.)
RUN apt-get update && DEBIAN_FRONTEND=noninteractive \
    apt-get install -y --no-install-recommends tini python3 python3-venv python3-pip bash curl wget ca-certificates && \
    pip install uv && \
    rm -rf /var/lib/apt/lists/*


WORKDIR /app

# Copy executable JAR (modify according to your actual build artifact name)
COPY orth-executor-samples/orth-executor-sample-springboot/target/*.jar /app/orth-executor.jar

# Application logs and GC logs
RUN mkdir -p /data/applogs /data/gclogs /data/err_logs /data/dumps && chmod -R 777 /data

# JVM tuning for low-resource environments (G1 + heap percentage + C1 JIT + OOM exit + GC logs)
ENV JAVA_TOOL_OPTIONS="\
    -XX:+UseG1GC \
    -XX:MaxRAMPercentage=40 \
    -XX:MinRAMPercentage=40 \
    -XX:InitialRAMPercentage=40 \
    -XX:MaxMetaspaceSize=128m \
    -XX:+UseStringDeduplication \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/data/dumps/oom-worker.hprof \
    -XX:ErrorFile=/data/err_logs/hs_err_pid%p.log \
    -Xlog:gc*,safepoint:file=/data/gclogs/gc-worker.log:time,level,tags:filecount=2,filesize=5M \
    "

# Enable remote debugging (default 5005; each container maps to a different host port in compose)
ENV JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Time zone
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Expose ports: executor port + debug port
EXPOSE 9999 5005

# Health check: consider healthy when port is open (you can also switch to /actuator/health)
# HEALTHCHECK --interval=20s --timeout=5s --start-period=20s --retries=5 \
#     CMD bash -lc "timeout 2 bash -lc 'echo > /dev/tcp/127.0.0.1/${ORTH_JOB_EXECUTOR_PORT}' || exit 1"

# Use tini to manage PID 1
ENTRYPOINT ["/usr/bin/tini","--"]
CMD ["java", "-jar", "/app/orth-executor.jar"]