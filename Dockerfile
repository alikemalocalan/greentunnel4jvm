FROM adoptopenjdk/openjdk11:jdk-11.0.8_10-alpine-slim

COPY . /src
WORKDIR /src
RUN ./gradlew --no-daemon shadowJar

RUN mkdir /bin/runner
RUN cp /src/build/libs/greentunnel4jvm.jar /bin/runner/
WORKDIR /bin/runner
RUN rm -rf /src

CMD ["java","-XX:MaxRAMPercentage=45", "-XshowSettings:vm","-jar","greentunnel4jvm.jar"]
EXPOSE 8080