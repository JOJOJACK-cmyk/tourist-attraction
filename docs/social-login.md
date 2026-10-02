# 카카오·구글 로그인 설정

소셜 로그인은 일반 로그인과 함께 사용합니다. 인증 키가 없으면 사이트는 정상 실행하며 해당 소셜 버튼만 표시하지 않습니다. 연결 상태는 `/api/auth/social/providers`의 불리언 값으로 확인합니다. 키·시크릿·외부 계정 ID는 이 API에 노출하지 않습니다.

## 1. 카카오 앱

[카카오 디벨로퍼스](https://developers.kakao.com/)에서 앱을 만들고 카카오 로그인을 활성화합니다. REST API 키와 Client Secret을 준비합니다. JavaScript 키나 Admin 키를 사용하지 않습니다.

Redirect URI에 다음 주소를 정확히 등록합니다.

```
http://localhost:8080/login/oauth2/code/kakao
```

웹 플랫폼/허용 도메인 설정을 사용하는 경우 `http://localhost:8080`도 등록합니다. 필요한 항목은 현재 앱 콘솔 안내에 따라 설정합니다. 이 프로젝트는 닉네임·프로필 사진·이메일·친구 목록 권한을 요청하지 않고 사용자 정보 응답의 `id`로 계정을 구분합니다. 별도 OpenID Connect 활성화는 필요하지 않으며 REST OAuth2 사용자 정보 조회 방식을 사용합니다.

공식 설정/흐름:
- https://developers.kakao.com/docs/ko/kakaologin/prerequisite
- https://developers.kakao.com/docs/ko/kakaologin/rest-api

## 2. 구글 앱

[Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트와 OAuth 동의 화면을 준비한 뒤 **웹 애플리케이션** 유형의 OAuth 클라이언트 ID를 만듭니다. 콘솔에서 요구하는 앱 이름·지원 연락처·테스트 사용자 설정 등을 완료합니다.

승인된 리디렉션 URI에 다음을 등록합니다.

```
http://localhost:8080/login/oauth2/code/google
```

Client ID와 Client Secret을 준비합니다. 권한은 `openid`만 요청합니다. 서명된 ID 토큰 검증은 Spring Security의 OIDC 로그인에 맡기며 `sub`를 계정 식별자로 사용합니다. 이메일이나 프로필로 계정을 합치지 않습니다.

공식 안내:
- https://developers.google.com/identity/protocols/oauth2/web-server
- https://developers.google.com/identity/openid-connect/openid-connect

## 3. 실행 환경변수

PowerShell에서 실제 발급받은 값으로 설정한 뒤 같은 창에서 앱을 시작합니다.

```powershell
$env:KAKAO_CLIENT_ID="카카오_REST_API_키"
$env:KAKAO_CLIENT_SECRET="카카오_Client_Secret"
$env:GOOGLE_CLIENT_ID="구글_Client_ID"
$env:GOOGLE_CLIENT_SECRET="구글_Client_Secret"
.\mvnw.cmd spring-boot:run
```

IntelliJ 실행이면 **Run Configuration → Environment variables**에 네 변수를 등록하고 다시 실행합니다. `.env` 파일을 자동으로 읽는 기능은 없습니다. 키는 저장소에 커밋하지 않습니다. 한 제공자의 ID와 Secret이 모두 설정되었을 때 해당 로그인만 활성화됩니다. 실제 키 없이 임의 값을 넣으면 버튼은 표시되지만 외부 로그인은 성공하지 않습니다.

포트/호스트를 바꾸면 개발자 콘솔 Redirect URI도 같은 값으로 바꿉니다. 배포 시 HTTPS 도메인과 `/login/oauth2/code/kakao`, `/login/oauth2/code/google`을 각각 등록합니다. 리버스 프록시 환경에서는 신뢰하는 프록시의 전달 헤더 및 Spring의 외부 base URL 처리를 별도로 맞춰야 합니다.

## 4. 사용 및 검증

`/community` → 로그인·회원가입 → 카카오/구글로 시작하기. 취소·오류는 게시판으로 돌아와 안내합니다. 성공하면 같은 게시판으로 돌아오며 CSRF 토큰을 새로 가져와 글·댓글을 작성합니다. 로그아웃은 여행물가 세션을 종료하며 카카오/구글 자체 계정에서 로그아웃하는 기능은 아닙니다.

내부 회원은 제공자와 식별자의 SHA-256 해시로 구분해 저장하고, 신규 계정은 항상 MEMBER입니다. 임의의 내부 아이디와 접근할 수 없는 비밀번호 해시를 생성하며 소셜 계정의 일반 비밀번호 로그인은 거부합니다. 이메일 기반 자동 통합과 관리자 자동 승격은 없습니다. 일반 계정과 소셜 계정은 별개이며 이전 일반 계정의 글이 소셜 계정으로 자동 이전되지 않습니다. 같은 제공자·같은 계정으로 재로그인하면 자신의 기존 글을 관리할 수 있습니다.

기존 DB에는 JPA update로 회원의 선택 소셜 필드와 고유 제약이 추가됩니다. 먼저 동일 제공자로 두 번 로그인해 회원 중복이 없는지 확인한 다음 글 작성·댓글·사진·수정·로그아웃을 확인합니다.

자동 테스트는 제공자 설정 유무, 인가 요청 리디렉션과 state, 유효하지 않은 콜백 거부, 계정 재사용·제공자 분리·대소문자 구분, 익명 작성·관리자 접근 거부·비밀번호 로그인 거부, 게시판 복귀를 검증합니다. 실제 카카오/구글의 토큰 발급·동의·구글 서명 검증 전체 흐름과 MySQL 변경은 발급한 키 및 실제 환경에서 추가 확인해야 합니다. 외부 인증 완료를 가짜로 처리하지 않습니다.
