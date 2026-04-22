FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package
RUN set -eux; \
		mkdir -p /workspace/runtime-jars; \
		for artifact in \
			org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1 \
			org.apache.spark:spark-token-provider-kafka-0-10_2.12:3.5.1 \
			org.apache.kafka:kafka-clients:3.4.1 \
			org.lz4:lz4-java:1.8.0 \
			org.xerial.snappy:snappy-java:1.1.10.3 \
			org.slf4j:slf4j-api:2.0.7 \
			org.apache.hadoop:hadoop-client-runtime:3.3.4 \
			org.apache.hadoop:hadoop-client-api:3.3.4 \
			commons-logging:commons-logging:1.1.3 \
			com.google.code.findbugs:jsr305:3.0.0 \
			org.apache.commons:commons-pool2:2.11.1 \
			org.apache.spark:spark-avro_2.12:3.5.1 \
			org.tukaani:xz:1.9 \
			org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.5.2 \
			org.apache.hadoop:hadoop-aws:3.3.4 \
			com.amazonaws:aws-java-sdk-bundle:1.12.262 \
			org.wildfly.openssl:wildfly-openssl:1.0.7.Final; do \
			mvn -q dependency:copy -Dartifact="$artifact" -DoutputDirectory=/workspace/runtime-jars; \
		done

FROM apache/spark:3.5.1 AS spark-runtime
USER root
RUN apt-get update \
	&& apt-get install -y --no-install-recommends openjdk-17-jre-headless \
	&& ARCH="$(dpkg --print-architecture)" \
	&& JAVA17_HOME="/usr/lib/jvm/java-17-openjdk-${ARCH}" \
	&& test -x "${JAVA17_HOME}/bin/java" \
	&& update-alternatives --set java "${JAVA17_HOME}/bin/java" \
	&& ln -sfn "${JAVA17_HOME}" /usr/lib/jvm/java-17-openjdk \
	&& ln -sfn "${JAVA17_HOME}" /usr/lib/jvm/java-17-openjdk-amd64 \
	&& rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/runtime-jars /opt/spark/jars/
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk
ENV PATH=${JAVA_HOME}/bin:${PATH}
USER spark

FROM spark-runtime
WORKDIR /opt/app
COPY --from=build /workspace/target/spark-job-1.0.0.jar /opt/app/spark-job.jar
COPY --from=build /workspace/runtime-jars /opt/spark/jars/
COPY sql /opt/app/sql
