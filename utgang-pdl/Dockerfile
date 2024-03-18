FROM ghcr.io/navikt/baseimages/temurin:21
ENV APPLIKASJON_JAR=App.jar

COPY build/libs/fat.jar app.jar
CMD ["java", "-jar", "$APPLIKASJON_JAR"]