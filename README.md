# gonggeum_back

26-2 창업동아리 프로젝트 팀 공금이의 백엔드 저장소입니다.

Java 21 / Spring Boot 3.5.16 / MySQL 8.4 / Redis 7.4 / Flyway 11.20.3을 사용합니다.

## 현재 구현 범위

- 인증·파일 참조·공통 기록 기반 테이블 14개, Flyway 마이그레이션 V1/V2
- A01 `GET /api/v1/terms`: 현재 약관 조회, 서명 커서 페이징
- A02 `POST /api/v1/auth/availability`: 아이디/이메일 정규화 및 중복 확인
- 공통 응답·오류·요청 ID·엄격한 JSON 입력·Redis 요청 제한
- 기본/단위 테스트 10개와 MySQL·Redis 통합 테스트 12개

가입·로그인·JWT·팀·프로젝트·거래 API는 아직 구현하지 않았습니다. 상태 확인과 A01/A02만 공개하고, 나머지 업무 경로는 차단합니다. 회원가입(A03)과 세션 인증이 다음 작업입니다. DB에는 임의 계정이나 실제 약관 전문을 넣지 않았습니다.

## 로컬 실행

JDK 21과 Docker Desktop(Linux containers)이 필요합니다. IntelliJ에서 `pom.xml`을 열고 Project SDK/Maven Runner를 21로 지정합니다. 별도 Maven 설치는 필요하지 않습니다.

프로젝트 폴더의 PowerShell에서:

```powershell
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

macOS/Linux에서는 `./mvnw spring-boot:run`을 사용합니다. 최초 실행 시 의존성 및 이미지 다운로드가 필요합니다. Flyway가 테이블을 생성하고 재실행 시 checksum을 검증합니다.

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod http://localhost:8080/api/v1/terms
Invoke-RestMethod http://localhost:8080/api/v1/auth/availability -Method Post -ContentType 'application/json' -Body '{"field":"email","value":" Test@Example.com "}'
```

약관이 없으면 200/빈 목록을 반환합니다. 앱 종료는 Ctrl+C, 개발 DB 중지는 `docker compose stop`입니다. 데이터는 전용 볼륨에 유지됩니다.

| 서비스 | 로컬 접속 | 계정 / 비밀번호 |
| --- | --- | --- |
| MySQL | `127.0.0.1:3306/gonggeumi` | `gonggeumi` / `local-only-password` |
| Redis | `127.0.0.1:6379` | 로컬 전용, 인증 없음 |
| API | `localhost:8080` | 아직 JWT 미구현 |

Compose의 값은 로컬 개발용 공개 기본값입니다. 컨테이너 포트는 loopback에만 노출합니다. 운영 접속 정보로 사용하지 않습니다.

## 테스트와 빌드

외부 인프라 없는 기본/단위 테스트:

```powershell
.\mvnw.cmd verify
```

실제 MySQL·Redis 통합 테스트:

```powershell
docker compose -f compose.test.yml up -d --wait
.\mvnw.cmd -Pmysql-it verify
docker compose -f compose.test.yml stop
```

테스트 MySQL은 13306/gonggeumi_test, Redis는 16379로 개발 DB와 분리합니다. 테스트 MySQL은 tmpfs이므로 컨테이너를 재생성하면 비워집니다. 통합 테스트 프로필은 외부 저장소가 준비되지 않으면 실패하며 테스트를 생략하지 않습니다.

`verify` 성공 시 실행 JAR는 `target/gonggeumi-api-0.0.1-SNAPSHOT.jar`에 생성됩니다. GitHub Actions도 같은 `-Pmysql-it verify`를 실행합니다. 원격 CI 결과는 각 커밋의 Actions에서 확인합니다.

## 설정과 구현 경계

`application-local.yml`이 기본 개발 설정입니다. `.env`를 Spring Boot가 자동으로 읽지는 않으므로 IDE 또는 셸 환경변수로 값을 주입합니다.

| 변수 | 용도 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 운영은 `prod` 사용, local과 혼용하지 않음 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 운영 MySQL 연결. UTC 및 TLS 검증 설정 필요 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis 연결 |
| `SERVER_PORT` | 기본 8080 |
| `CURSOR_SIGNING_KEY` | 운영 필수: 32 bytes 이상 난수의 Base64, 다중 인스턴스 공통 값 |
| `PUBLIC_AUTH_RATE_LIMIT` | 공개 API별·접속 IP별 분당 요청 수, 기본 60 |

로컬/테스트는 커서 키 미설정 시 실행별 난수 키를 사용하므로 재시작 후 이전 커서는 무효가 됩니다. 이 키는 JWT 키가 아닙니다. Redis 장애 시 공개 API는 요청 제한을 우회하지 않고 503을 반환합니다. 프록시 배포 전 실제 클라이언트 IP 신뢰 경계를 정해야 합니다.

JPA는 `ddl-auto=validate`, OSIV 비활성화입니다. 현재 읽기는 JdbcClient와 record를 사용하며 JPA 엔티티 매핑은 아직 없습니다. 따라서 스키마 무결성은 실제 MySQL 통합 테스트로 확인합니다. 적용된 V1/V2를 수정하지 말고 다음 버전의 마이그레이션을 추가합니다.

## 설계·작업 문서

- [문서 색인](docs/README.md)
- [개발 순서와 완료 기준](docs/DEVELOPMENT_PLAN.md)
- [ERD 테이블 명세 v1.3](docs/erd/공금이_ERD_테이블_명세서_v1.3.md) · [전체도](docs/erd/공금이_ERD_전체도_v1.3.png)
- [API 명세 v1.0](docs/api/공금이_API_명세서_v1.0.md) · [OpenAPI JSON](docs/api/공금이_OpenAPI_v1.0.json)
- [인증 기반 작업 보고서 — 텍스트](docs/reports/2026-10-07-auth-foundation.txt)
- [구현 결정 기록](docs/ADR_001_AUTH_FOUNDATION.md)
- [첫 푸시 검토](docs/reports/2026-10-07-first-push-review.md)

원본 설계 문서의 작성 당시 절대 경로는 보존했으므로 프로젝트 내 문서 탐색은 위 색인을 이용합니다. 현재 상태는 개발 기반 커밋이며 운영 서비스 배포 완료를 뜻하지 않습니다.
