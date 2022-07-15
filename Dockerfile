# syntax=docker/dockerfile:1

FROM maven:3-jdk-11

COPY . /tmp/reuse-metadata-creation
RUN cd /tmp/reuse-metadata-creation && mvn clean package

RUN apt-get update && apt-get install -y --no-install-recommends --no-install-suggests build-essential curl python3 python3-dev python3-pip
RUN curl https://sh.rustup.rs -sSf | sh -s -- -y

ENV PATH="/root/.cargo/bin:${PATH}" 
RUN cargo install askalono-cli

RUN pip3 install setuptools
RUN pip3 install reuse

ENTRYPOINT [ "java", "-jar", "/tmp/reuse-metadata-creation/target/metadata-creation-tool-for-reuse-0.0.1-SNAPSHOT-jar-with-dependencies.jar" ]