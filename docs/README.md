# 공금이 설계 문서

2026-10-07 확인 시 IntelliJ 백엔드 프로젝트에는 ERD/API 원문이 없었다. 이 폴더에 원본 사본을 포함했다. 최상위 README의 외부 경로에 의존하지 않고 아래 문서를 사용한다.

| 문서 | 용도 |
| --- | --- |
| [개발 순서](DEVELOPMENT_PLAN.md) | 구현 단계, API 범위, 선행 조건, 완료 기준 |
| [기능명세 v3.3](requirements/공금이_웹앱_기능명세서_v3.3.md) | 업무 정책의 우선 기준 |
| [ERD 테이블 명세 v1.3](erd/공금이_ERD_테이블_명세서_v1.3.md) | 36개 테이블, FK, CHECK, 트랜잭션 규칙 |
| [ERD 전체도 v1.3](erd/공금이_ERD_전체도_v1.3.png) | 도메인 관계도 |
| [API 명세 v1.0](api/공금이_API_명세서_v1.0.md) | 인증·권한·업무 계약 및 API 색인 |
| [OpenAPI v1.0](api/공금이_OpenAPI_v1.0.json) | 도구 연동용 OpenAPI 3.1.1 |
| [기존 API 문서 검증 결과](api/검증_결과.json) | 문서 생성 시 구조 검사 결과. 서버 시험 결과가 아님 |
| [사본 확인 기록](SOURCE_MANIFEST.json) | 원본 파일명·사본 위치·SHA-256 일치 확인 |

원본은 내용과 해시를 보존해 복사했다. 원문 안에는 작성 당시 Downloads/작업 폴더의 절대 링크가 남아 있을 수 있으므로 프로젝트 내 이동에는 이 색인을 사용한다. 화면설계 PDF는 이번 백엔드 문서 묶음에 포함하지 않았다.

ERD 사본을 넣은 것은 DB 테이블 생성과 다르다. 업무 DDL/API는 개발 순서에 따라 별도로 구현해야 한다. API 문서의 설계 제안은 확정 업무 정책과 구분한다.

현재 구현: [인증 기반 작업 보고서(텍스트)](reports/2026-10-07-auth-foundation.txt) · [구현 결정 기록](ADR_001_AUTH_FOUNDATION.md) · [ERD 컬럼 대조](reports/2026-10-07-schema-verification.json)
