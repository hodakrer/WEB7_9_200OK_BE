# WindFall - 네덜란드식 경매 기반 전국 중고 거래 플랫폼
<img width="1256" height="446" alt="image" src="https://github.com/user-attachments/assets/b151f22b-e990-4fa9-b157-502f7833fb52" />

시간이 지날수록 가격이 내려가는 하락형 경매로, 중고 거래의 '협상'을 '선택의 타이밍'으로 바꾼 서비스입니다.
6인 팀 프로젝트(2025.12.03 ~ 2026.01.07)이며, 이 저장소는 **제가 담당한 영역을 정리한 개인 기록**입니다.

- 원본 팀 저장소: https://github.com/prgrms-web-devcourse-final-project/WEB7_9_200OK_BE
- 시연 영상: [YouTube](https://youtu.be/UX1E70MlFtU)

<br>

---
<br>

## 담당 영역

| 구분 | 내용 |
| --- | --- |
| **결제 · 정산 도메인** | Toss Payments 연동, 승인 동시성 제어, 재시도 정책, 결제 상태 미확정 객체에 대한 상태 확정 배치 스케줄러 |
| **인증 도메인** | OAuth2 소셜 로그인 연동 (Naver / Google / Kakao), JWT 발급 및 Refresh Token 회전 |

6인 팀에서 도메인을 나눠 맡았고, 인증 도메인을 먼저 담당한 뒤 결제·정산 도메인을 맡았습니다.
결제 도메인은 PG 연동부터 동시성 제어, 사후 정산 배치까지 제가 끝까지 담당했습니다.
<br>


### 핵심 기여

- 조건부 UPDATE와 UNIQUE 제약으로 **동시 요청 100건에서 중복 승인 0건** 달성
- 지수 백오프 + Full Jitter 재시도로 승인 실패의 일시적 원인과 확정적 원인을 분리
- Spring Batch로 **PROCESSING 상태로 남은 거래를 PG 기준으로 사후 보정**하는 배치 구성

<br>


## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| Language / Framework | Java 21, Spring Boot 3.5.8, Spring Security, Spring Batch |
| Persistence | JPA, JPQL, MySQL 8.0 |
| External | Toss Payments, OAuth2 (Naver / Google / Kakao) |
| Test | JUnit 5, MockWebServer, JMeter |
| Infra | Docker, AWS EC2, GitHub Actions |

<br>

---

<br>


## 📌 주요 기능

1. Oauth 기반 소셜 로그인 (네이버, 구글, 카카오)
2. Redis ZSet 기반 실시간 인기 랭킹
3. WebSocket 기반 실시간 사용자 집계 및 판매자 감정 표현 (이모지)
4. WebSocket 기반 자동 가격 하락 및 경매 상태 변경
5. 경매 찜 및 태그 검색
6. 경매 검색 (제목 + 내용)
7. Toss Payments 기반 결제
8. WebSocket 기반 채팅
9. 마이페이지
10. SSE 기반 알림

---

<br>

## 아키텍처 · ERD

<img width="3208" alt="System Architecture" src="https://github.com/user-attachments/assets/387d0a7f-5569-4fc8-8947-2229496e7611" />

<img width="1099" alt="ERD" src="https://github.com/user-attachments/assets/5e5226f8-18b1-454e-810e-5d4683aaf129" />

<br>

---

<br>


## 문제 해결 기록

결제 승인은 **사전 차단 → 실시간 재시도 → 사후 보정**의 세 층으로 방어했습니다.
아래 세 기록은 그 순서대로 이어집니다.

<br>

### 1. 동일 결제 요청의 중복 승인 차단

**문제**
결제 승인 요청이 짧은 간격으로 중복 도달할 때(버튼 연타, 네트워크 재시도) 같은 거래에 승인이 두 번 일어날 수 있었습니다.

**원인**
승인 전 상태를 조회하고 조건이 맞으면 승인하는 구조라, 두 요청이 모두 "아직 승인 전"을 읽는 구간이 존재했습니다.

**선택**
- 조건부 UPDATE로 상태 전이를 원자화하고, 갱신된 행이 0이면 후행 요청으로 판단해 `409 PAYMENT_REQUEST_LATE` 반환
- UNIQUE 제약을 최후 방어선으로 추가
- 승인 직전 주문번호와 결제 금액을 서버가 보관한 값과 대조해, 클라이언트가 전달한 금액을 신뢰하지 않도록 구성
- 대안 검토: 비관적 락은 외부 PG 호출 구간까지 락이 유지되어 제외, Redis 분산 락은 단일 DB 환경에서 인프라 추가 대비 이득이 없다고 판단

**검증**
- JMeter 100 스레드 동시 요청, 3회 반복 실행
- 승인 성공 1건 / 409 반환 99건 / **중복 승인 0건**
- SQL 집계와 애플리케이션 로그로 교차 확인

<br>

### 2. 결제 승인 재시도의 신뢰 범위

**문제**
PG 승인 요청은 네트워크 사정으로 실패할 수 있습니다. 바로 실패 처리하면 복구 가능한 건까지 잃고, 무조건 재시도하면 승인이 중복될 위험이 있습니다.

**원인**
재시도가 안전하려면 두 조건이 필요합니다. 같은 요청이 여러 번 도달해도 결과가 같아야 하고, 재시도가 원래의 실패 원인을 키우지 않아야 합니다.

**선택**
- **멱등성**: 승인 요청에 `Idempotency-Key` 헤더를 추가해 재시도가 중복 승인으로 이어지지 않도록 했습니다
- **Exponential Backoff + Full Jitter**: 지수 백오프만 쓰면 동시에 실패한 요청들이 같은 시각에 다시 몰려 부하를 재생산합니다. 대기 시간을 무작위로 흩어 재시도 파동을 분산시켰습니다
- `BackoffStrategy`를 인터페이스로 분리해 정책 교체와 테스트가 가능한 구조로 두었습니다

**한계와 개선점**
- 멱등키로 토스가 발급한 `paymentKey`를 사용했습니다. 재시도 간 값이 같아 멱등성 자체는 동작하지만, 멱등키는 *요청*을 식별하는 값이고 `paymentKey`는 *리소스*를 식별하는 값이라 역할이 어긋납니다. 다시 만든다면 거래 생성 시점에 서버가 UUID를 발급해 저장하고, 재시도마다 그 값을 사용하겠습니다
- 응답 타임아웃은 이 루프에서 잡히지 않습니다. **재시도로 막을 수 없는 실패가 남는다는 점이 아래 정산 배치를 만든 직접적인 이유입니다**

<br>

### 3. PROCESSING 상태로 남은 거래의 사후 보정 배치

**문제**
승인 요청 후 응답을 받지 못하면(타임아웃, 서버 재시작) 거래가 PROCESSING 상태로 남았습니다. PG에는 승인이 끝났는데 서비스 DB만 중간 상태인 불일치가 발생할 수 있었습니다.

**원인**
승인 요청과 결과 반영이 서로 다른 시스템에서 일어나기 때문에, 그 사이가 끊기면 어느 쪽이 사실인지 서비스가 스스로 판단할 수 없었습니다.

**선택**
- **Reader**: `JpaCursorItemReader`. 페이징 방식은 처리 도중 대상 상태가 바뀌면 OFFSET이 밀려 누락이 생깁니다. 커서는 단일 트랜잭션이라 수평 확장이 어렵다는 대가가 있으나, 현재 처리량에서는 문제되지 않는다고 판단했습니다
- **트랜잭션 경계**: 외부 API 호출을 `@Transactional` 밖에 두었습니다. 안에 두면 HTTP 대기 시간만큼 커넥션을 점유해 풀이 고갈됩니다
- **Processor → Writer**: 엔티티 대신 `FinalizationCommand` record로 전달해 청크 경계에서 detached 엔티티를 다루는 문제를 피했습니다
- **멱등키 없음**: 상태 조회는 `GET`이며, 토스페이먼츠는 POST 외 메서드의 멱등성을 자체 보장하고 GET에 붙인 멱등키 헤더는 무시하므로 별도 키를 두지 않았습니다
- **스케줄링**: `fixedDelay`로 이전 실행이 끝난 뒤 5분. 실행 중첩을 구조적으로 차단했습니다

**검증** — MockWebServer + `@SpringBootTest` 통합 테스트 (로컬)
- 상태 반영: `IN_PROGRESS` → 변경 없음 / `DONE` → 거래 완료 / `ABORTED` → 결제 실패
- 내구성: PG가 500을 반환해도 배치는 정상 종료되고 해당 건만 미처리로 남음
- 내구성: Payment 레코드가 없으면 PG 호출 없이 통과
- 멱등성: 동일 대상 2회 실행 시 2회차는 조회·쓰기 모두 발생하지 않음

**남은 한계**
대상 조건이 상태값뿐이라 경과 시간 필터가 없습니다. 진행 중인 정상 결제도 5분마다 조회 대상에 포함되고, Payment 레코드가 아예 없는 거래는 계속 PROCESSING으로 누적됩니다.

<br>

---

<br>


### 전체 기능
<details>
1. OAuth2 기반 소셜 로그인 (Naver / Google / Kakao) — **담당**
2. Toss Payments 기반 결제 및 정산 배치 — **담당**
3. Redis ZSet 기반 실시간 인기 랭킹
4. WebSocket 기반 자동 가격 하락 및 경매 상태 변경
5. WebSocket 기반 실시간 사용자 집계 및 판매자 이모지 표현
6. WebSocket 기반 채팅
7. 경매 찜, 태그 검색, 제목·내용 검색
8. SSE 기반 알림
9. 마이페이지

</details>

<details>
<summary><b>팀 구성 및 협업 방식</b></summary>

<br>

**기간** 2025.12.03 ~ 2026.01.07 (5주)
**구성** 6인 (PO 1명, 백엔드 5명)

**브랜치 전략** — GitHub Flow 기반
`develop`을 기준 브랜치로 두고 이슈 단위로 `feature/`, `fix/`, `refactor/` 브랜치를 생성했습니다.
`main`과 `develop`은 브랜치 보호 규칙을 적용해 **PR과 팀원 2명 이상의 리뷰 승인을 거쳐야 머지**할 수 있도록 했습니다.

**커밋 컨벤션** — `타입 : 작업내용` 형식
`feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `remove`, `rename`

</details>

<br>

---

