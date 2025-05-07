## 1. Gradle을 사용하여 JAR 파일을 빌드
#FROM gradle:8.13-jdk17 AS build
#
#WORKDIR /app
#
#COPY . .
#
## Gradle 명령 실행, JAR 파일 생성
#RUN gradle clean build -x test

# 2. 경량 실행 환경 설정
FROM openjdk:17-jdk-alpine

WORKDIR /app

# 빌드 단계에서 생성된 JAR 파일 복사
COPY build/libs/release/edge-service-0.0.1-SNAPSHOT.jar /app/edge-service.jar

# 실행 명령어 설정
ENTRYPOINT ["java", "-jar", "/app/edge-service.jar"]