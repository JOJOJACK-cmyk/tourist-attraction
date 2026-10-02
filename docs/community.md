# 커뮤니티 구현

`/community`에서 자유 게시판(GENERAL)과 질문 게시판(QUESTION)을 이용합니다. `/community?board=free` 및 `/community?board=question`으로 바로 열 수 있습니다. 초기 가짜 게시글을 넣지 않으며 실제 작성한 글을 MySQL에 저장합니다. 선택 관광지 태그, 게시판, 9개 분류와 제목·내용 검색을 조합하고 12건씩 최신순으로 조회합니다. 방문일과 사진 한 장은 선택 사항입니다.

## 데이터와 권한

| 테이블 | 내용 |
|---|---|
| community_member | 아이디, BCrypt 비밀번호 해시, MEMBER/ADMIN 역할 |
| community_post | 작성자·선택 관광지 관계, 게시판·분류, 제목·본문·방문일·사진 키, 숨김 여부, 작성·수정 시각 |
| community_comment | 글·작성자 관계, 내용, 작성 시각 |
| community_photo | 사진 UUID, 업로드한 회원, jpg/png 확장자 |

Spring Security 세션 인증을 사용합니다. 로그인 성공 시 세션 ID와 CSRF 토큰을 바꾸고 인증 정보를 서버 세션에 저장합니다. 쓰기 요청은 CSRF 헤더가 필요합니다. 화면은 로그인·로그아웃 후 토큰을 다시 받아 사용합니다. Gemini 분석 API는 기존 익명 사용 흐름을 유지합니다. Ollama 분석 및 연결 상태 API(`/api/reviews/**`)와 분석 화면(`/admin/reviews`)은 관리자 전용입니다. Ollama 분석 요청에도 CSRF 헤더가 필요합니다.

공개 글·댓글 응답에는 회원 아이디, 회원 ID, 비밀번호 해시를 넣지 않습니다. `author`는 항상 `익명`이며, `mine`과 `canDelete`로 현재 이용자의 버튼 표시를 결정합니다. 서버는 화면 버튼과 별개로 DB의 회원 관계를 검사합니다. 본인 글 수정·삭제, 본인 댓글 삭제, 관리자 글 숨김·공개·삭제 및 댓글 삭제를 허용합니다. 숨긴 글을 작성자가 수정해도 숨김이 유지됩니다. 공개 목록·상세·댓글·사진 접근은 숨김 상태를 반영하며, 작성자와 관리자는 숨긴 상세를 확인할 수 있습니다. 숨긴 글에는 댓글을 추가하지 못합니다.

## API

| 메서드·주소 | 동작 |
|---|---|
| GET /api/auth/csrf | CSRF 헤더 이름과 토큰 |
| GET /api/auth/me | 현재 로그인 여부, 로그인한 본인의 아이디·관리자 여부 |
| POST /api/auth/signup | `loginId`, `password`로 일반회원 가입 |
| POST /api/auth/login | 세션 로그인 |
| POST /api/auth/logout | 세션 로그아웃 |
| GET /api/community/posts | 목록: `destination`, `kind`, `category`, `q`, `page`(0부터), 관리자 `includeHidden` |
| POST /api/community/posts | 글 작성 |
| GET /api/community/posts/{id} | 상세 |
| PUT /api/community/posts/{id} | 본인 글 수정 |
| DELETE /api/community/posts/{id} | 본인 또는 관리자 삭제, 댓글 함께 삭제 |
| PATCH /api/community/posts/{id}/visibility | 관리자: `{"hidden":true}` 또는 false |
| GET /api/community/posts/{id}/comments | 최신 댓글 최대 100건을 작성순으로 표시 |
| POST /api/community/posts/{id}/comments | `body`로 댓글 작성 |
| DELETE /api/community/posts/{postId}/comments/{id} | 본인 또는 관리자 댓글 삭제 |
| POST /api/community/images | multipart `file`: 사진 업로드 |
| GET /api/community/images/{key} | 공개 글의 사진, 또는 사진 소유자·관리자 접근 |

글 입력 예시:

```json
{
  "destinationId": "sokcho",
  "kind": "GENERAL",
  "category": "PARKING",
  "title": "시장 주변 주차 경험",
  "body": "방문 시간과 실제 이용한 주차장의 경험을 적습니다.",
  "visitedAt": "2026-09-30",
  "imageKey": null
}
```

분류: FOOD, LODGING, PARKING, TRANSPORT, ADMISSION, SHOPPING, RENTAL, AMENITIES, OTHER. 미래 방문일, 공백 제목·내용, 알 수 없는 관광지, 다른 회원의 사진 첨부를 거부합니다. 화면은 사용자 입력을 `textContent`로 표시합니다.

## 사진 보관

사진은 5MB 이하 JPG·PNG이며 ImageIO로 실제 형식·크기(한 변 6,000px 이하, 총 2,000만 픽셀 이하)를 확인하고 다시 인코딩합니다. 원래 파일 이름과 메타데이터는 저장하지 않습니다. UUID 파일 이름으로 `APP_UPLOAD_DIR`(기본 `./uploads/community`)에 저장하며 업로드 폴더는 Git에서 제외합니다.

다른 글에서 참조하지 않는 사진은 글 삭제·사진 교체·사진 제거 시 DB 트랜잭션 성공 후 파일도 삭제합니다. 숨김 상태의 사진은 파일을 유지하지만 공개 접근을 차단하고 응답에 `Cache-Control: no-store`를 설정합니다. 이미 다운로드한 사진을 회수하는 기능은 아닙니다. 업로드 후 글 저장에 실패한 미사용 사진의 주기적 정리는 아직 구현하지 않았습니다. DB 백업과 사진 폴더 백업을 함께 해야 합니다.

## 범위

회원가입·로그인·글 CRUD·검색·페이지 구분·댓글·관리자 권한·사진 첨부를 제공합니다. 댓글 수정, 비밀번호 재설정, 이메일 인증, 글 신고 접수함, 알림, 자동 AI 분석·집계는 이번 구현 범위에 포함하지 않습니다. 여기서 **정보 제보**는 여행 정보를 공유하는 게시글 유형입니다.


## 기존 데이터 호환

기존 REVIEW·REPORT 행은 자유 게시판 목록에 함께 포함하고 응답 kind는 GENERAL로 표시합니다. QUESTION은 질문 게시판에 표시합니다. 글·댓글·사진의 ID와 소유권은 유지됩니다. 신규/수정 입력의 REVIEW·REPORT도 GENERAL로 정규화하므로 이전 클라이언트 요청을 거부하지 않습니다. 원래 저장 행을 일괄 변경하는 작업은 없습니다.

관광지 태그는 `destinationId: null`, 빈 문자열 또는 생략으로 작성할 수 있습니다. 지정한 태그는 등록된 관광지인지 검사합니다. MySQL 기존 destination_id는 시작 시 필요한 경우에만 NULL 허용으로 변경합니다. MySQL native enum에 GENERAL이 없으면 시작 시 기존 enum 값을 유지하면서 추가합니다. 이 변경에는 DB 계정의 ALTER 권한이 필요합니다.
