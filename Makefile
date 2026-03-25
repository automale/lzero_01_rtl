# L-ZERO RTL Track 간편 명령어 세트

# 1. 환경 빌드 및 실행
up:
	docker compose up -d --build

# 2. 컨테이너 내부 접속 (가장 많이 씀)
shell:
	zero_rtl_env /bin/bash

setup:
	@echo "Rocket-chip essential library building (5-10min)"
	mkdir -p /usr/local/riscv64-unknown-elf/share/riscv-tests

	# 1. Macros 빌드 2. CDE 빌드 3. Hardfloat 빌드 4. Diplomacy 빌드 5. Rocket-chip 본체 빌드
	cd rocket-chip && \
	/usr/local/bin/mill --no-server macros.publishLocal && \
	/usr/local/bin/mill --no-server cde.publishLocal && \
	/usr/local/bin/mill --no-server "hardfloat[6.7.0].publishLocal" && \
	/usr/local/bin/mill --no-server "diplomacy[6.7.0].publishLocal" && \
	/usr/local/bin/mill --no-server "rocketchip[6.7.0].publishLocal"

	@echo "build succes! 'sbt compile' is available"

# 3. Chisel 컴파일 및 Verilog 생성 (접속 안 하고 밖에서 바로 실행)
gen:
	lzero_rtl_env sbt "runMain MacUnitMain"

# 4. 종료
down:
	exit

# 5. 청소 (Clean)
clean:
	lzero_rtl_env sbt clean
	rm -rf out/ generated/
