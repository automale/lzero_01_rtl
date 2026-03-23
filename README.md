# 📚 L-ZERO RTL Track: Rocket-chip & Chisel 공식 학습 리소스

Rocket-chip과 Chisel은 상용 툴이 아닌 오픈소스 프로젝트이므로, 정규 인터넷 강의보다는 **공식 튜토리얼, 공식 문서, 그리고 대학의 오픈 코스웨어** 를 직접 따라 해보는 것이 가장 빠르고 확실한 학습 방법입니다. L-ZERO 학회원들은 아래의 Phase 순서대로 스터디를 진행하는 것을 권장합니다.

## 🛠️ Phase 1: 하드웨어 설계 언어 정복 (Chisel)

Rocket-chip의 코어 로직을 이해하고 수정하려면 가장 먼저 Chisel(Scala 기반)에 익숙해져야 합니다. Verilog와 달리 객체 지향 및 함수형 프로그래밍 패러다임을 하드웨어 설계에 도입한 언어입니다.

- [**Chisel Bootcamp (GitHub)**](https://github.com/freechipsproject/chisel-bootcamp)
  - **설명:** UC 버클리에서 제공하는 **가장 완벽한 대화형 공식 튜토리얼** 입니다. Jupyter Notebook 환경에서 웹 브라우저를 통해 Chisel 코드를 직접 짜고 즉시 회로(Verilog)가 어떻게 생성되는지 눈으로 확인할 수 있습니다.
  - **활용 팁:** 환경 설정 없이 웹에서 바로 실행 가능하므로, 학회원들의 첫 주차 스터디 과제로 매우 적합합니다.
- [**Chisel 3 Documentation**](https://www.chisel-lang.org/chisel3/)
  - **설명:** Chisel의 공식 API 및 문법 가이드 문서입니다. 코딩 중 모르는 모듈(예: `Decoupled` , `Valid` , `RegInit` )이 나올 때 사전처럼 활용하세요.
- [**Chisel Cheatsheet (PDF)**](https://github.com/freechipsproject/chisel-cheatsheet/releases/latest/download/chisel_cheatsheet.pdf)
  - **설명:** 자주 쓰이는 Chisel 문법을 한 장으로 요약한 치트시트입니다. 출력해서 모니터 옆에 붙여두는 것을 강력히 추천합니다.

## 🚀 Phase 2: 아키텍처 및 SoC 통합 (Rocket-chip & Chipyard)

Chisel에 익숙해졌다면, Rocket-chip 본체의 구조를 파악하고 커스텀 하드웨어 가속기(Accelerator)를 붙이는 방법을 배웁니다.

- [**The Rocket Chip Generator (Technical Report)**](https://www2.eecs.berkeley.edu/Pubs/TechRpts/2016/EECS-2016-17.pdf)
  - **설명:** 2016년에 발표된 Rocket-chip의 근본이 되는 공식 기술 논문(PDF)입니다.
  - **활용 팁:** 코드를 보기 전, 코어 파이프라인(5-stage)과 캐시 구조, 전체적인 시스템 다이어그램을 머릿속에 그릴 때 반드시 읽어야 하는 바이블입니다.
- [**Chipyard Official Documentation**](https://chipyard.readthedocs.io/en/latest/)
  - **설명:** 현재 전 세계적으로 Rocket-chip을 다룰 때 가장 많이 사용하는 SoC 제너레이터 프레임워크인 'Chipyard'의 공식 문서입니다.
  - **핵심 포인트:** 문서 내의 **"Customization"** 파트를 보면 **RoCC (Rocket Custom Coprocessor)** 인터페이스를 사용하여 나만의 가속기(Accelerator)를 코어에 직접 연결하는 방법이 상세히 나와 있습니다. PIM 또는 행렬 연산기 설계 시 필수적인 내용입니다.

## 🔗 Phase 3: 심화 - 내부 버스 프로토콜 (Diplomacy & TileLink)

메모리 병목을 해결하거나 시스템 레벨의 병렬 처리를 설계하기 위해 반드시 알아야 하는 데이터 통신 규약입니다.

- [**TileLink Specification**](https://www.google.com/search?q=https://sifive.cdn.prismic.io/sifive%252F1a82e600-1f93-4f46-9b53-0005a5cbbeaa_tilelink-spec-1.7-draft.pdf)
  - **설명:** Rocket-chip 내부의 모듈들이 데이터를 주고받는 표준 버스 프로토콜인 TileLink의 공식 스펙 문서입니다. ARM의 AXI 프로토콜과 비교하며 공부하기 좋습니다. 캐시 일관성(Cache Coherence) 관리에 유리하게 설계되었습니다.
- [**Diplomacy Framework Tutorial**](https://www.google.com/search?q=https://chipyard.readthedocs.io/en/latest/TileLink-Diplomacy-Reference/Diplomacy.html)
  - **설명:** 파라미터 협상 프레임워크인 Diplomacy의 개념을 설명하는 문서입니다. 초기 진입 장벽이 매우 높지만, 모듈 간의 연결(Node)과 주소 공간(Address Map)을 자동으로 구성하기 위해 꼭 넘어야 할 산입니다.

## 🎓 Phase 4: 대학 오픈 코스웨어 (Hands-on Labs)

실제 아키텍처 수업에서 이 툴들을 사용하여 어떻게 과제를 진행하는지 직접 확인하고 실습해 볼 수 있습니다.

- [**UC Berkeley CS152: Computer Architecture and Engineering**](https://inst.eecs.berkeley.edu/~cs152/)
  - **설명:** 버클리의 학부 아키텍처 수업입니다. 학기마다 업데이트되는 강의 슬라이드는 물론, **실제 Lab 과제 자료(RISC-V 툴체인 활용 및 Chisel 기반 프로세서 파이프라인 설계 실습)** 가 공개되어 있습니다.
  - **활용 팁:** L-ZERO 학회의 자체 커리큘럼이나 해커톤 주제를 짤 때 벤치마킹하기 가장 좋은 레퍼런스입니다.
- [**CS252: Graduate Computer Architecture**](https://www.google.com/search?q=https://inst.eecs.berkeley.edu/~cs252/)
  - **설명:** 심화 과정을 원한다면 대학원 레벨의 아키텍처 강의 자료를 참고하세요. BOOM(Berkeley Out-of-Order Machine) 코어와 같은 더 복잡한 슈퍼스칼라 아키텍처를 다룹니다.

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
---

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

---

## ⌨️ 4. 주요 명령어 사용법 (Makefile)
터미널에서 아래 명령어를 사용하여 개발 사이클을 관리하세요.
명령어설명
make up      Docker 컨테이너 실행 및 환경 빌드
make shell   컨테이너 내부 터미널 접속 (디버깅/수동 조작용)
make setup   로켓칩 핵심 라이브러리 로컬 빌드 (최초 1회 필수)
make gen     Chisel 코드를 SystemVerilog로 컴파일
make clean   SBT 빌드 부산물 및 생성된 Verilog 파일 삭제 (가벼운 초기화)
make down    컨테이너 실행 종료

---

## 💡 5. 개발 워크플로우 (Example)
코드 수정: 로컬의 src/main/scala/ 경로에서 Chisel 코드를 설계합니다.

Verilog 생성: 터미널에서 make gen 명령어를 실행합니다.

결과 확인: 성공 시 generated/ 폴더 내에 생성된 .sv 파일을 확인합니다.

---

## ⚠️ 주의사항
메모리 할당: Docker Desktop 설정에서 최소 4GB~8GB 이상의 RAM을 할당해야 로켓칩 빌드 시 OutOfMemory 에러가 발생하지 않습니다.

CPU 아키텍처 호환성: 본 환경은 Windows/Intel 및 Apple Silicon(M1/M2) 아키텍처 호환성을 모두 지원하도록 설계되었습니다.
