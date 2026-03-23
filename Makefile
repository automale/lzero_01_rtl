# L-ZERO RTL Track 간편 명령어 세트

# 1. 환경 빌드 및 실행
up:
	docker compose up -d --build

# 2. 컨테이너 내부 접속 (가장 많이 씀)
shell:
	docker exec -it lzero_rtl_env /bin/bash

# 3. Chisel 컴파일 및 Verilog 생성 (접속 안 하고 밖에서 바로 실행)
gen:
	docker exec -it lzero_rtl_env sbt "runMain MacUnitMain"

# 4. 종료
down:
	docker compose down