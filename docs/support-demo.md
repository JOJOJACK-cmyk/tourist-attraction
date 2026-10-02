# 토스페이먼츠 후원 결제

## 로컬 설정

토스페이먼츠 개발자센터에서 본인 상점의 **API 개별 연동 키**를 발급합니다. 결제위젯 전용 gck/gsk 키 대신 이 구현에 맞는 ck/sk 키를 사용합니다. 키를 코드나 GitHub에 넣지 않습니다.

PowerShell에서 앱을 실행할 터미널에 설정하세요.

```powershell
$env:TOSS_PAYMENT_MODE="TEST"
$env:TOSS_CLIENT_KEY="본인의 test_ck_ 클라이언트 키"
$env:TOSS_SECRET_KEY="본인의 test_sk_ 시크릿 키"
.\mvnw.cmd spring-boot:run
```

IntelliJ 실행이라면 Run Configuration → Environment variables에 같은 세 변수를 등록하고 재시작합니다. 클라이언트 키는 결제창에 전달되고 시크릿 키는 서버에만 남습니다. 테스트 키에서는 실제 청구되지 않습니다.

## 확인 순서

1. `http://localhost:8080/support`에서 금액 선택 → 토스 결제창 이동.
2. 결제창 인증 후 `/support/success`에서 서버 승인 확인.
3. 완료 새로고침 시 동일 주문 완료 유지. DB에는 TEST/LIVE 구분 저장.
4. 결제창 닫기·실패 시 완료로 표시되지 않는지 확인.
5. 다른 브라우저 세션에서 주문에 접근하면 404. 금액 변조는 400.

실제 키가 없는 자동 테스트는 HTTP 가짜 결제사와 서비스 대역으로 검증합니다. 이것은 본인 토스 계정으로 실제 결제창 승인을 검증한 것과 구분합니다.

## 구현

- `GET /api/support/orders/config`: 공개 클라이언트 키와 모드, 사용 가능 여부.
- `POST /api/support/orders`: 서버 금액과 주문번호 저장. CSRF 필요.
- `POST /api/support/orders/{id}/confirm`: CSRF·세션·금액·결제키 확인 후 `/v1/payments/confirm` 호출.
- 결제사 응답의 DONE, orderId, paymentKey, totalAmount, currency가 전부 맞아야 SUCCEEDED.
- 승인 요청 전 결제키를 DB에 저장합니다. 타임아웃·통신 장애는 완료나 실패로 단정하지 않습니다. 같은 주문과 멱등키로 재시도하고 주문 조회 API로 승인 여부를 복구합니다.
- 기존 `/{id}/result` 모의 API는 제거. 기존 DB의 paymentMode 없는 모의 주문은 실제 승인에 사용할 수 없습니다.
- 기존 `support_demo_order` 테이블 이름은 이전 데이터 보존을 위해 유지하고 JPA가 payment_mode/payment_key/confirmation_started_at 컬럼을 추가합니다.
- 카드 통합 결제창을 사용하며 카드 정보는 토스에서 입력합니다. 후원은 게시판·채팅 이용 권한과 무관합니다.

## 실제 결제 운영

본인 상점의 라이브 결제 사용 가능 상태와 후원 결제 취급을 토스에 확인한 후 `TOSS_PAYMENT_MODE=LIVE`, 대응하는 `live_ck_`/`live_sk_`를 설정합니다. 도메인은 HTTPS로 운영합니다. 결제사 심사나 계약 없이 키 설정만으로 실제 수납이 가능하다고 가정하지 않습니다.

이 변경은 일회성 결제창·승인 흐름입니다. 관리자 환불 UI, 웹훅 기반 취소 상태 동기화, 운영 정산 대사는 포함하지 않습니다. 운영 전에는 이 흐름을 추가하고, 응답 유실 주문은 개발자센터 거래 내역과 대조해야 합니다. 승인 확인 오류가 나면 새 주문을 만들기 전에 같은 주문의 승인 결과를 확인합니다. 시크릿 키를 브라우저·로그·저장소에 공개하지 않습니다.

공식 문서: https://docs.tosspayments.com/sdk/v2/js/payment · https://docs.tosspayments.com/reference · https://docs.tosspayments.com/reference/using-api/authorization
