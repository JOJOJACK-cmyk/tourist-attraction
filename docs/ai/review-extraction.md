# 로컬 LLM 후기 경험 추출

## 포함된 파일

- `src/main/resources/prompts/review-extraction-system.txt`: 지출·방문·이용 판단 중심 시스템 프롬프트.
- `src/main/java/com/travelprice/service/ReviewPromptProvider.java`: 리소스를 UTF-8로 읽고 system/user 메시지를 만든다. JAR로 실행해도 클래스패스에서 읽는다.

후기마다 경험을 먼저 추출하고, 원본 중복 제거 후 상품·장소·조건별로 종합하는 흐름을 위한 준비다. Ollama 호출과 단일 후기 분석 화면을 연결했다. 기존 관광지 정보는 수동 검토 자료를 유지한다. 원본 중복 제거와 여러 후기의 자동 집계는 아직 구현하지 않았다.

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

`messages`에는 `role=system`인 시스템 프롬프트와 `role=user`인 후기 입력 JSON이 순서대로 들어 있다. `OllamaReviewClient`가 이 메시지를 로컬 모델에 전달한다. 작성일이 없으면 `null`을 전달한다. `sourceId`는 내부 출처 ID이며 작성자 계정명을 사용하지 않는다. 본문은 ObjectMapper로 JSON 직렬화해 따옴표·줄바꿈을 보존한다.

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
      "category": "food",
      "topic": "value_satisfaction",
      "sentiment": "positive",
      "experience_type": "direct",
      "place": "속초관광수산시장",
      "item": "술빵",
      "summary": "둘이 나눠 먹기에 충분한 양과 가격에 만족했다는 경험",
      "condition": "두 사람이 나눠 먹음",
      "reason": "크기와 양이 충분하고 지불한 가격에 만족함",
      "alternative": null,
      "check_before_visit": null,
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

프롬프트는 요청이지 결과 보증이 아니다. 클라이언트는 JSON 파싱, 필수 필드·허용된 영역·주제·감정 값, 입력 출처 ID 일치, 작성일 일치와 방문일 형식을 검증한다. 본문 내용의 정확성·근거의 충실함·닉네임 누출 여부는 구조 검증만으로 보장하지 못한다. 실패한 응답을 정상 분석처럼 화면에 표시하지 않는다. 실제 로컬 모델을 사용한 추출 품질 검증은 아직 하지 않았다.

## 음식점 외 판단 범위

영역과 판단 주제를 분리한다. 같은 주차 후기라도 요금 만족, 이동 불편, 만차 대기는 서로 다른 경험이다. 무료 화장실·휴식 공간 등도 비용이 없다는 이유로 제외하지 않는다.

| 영역 | category | 추출할 경험 예시 |
|---|---|---|
| 숙박 | lodging | 추가 요금, 청결, 소음, 응대, 예약 내용과 실제 차이 |
| 주차 | parking | 요금·할인 안내, 정산, 만차, 목적지까지 거리 |
| 교통 | transport | 택시·버스 이용 비용, 대기, 환승, 이동 편의 |
| 입장·체험 | admission | 비용 대비 만족, 대기, 실제 이용 시간, 별도 유료 항목 |
| 쇼핑 | shopping | 가격 대비 품질·구성, 구매 후회, 안내와 실제 차이 |
| 대여 | rental | 요금·추가 비용, 장비 상태, 이용 조건 |
| 편의시설 | amenities | 화장실 청결, 쉴 곳, 유아차·휠체어 이용 편의 |
| 음식 | food | 가격 대비 만족, 대기 후 후회, 포장 조건 |

표의 내용은 분류 예시다. 특정 관광지에서 실제 발생한 문제로 취급하지 않는다. 시장 단위 분석과 도시 단위 분석의 포함 범위를 구분하며, 관련 없는 주변 숙박 후기를 시장의 평가로 합치지 않는다.

### 비음식 후기 입력 예시 — 가상 자료

```json
{
  "destination": "예시 관광지",
  "source_id": "example-parking-001",
  "published_at": "2026-09-01",
  "body": "주차장에서 관광지 입구까지 오르막이 길어서 유아차를 끌고 이동하기 힘들었다. 현장 화장실은 깨끗해서 만족했다."
}
```

이 글에서는 `category=parking, topic=access_convenience, sentiment=negative`와
`category=amenities, topic=facility_condition, sentiment=positive`를 따로 추출할 수 있다.
주차 요금·방문일·다른 이동 수단은 언급이 없으므로 만들지 않는다.
`check_before_visit`에는 해당 경험에 근거한 방문 전 확인 사항을 넣는다.
