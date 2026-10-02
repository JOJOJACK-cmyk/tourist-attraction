# 여행물가 — 관광지 가격 정보 탐색기

관광지와 연도를 선택해 온라인에서 언급된 가격·반응·근거를 살펴보는 개인 프로젝트입니다. 아기자기한 픽셀 해변 배경, 관광지 선택, 연도별 후기 정보 화면을 포함합니다. 게임 표현 대신 관광지·후기 중심 문구를 사용합니다. 제목·본문·버튼·가격·날짜·출처에는 Pretendard 웹폰트를 사용합니다. 픽셀 풍경은 프로젝트에 포함된 SVG라 외부 이미지 서비스 없이 표시됩니다. 폰트는 Pretendard 공식 저장소의 고정 버전 jsDelivr 웹폰트 주소에서 불러오며, 연결이 안 되면 시스템 글꼴을 사용합니다.

## 사용 기술

- Java 17 이상 (Java 25 사용 가능)
- Spring Boot 3.5.16, Spring Web, Thymeleaf, Spring Security
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

브라우저에서 **http://localhost:8080** 을 엽니다. 기본 실행은 실제 MySQL에 연결합니다. 첫 실행 시 JPA가 관광지·검토 자료 및 커뮤니티 테이블을 만들고, 관광지 6곳과 검토 자료 2건을 저장합니다. 다시 실행해도 같은 자료가 중복 생성되지 않습니다. 상단 **커뮤니티** 또는 **http://localhost:8080/community** 에서 커뮤니티를 이용합니다.

IntelliJ의 Run Configuration 환경변수에서 `DB_USERNAME`, `DB_PASSWORD`를 설정한 뒤 `TouristAttractionApplication`을 실행해도 됩니다.

Linux/macOS:

```sh
export DB_USERNAME=root
export DB_PASSWORD='본인의 MySQL 비밀번호'
./mvnw spring-boot:run
```

## 4. 실제 AI 웹 검색 연결

키가 없어도 첫 화면에서 **올해에 작성된 속초 후기**를 볼 수 있습니다. 연도를 선택하면 해당 연도의 후기와 주제별 언급 수만 표시하며, ‘전체 검토 (2025~2026)’를 선택하면 30건을 함께 확인합니다. 작성자 이름은 화면과 공개 후기 JSON에서 익명 처리합니다. 이용자 화면에는 전체 후기 수, 칭찬·불만 언급 수, 주요 키워드와 이슈를 종합해 표시합니다. 개별 블로그·원문·경험별 분석 목록은 표시하지 않습니다. 작성일은 2025-01-20~2026-09-13이고, 연도 전체를 대표하는 표본으로 단정하지 않습니다.

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
| 회원가입·로그인·질문·자유 게시판·댓글 | `GET /community` |
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

테스트만 H2 임시 데이터베이스를 사용합니다. 기본 실행은 MySQL입니다. 검토 자료 조회, 다른 지역·연도로 예시가 섞이지 않는지, 입력 검증, 초기 데이터 중복 방지, 출처가 없는 AI 응답 차단을 확인합니다. 커뮤니티에서는 실제 세션 로그인과 CSRF 토큰 교체, BCrypt 비밀번호, 본인 글·댓글 권한, 검색·페이지 구분, 관리자 숨김, 사진 형식·소유권·공개 범위를 확인합니다. 실제 MySQL 접속과 외부 AI 유료 호출은 사용자의 환경에서 추가 확인해야 합니다.

## 질문·자유 게시판

질문 게시판과 자유 게시판을 탭으로 구분하고, 관광지 태그·분류·제목·내용으로 검색합니다. 관광지 태그는 선택이며 태그 없이도 글을 작성할 수 있습니다. 기존 질문은 질문 게시판에, 기존 후기·제보는 자유 게시판에 표시합니다. 음식 외에도 숙박, 주차, 교통, 입장·체험, 쇼핑, 대여, 편의시설, 기타 경험을 작성할 수 있습니다. 회원가입 후 글과 댓글을 작성하며 공개 화면의 작성자는 모두 **익명**입니다. 본인 아이디는 로그인 상태 표시에서만 확인합니다.

본인 글은 수정·삭제, 본인 댓글은 삭제할 수 있습니다. 관리자는 글 숨김·다시 공개·삭제 및 댓글 삭제를 할 수 있으며 다른 사람 글의 내용은 수정할 수 없습니다. 사진 한 장은 JPG·PNG, 5MB 이하로 첨부합니다. 사진은 서버의 `uploads/community` 폴더에, 회원·글·댓글·사진 메타데이터는 MySQL에 저장합니다. DB와 업로드 폴더를 함께 보관해야 사진이 유지됩니다.

관리자 계정이 필요하면 **아직 사용하지 않은 아이디**로 환경변수를 설정한 뒤 앱을 시작합니다. 기본 관리자 계정은 없습니다.

```powershell
$env:APP_ADMIN_LOGIN="travel_admin"
$env:APP_ADMIN_PASSWORD="직접 정한 8자 이상의 비밀번호"
.\mvnw.cmd spring-boot:run
```

계정은 DB에 저장되므로 다음 실행부터 두 관리자 환경변수는 지워도 됩니다. 기존 일반회원 아이디는 자동 승격하지 않으며, 기존 관리자 비밀번호도 환경변수로 변경되지 않습니다. 비밀번호는 8~64자, UTF-8 기준 72바이트 이하입니다. 사진 경로는 `APP_UPLOAD_DIR`로 바꿀 수 있습니다. 자세한 구조와 API는 [커뮤니티 구현 안내](docs/community.md)에 정리했습니다. 커뮤니티 글은 현재 AI 분석 결과에 자동 합산하지 않습니다.

## 로컬 LLM용 후기 추출 프롬프트

음식·숙박·주차·교통·입장/체험·쇼핑·대여·편의시설의 경험을 추출하는 시스템 프롬프트를 포함합니다. 영역과 판단 주제를 나눠 가격 대비 만족·소비 후회·추가 비용뿐 아니라 접근성·혼잡·청결·응대도 분석 대상으로 삼습니다.

- 프롬프트: `src/main/resources/prompts/review-extraction-system.txt`
- 입력 메시지 준비: `ReviewPromptProvider`
- 사용법과 가상 입력·출력 예시: [후기 추출 안내](docs/ai/review-extraction.md)

작성자 이름 대신 출처 ID를 사용하고, 단순 감상과 지출·방문·이용 판단에 도움이 되는 경험을 구분합니다. 출력에 `category`를 포함하고 `check_before_visit`으로 방문 전 확인 사항을 정리합니다. Ollama 연결과 단일 후기 분석 화면을 제공합니다. 수집·원본 중복 제거·여러 후기의 자동 집계는 아직 구현하지 않았습니다.

## Ollama로 후기 분석하기 — Windows

Ollama와 Spring Boot를 같은 컴퓨터에서 실행합니다. 기본 모델은 `gemma12b:latest`, 주소는 `http://localhost:11434`입니다.

```powershell
git pull origin main
$env:OLLAMA_MODEL="gemma12b:latest"
$env:OLLAMA_BASE_URL="http://localhost:11434"
.\mvnw.cmd spring-boot:run
```

기존 MySQL 환경변수도 설정되어 있어야 합니다. IntelliJ에서는 Run Configuration의 환경변수에 같은 값을 넣고 앱을 재시작합니다. Ollama 앱 실행 후 `ollama list`와 `Invoke-RestMethod http://localhost:11434/api/tags`로 확인할 수 있습니다.

**관리자 계정으로 커뮤니티 화면에서 로그인**한 뒤 상단 ‘후기 분석 관리’ 또는 `http://localhost:8080/admin/reviews`로 이동합니다. 이곳에 후기 **한 편**을 붙여 넣고 분석합니다. Gemini 웹 검색 버튼과 별개이며 로컬 모델은 인터넷 글을 자동 수집하지 않습니다. 본문은 최대 4,000자, 작성일은 모르면 비워둡니다. 수동 검토 자료 30건은 그대로 유지됩니다. 입력과 분석 결과를 DB에 저장하지 않습니다.

- 모델 변경: `OLLAMA_MODEL`을 설치된 정확한 이름으로 설정.
- 응답 제한 시간: `OLLAMA_TIMEOUT_SECONDS` (기본 300초).
- 컨텍스트 크기: `OLLAMA_CONTEXT_SIZE` (기본 16384). 메모리 사용이 부담되면 낮추고 더 짧은 본문으로 테스트합니다.
- 서버 연결/모델 확인: `GET /api/reviews/status` (관리자 전용).
- 단일 후기 추출: `POST /api/reviews/extract` (`destinationId`, `publishedAt`, `body`), 관리자 세션과 CSRF 헤더 필요.
- 시스템 프롬프트와 출력 JSON 스키마를 전달하고 서버에서 구조·허용값·출처 ID·날짜를 확인합니다. 이는 내용의 사실성이나 익명화의 완전성을 보증하지 않습니다.
- 비회원·일반회원에게는 분석 메뉴를 표시하지 않으며 관리자 화면·분석 API·연결 상태 API 접근을 서버에서도 차단합니다.
- 로컬 모델의 동시에 실행되는 분석은 1건으로 제한합니다.
- 테스트는 모의 Ollama 서버를 사용합니다. 사용자 PC의 실제 Gemma 추출 품질과 속도는 실행 후 확인이 필요합니다.

Ollama 공식 문서: https://docs.ollama.com/api/chat · https://docs.ollama.com/capabilities/structured-outputs

## 사이트 글꼴

**Pretendard v1.3.9**를 모든 화면에 적용합니다. 본문은 16px(첫 화면 소개는 17px), 굵기 400, 줄간격 1.7이며 제목은 굵기 700입니다. 실제 Regular/Bold 웹폰트 두 개를 불러옵니다. `font-display: swap`으로 로딩 중에도 글을 읽을 수 있고, CDN 연결이 안 되면 시스템 글꼴로 표시합니다.

- 웹폰트 CSS: `src/main/resources/static/css/style.css`
- 제작자·공식 배포: https://github.com/orioncactus/pretendard
- 저작권: Copyright (c) 2021 Kil Hyung-jin, with Reserved Font Name Pretendard.
- 라이선스: SIL Open Font License 1.1 (공식 저장소의 LICENSE 파일 참조).

## 시스템 프롬프트 적용 예시 — 속초 30건

기존 검토 요약 30건을 시스템 프롬프트 형식으로 분석한 **저장된 예시**를 메인 화면에 연결했습니다. 실제 Ollama 호출이나 새 후기 30건 수집 결과가 아닙니다. 30개 출처별 분석에서 경험 57개를 추출했으며, 음식·주차·쇼핑의 근거가 있습니다. 단순 감상만 있는 2건은 경험 배열을 비웠습니다.

관광지에서 속초를 선택하면 **전체 후기·칭찬 언급·불만 언급 건수**, **칭찬·불만 키워드**, **주요 이슈**를 바로 보여줍니다. 공개 화면은 종합 결과 중심이며, 개별 출처·경험 분석·근거 펼침·영역별 필터는 표시하지 않습니다. 입력과 출처별 분석 자료는 검토·재집계용으로 유지합니다.

같은 후기의 여러 긍정 항목은 칭찬 1건, 여러 부정 항목은 불만 1건으로 집계합니다. 한 후기에 칭찬과 불만이 공존하면 각각에 포함하며 중립·의견 혼합을 임의로 긍정·부정으로 바꾸지 않습니다. 현재 전체 30건은 칭찬 언급 26건, 불만 언급 12건이고, 2025·2026년은 각각 전체 15건·칭찬 13건·불만 6건입니다. 실제 여행자 수나 관광지 전체 평점이 아닙니다.

- 저장 결과: `src/main/resources/static/data/sokcho-prompt-pilot.json`
- 화면 구성: `src/main/resources/static/js/review-insights.js`
- 상세 해석: [프롬프트 적용 예시 안내](docs/ai/sokcho-prompt-pilot.md)
- JavaScript 검증(개발 시 Node.js 사용): `node src/test/js/review-insights.test.cjs`

기본 연도 2026에는 주차 근거가 없습니다. 2025 또는 전체 검토에는 주차권 제공 차이 이슈가 개별 언급 1건으로 표시됩니다. 후기 작성 연도에 맞춰 건수·키워드·이슈를 함께 집계합니다. 모델 연결은 계속 관리자 전용이며, 공개 이용자는 저장된 분석 자료를 읽습니다.


## 속초관광수산시장 소개

공개 페이지 `/destinations/sokcho`에서 시장 특징, 대표 먹거리 6종, 젓갈·건어물·포장 선물, 5개 골목, 주변 볼거리, 주소·문의·주차 안내를 제공합니다. 메인 관광지 카드의 **시장 소개 보기**로 접근하며 소개에서 종합 후기와 커뮤니티로 이어집니다. 기존 종합 후기 집계와 관리자 전용 Ollama 기능은 그대로 유지됩니다.

소개는 공식 관광 자료의 사실을 확인해 직접 작성한 정적 콘텐츠입니다. 실시간 API 결과나 가격·평점 데이터가 아닙니다. 점포별 영업시간, 고정 주차요금, 재고와 대기시간을 임의로 넣지 않았으며 지도와 현장 확인 안내로 연결합니다. 기존 프로젝트의 도트 풍경을 재사용하고 외부 사진은 복제하지 않았습니다. 확인 자료와 편집 기준: [속초시장 소개 자료](docs/research/sokcho-market-introduction.md).


게시판 바로가기: `/community?board=question`, `/community?board=free`. API는 `kind=QUESTION`, `kind=GENERAL`로 필터링하며 자유 게시판은 기존 REVIEW·REPORT 저장 행도 포함합니다. 기존 글·댓글을 삭제하거나 일괄 재작성하지 않습니다. 신규/수정 글은 QUESTION 또는 GENERAL로 저장합니다. 관광지 태그가 없으면 destinationId는 null 또는 빈 문자열입니다.

기존 MySQL에서는 시작 시 `CommunitySchemaUpdater`가 필요한 경우에만 MySQL enum에 GENERAL을 추가하고 기존 destination_id의 NOT NULL을 해제합니다. 기존 enum 값은 유지합니다. DB 계정은 기존 JPA 스키마 변경과 동일하게 ALTER 권한이 필요합니다. H2로 게시판 동작·권한을 검증했으며 실제 MySQL 스키마 갱신은 사용자 환경에서 확인해야 합니다.

화면 동작 검증: `node src/test/js/community-boards.test.cjs` (게시판 바로가기·탭·페이지 초기화·선택 태그·CSRF 저장·이동한 게시판 갱신).


## 카카오·구글 로그인

커뮤니티 로그인 패널에 카카오·구글 버튼을 제공합니다. 일반 로그인은 유지하고 소셜 로그인으로도 익명 글·댓글·사진을 작성할 수 있습니다. 제공자별 Client ID와 Secret이 모두 있어야 버튼이 활성화되며, 설정 없이도 앱을 실행할 수 있습니다.

- 카카오 콜백: `http://localhost:8080/login/oauth2/code/kakao`
- 구글 콜백: `http://localhost:8080/login/oauth2/code/google`
- 환경변수: `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- 개발자 콘솔 등록·PowerShell/IntelliJ 설정·검증: [소셜 로그인 안내](docs/social-login.md)

소셜 계정은 별도 일반회원으로 생성됩니다. 기존 일반 계정과 이메일 기반 자동 통합은 하지 않으며, 소셜 계정이 관리자로 자동 승격되지 않습니다. 같은 제공자와 계정은 다시 로그인해도 기존 회원을 사용합니다. 일반 로그인을 통한 관리자 접속은 유지됩니다. 키 없는 환경 및 모의 인증 테스트와 실제 외부 로그인 성공은 구분합니다.
