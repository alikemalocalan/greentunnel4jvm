FROM adoptopenjdk/openjdk11:alpine-slim

COPY . /src
WORKDIR /src
RUN ./gradlew --no-daemon shadowJar

RUN mkdir /bin/runner
RUN cp /src/build/libs/greentunnel4jvm.jar /bin/runner/
RUN rm -rf /src
RUN rm -rf /root/.gradle
WORKDIR /bin/runner

#-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC
CMD ["java","-XX:MaxRAMPercentage=45", "-XshowSettings:vm","-Dio.netty.tryReflectionSetAccessible=true","--illegal-access=warn","--add-opens","java.base/jdk.internal.misc=ALL-UNNAMED","-cp","greentunnel4jvm.jar","com.github.alikemalocalan.greentunnel4jvm.ProxyServerKt"]
EXPOSE 8080