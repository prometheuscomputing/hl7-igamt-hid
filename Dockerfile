FROM eclipse-temurin:8-jdk

RUN mkdir -p /usr/local/hl7-igamt

COPY hl7-igamt.jar /usr/local/hl7-igamt/hl7-igamt.jar
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
