# 여행물가 — 관광지 가격 정보 탐색기

관광지와 연도를 선택해 온라인에서 언급된 가격·반응·근거를 살펴보는 개인 프로젝트입니다. 고전 아케이드 스타일의 픽셀 해변, 관광지 스테이지 선택, 연도별 탐험 기록 화면을 포함합니다. 가격·날짜·출처는 일반 글꼴로 표시합니다. 픽셀 풍경은 프로젝트에 포함된 SVG라 외부 이미지·폰트 서비스 없이 표시됩니다.

## 사용 기술

- Java 17 이상 (Java 25 사용 가능)
- Spring Boot 3.5.16, Spring Web, Thymeleaf
- HTML, CSS, 기본 JavaScript (별도 Node.js 설치 불필요)
- Spring Data JPA, MySQL 8
- Gemini API + Google Search grounding (키 설정 시 사용)

Spring Boot 3.5.x는 학습용 1차 구현의 버전입니다. 공개 서비스 운영 전 지원 중인 Spring Boot 버전으로 업데이트하세요.

## 1. 프로젝트 받기

```powershell
git clone https://github.com/JOJOJACK-cmyk/tourist-attraction.git
cd tourist-attraction
```

IntelliJ에서는 `pom.xml`을 프로젝트로 열고 Maven 의존성 다운로드를 기다립니다.

## 2. MySQL 데이터베이스 만들기

MySQL Workbench에서 아래 SQL을 실행합니다. `database/setup.sql`에도 같은 내용이 있습니다.

```sql
CREATE DATABASE IF NOT EXISTS tourist_attraction
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

기본 연결 주소는 `localhost:3306/tourist_attraction`입니다. 다른 서버를 쓰면 `DB_URL` 환경변수를 설정합니다.

## 3. 실행하기 — Windows PowerShell

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="본인의 MySQL 비밀번호"
.\mvnw.cmd spring-boot:run
```

브라우저에서 **http://localhost:8080** 을 엽니다. 기본 실행은 실제 MySQL에 연결합니다. 첫 실행 시 JPA가 `destination`, `evidence`, `price_mention` 테이블을 만들고, 관광지 6곳과 검토 자료 2건을 저장합니다. 다시 실행해도 같은 자료가 중복 생성되지 않습니다.

IntelliJ의 Run Configuration 환경변수에서 `DB_USERNAME`, `DB_PASSWORD`를 설정한 뒤 `TouristAttractionApplication`을 실행해도 됩니다.

Linux/macOS:

```sh
export DB_USERNAME=root
export DB_PASSWORD='본인의 MySQL 비밀번호'
./mvnw spring-boot:run
```

## 4. 실제 AI 웹 검색 연결

키가 없어도 첫 화면에서 **올해에 작성된 속초 후기**를 볼 수 있습니다. 연도를 선택하면 해당 연도의 후기와 주제별 언급 수만 표시하며, ‘전체 검토 (2025~2026)’를 선택하면 30건을 함께 확인합니다. 작성자 이름은 화면과 공개 후기 JSON에서 익명 처리합니다. 반복된 만족·불편·조건에 따라 갈린 경험을 나누고, 각 주제를 펼치면 근거 후기와 원문 링크가 표시됩니다. 전체 30건 목록도 펼쳐 확인할 수 있습니다. 작성일은 2025-01-20~2026-09-13이고, 연도 전체를 대표하는 표본으로 단정하지 않습니다.

자료는 `static/data/sokcho-review-pilot.json`에 저장한 수동 검토 결과이며 DB에 넣지 않습니다. 검토 근거와 선정 한계는 `docs/research/sokcho-market-review-pilot-2026-09-30.md`에 있습니다. 다른 관광지에는 준비 중으로 표시합니다. 기존 연도별 가격 예시는 별도로 유지하며, AI 웹 검색은 실제 연도를 선택해 이용합니다.

Google AI Studio에서 발급한 Gemini API 키를 서버 환경변수로 설정하고 앱을 다시 실행하면 **AI 웹 검색** 선택이 활성화됩니다.

```powershell
$env:GEMINI_API_KEY="발급받은 키"
$env:GEMINI_MODEL="gemini-2.5-flash"
.\mvnw.cmd spring-boot:run
```

- API 키는 브라우저로 보내지 않으며 소스에 넣지 않습니다.
- 모델 이름은 본인 계정에서 사용 가능한 Google Search grounding 지원 모델로 변경할 수 있습니다.
- 실제 API 호출에는 모델·검색 사용 비용이 발생할 수 있습니다.
- 출처와 문장별 grounding 연결이 없는 결과는 오류로 처리합니다.
- 응답은 화면에서만 사용하며 AI 결과·검색 원문을 MySQL 또는 브라우저 저장소에 누적하지 않습니다. 연도별 결과 보관 기능은 제공자의 저장·표시 조건 확인 후 설계해야 합니다.
- 같은 IP의 실시간 호출 간격은 15초입니다. 공개 배포 전 사용자 인증과 전체 호출량 제한을 추가해야 합니다.
- 네이버 검색 결과의 저장·재가공 제한은 별도로 적용됩니다. 네이버 원문 크롤러를 구현한 프로젝트가 아닙니다.
- `.env.example`은 변수 설명용입니다. Spring Boot는 `.env` 파일을 자동으로 읽지 않습니다.

공식 API 문서: https://ai.google.dev/gemini-api/docs/generate-content/google-search

## 제공 화면과 API

| 기능 | 주소 |
|---|---|
| 관광지·연도 선택 및 결과 화면 | `GET /` |
| MySQL 관광지 목록 | `GET /api/destinations` |
| 수동 검토 30건·주제별 근거 | `GET /data/sokcho-review-pilot.json` |
| AI 연결 여부 | `GET /api/status` |
| 검토 예시 또는 실시간 분석 | `POST /api/analysis` |

분석 요청 예시:

```json
{"destinationId":"sokcho","year":2026,"mode":"sample"}
```

실시간 요청은 `mode`를 `live`로 바꿉니다.

## 구조

```text
src/main/java/com/travelprice/
  api/         화면·REST 컨트롤러, 요청·응답 DTO, 예외 처리
  config/      중복 없이 초기 검토 자료 저장
  domain/      Destination, Evidence, PriceMention 엔티티
  repository/  MySQL 접근
  service/     연도별 조회, AI 검색 호출 및 출처 검증
src/main/resources/
  templates/index.html
  static/css/style.css
  static/js/app.js
  static/images/beach-hero.webp
  application.yml
```

## 검토 자료의 한계

검토 자료는 2026-09-30에 확인한 공개 글의 링크와 가격 언급을 짧게 정리한 예시입니다. 현재 판매 가격 또는 시장 전체를 대표하는 통계가 아닙니다. 작성일과 방문일이 다를 수 있고 상품 중량·구성은 확인되지 않았습니다.

- 2026-07-16 방문 리뷰: https://polle.com/lkhun71/posts/1215
- 2026-07-21 개인 블로그: https://030i0i.blogspot.com/2026/07/blog-post.html

일반 상권·다른 관광지·이전 연도와의 수치 비교는 아직 구현되지 않았습니다. 화면에 자료 부족으로 표시합니다.

## 테스트

```powershell
.\mvnw.cmd test
```

테스트만 H2 임시 데이터베이스를 사용합니다. 기본 실행은 MySQL입니다. 검토 자료 조회, 다른 지역·연도로 예시가 섞이지 않는지, 입력 검증, 초기 데이터 중복 방지, 출처가 없는 AI 응답 차단을 확인합니다. 실제 MySQL 접속과 외부 AI 유료 호출은 사용자의 환경에서 추가 확인해야 합니다.
