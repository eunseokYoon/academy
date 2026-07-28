# --- build ---
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /src
# 의존성 레이어 캐싱: 소스보다 빌드 스크립트를 먼저 복사한다
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# --- runtime ---
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone && \
    addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
