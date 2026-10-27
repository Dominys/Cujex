# Runtime image for the Order service.
#
# The e2e module builds this image with Testcontainers, using the order-service
# directory as the build context. Because `mvn verify` builds the service jar
# before the test module runs, target/*.jar already exists when this runs.
FROM eclipse-temurin:21-jre

WORKDIR /app

# The base image runs as root by default. Create an unprivileged user and run
# the service as it, so a compromised container has no root in the container.
RUN groupadd --system app && useradd --system --gid app --no-create-home app

# The Spring Boot plugin repackages a single executable jar here; the
# non-executable *.jar.original is left untouched and does not match this glob.
COPY target/*.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
