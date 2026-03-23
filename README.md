# 🚀 L-ZERO RTL Track: 개발 환경 설정 가이드

본 저장소는 **Processing-In-Memory(PIM)** 및 **Custom HW Accelerator** 설계를 위한 Chisel/Rocket-chip 통합 개발 환경입니다. Docker를 통해 복잡한 의존성 설치 없이 즉시 설계를 시작할 수 있습니다.

---

## 📋 1. 사전 준비 (Prerequisites)
시작 전 아래 도구들이 설치되어 있어야 합니다.
* **Docker Desktop**: 컨테이너 기반 개발 환경 실행
* **Git**: 소스 코드 버전 관리

---

## 🛠️ 2. 저장소 복제 및 서브모듈 설정 (Fork & Clone)

본인의 계정에서 로켓칩 코드를 수정하고 관리하기 위해 **메인 레포지토리와 서브모듈을 각각 Fork** 해야 합니다.

### ① GitHub에서 Fork 수행
1. L-ZERO 메인 레포지토리(`lzero_01_rtl`)를 자신의 계정으로 **Fork** 합니다.
2. `rocket-chip` [원본 레포지토리](https://github.com/chipsalliance/rocket-chip)도 자신의 계정으로 **Fork** 합니다.

### ② 로컬로 복제 (Recursive Clone)
서브모듈 내용물까지 한 번에 가져오기 위해 `--recursive` 옵션을 반드시 사용합니다.
```bash
# 본인의 포크된 레포지토리 주소를 사용하세요.
git clone --recursive [https://github.com/YOUR_USERNAME/lzero_01_rtl.git](https://github.com/YOUR_USERNAME/lzero_01_rtl.git)
cd lzero_01_rtl
```

### ③ [중요] 서브모듈 연결 변경
자신의 포크된 로켓칩에 코드를 Push할 수 있도록 .gitmodules 설정을 본인 계정 주소로 변경합니다.
1. .gitmodules 파일 수정 (텍스트 에디터로 열기)
```bash
[submodule "rocket-chip"]
    path = rocket-chip
    url = [https://github.com/YOUR_USERNAME/rocket-chip.git](https://github.com/YOUR_USERNAME/rocket-chip.git)  # 본인의 포크 주소로 수정
```

2. 설정 동기화 및 업데이트
```bash
git submodule sync
git submodule update --init --recursive
```

## 🚢 3. 환경 빌드 및 초기 셋업 (Docker)
L-ZERO 전용 Makefile을 통해 환경 구축을 자동화합니다.
① 컨테이너 실행
```bash
make up
```
Docker 이미지를 빌드하고 컨테이너를 백그라운드에서 실행합니다.

② 로켓칩 라이브러리 로컬 빌드 (최초 1회 필수)
Chisel 프로젝트에서 로켓칩 라이브러리를 참조할 수 있도록 로컬에 굽는 과정입니다. 약 5~10분이 소요됩니다.
```bash
make setup
```
cde, diplomacy, hardfloat, rocketchip, macros 등 핵심 모듈이 자동으로 빌드됩니다.

## ⌨️ 4. 주요 명령어 사용법 (Makefile)
터미널에서 아래 명령어를 사용하여 개발 사이클을 관리하세요.
명령어설명
make up      Docker 컨테이너 실행 및 환경 빌드
make shell   컨테이너 내부 터미널 접속 (디버깅/수동 조작용)
make setup   로켓칩 핵심 라이브러리 로컬 빌드 (최초 1회 필수)
make gen     Chisel 코드를 SystemVerilog로 컴파일
make clean   SBT 빌드 부산물 및 생성된 Verilog 파일 삭제 (가벼운 초기화)
make down    컨테이너 실행 종료

## 💡 5. 개발 워크플로우 (Example)
코드 수정: 로컬의 src/main/scala/ 경로에서 Chisel 코드를 설계합니다.

Verilog 생성: 터미널에서 make gen 명령어를 실행합니다.

결과 확인: 성공 시 generated/ 폴더 내에 생성된 .sv 파일을 확인합니다.

## ⚠️ 주의사항
메모리 할당: Docker Desktop 설정에서 최소 4GB~8GB 이상의 RAM을 할당해야 로켓칩 빌드 시 OutOfMemory 에러가 발생하지 않습니다.

CPU 아키텍처 호환성: 본 환경은 Windows/Intel 및 Apple Silicon(M1/M2) 아키텍처 호환성을 모두 지원하도록 설계되었습니다.
