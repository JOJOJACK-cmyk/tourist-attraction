# 로컬 LLM 후기 경험 추출

## 포함된 파일

- `src/main/resources/prompts/review-extraction-system.txt`: 소비 판단 중심 시스템 프롬프트.
- `src/main/java/com/travelprice/service/ReviewPromptProvider.java`: 리소스를 UTF-8로 읽고 system/user 메시지를 만든다. JAR로 실행해도 클래스패스에서 읽는다.

후기마다 경험을 먼저 추출하고, 원본 중복 제거 후 상품·장소·조건별로 종합하는 흐름을 위한 준비다. 프롬프트와 입력 메시지 준비까지만 구현했다. Ollama 호출, 모델 응답 검증, 중복 제거, 경험 집계와 화면 연결은 아직 구현하지 않았다. 기존 화면은 수동 검토 자료를 사용한다.

## Java에서 사용

`ReviewPromptProvider`를 생성자 주입받아 사용한다.

```java
var messages = reviewPromptProvider.messages(
    "속초관광수산시장",
    "review-001",
    LocalDate.of(2026, 9, 1),
    reviewBody
);
```

`messages`에는 `role=system`인 시스템 프롬프트와 `role=user`인 후기 입력 JSON이 순서대로 들어 있다. 향후 모델 클라이언트가 이 메시지를 전달한다. 작성일이 없으면 `null`을 전달한다. `sourceId`는 내부 출처 ID이며 작성자 계정명을 사용하지 않는다. 본문은 ObjectMapper로 JSON 직렬화해 따옴표·줄바꿈을 보존한다.

## 입력 예시 — 가상 후기

아래 내용과 날짜는 동작 설명용으로 작성한 가상 자료이며 실제 속초시장 후기나 사이트 집계에 포함하지 않는다.

```json
{
  "destination": "속초관광수산시장",
  "source_id": "example-001",
  "published_at": "2026-09-01",
  "body": "시장에서 술빵을 샀다. 크기가 커서 둘이 나눠 먹기에 충분했고 지불한 가격에도 만족했다."
}
```

가능한 출력 예시:

```json
{
  "experiences": [
    {
      "source_id": "example-001",
      "topic": "value_satisfaction",
      "sentiment": "positive",
      "experience_type": "direct",
      "place": "속초관광수산시장",
      "item": "술빵",
      "summary": "둘이 나눠 먹기에 충분한 양과 가격에 만족했다는 경험",
      "condition": "두 사람이 나눠 먹음",
      "reason": "크기와 양이 충분하고 지불한 가격에 만족함",
      "alternative": null,
      "check_before_purchase": null,
      "published_at": "2026-09-01",
      "visited_at": null,
      "support": "작성자는 술빵의 양이 둘이 먹기에 충분했고 가격에도 만족했다고 설명했다.",
      "uncertainties": ["가게명과 실제 지불 금액은 확인되지 않음"]
    }
  ],
  "limitations": ["방문일이 명시되어 있지 않음"]
}
```

“맛있다”처럼 소비 판단과 연결되지 않은 글에는 `{"experiences":[],"limitations":[]}`를 반환할 수 있다. 반복 기준 3건은 화면 분류 기준이다. 같은 글·재게시물·전언을 서로 다른 직접 경험으로 집계하지 않는다.

## 모델 연결 시 확인할 점

프롬프트는 요청이지 결과 보증이 아니다. 연결 단계에서는 JSON 파싱, 허용된 주제·감정 값, 입력 출처 ID 일치, 익명 처리, 날짜와 근거를 검증해야 한다. 실패한 응답을 정상 분석처럼 화면에 표시하지 않는다. 실제 로컬 모델을 사용한 추출 품질 검증은 아직 하지 않았다.
