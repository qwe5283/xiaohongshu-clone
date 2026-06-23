# ======================== 构建阶段 ========================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
RUN mkdir -p /root/.m2 && echo '<settings><mirrors><mirror><id>aliyunmaven</id><mirrorOf>*</mirrorOf><name>aliyun</name><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ======================== 运行阶段 ========================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="xiaohongshu"

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# JVM 参数：容器环境优化
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
