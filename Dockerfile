FROM gradle:jdk8

COPY ./generator/ ./
RUN gradle distZip --no-daemon
RUN unzip build/distributions/generator-0.0.1.zip
RUN chmod +x generator-0.0.1/bin/generator

ENTRYPOINT ["sh", "generator-0.0.1/bin/generator"]
