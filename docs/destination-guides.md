# 관광지 소개와 방문 가이드

속초관광수산시장의 기존 소개 페이지에 더해 8개 관광지의 소개 페이지를 제공합니다.

| 관광지 | 소개 경로 | 주요 내용 |
|---|---|---|
| 경주 황리단길 | `/destinations/gyeongju` | 한옥 골목, 음식점·카페·공방, 주차 안내 |
| 전주 한옥마을 | `/destinations/jeonju` | 문화유산, 한복·공예 체험, 한옥 숙박 확인 사항 |
| 부산 해운대 | `/destinations/busan` | 해변·구남로, 계절별 이용, 숙박·주차 확인 사항 |
| 제주 동문시장 | `/destinations/jeju` | 특산품, 낮 시장과 야시장, 출입구·포장 확인 사항 |
| 강릉 안목해변 | `/destinations/gangneung` | 커피거리, 해변 산책, 좌석·주차 확인 사항 |
| 대구 서문시장 | `/destinations/seomun` | 직물·생활용품, 낮 시장과 야시장 구분 |
| 여수 낭만포차거리 | `/destinations/yeosu` | 공식 포차와 주변 상가 구분, 식사·밤바다 동선 |
| 담양 죽녹원 | `/destinations/damyang` | 대숲 산책, 입장 확인, 주변 식사와 관람비 구분 |

각 페이지는 장소 소개, 먹거리·체험, 네 분야의 비용·방문 체크, 추천 동선, 방문 정보와 공식 출처를 제공합니다. 추천 동선과 체크리스트는 여행 준비 제안이며, 방문자 후기 통계나 현재 가격 평가가 아닙니다. 실제 후기 데이터와 집계 수는 변경하지 않았습니다.

소개 자료는 `src/main/resources/data/destination-guides.json`에 있습니다. 서버의 `DestinationGuideCatalog`가 읽고 공통 Thymeleaf 템플릿으로 렌더링합니다. 모르는 관광지 소개 경로는 404를 반환합니다. 페이지의 종합 후기 링크는 `/?destination={slug}#report`이며, 홈에서 해당 관광지를 선택합니다. 잘못된 선택값은 기본 관광지로 돌아갑니다.

검토 후기가 없는 관광지에서도 해당 관광지의 소개를 볼 수 있습니다. 속초 전체 후기 버튼은 속초를 선택한 경우에만 표시합니다. 메인 화면의 모든 관광지 카드에 소개 링크를 제공합니다.

## 출처와 확인 기준

소개 정보 확인일: 2026-10-08. 가변적인 가격과 운영시간을 고정하지 않고, 방문 전에 공식 공지와 해당 업체를 확인하도록 안내합니다.

- 경주시 황리단길 안내: https://www.gyeongju.go.kr/hwangridan/index.nm
- 전주시 한옥마을: https://hanok.jeonju.go.kr/contents/info
- 해운대구 문화관광: https://www.haeundae.go.kr/tour/index.do
- 제주관광공사 동문시장: https://www.visitjeju.net/kr/detail/view?contentsid=CONT_000000000500745
- 한국관광공사 안목해변 소개: https://access.visitkorea.or.kr/cos/detail.do?cotId=fbaf1a47-ebd9-4e3a-ad13-c835086b39e5
- 서문야시장 공식 안내: https://www.nightseomun.com/
- 여수시 낭만포차: https://www.yeosu.go.kr/tour/leisure/bambada/gun_carriage
- 죽녹원: https://www.juknokwon.go.kr/

## 검증

- `./mvnw test`: 공개 접근, 8개 소개 렌더링, 관광지별 후기 링크, 미등록 경로 404를 기존 기능 테스트와 함께 검증합니다.
- `node src/test/js/destination-guides.test.cjs`: 8개 직접 선택, 9개 카드 링크, 관광지별 빈 결과 안내, 전체 연도와 잘못된 선택값을 검증합니다.
