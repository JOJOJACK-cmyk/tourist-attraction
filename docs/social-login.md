# 네이버·구글 로그인 설정

독립 로그인 페이지 `/login`과 커뮤니티에서 네이버·구글 로그인을 제공합니다. 실제 Client ID와 Client Secret이 둘 다 설정된 제공자만 표시합니다. `/api/auth/social/providers`는 `naver`·`google`의 연결 여부만 반환합니다. 카카오 로그인 버튼·등록 설정을 네이버로 교체했고 기존 회원 데이터와 카카오페이 기능은 변경하지 않았습니다.

## 네이버 등록

[NAVER Developers](https://developers.naver.com/apps/#/register)에서 애플리케이션을 등록하고 사용 API로 **네이버 로그인**, 환경으로 **PC 웹**을 설정합니다. 서비스 URL과 Callback URL을 등록하고 발급된 Client ID·Client Secret을 준비합니다.

| 항목 | 로컬 개발 값 |
|---|---|
| 서비스 URL | `http://localhost:8080` |
| Callback URL | `http://localhost:8080/login/oauth2/code/naver` |

개발·검수 단계의 사용 가능 계정과 공개 서비스 전환은 현재 네이버 콘솔 안내에 따라 설정합니다. 이름·이메일로 회원을 합치지 않고 네이버 사용자 정보의 `response.id`를 해시하여 제공자와 함께 계정을 구분합니다. 추가 개인정보를 저장하지 않습니다. OAuth 인가의 state는 Spring Security가 세션과 검증하고, 토큰 요청에도 검증된 인가 요청의 state를 전달합니다. 프로필 응답 성공 코드와 중첩 id를 확인하며 실패·누락 응답은 로그인 성공으로 처리하지 않습니다.

공식 참고:
- [네이버 웹 로그인](https://developers.naver.com/docs/login/web/web.md)
- [인가·토큰 API](https://developers.naver.com/docs/login/api/api.md)
- [회원 프로필 API](https://developers.naver.com/docs/login/profile/profile.md)

## 구글 등록

[Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트와 OAuth 동의 화면을 준비하고 **웹 애플리케이션** 유형의 OAuth 클라이언트를 생성합니다. 콘솔이 요구하는 앱 정보·대상 사용자·테스트 계정을 설정합니다.

승인된 리디렉션 URI:

```
http://localhost:8080/login/oauth2/code/google
```

Google Client ID·Client Secret을 준비합니다. 기존 OIDC `openid` 흐름을 유지하며 서명된 ID 토큰 검증은 Spring Security에 맡기고 `sub`를 식별자로 사용합니다.

[Google 웹 서버 OAuth 안내](https://developers.google.com/identity/protocols/oauth2/web-server)

## IntelliJ 또는 PowerShell 실행

IntelliJ의 **Run → Edit Configurations → Spring Boot 실행 설정 → Environment variables**에 아래 네 변수를 등록하고 서버를 다시 시작합니다. 실제 발급 값을 사용하며 값은 저장소에 커밋하지 않습니다.

```text
NAVER_CLIENT_ID=발급받은_네이버_Client_ID
NAVER_CLIENT_SECRET=발급받은_네이버_Client_Secret
GOOGLE_CLIENT_ID=발급받은_구글_Client_ID
GOOGLE_CLIENT_SECRET=발급받은_구글_Client_Secret
```

PowerShell로 실행한다면 같은 창에서 다음과 같이 설정합니다.

```powershell
$env:NAVER_CLIENT_ID="발급받은_네이버_Client_ID"
$env:NAVER_CLIENT_SECRET="발급받은_네이버_Client_Secret"
$env:GOOGLE_CLIENT_ID="발급받은_구글_Client_ID"
$env:GOOGLE_CLIENT_SECRET="발급받은_구글_Client_Secret"
.\mvnw.cmd spring-boot:run
```

`.env` 자동 로딩은 없습니다. 포트·호스트를 바꾸면 각 콘솔 콜백 주소도 바꿉니다. 배포 시 로컬 주소 대신 HTTPS 도메인을 등록하고 콜백 경로는 `/login/oauth2/code/naver`와 `/login/oauth2/code/google`을 사용합니다. 외부 호스트가 프록시를 거치면 외부 base URL과 신뢰하는 전달 헤더도 맞춰야 합니다.

## 확인

`/login`에서 각 버튼 → 제공자 동의 → 원래 화면 복귀 → 내 계정 표시를 확인합니다. 독립 로그인 화면의 `next`와 기존 게시판 `board` 복귀를 모두 유지합니다. 신규 계정은 MEMBER이며 소셜 회원은 일반 비밀번호 로그인과 관리자 자동 승격을 허용하지 않습니다. 이메일 기반 자동 통합은 없습니다. 글·댓글은 회원이 설정한 닉네임으로 표시하며, 미설정 계정과 직접 방문 후기는 익명으로 표시합니다. 소셜 회원도 내 계정에서 닉네임을 설정할 수 있고 본인 글 관리 권한은 내부 회원 ID로 확인합니다. 로그아웃은 여행온도 세션 종료이며 네이버·구글 서비스 자체의 로그아웃은 아닙니다.

자동 검증은 인가 URL·state·콜백 복귀, 제공자별 계정 분리, 네이버 중첩 id·실패 응답, 네이버 토큰 state 전송과 구글 요청 분리, 권한·익명 표시·UI 버튼 노출을 확인합니다. 모의 토큰 응답을 사용한 테스트는 실제 네이버·구글 계정의 동의와 토큰 발급 성공을 입증하지 않습니다. 발급 키를 설정한 환경에서 최종 확인이 필요합니다.
