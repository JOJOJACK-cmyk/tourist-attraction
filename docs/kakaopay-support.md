# 카카오페이 후원 결제

## 개발자센터 설정

1. https://developers.kakaopay.com 에서 로그인 후 애플리케이션을 생성합니다.
2. 애플리케이션 기본 정보에서 **Secret key(dev)**를 발급합니다.
3. 플랫폼 → Web → 사이트도메인에 `http://localhost:8080`을 등록합니다. 앱을 실제로 여는 주소와 서버 환경변수 PAYMENT_SITE_URL이 일치해야 합니다. localhost와 127.0.0.1을 섞지 마세요.
4. IntelliJ Run Configuration → Environment variables에서 아래 변수를 설정하고 앱을 재시작합니다.

```text
KAKAOPAY_SECRET_KEY=본인이 발급받은 Secret key(dev)
KAKAOPAY_PAYMENT_MODE=TEST
KAKAOPAY_CID=TC0ONETIME
PAYMENT_SITE_URL=http://localhost:8080
```

PowerShell 실행이라면:

```powershell
$env:KAKAOPAY_SECRET_KEY="본인의 Secret key(dev)"
$env:KAKAOPAY_PAYMENT_MODE="TEST"
$env:KAKAOPAY_CID="TC0ONETIME"
$env:PAYMENT_SITE_URL="http://localhost:8080"
.\mvnw.cmd spring-boot:run
```

개발용 키는 DEV로 시작하는 Secret key(dev)를 사용합니다. 카카오 로그인 REST API 키/Client Secret/Admin 키나 이전 토스 키로는 연결되지 않습니다. API 키를 소스·GitHub·채팅에 붙이지 않습니다.

## 결제 확인

`/support` → 1,000/3,000/5,000원 선택 → 카카오페이 결제 페이지 → 카카오톡 인증 → 사이트로 복귀 → 서버 승인 확인 순서입니다. TEST는 실제 청구 없이 결제사의 준비·승인 API를 사용하는 환경입니다.

서버가 보내는 콜백:

- 성공: `http://localhost:8080/support/success?orderId=주문번호` (카카오페이가 pg_token 추가)
- 취소: `http://localhost:8080/support/cancel?orderId=주문번호`
- 실패: `http://localhost:8080/support/fail?orderId=주문번호`

사이트도메인은 위 주소의 origin을 등록합니다. 결제 화면이 열리지 않으면 키 종류, CID, 사이트도메인, 포트부터 확인합니다. 다른 컴퓨터/휴대폰에서 직접 사이트를 이용할 때는 접근 가능한 도메인을 등록하고 PAYMENT_SITE_URL도 맞춥니다. 전화번호·카드 정보 입력과 인증은 카카오페이 화면에서 진행되며 사이트에는 저장하지 않습니다.

## 구현 및 검증 범위

- 공식 새 API `https://open-api.kakaopay.com/online/v1/payment/{ready,approve,order}`와 `Authorization: SECRET_KEY …`를 사용합니다.
- 주문 금액과 익명 UUID 사용자 식별자는 서버에서 정합니다. ready의 TID 및 PC/모바일 URL을 DB에 저장합니다. 허용된 카카오 도메인의 HTTPS URL로만 이동합니다.
- confirm은 pg_token만 받습니다. TID·CID·금액·사용자 식별자를 브라우저에서 받지 않습니다. 승인 결과 모두가 저장된 주문과 맞아야 완료됩니다.
- 주문 조회 시 `SUCCESS_PAYMENT`만 완료로 인정합니다. 취소/실패 URL 방문만으로 DB 완료나 환불 처리를 하지 않습니다.
- 준비·승인은 DB 행 잠금으로 동일 주문의 동시 요청을 직렬화합니다. 승인 전 시도와 토큰 SHA-256 해시를 저장하고 원문 토큰은 DB에 저장하지 않습니다.
- 승인 전 주문 조회로 이미 승인된 거래를 복구합니다. approve 응답이 유실되어도 order API로 다시 확인합니다. 불확실한 응답은 완료/실패로 단정하지 않고 같은 주문으로 재확인합니다. 새로고침에도 pg_token이 남아 있으므로 같은 승인 요청을 재시도할 수 있습니다. 완료 뒤 URL에서 토큰을 제거하고, 이후 `/reconcile`로 저장된 TID를 조회합니다.
- CSRF·동일 HTTP 세션 검사를 유지합니다. 결제 중 로그아웃하거나 세션이 사라지면 해당 주문에 접근할 수 없습니다. 같은 주문 응답 유실 시 새 결제를 시작하기 전에 승인 내역을 확인하세요.
- 기존 support_demo_order 테이블과 이전 데이터는 보존하며 JPA update로 새 nullable 컬럼을 추가합니다. provider가 KAKAOPAY인 주문만 이 승인 흐름을 사용할 수 있습니다. 이전 토스/모의 결과는 LEGACY로 구분합니다.
- 자동 테스트는 가짜 HTTP 결제사·서비스 대역과 UI 환경에서 실행합니다. 본인 개발용 키로 카카오톡 인증 및 승인까지 완료한 실제 테스트와 구분합니다.

## 운영 결제

실제 수납은 카카오페이 제휴와 운영 CID/Secret key가 필요합니다. 후원 결제의 취급 가능 여부를 확인한 뒤 LIVE 모드와 운영 키/CID, HTTPS PAYMENT_SITE_URL을 설정합니다. TC0ONETIME 또는 개발 키로 LIVE 결제를 시작할 수 없습니다. 계약 없이 실제 수납이 가능한 기능이라고 설명하지 않습니다.

이번 구현은 일회성 결제 준비·승인·승인 응답 유실 복구입니다. 관리자 환불 UI, 취소 웹훅 동기화, 자동 정산 대사는 포함하지 않습니다. 운영 전에 해당 흐름을 추가하고 불확실한 주문은 결제사 거래 내역과 대조합니다. 승인 콜백 쿼리의 pg_token이 접근 로그에 남지 않도록 배포 서버 로그 설정도 확인합니다.

공식 문서: https://developers.kakaopay.com/docs/payment/online/single-payment · https://developers.kakaopay.com/docs/payment/online/payment-detail · https://developers.kakaopay.com/docs/getting-started/applications/basic-info
