# Base OS: Ubuntu 24.04 LTS
FROM ubuntu:24.04

# 설치 중 사용자의 입력을 묻지 않도록 설정
ENV DEBIAN_FRONTEND=noninteractive

# 기본 패키지 및 시뮬레이션 툴 설치 (ca-certificates 추가)
RUN apt-get update && apt-get install -y \
build-essential \
make \
gcc \
g++ \
git \
curl \
gnupg \
gnupg2 \
ca-certificates \
gtkwave \
autoconf \
flex \
bison \
verilator \
zip \
unzip \
openjdk-17-jdk \
libc6 libstdc++6 zlib1g \
&& rm -rf /var/lib/apt/lists/*

# # SDKMAN으로 SBT 설치 (안정적)
# RUN curl -s "https://get.sdkman.io" | bash && \
#     bash -c "source /root/.sdkman/bin/sdkman-init.sh && sdk install sbt"

# SBT 설치 (SDKMAN 방식은 쉘 스크립트 실행이 복잡하므로 공식 deb 방식 추천)
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add && \
    apt-get update && apt-get install -y sbt


# Mill 최신 버전을 다운로드하고 실행 권한을 부여합니다.
RUN curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.7/0.11.7 > /usr/local/bin/mill && \
chmod +x /usr/local/bin/mill

# firtool (CIRCT) 엔진 수동 장착 (Chisel 6.x 필수 부품)
# Maven에서 직접 인텔용 JAR를 받아 압축을 풀고 시스템에 꽂아넣습니다.
RUN curl -L -O https://repo1.maven.org/maven2/org/chipsalliance/llvm-firtool/1.62.1/llvm-firtool-1.62.1-linux-x64.jar && \
    mkdir firtool_extracted && cd firtool_extracted && \
    jar xvf ../llvm-firtool-1.62.1-linux-x64.jar && \
    cp $(find . -name "firtool" -type f) /usr/local/bin/ && \
    chmod +x /usr/local/bin/firtool && \
    cd .. && rm -rf firtool_extracted llvm-firtool-1.62.1-linux-x64.jar

# 환경 변수 설정 (Chisel이 firtool을 바로 찾을 수 있게 고정)
ENV CHISEL_FIRTOOL_PATH=/usr/local/bin
ENV RISCV=/usr/local

# 작업 디렉토리 설정
WORKDIR /workspace/lzero_01_rtl

# 컨테이너 실행 시 기본으로 띄울 쉘
CMD ["/bin/bash"]