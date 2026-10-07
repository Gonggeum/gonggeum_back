# 인증 기반 개발 결정 기록 — 2026-10-07

## 이번 구현 범위

MySQL 물리 스키마 14개, 약관 조회 A01, 아이디/이메일 중복 확인 A02, 관련 공통 처리와 통합 시험을 구현한다. 가입 A03, 로그인 A04, 갱신 A06, 로그아웃 A07, 내 정보 S01은 다음 기능 단위다. JWT 인증이 없는 상태에서 이 경로들을 허용하지 않는다.

## 물리 스키마

ERD v1.3 컬럼을 기준으로 BIGINT UNSIGNED, DATETIME(6), 명시된 FK 삭제 규칙을 구현했다. ID는 Java long에 축소하지 않고 필요한 읽기에서 BigDecimal → BigInteger → 10진 문자열을 사용한다. 로그인 ID는 ascii_bin, 정규화 이메일은 utf8mb4_0900_as_cs, 객체 키/제공자 subject/멱등 키는 대소문자를 보존하는 비교를 쓴다. DB 기본 문자셋·collation은 기존 utf8mb4 / utf8mb4_0900_ai_ci를 유지한다.

읽기 API는 JdbcClient와 도메인 record로 구현했다. 기존 JPA 설정은 유지하되 엔티티를 생성했다고 주장하지 않는다. 이후 쓰기 도메인에서 JPA를 선택할 경우 unsigned 범위 및 optimistic version 매핑을 먼저 검증해야 한다.

소셜·TOTP·재설정 테이블은 ERD에 따른 기반만 준비했다. 부모 인증 자격의 동일 사용자/목적/순환 방지 등 서비스 규칙은 해당 기능 구현 시 검증한다. 로그인 성공 시 사용자/세션 존재 확인도 서비스에서 필요하다.

감사 테이블은 team/project FK 대상이 없으므로 이번 migration에 부분 제약으로 만들지 않았다. 팀·프로젝트 테이블과 함께 추가하고 첫 업무 쓰기부터 동일 트랜잭션에 기록한다. 멱등 replay와 삭제 워커도 아직 구현하지 않았다.

## 약관 조회

게시 시각이 현재 이하이고 폐기 시각이 없거나 현재 이후인 버전을 대상으로 유형별 최신 published_at, 동일 시각이면 큰 ID를 선택한다. 조회 결과는 ID 오름차순이며 limit은 1~100, 기본 20이다. 필수 약관 변경은 가입 시 다시 검사해야 하며 이번 조회 결과가 가입을 예약하지 않는다.

커서는 terms:v1 목적·마지막 ID·limit·만료를 HMAC-SHA256으로 서명한다. TTL 30분이며 변조, 다른 limit, 만료를 400으로 거절한다. 공개 목록이므로 사용자 바인딩은 없고 보호 목록에는 이 커서를 재사용하지 않는다. 페이지 사이의 게시 변경에 대한 불변 스냅샷은 보장하지 않는다.

운영은 CURSOR_SIGNING_KEY에 32 bytes 이상 난수의 Base64 값을 외부 주입해야 한다. 로컬/테스트는 미설정 시 실행별 난수 키를 사용하므로 앱 재시작 후 이전 커서는 무효다. 운영 다중 인스턴스는 동일 키를 사용한다. 이 키는 JWT 키가 아니다.

## 요청 제한과 웹 인증 경계

두 공개 API는 각각 접속 IP별 분당 60회가 기본이며 PUBLIC_AUTH_RATE_LIMIT로 조정한다. Redis Lua의 INCR/EXPIRE를 원자 실행하고 초과 시 429 및 Retry-After를 반환한다. Redis 장애는 우회 허용하지 않고 503으로 응답한다. 유효성 검사에서 먼저 거절된 요청은 일부 카운트 전에 종료되므로 전체 악성 트래픽 방어는 별도 프록시 제한이 필요하다.

클라이언트 X-Forwarded-For를 임의 신뢰하지 않는다. 실제 프록시 배포 시 신뢰할 프록시 범위와 원 IP 처리를 확정해야 한다. 현재 CORS 허용 목록은 추가하지 않았다.

POST /api/v1/auth/availability는 쿠키 인증과 상태 변경이 없는 공개 조회이므로 해당 메서드·경로만 CSRF 검사에서 제외했다. 다른 쓰기 경로의 CSRF 보호와 미구현 업무 경로 denyAll은 유지한다.

401 코드는 API 명세에 맞춰 기존 UNAUTHORIZED에서 UNAUTHENTICATED로 정정했다. Jackson의 숫자→문자열 강제 변환도 별도로 차단해 DTO 자료형 계약을 지킨다.

## Flyway

11.7.2에서 먼저 11.14.1을 검증했으나 MySQL 8.4 검증 범위 경고가 남았다. 최종 11.20.3으로 고정했다. 공식 MySQLDatabase 소스의 검증 상한은 9.4이며, 실제 사용 중인 MySQL 8.4에서 마이그레이션과 시험을 수행한다.

근거: [Flyway 11.20.3 MySQLDatabase 공식 소스](https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-mysql/src/main/java/org/flywaydb/database/mysql/MySQLDatabase.java), [Flyway MySQL 문서](https://documentation.red-gate.com/fd/mysql-277579322.html).

## 시험 환경

compose.test.yml의 gonggeumi-test 프로젝트를 사용한다. MySQL 13306 / gonggeumi_test, Redis 16379로 개발 3306/6379와 분리한다. MySQL 데이터는 테스트용 tmpfs이고 재생성 시 사라진다. 통합 테스트는 실행 전 DB 이름을 확인하며 fixture는 rollback 또는 자기 생성 데이터만 정리한다.

일반 verify는 외부 인프라 없는 기본/단위 시험을, -Pmysql-it verify는 실제 MySQL/Redis 통합 시험까지 수행한다. Docker 컨테이너가 준비되지 않았으면 통합 시험은 건너뛰지 않고 실패한다.
